#!/usr/bin/env node
/**
 * Seed public.wildlife_reference from research-grade iNaturalist photos.
 *
 * Not part of the Android app. Run manually or from the
 * "Seed wildlife reference library" GitHub workflow.
 *
 *   node scripts/seed-wildlife-reference.mjs
 *   node scripts/seed-wildlife-reference.mjs --dry-run
 *
 * Full run expects 4,650 organism photos (31 taxa x 150) and up to 1,860
 * track/scat photos (31 taxa x 2 signs x 30) when iNaturalist has them.
 * Licenses kept: CC0, CC-BY, CC-BY-NC, with the photographer attribution.
 *
 * Env for a full run (no new secret; the embedding model uses the existing xAI key):
 *   SUPABASE_URL
 *   SUPABASE_SERVICE_ROLE_KEY
 *   XAI_API_KEY
 *
 * Apply supabase/migrations/20261104190000_wildlife_reference.sql first.
 * iNaturalist is limited to about 1 request per second.
 */

import { expectedLoad, NORTHEAST_PLACE_IDS, PHOTOS_PER_SPECIES, SIGN_TYPES, SIGNS_PER_SPECIES, TARGETS } from "./wildlife-reference/catalog.mjs";
import {
  coordinatesOf,
  createRateLimiter,
  INAT_USER_AGENT,
  observationQuery,
  observedOn,
  photoFromObservation,
} from "./wildlife-reference/inat.mjs";
import {
  DEFAULT_EMBEDDING_MODEL,
  EMBEDDING_DIMENSIONS,
  RETRIEVAL_PROMPT,
  embedInput,
  parseEmbeddingVector,
} from "../supabase/functions/ai-assistant/reference-library.ts";

const FAST_XAI_MODEL = "grok-4.20-0309-non-reasoning";
const MAX_IMAGE_BYTES = 4_000_000;

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const photosPerSpecies = numberArg("--max-per-species", PHOTOS_PER_SPECIES);
const signsPerSpecies = numberArg("--max-signs", SIGNS_PER_SPECIES);
const only = stringArg("--species");

function numberArg(flag, fallback) {
  const index = args.indexOf(flag);
  if (index < 0) return fallback;
  const value = Number(args[index + 1]);
  return Number.isFinite(value) && value >= 0 ? Math.floor(value) : fallback;
}

function stringArg(flag) {
  const index = args.indexOf(flag);
  if (index < 0) return "";
  return String(args[index + 1] ?? "").trim().toLowerCase();
}

function requiredEnv(name) {
  const value = String(process.env[name] ?? "").trim();
  if (!value) {
    console.error(`Missing ${name}.`);
    process.exit(1);
  }
  return value;
}

const waitForInat = createRateLimiter();

async function inatJson(url) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await waitForInat();
    const response = await fetch(url, { headers: { Accept: "application/json", "User-Agent": INAT_USER_AGENT } });
    if (response.status === 429) {
      await new Promise((resolve) => setTimeout(resolve, 5000 * (attempt + 1)));
      continue;
    }
    if (!response.ok) throw new Error(`iNaturalist HTTP ${response.status}`);
    return response.json();
  }
  throw new Error("iNaturalist rate limit persisted");
}

async function dryRunReport(targets) {
  const expect = expectedLoad(targets);
  console.log(`Dry run. Full seed aims for ${expect.organism} organism photos and up to ${expect.signs} sign photos (${expect.total} max) across ${expect.species} taxa.`);
  for (const target of targets) {
    const url = observationQuery({
      taxonId: target.taxonId,
      placeIds: NORTHEAST_PLACE_IDS,
      page: 1,
      perPage: 1,
    });
    const body = await inatJson(url);
    console.log(`${target.commonName}: ${body.total_results ?? 0} research-grade licensed observations in the Northeast query`);
  }
}

function supabaseConfig() {
  const url = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const key = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  return { url, key };
}

function xaiConfig() {
  const key = requiredEnv("XAI_API_KEY");
  const baseUrl = String(process.env.XAI_BASE_URL ?? "https://api.x.ai/v1").replace(/\/$/, "");
  const chatModel = String(process.env.XAI_MODEL ?? FAST_XAI_MODEL).trim() || FAST_XAI_MODEL;
  const embedModel = String(process.env.XAI_EMBEDDING_MODEL ?? DEFAULT_EMBEDDING_MODEL).trim() || DEFAULT_EMBEDDING_MODEL;
  return { key, baseUrl, chatModel, embedModel };
}

async function xaiJson(path, body, config) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const response = await fetch(`${config.baseUrl}${path}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${config.key}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
    if (response.status === 429) {
      await new Promise((resolve) => setTimeout(resolve, 4000 * (attempt + 1)));
      continue;
    }
    const raw = await response.text();
    if (!response.ok) throw new Error(`xAI ${path} HTTP ${response.status}: ${raw.slice(0, 280)}`);
    return JSON.parse(raw);
  }
  throw new Error(`xAI ${path} rate limit persisted`);
}

async function describeImage(bytes, mime, config) {
  const dataUrl = `data:${mime};base64,${Buffer.from(bytes).toString("base64")}`;
  const decoded = await xaiJson("/chat/completions", {
    model: config.chatModel,
    temperature: 0,
    max_tokens: 180,
    messages: [{
      role: "user",
      content: [
        { type: "text", text: RETRIEVAL_PROMPT },
        { type: "image_url", image_url: { url: dataUrl } },
      ],
    }],
  }, config);
  const text = decoded?.choices?.[0]?.message?.content;
  return String(text ?? "").replace(/\s+/g, " ").trim().slice(0, 1200);
}

async function embedPassage(text, config) {
  const decoded = await xaiJson("/embeddings", {
    model: config.embedModel,
    input: embedInput("passage", text),
    dimensions: EMBEDDING_DIMENSIONS,
    encoding_format: "float",
  }, config);
  const vector = parseEmbeddingVector(decoded, EMBEDDING_DIMENSIONS);
  if (!vector) throw new Error(`Embedding was not ${EMBEDDING_DIMENSIONS} dimensions`);
  return vector;
}

async function rest(config, path, init = {}) {
  const response = await fetch(`${config.url}${path}`, {
    ...init,
    headers: {
      apikey: config.key,
      Authorization: `Bearer ${config.key}`,
      ...(init.headers ?? {}),
    },
  });
  return response;
}

async function existingPhotoIds(config, taxonId, signType) {
  const ids = new Set();
  for (let offset = 0; offset < 20000; offset += 1000) {
    const response = await rest(
      config,
      `/rest/v1/wildlife_reference?source=eq.inaturalist&taxon_id=eq.${taxonId}&sign_type=eq.${signType}&select=source_photo_id`,
      { headers: { Range: `${offset}-${offset + 999}` } },
    );
    if (!response.ok) throw new Error(`Could not read wildlife_reference (${response.status}). Apply the migration first.`);
    const rows = await response.json();
    if (!Array.isArray(rows) || rows.length === 0) break;
    rows.forEach((row) => ids.add(String(row.source_photo_id)));
    if (rows.length < 1000) break;
  }
  return ids;
}

async function uploadImage(config, storagePath, bytes, mime) {
  const response = await rest(config, `/storage/v1/object/wildlife-reference/${storagePath}`, {
    method: "POST",
    headers: {
      "Content-Type": mime,
      "x-upsert": "true",
    },
    body: bytes,
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Storage upload failed (${response.status}): ${detail.slice(0, 200)}`);
  }
}

async function insertRow(config, row) {
  const response = await rest(config, "/rest/v1/wildlife_reference", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Prefer: "return=minimal,resolution=ignore-duplicates",
    },
    body: JSON.stringify(row),
  });
  if (!response.ok && response.status !== 409) {
    const detail = await response.text();
    throw new Error(`Insert failed (${response.status}): ${detail.slice(0, 240)}`);
  }
}

async function downloadPhoto(url) {
  const response = await fetch(url, { headers: { "User-Agent": INAT_USER_AGENT, Accept: "image/jpeg,image/png,image/webp" } });
  if (!response.ok) throw new Error(`Photo HTTP ${response.status}`);
  const mime = (response.headers.get("content-type") || "image/jpeg").split(";")[0].trim().toLowerCase();
  if (!["image/jpeg", "image/png", "image/webp"].includes(mime)) throw new Error(`Unexpected photo type ${mime}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length === 0 || bytes.length > MAX_IMAGE_BYTES) throw new Error(`Photo size ${bytes.length}`);
  return { bytes, mime };
}

async function storePhoto({ config, xai, target, signType, observation, photo, seen }) {
  if (seen.has(photo.photoId)) return false;
  const { bytes, mime } = await downloadPhoto(photo.imageUrl);
  const description = await describeImage(bytes, mime, xai);
  if (!description) throw new Error("Empty description");
  const embedding = await embedPassage(description, xai);
  const extension = mime === "image/png" ? "png" : mime === "image/webp" ? "webp" : "jpg";
  const storagePath = `inaturalist/${target.taxonId}/${signType}/${photo.photoId}.${extension}`;
  await uploadImage(config, storagePath, bytes, mime);
  const point = coordinatesOf(observation);
  await insertRow(config, {
    source: "inaturalist",
    source_photo_id: photo.photoId,
    species: target.scientific,
    common_name: target.commonName,
    field_label: target.fieldLabel,
    taxon_id: target.taxonId,
    latitude: point.latitude,
    longitude: point.longitude,
    observed_on: observedOn(observation),
    license: photo.license,
    attribution: photo.attribution,
    source_url: observation.uri || `https://www.inaturalist.org/photos/${photo.photoId}`,
    storage_path: storagePath,
    image_url: photo.imageUrl,
    sign_type: signType,
    description,
    embedding: `[${embedding.join(",")}]`,
  });
  seen.add(photo.photoId);
  return true;
}

async function fillTarget({ config, xai, target, signType, cap }) {
  const seen = await existingPhotoIds(config, target.taxonId, signType);
  let stored = 0;
  let already = seen.size;
  for (let page = 1; page <= 40 && stored + Math.min(already, cap) < cap; page += 1) {
    const url = observationQuery({
      taxonId: target.taxonId,
      placeIds: NORTHEAST_PLACE_IDS,
      page,
      perPage: 200,
      sign: signType === "organism" ? null : SIGN_TYPES.find((item) => item.signType === signType),
    });
    const body = await inatJson(url);
    const results = Array.isArray(body.results) ? body.results : [];
    if (results.length === 0) break;
    for (const observation of results) {
      if (stored + Math.min(already, cap) >= cap) break;
      const photo = photoFromObservation(observation);
      if (!photo) continue;
      if (seen.has(photo.photoId)) continue;
      try {
        const wrote = await storePhoto({ config, xai, target, signType, observation, photo, seen });
        if (wrote) {
          stored += 1;
          console.log(`${target.commonName} ${signType} ${stored + Math.min(already, cap)}/${cap} photo ${photo.photoId} ${photo.license}`);
        }
      } catch (error) {
        console.warn(`Skip ${target.commonName} photo ${photo.photoId}: ${error.message}`);
      }
    }
    if (results.length < 200) break;
  }
  return { stored, already: Math.min(already, cap) };
}

async function main() {
  const targets = only
    ? TARGETS.filter((item) => item.commonName.toLowerCase().includes(only) || item.scientific.toLowerCase().includes(only))
    : TARGETS;
  if (targets.length === 0) {
    console.error(`No species matched "${only}".`);
    process.exit(1);
  }
  const expect = expectedLoad();
  console.log(`Wildlife reference seed. Catalog expects ${expect.organism} organism photos plus up to ${expect.signs} sign photos (${expect.species} taxa). This run caps organism photos at ${photosPerSpecies} and each sign at ${signsPerSpecies} per taxon.`);
  if (dryRun) {
    await dryRunReport(targets);
    return;
  }
  const config = supabaseConfig();
  const xai = xaiConfig();
  let stored = 0;
  let already = 0;
  for (const target of targets) {
    const organism = await fillTarget({ config, xai, target, signType: "organism", cap: photosPerSpecies });
    stored += organism.stored;
    already += organism.already;
    if (signsPerSpecies > 0) {
      for (const sign of SIGN_TYPES) {
        const signs = await fillTarget({ config, xai, target, signType: sign.signType, cap: signsPerSpecies });
        stored += signs.stored;
        already += signs.already;
      }
    }
  }
  console.log(`Done. Newly stored ${stored}. Already present (counted toward the cap) ${already}.`);
  if (stored === 0 && already === 0) process.exit(1);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
