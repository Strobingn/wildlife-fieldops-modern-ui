const ALLOWED_LICENSES = new Set(["cc0", "cc-by", "cc-by-nc"]);

export const INAT_USER_AGENT = "WildlifeFieldOps/2.9.15-wildlife-library (Northeast reference library)";
export const INAT_MIN_INTERVAL_MS = 1100;

export function licenseOk(code) {
  return ALLOWED_LICENSES.has(String(code ?? "").trim().toLowerCase());
}

export function mediumPhotoUrl(url) {
  const raw = String(url ?? "").trim();
  if (!raw) return "";
  return raw
    .replace("/square.", "/medium.")
    .replace("/thumb.", "/medium.")
    .replace("/small.", "/medium.")
    .replace("/large.", "/medium.")
    .replace("/original.", "/medium.");
}

export function observationQuery({ taxonId, placeIds, page, perPage, sign }) {
  const params = new URLSearchParams();
  params.set("taxon_id", String(taxonId));
  params.set("place_id", placeIds.join(","));
  params.set("quality_grade", "research");
  params.set("photos", "true");
  params.set("photo_license", "cc0,cc-by,cc-by-nc");
  params.set("order_by", "votes");
  params.set("order", "desc");
  params.set("page", String(page));
  params.set("per_page", String(perPage));
  if (sign) {
    params.set("term_id", String(sign.termId));
    params.set("term_value_id", String(sign.termValueId));
  }
  return `https://api.inaturalist.org/v1/observations?${params.toString()}`;
}

export function photoFromObservation(observation) {
  const photos = Array.isArray(observation?.photos) ? observation.photos : [];
  for (const photo of photos) {
    if (!photo || photo.hidden) continue;
    if (!licenseOk(photo.license_code)) continue;
    const url = mediumPhotoUrl(photo.url);
    if (!url.startsWith("https://")) continue;
    const id = String(photo.id ?? "").trim();
    if (!id) continue;
    const attribution = String(photo.attribution ?? "").replace(/\s+/g, " ").trim()
      || `iNaturalist observer, ${String(photo.license_code).toLowerCase()}`;
    return {
      photoId: id,
      imageUrl: url,
      license: String(photo.license_code).trim().toLowerCase(),
      attribution: attribution.slice(0, 500),
    };
  }
  return null;
}

export function coordinatesOf(observation) {
  const location = String(observation?.location ?? "");
  const pair = /^(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)$/.exec(location.trim());
  if (!pair) return { latitude: null, longitude: null };
  const latitude = Number(pair[1]);
  const longitude = Number(pair[2]);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return { latitude: null, longitude: null };
  return { latitude, longitude };
}

export function observedOn(observation) {
  const value = String(observation?.observed_on ?? "").trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}

export function createRateLimiter(minIntervalMs = INAT_MIN_INTERVAL_MS, now = () => Date.now(), sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
  let nextAt = 0;
  return async function waitTurn() {
    const start = now();
    const delay = Math.max(0, nextAt - start);
    nextAt = Math.max(nextAt, start) + minIntervalMs;
    if (delay > 0) await sleep(delay);
  };
}
