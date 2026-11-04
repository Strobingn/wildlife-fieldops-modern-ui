#!/usr/bin/env node
/**
 * Review ai_detection_feedback, then promote only the ids you pass.
 * user_id is an ANDROID_ID the anon key can write, so it is not owner proof.
 * The default is a dry run. Nothing is inserted unless you pass --apply and --ids.
 *
 *   node scripts/promote-wildlife-corrections.mjs
 *   node scripts/promote-wildlife-corrections.mjs --ids <feedback-id>,<feedback-id>
 *   node scripts/promote-wildlife-corrections.mjs --apply --ids <feedback-id>,<feedback-id>
 *
 * Listing needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.
 * --apply also needs XAI_API_KEY.
 * Apply supabase/migrations/20261104190000_wildlife_reference.sql first.
 */

import {
  candidateLine,
  correctLabel,
  fieldLabelFor,
  parseIdList,
  promotionPlan,
} from "./wildlife-reference/labels.mjs";
import {
  DEFAULT_EMBEDDING_MODEL,
  EMBEDDING_DIMENSIONS,
  RETRIEVAL_PROMPT,
  embedInput,
  parseEmbeddingVector,
} from "../supabase/functions/ai-assistant/reference-library.ts";

const FAST_XAI_MODEL = "grok-4.20-0309-non-reasoning";
const DETECTION_BUCKET = "observation-photos";

function collectIds(argv) {
  const chunks = [];
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === "--ids") chunks.push(argv[index + 1] ?? "");
  }
  return parseIdList(chunks);
}

function requiredEnv(name) {
  const value = String(process.env[name] ?? "").trim();
  if (!value) {
    console.error(`Missing ${name}.`);
    process.exit(1);
  }
  return value;
}

const argv = process.argv.slice(2);
const apply = argv.includes("--apply") && !argv.includes("--dry-run");
const ids = collectIds(argv);
const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");

async function rest(path, init = {}) {
  const response = await fetch(`${supabaseUrl}${path}`, {
    ...init,
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      ...(init.headers ?? {}),
    },
  });
  return response;
}

async function xaiJson(path, body) {
  const xaiKey = requiredEnv("XAI_API_KEY");
  const xaiBase = String(process.env.XAI_BASE_URL ?? "https://api.x.ai/v1").replace(/\/$/, "");
  const response = await fetch(`${xaiBase}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${xaiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const raw = await response.text();
  if (!response.ok) throw new Error(`xAI ${path} HTTP ${response.status}: ${raw.slice(0, 240)}`);
  return JSON.parse(raw);
}

async function embedPassage(text) {
  const embedModel = String(process.env.XAI_EMBEDDING_MODEL ?? DEFAULT_EMBEDDING_MODEL).trim() || DEFAULT_EMBEDDING_MODEL;
  const decoded = await xaiJson("/embeddings", {
    model: embedModel,
    input: embedInput("passage", text),
    dimensions: EMBEDDING_DIMENSIONS,
    encoding_format: "float",
  });
  const vector = parseEmbeddingVector(decoded, EMBEDDING_DIMENSIONS);
  if (!vector) throw new Error(`Embedding was not ${EMBEDDING_DIMENSIONS} dimensions`);
  return vector;
}

async function describeBytes(bytes, mime) {
  const chatModel = String(process.env.XAI_MODEL ?? FAST_XAI_MODEL).trim() || FAST_XAI_MODEL;
  const dataUrl = `data:${mime};base64,${Buffer.from(bytes).toString("base64")}`;
  const decoded = await xaiJson("/chat/completions", {
    model: chatModel,
    temperature: 0,
    max_tokens: 180,
    messages: [{
      role: "user",
      content: [
        { type: "text", text: RETRIEVAL_PROMPT },
        { type: "image_url", image_url: { url: dataUrl } },
      ],
    }],
  });
  return String(decoded?.choices?.[0]?.message?.content ?? "").replace(/\s+/g, " ").trim().slice(0, 1200);
}

async function loadFeedback() {
  const rows = [];
  for (let offset = 0; offset < 20000; offset += 1000) {
    const response = await rest(
      "/rest/v1/ai_detection_feedback?verdict=in.(YES,NO,PARTLY)&select=id,user_id,verdict,corrected_label,model_label,what_image_shows,notes,photo_storage_path&order=created_at.asc",
      { headers: { Range: `${offset}-${offset + 999}` } },
    );
    if (!response.ok) throw new Error(`Could not read ai_detection_feedback (${response.status}).`);
    const page = await response.json();
    if (!Array.isArray(page) || page.length === 0) break;
    rows.push(...page);
    if (page.length < 1000) break;
  }
  return rows;
}

function printCandidates(rows) {
  if (rows.length === 0) {
    console.log("No corrections with a correct label.");
    return;
  }
  console.log("user_id is the phone ANDROID_ID and is not owner proof. Review each photo, then pass --ids.");
  for (const row of rows) console.log(candidateLine(row, supabaseUrl));
}

async function copyPhoto(row) {
  const path = String(row.photo_storage_path ?? "").trim().replace(/^\/+/, "");
  if (!path || path.includes("..")) return null;
  const response = await rest(`/storage/v1/object/${DETECTION_BUCKET}/${path.split("/").map(encodeURIComponent).join("/")}`);
  if (!response.ok) return null;
  const mime = (response.headers.get("content-type") || "image/jpeg").split(";")[0].trim().toLowerCase();
  if (!["image/jpeg", "image/png", "image/webp"].includes(mime)) return null;
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length === 0 || bytes.length > 4_000_000) return null;
  const extension = mime === "image/png" ? "png" : mime === "image/webp" ? "webp" : "jpg";
  const storagePath = `field-correction/${row.id}.${extension}`;
  const upload = await rest(`/storage/v1/object/wildlife-reference/${storagePath}`, {
    method: "POST",
    headers: { "Content-Type": mime, "x-upsert": "true" },
    body: bytes,
  });
  if (!upload.ok) return null;
  const description = await describeBytes(bytes, mime);
  return { storagePath, description };
}

async function promoteRow(row) {
  const label = correctLabel(row);
  const fieldLabel = fieldLabelFor(label);
  let storagePath = "";
  let description = `Reviewed field correction. Correct label: ${label}. Shows: ${String(row.what_image_shows ?? "").replace(/\s+/g, " ").trim()} Notes: ${String(row.notes ?? "").replace(/\s+/g, " ").trim()}`.trim();
  try {
    const copied = await copyPhoto(row);
    if (copied?.description) {
      storagePath = copied.storagePath;
      description = copied.description;
    }
  } catch (error) {
    console.warn(`Photo copy skipped for ${row.id}: ${error.message}`);
  }
  const embedding = await embedPassage(description);
  const response = await rest("/rest/v1/wildlife_reference", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Prefer: "return=representation,resolution=ignore-duplicates",
    },
    body: JSON.stringify({
      source: "field_correction",
      source_photo_id: row.id,
      species: label,
      common_name: label,
      field_label: fieldLabel,
      license: "owner-verified",
      attribution: "Wildlife FieldOps reviewed correction",
      source_url: `fieldops://ai_detection_feedback/${row.id}`,
      storage_path: storagePath,
      sign_type: "organism",
      description,
      embedding: `[${embedding.join(",")}]`,
    }),
  });
  const raw = await response.text();
  if (!response.ok && response.status !== 409) {
    console.warn(`Skip ${row.id}: ${raw.slice(0, 200)}`);
    return false;
  }
  let body = null;
  try {
    body = raw ? JSON.parse(raw) : null;
  } catch {
    body = null;
  }
  const inserted = response.status !== 409 && (Array.isArray(body) ? body.length > 0 : Boolean(body?.id));
  if (!inserted) {
    console.log(`Already in the library: ${row.id}`);
    return false;
  }
  console.log(`Promoted ${row.id} as ${label} (${fieldLabel})`);
  return true;
}

async function main() {
  const rows = await loadFeedback();
  const plan = promotionPlan({ rows, ids, apply });
  if (plan.mode !== "apply") {
    printCandidates(plan.mode === "refused" ? plan.list : plan.list);
    if (plan.unknown.length > 0) console.log(`Not promotable: ${plan.unknown.join(", ")}`);
    if (plan.mode === "refused") {
      console.error(plan.reason);
      process.exit(1);
    }
    console.log("Dry run. Nothing promoted. After you review the photos, rerun with --apply --ids <id>,<id>.");
    return;
  }
  if (plan.unknown.length > 0) {
    console.error(`Not promotable: ${plan.unknown.join(", ")}`);
    console.error("Nothing was promoted.");
    process.exit(1);
  }
  let stored = 0;
  for (const row of plan.promote) {
    if (await promoteRow(row)) stored += 1;
  }
  console.log(`Done. Promoted ${stored}.`);
  if (stored === 0) process.exit(1);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
