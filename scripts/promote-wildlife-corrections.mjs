#!/usr/bin/env node
/**
 * Copy owner-verified ai_detection_feedback rows into the reference library.
 * This does not run from the app and does not promote anonymous rows.
 *
 *   node scripts/promote-wildlife-corrections.mjs --owner-user-id <android-id>
 *
 * The owner id is the user_id stored on ai_detection_feedback (the phone's ANDROID_ID).
 * Requires SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, and XAI_API_KEY.
 * Apply supabase/migrations/20261104190000_wildlife_reference.sql first.
 */

import { correctLabel, fieldLabelFor, isOwnerCorrection } from "./wildlife-reference/labels.mjs";
import {
  DEFAULT_EMBEDDING_MODEL,
  EMBEDDING_DIMENSIONS,
  RETRIEVAL_PROMPT,
  embedInput,
  parseEmbeddingVector,
} from "../supabase/functions/ai-assistant/reference-library.ts";

const FAST_XAI_MODEL = "grok-4.20-0309-non-reasoning";
const DETECTION_BUCKET = "observation-photos";

function arg(flag) {
  const index = process.argv.indexOf(flag);
  if (index < 0) return "";
  return String(process.argv[index + 1] ?? "").trim();
}

function requiredEnv(name) {
  const value = String(process.env[name] ?? "").trim();
  if (!value) {
    console.error(`Missing ${name}.`);
    process.exit(1);
  }
  return value;
}

const ownerUserId = arg("--owner-user-id") || String(process.env.FIELD_OWNER_USER_ID ?? "").trim();
if (!ownerUserId) {
  console.error("Pass --owner-user-id. Anonymous feedback is not promoted.");
  process.exit(1);
}

const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
const xaiKey = requiredEnv("XAI_API_KEY");
const xaiBase = String(process.env.XAI_BASE_URL ?? "https://api.x.ai/v1").replace(/\/$/, "");
const chatModel = String(process.env.XAI_MODEL ?? FAST_XAI_MODEL).trim() || FAST_XAI_MODEL;
const embedModel = String(process.env.XAI_EMBEDDING_MODEL ?? DEFAULT_EMBEDDING_MODEL).trim() || DEFAULT_EMBEDDING_MODEL;

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
      `/rest/v1/ai_detection_feedback?user_id=eq.${encodeURIComponent(ownerUserId)}&verdict=in.(YES,NO,PARTLY)&select=id,user_id,verdict,corrected_label,model_label,what_image_shows,notes,photo_storage_path&order=created_at.asc`,
      { headers: { Range: `${offset}-${offset + 999}` } },
    );
    if (!response.ok) throw new Error(`Could not read ai_detection_feedback (${response.status}).`);
    const page = await response.json();
    if (!Array.isArray(page) || page.length === 0) break;
    rows.push(...page);
    if (page.length < 1000) break;
  }
  return rows.filter((row) => isOwnerCorrection(row, ownerUserId));
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

async function main() {
  const rows = await loadFeedback();
  console.log(`Owner ${ownerUserId}: ${rows.length} verified correction(s) with a correct label.`);
  let stored = 0;
  for (const row of rows) {
    const label = correctLabel(row);
    const fieldLabel = fieldLabelFor(label);
    let storagePath = "";
    let description = `Owner-verified field correction. Correct label: ${label}. Shows: ${String(row.what_image_shows ?? "").replace(/\s+/g, " ").trim()} Notes: ${String(row.notes ?? "").replace(/\s+/g, " ").trim()}`.trim();
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
        Prefer: "return=minimal,resolution=ignore-duplicates",
      },
      body: JSON.stringify({
        source: "field_correction",
        source_photo_id: row.id,
        species: label,
        common_name: label,
        field_label: fieldLabel,
        license: "owner-verified",
        attribution: "Wildlife FieldOps owner correction",
        source_url: `fieldops://ai_detection_feedback/${row.id}`,
        storage_path: storagePath,
        sign_type: "organism",
        description,
        embedding: `[${embedding.join(",")}]`,
      }),
    });
    if (!response.ok && response.status !== 409) {
      const detail = await response.text();
      console.warn(`Skip ${row.id}: ${detail.slice(0, 200)}`);
      continue;
    }
    stored += 1;
    console.log(`Promoted ${row.id} as ${label} (${fieldLabel})`);
  }
  console.log(`Done. Promoted ${stored}.`);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
