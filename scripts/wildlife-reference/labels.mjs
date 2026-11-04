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
 * A verified correction has a correct label.
 * YES may confirm the model label. NO and PARTLY only count when the owner typed a replacement.
 * UNLABELED rows are never promoted.
 */
export function correctLabel(row) {
  const verdict = String(row?.verdict ?? "").trim().toUpperCase();
  const corrected = String(row?.corrected_label ?? "").replace(/\s+/g, " ").trim();
  const model = String(row?.model_label ?? "").replace(/\s+/g, " ").trim();
  if (verdict === "YES") return (corrected || model).slice(0, 80);
  if (verdict === "NO" || verdict === "PARTLY") return corrected.slice(0, 80);
  return "";
}

export function isOwnerCorrection(row, ownerUserId) {
  const owner = String(ownerUserId ?? "").trim();
  if (!owner) return false;
  if (String(row?.user_id ?? "").trim() !== owner) return false;
  return correctLabel(row).length > 0;
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
