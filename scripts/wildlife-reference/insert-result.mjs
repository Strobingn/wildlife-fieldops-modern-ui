/**
 * A duplicate unique key is not a new library photo.
 * PostgREST ignore-duplicates returns an empty representation, and 409 is the same outcome.
 */
export function insertedRow(status, body) {
  if (status === 409) return false;
  if (status < 200 || status >= 300) return false;
  if (Array.isArray(body)) return body.length > 0;
  if (body && typeof body === "object") return Boolean(body.id);
  return false;
}
