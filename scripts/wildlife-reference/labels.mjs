import { TARGETS } from "./catalog.mjs";

const EXTRA_FIELD_LABELS = [
  "Canada goose",
  "Feral cat",
  "Bees",
  "Wasps",
  "Hornets",
  "Dog",
];

/**
 * A correction has a usable label.
 * YES may confirm the model label. NO and PARTLY only count when a replacement was typed.
 * UNLABELED rows are never candidates. user_id is not proof of who reviewed the row.
 */
export function correctLabel(row) {
  const verdict = String(row?.verdict ?? "").trim().toUpperCase();
  const corrected = String(row?.corrected_label ?? "").replace(/\s+/g, " ").trim();
  const model = String(row?.model_label ?? "").replace(/\s+/g, " ").trim();
  if (verdict === "YES") return (corrected || model).slice(0, 80);
  if (verdict === "NO" || verdict === "PARTLY") return corrected.slice(0, 80);
  return "";
}

export function parseIdList(raw) {
  const parts = Array.isArray(raw) ? raw : [raw];
  const ids = [];
  for (const part of parts) {
    for (const piece of String(part ?? "").split(/[\s,]+/)) {
      const id = piece.trim();
      if (id && !ids.includes(id)) ids.push(id);
    }
  }
  return ids;
}

export function feedbackPhotoUrl(supabaseUrl, storagePath) {
  const path = String(storagePath ?? "").trim().replace(/^\/+/, "");
  if (!path || path.includes("..")) return "";
  const base = String(supabaseUrl ?? "").trim().replace(/\/$/, "");
  if (!base) return "";
  const encoded = path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
  return `${base}/storage/v1/object/public/observation-photos/${encoded}`;
}

export function candidateLine(row, supabaseUrl) {
  const label = correctLabel(row);
  const photo = feedbackPhotoUrl(supabaseUrl, row?.photo_storage_path);
  const userId = String(row?.user_id ?? "");
  return `candidate id=${row?.id} verdict=${row?.verdict} label=${JSON.stringify(label)} user_id=${JSON.stringify(userId)} photo=${photo || "(none)"}`;
}

/**
 * Dry-run is the default. Writes happen only for ids the operator passes after reading the list.
 * A matching user_id never selects a row by itself.
 */
export function promotionPlan({ rows, ids, apply }) {
  const candidates = (Array.isArray(rows) ? rows : []).filter((row) => row?.id && correctLabel(row));
  const requested = parseIdList(ids);
  if (!apply) {
    const list = requested.length
      ? candidates.filter((row) => requested.includes(String(row.id)))
      : candidates;
    const unknown = requested.filter((id) => !candidates.some((row) => String(row.id) === id));
    return { mode: "dry-run", promote: [], list, unknown, reason: "" };
  }
  if (requested.length === 0) {
    return {
      mode: "refused",
      promote: [],
      list: candidates,
      unknown: [],
      reason: "Pass --ids with the feedback ids you reviewed. Nothing was promoted.",
    };
  }
  const promote = [];
  const unknown = [];
  for (const id of requested) {
    const row = candidates.find((item) => String(item.id) === id);
    if (row) promote.push(row);
    else unknown.push(id);
  }
  return { mode: "apply", promote, list: promote, unknown, reason: "" };
}

export function fieldLabelFor(label) {
  const normalized = String(label ?? "").trim().toLowerCase();
  if (!normalized) return "";
  const byCommon = TARGETS.find((item) => item.commonName.toLowerCase() === normalized);
  if (byCommon) return byCommon.fieldLabel;
  const byField = [...TARGETS.map((item) => item.fieldLabel), ...EXTRA_FIELD_LABELS]
    .find((item) => item.toLowerCase() === normalized);
  return byField || String(label).trim().slice(0, 80);
}
