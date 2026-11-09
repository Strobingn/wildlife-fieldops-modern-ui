/**
 * Pairwise duplicate detection with fields normalized once per customer.
 * Match order is phone, then email, then address — the same order as the
 * previous nested loop, including the "non-digit phones compare equal" case.
 */

export function normalizePhone(phone) {
  return String(phone ?? '').replace(/\D/g, '');
}

function normalizeAddress(address) {
  return String(address ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
}

/**
 * Prefix similarity of already-normalized addresses. Length ratio at or below
 * 0.8 cannot exceed the 0.8 match threshold, so those pairs skip the scan.
 * @param {string} normA
 * @param {string} normB
 * @returns {number}
 */
export function normalizedAddressSimilarity(normA, normB) {
  if (normA === normB) return 1;
  const maxLen = Math.max(normA.length, normB.length);
  const minLen = Math.min(normA.length, normB.length);
  if (maxLen === 0 || minLen / maxLen <= 0.8) return 0;
  let matches = 0;
  for (let i = 0; i < minLen; i++) {
    if (normA[i] === normB[i]) matches++;
  }
  return matches / maxLen;
}

/**
 * @param {Array<Record<string, any>>|null|undefined} customers
 * @returns {Array<{ customer: Record<string, any>, potentialDuplicates: Array<{ customer: Record<string, any>, reason: string }> }>}
 */
export function detectDuplicateCustomers(customers) {
  if (!Array.isArray(customers) || customers.length === 0) return [];

  const prepared = customers.map((c) => ({
    raw: c,
    normPhone: c?.phone ? normalizePhone(c.phone) : null,
    normEmail: c?.email ? String(c.email).toLowerCase() : null,
    normAddress: c?.address ? normalizeAddress(c.address) : null,
  }));

  const n = prepared.length;
  // Performance optimization: Exploit pairwise symmetry.
  // Equality of phone, email, and normalizedAddressSimilarity are symmetric relations.
  // Evaluating each unique pair (i, j) with j > i and recording the match symmetrically
  // reduces pairwise comparisons from N*(N-1) down to N*(N-1)/2 (~50% fewer checks, ~2x speedup).
  const potentialsList = Array.from({ length: n }, () => []);

  for (let i = 0; i < n; i++) {
    const c = prepared[i];
    for (let j = i + 1; j < n; j++) {
      const other = prepared[j];
      let reason = null;
      if (c.normPhone !== null && other.normPhone !== null && c.normPhone === other.normPhone) {
        reason = 'Same phone';
      } else if (c.normEmail !== null && other.normEmail !== null && c.normEmail === other.normEmail) {
        reason = 'Same email';
      } else if (
        c.normAddress !== null &&
        other.normAddress !== null &&
        normalizedAddressSimilarity(c.normAddress, other.normAddress) > 0.8
      ) {
        reason = 'Similar address';
      }

      if (reason !== null) {
        potentialsList[i].push({ customer: other.raw, reason });
        potentialsList[j].push({ customer: c.raw, reason });
      }
    }
  }

  const duplicates = [];
  for (let i = 0; i < n; i++) {
    if (potentialsList[i].length > 0) {
      duplicates.push({ customer: prepared[i].raw, potentialDuplicates: potentialsList[i] });
    }
  }
  return duplicates;
}
