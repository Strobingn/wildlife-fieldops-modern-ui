// Describe-then-text-embed contract for the wildlife reference library.
// Vectors use the existing XAI_API_KEY (model grok-embedding-small, 768 dimensions).
// No new secret. Image embeddings are not part of the xAI embeddings API, so each
// photo is described with the chat model and that paragraph is embedded as text.
// Passage rows are stored by the seed script. Query rows are built here at detection time.

export const EMBEDDING_DIMENSIONS = 768;
export const DEFAULT_EMBEDDING_MODEL = "grok-embedding-small";
export const SIMILAR_DISTANCE = 0.55;
export const PASS_DISTANCE = 0.7;
export const REFERENCE_MARKER = "Reference library neighbors:";

export const RETRIEVAL_PROMPT =
  "Describe this wildlife photo for similarity search. One paragraph. Cover body shape, size class, fur feathers or scales, color, tail, ears, markings, posture, and whether the photo shows the animal, a track, or scat. Do not name a species unless the traits make it obvious. Do not invent a location.";

export type ReferenceNeighbor = {
  species?: string;
  common_name?: string;
  field_label?: string;
  license?: string;
  attribution?: string;
  observed_on?: string | null;
  sign_type?: string;
  distance?: number;
};

export function embedInput(kind: "query" | "passage", text: string): string {
  const body = text.replace(/\s+/g, " ").trim().slice(0, 2000);
  return `${kind}: ${body}`;
}

export function formatUsDate(iso: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso ?? "").trim());
  if (!match) return "";
  return `${match[2]}/${match[3]}/${match[1]}`;
}

export function consensusMatch(
  rows: ReferenceNeighbor[],
): { species: string; similarPhotos: number } | null {
  const near = rows.filter((row) =>
    typeof row.distance === "number" && Number.isFinite(row.distance) && row.distance <= SIMILAR_DISTANCE
  );
  if (near.length === 0) return null;
  const counts = new Map<string, number>();
  for (const row of near) {
    const name = String(row.common_name ?? "").replace(/\s+/g, " ").trim();
    if (!name) continue;
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  if (counts.size === 0) return null;
  let species = String(near[0].common_name ?? "").replace(/\s+/g, " ").trim();
  let similarPhotos = counts.get(species) ?? 0;
  for (const [name, count] of counts) {
    if (count > similarPhotos) {
      species = name;
      similarPhotos = count;
    }
  }
  if (!species || similarPhotos <= 0) return null;
  return { species, similarPhotos };
}

function oneLine(value: unknown, max = 180): string {
  return String(value ?? "").replace(/[\r\n]+/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

export function formatReferenceGrounding(rows: ReferenceNeighbor[]): {
  text: string;
  match: { species: string; similarPhotos: number } | null;
} {
  const usable = rows
    .filter((row) => typeof row.distance === "number" && Number.isFinite(row.distance) && row.distance <= PASS_DISTANCE)
    .slice(0, 4);
  if (usable.length === 0) return { text: "", match: null };
  const lines = usable.map((row) => {
    const observed = formatUsDate(row.observed_on);
    const when = observed ? ` Observed: ${observed}.` : "";
    const distance = typeof row.distance === "number" ? row.distance.toFixed(3) : "";
    return [
      `- Common name: ${oneLine(row.common_name)}.`,
      `Scientific: ${oneLine(row.species)}.`,
      `Field label: ${oneLine(row.field_label)}.`,
      `Sign: ${oneLine(row.sign_type) || "organism"}.`,
      `License: ${oneLine(row.license)}.`,
      `Attribution: ${oneLine(row.attribution)}.`,
      `${when} Distance: ${distance}.`,
    ].join(" ");
  });
  const text = [
    REFERENCE_MARKER,
    "These are nearby photos from the Northeast nuisance wildlife reference library (CC0, CC-BY, or CC-BY-NC, or an owner-verified correction). They are grounded examples, not a verdict for this photo.",
    "When this photo resembles them, prefer the field label. The technician still confirms the species. Manual identification remains available.",
    ...lines,
  ].join("\n");
  return { text, match: consensusMatch(usable) };
}

export function parseEmbeddingVector(payload: unknown, dimensions = EMBEDDING_DIMENSIONS): number[] | null {
  if (!payload || typeof payload !== "object") return null;
  const data = (payload as { data?: unknown }).data;
  if (!Array.isArray(data) || data.length === 0) return null;
  const embedding = (data[0] as { embedding?: unknown }).embedding;
  if (!Array.isArray(embedding) || embedding.length !== dimensions) return null;
  const vector: number[] = [];
  for (const value of embedding) {
    if (typeof value !== "number" || !Number.isFinite(value)) return null;
    vector.push(value);
  }
  return vector;
}
