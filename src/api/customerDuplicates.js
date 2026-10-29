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

  // Performance optimization: Pre-build Map indices for phone and email lookups
  // to avoid redundant string comparisons in inner loops.
  const phoneMap = new Map();
  const emailMap = new Map();

  for (let i = 0; i < prepared.length; i++) {
    const p = prepared[i];
    if (p.normPhone !== null) {
      let set = phoneMap.get(p.normPhone);
      if (!set) {
        set = new Set();
        phoneMap.set(p.normPhone, set);
      }
      set.add(i);
    }
    if (p.normEmail !== null) {
      let set = emailMap.get(p.normEmail);
      if (!set) {
        set = new Set();
        emailMap.set(p.normEmail, set);
      }
      set.add(i);
    }
  }

  const duplicates = [];
  for (let i = 0; i < prepared.length; i++) {
    const c = prepared[i];
    const potentials = [];

    const samePhones = c.normPhone !== null ? phoneMap.get(c.normPhone) : null;
    const sameEmails = c.normEmail !== null ? emailMap.get(c.normEmail) : null;

    for (let j = 0; j < prepared.length; j++) {
      if (i === j) continue;
      const other = prepared[j];

      // 1. Same phone check
      if (samePhones && samePhones.has(j)) {
        potentials.push({ customer: other.raw, reason: 'Same phone' });
        continue;
      }

      // 2. Same email check
      if (sameEmails && sameEmails.has(j)) {
        potentials.push({ customer: other.raw, reason: 'Same email' });
        continue;
      }

      // 3. Similar address check using normalizedAddressSimilarity
      if (
        c.normAddress !== null &&
        other.normAddress !== null &&
        normalizedAddressSimilarity(c.normAddress, other.normAddress) > 0.8
      ) {
        potentials.push({ customer: other.raw, reason: 'Similar address' });
      }
    }

    if (potentials.length > 0) {
      duplicates.push({ customer: c.raw, potentialDuplicates: potentials });
    }
  }
  return duplicates;
}
