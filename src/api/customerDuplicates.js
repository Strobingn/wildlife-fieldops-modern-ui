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
 * Detect duplicate customers using pre-indexed phone/email Maps and address length buckets.
 * Optimizes O(N^2) pairwise customer scans to index lookup + targeted similarity checks.
 *
 * @param {Array<Record<string, any>>|null|undefined} customers
 * @returns {Array<{ customer: Record<string, any>, potentialDuplicates: Array<{ customer: Record<string, any>, reason: string }> }>}
 */
export function detectDuplicateCustomers(customers) {
  if (!Array.isArray(customers) || customers.length === 0) return [];

  const prepared = customers.map((c) => {
    const normAddress = c?.address ? normalizeAddress(c.address) : null;
    return {
      raw: c,
      normPhone: c?.phone ? normalizePhone(c.phone) : null,
      normEmail: c?.email ? String(c.email).toLowerCase() : null,
      normAddress,
      addrLen: normAddress ? normAddress.length : 0,
    };
  });

  // Build index Maps for fast candidate lookup
  const phoneMap = new Map();
  const emailMap = new Map();
  const lenBuckets = new Map();

  for (let i = 0; i < prepared.length; i++) {
    const p = prepared[i];
    if (p.normPhone !== null) {
      let arr = phoneMap.get(p.normPhone);
      if (!arr) phoneMap.set(p.normPhone, (arr = []));
      arr.push(i);
    }
    if (p.normEmail !== null) {
      let arr = emailMap.get(p.normEmail);
      if (!arr) emailMap.set(p.normEmail, (arr = []));
      arr.push(i);
    }
    if (p.normAddress !== null && p.addrLen > 0) {
      let arr = lenBuckets.get(p.addrLen);
      if (!arr) lenBuckets.set(p.addrLen, (arr = []));
      arr.push(i);
    }
  }

  const duplicates = [];

  for (let i = 0; i < prepared.length; i++) {
    const c = prepared[i];
    const matchedReasons = new Map(); // target Index j -> match reason

    // 1. Phone index lookup
    if (c.normPhone !== null) {
      const phoneList = phoneMap.get(c.normPhone);
      if (phoneList) {
        for (let k = 0; k < phoneList.length; k++) {
          const j = phoneList[k];
          if (j !== i) matchedReasons.set(j, 'Same phone');
        }
      }
    }

    // 2. Email index lookup
    if (c.normEmail !== null) {
      const emailList = emailMap.get(c.normEmail);
      if (emailList) {
        for (let k = 0; k < emailList.length; k++) {
          const j = emailList[k];
          if (j !== i && !matchedReasons.has(j)) {
            matchedReasons.set(j, 'Same email');
          }
        }
      }
    }

    // 3. Address bucket candidate search (only length ratios >= 0.8)
    if (c.normAddress !== null && c.addrLen > 0) {
      const minL = Math.ceil(c.addrLen * 0.8);
      const maxL = Math.floor(c.addrLen / 0.8);
      for (let l = minL; l <= maxL; l++) {
        const bucket = lenBuckets.get(l);
        if (bucket) {
          for (let k = 0; k < bucket.length; k++) {
            const j = bucket[k];
            if (j !== i && !matchedReasons.has(j)) {
              const other = prepared[j];
              if (normalizedAddressSimilarity(c.normAddress, other.normAddress) > 0.8) {
                matchedReasons.set(j, 'Similar address');
              }
            }
          }
        }
      }
    }

    if (matchedReasons.size > 0) {
      // Sort indices j to preserve original index order (0..N-1)
      const sortedJs = Array.from(matchedReasons.keys()).sort((a, b) => a - b);
      const potentials = new Array(sortedJs.length);
      for (let k = 0; k < sortedJs.length; k++) {
        const j = sortedJs[k];
        potentials[k] = { customer: prepared[j].raw, reason: matchedReasons.get(j) };
      }
      duplicates.push({ customer: c.raw, potentialDuplicates: potentials });
    }
  }

  return duplicates;
}
