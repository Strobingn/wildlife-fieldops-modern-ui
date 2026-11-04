import assert from "node:assert/strict";
import test from "node:test";
import { expectedLoad, PHOTOS_PER_SPECIES, TARGETS } from "./catalog.mjs";
import { createRateLimiter, licenseOk, mediumPhotoUrl, photoFromObservation } from "./inat.mjs";
import { insertedRow } from "./insert-result.mjs";
import { candidateLine, correctLabel, fieldLabelFor, promotionPlan } from "./labels.mjs";
import { readFileSync } from "node:fs";
import {
  REFERENCE_LOOKUP_TIMEOUT_MS,
  consensusMatch,
  embedInput,
  formatReferenceGrounding,
  formatUsDate,
  parseEmbeddingVector,
  runWithinTimeout,
  EMBEDDING_DIMENSIONS,
} from "../../supabase/functions/ai-assistant/reference-library.ts";

test("expected load is 150 organism photos per Northeast taxon", () => {
  const load = expectedLoad();
  assert.equal(TARGETS.length, 31);
  assert.equal(PHOTOS_PER_SPECIES, 150);
  assert.equal(load.organism, 4650);
  assert.equal(load.signs, 1860);
  assert.equal(load.total, 6510);
  assert.equal(new Set(TARGETS.map((item) => item.taxonId)).size, TARGETS.length);
});

test("photo license and medium url stay inside the allowed set", () => {
  assert.equal(licenseOk("CC-BY"), true);
  assert.equal(licenseOk("cc-by-nc"), true);
  assert.equal(licenseOk("cc0"), true);
  assert.equal(licenseOk("cc-by-sa"), false);
  assert.equal(licenseOk("cc-by-nc-nd"), false);
  assert.equal(
    mediumPhotoUrl("https://inaturalist-open-data.s3.amazonaws.com/photos/18813926/square.jpeg"),
    "https://inaturalist-open-data.s3.amazonaws.com/photos/18813926/medium.jpeg",
  );
  const photo = photoFromObservation({
    photos: [
      { id: 1, license_code: "cc-by-nd", url: "https://example.com/photos/1/square.jpeg", attribution: "no" },
      { id: 2, license_code: "cc-by", url: "https://inaturalist-open-data.s3.amazonaws.com/photos/2/square.jpeg", attribution: "(c) Ada, CC BY" },
    ],
  });
  assert.equal(photo.photoId, "2");
  assert.equal(photo.license, "cc-by");
  assert.match(photo.imageUrl, /\/medium\.jpeg$/);
  assert.match(photo.attribution, /Ada/);
});

test("inat requests wait about one second", async () => {
  let clock = 0;
  const slept = [];
  const wait = createRateLimiter(1100, () => clock, async (ms) => {
    slept.push(ms);
    clock += ms;
  });
  await wait();
  await wait();
  await wait();
  assert.deepEqual(slept, [1100, 1100]);
});

test("query and passage embeddings share a prefix and dimension", () => {
  assert.equal(embedInput("passage", "ringed tail"), "passage: ringed tail");
  assert.equal(embedInput("query", "mask"), "query: mask");
  const vector = parseEmbeddingVector({ data: [{ embedding: Array.from({ length: EMBEDDING_DIMENSIONS }, () => 0.01) }] });
  assert.equal(vector.length, 768);
  assert.equal(parseEmbeddingVector({ data: [{ embedding: [1, 2] }] }), null);
});

test("nearest neighbors become a reference line only inside the similar distance", () => {
  assert.equal(formatUsDate("1994-03-23"), "03/23/1994");
  const grounding = formatReferenceGrounding([
    { common_name: "Raccoon", species: "Procyon lotor", field_label: "Raccoon", license: "cc-by", attribution: "(c) Ada", observed_on: "2024-05-02", sign_type: "organism", distance: 0.2 },
    { common_name: "Raccoon", species: "Procyon lotor", field_label: "Raccoon", license: "cc0", attribution: "(c) Bo", observed_on: "2020-01-09", sign_type: "organism", distance: 0.4 },
    { common_name: "Skunk", species: "Mephitis mephitis", field_label: "Skunk", license: "cc-by-nc", attribution: "(c) Cy", sign_type: "track", distance: 0.9 },
  ]);
  assert.equal(grounding.match.species, "Raccoon");
  assert.equal(grounding.match.similarPhotos, 2);
  assert.match(grounding.text, /Reference library neighbors:/);
  assert.match(grounding.text, /Observed: 05\/02\/2024/);
  assert.match(grounding.text, /\(c\) Ada/);
  assert.doesNotMatch(grounding.text, /Skunk/);
  assert.equal(consensusMatch([{ common_name: "Bat", distance: 0.8 }]), null);
});

test("promotion lists photos and writes only reviewed ids", () => {
  assert.equal(correctLabel({ verdict: "YES", model_label: "Raccoon", corrected_label: "" }), "Raccoon");
  assert.equal(correctLabel({ verdict: "NO", model_label: "Dog", corrected_label: "Raccoon" }), "Raccoon");
  assert.equal(correctLabel({ verdict: "PARTLY", corrected_label: "Soffit" }), "Soffit");
  assert.equal(correctLabel({ verdict: "NO", model_label: "Dog", corrected_label: "" }), "");
  assert.equal(correctLabel({ verdict: "UNLABELED", model_label: "Raccoon" }), "");
  assert.equal(fieldLabelFor("Striped skunk"), "Skunk");
  assert.equal(fieldLabelFor("Eastern cottontail"), "Eastern cottontail");

  const rows = [
    { id: "keep", user_id: "phone-a", verdict: "YES", model_label: "Raccoon", photo_storage_path: "detection/keep.jpg" },
    { id: "other", user_id: "phone-a", verdict: "NO", model_label: "Dog", corrected_label: "Skunk", photo_storage_path: "detection/other.jpg" },
    { id: "blank", user_id: "phone-a", verdict: "UNLABELED", model_label: "Raccoon", photo_storage_path: "detection/blank.jpg" },
    { id: "spoofed", user_id: "phone-a", verdict: "YES", model_label: "Coyote", photo_storage_path: "../secret.jpg" },
  ];
  const dry = promotionPlan({ rows, ids: "", apply: false });
  assert.equal(dry.mode, "dry-run");
  assert.deepEqual(dry.promote, []);
  assert.deepEqual(dry.list.map((row) => row.id), ["keep", "other", "spoofed"]);
  const line = candidateLine(rows[0], "https://example.supabase.co");
  assert.match(line, /id=keep/);
  assert.match(line, /photo=https:\/\/example\.supabase\.co\/storage\/v1\/object\/public\/observation-photos\/detection\/keep\.jpg/);
  assert.match(candidateLine(rows[3], "https://example.supabase.co"), /photo=\(none\)/);

  const preview = promotionPlan({ rows, ids: "keep, missing", apply: false });
  assert.deepEqual(preview.promote, []);
  assert.deepEqual(preview.list.map((row) => row.id), ["keep"]);
  assert.deepEqual(preview.unknown, ["missing"]);

  const refused = promotionPlan({ rows, ids: "", apply: true });
  assert.equal(refused.mode, "refused");
  assert.deepEqual(refused.promote, []);

  const chosen = promotionPlan({ rows, ids: "other", apply: true });
  assert.equal(chosen.mode, "apply");
  assert.deepEqual(chosen.promote.map((row) => row.id), ["other"]);
  assert.equal(chosen.promote[0].user_id, "phone-a");
});

test("duplicate library rows are not counted as inserts", () => {
  assert.equal(insertedRow(201, [{ id: "new" }]), true);
  assert.equal(insertedRow(201, []), false);
  assert.equal(insertedRow(409, [{ id: "old" }]), false);
  assert.equal(insertedRow(201, { id: "new" }), true);
  assert.equal(insertedRow(201, {}), false);
});

test("reference lookup stops at about 4 seconds and detection can continue", async () => {
  assert.equal(REFERENCE_LOOKUP_TIMEOUT_MS, 4000);
  const started = Date.now();
  const missed = await runWithinTimeout(() => new Promise(() => {}), 30);
  assert.equal(missed, null);
  assert.ok(Date.now() - started < 500);
  assert.equal(await runWithinTimeout(async () => "ready", 50), "ready");
  const source = readFileSync(new URL("../../supabase/functions/ai-assistant/index.ts", import.meta.url), "utf8");
  assert.equal(source.match(/data:image\\\/\(jpeg\|jpg\|png\|webp\)/g).length, 1);
  assert.match(source, /runWithinTimeout/);
  assert.match(source, /REFERENCE_LOOKUP_TIMEOUT_MS/);
});
