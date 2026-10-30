import { describe, it, expect } from 'vitest';
import { detectDuplicateCustomers, normalizedAddressSimilarity } from './customerDuplicates.js';

describe('detectDuplicateCustomers', () => {
  it('matches phone before email and keeps the same reasons', () => {
    const customers = [
      { id: '1', phone: '(845) 555-0100', email: 'a@example.com', address: '1 Main St' },
      { id: '2', phone: '8455550100', email: 'A@example.com', address: '99 Other Rd' },
    ];
    const found = detectDuplicateCustomers(customers);
    expect(found).toHaveLength(2);
    expect(found[0].potentialDuplicates).toEqual([
      { customer: customers[1], reason: 'Same phone' },
    ]);
    expect(found[1].potentialDuplicates[0].reason).toBe('Same phone');
  });

  it('matches email case-insensitively when phones differ', () => {
    const customers = [
      { id: '1', phone: '111', email: 'Pat@Example.com', address: '' },
      { id: '2', phone: '222', email: 'pat@example.com', address: '' },
    ];
    const found = detectDuplicateCustomers(customers);
    expect(found[0].potentialDuplicates[0].reason).toBe('Same email');
  });

  it('matches similar addresses and skips pairs that cannot clear 0.8', () => {
    expect(normalizedAddressSimilarity('abcdefghi', 'abcdefghij')).toBeCloseTo(0.9);
    expect(normalizedAddressSimilarity('abc', 'abcdefghij')).toBe(0);
    const customers = [
      { id: '1', address: '12 Oak Street' },
      { id: '2', address: '12 Oak Streett' },
      { id: '3', address: '400 Completely Different Road' },
    ];
    const found = detectDuplicateCustomers(customers);
    const first = found.find((row) => row.customer.id === '1');
    expect(first.potentialDuplicates.map((p) => p.customer.id)).toEqual(['2']);
    expect(first.potentialDuplicates[0].reason).toBe('Similar address');
  });

  it('treats non-digit phones as equal, matching the old loop', () => {
    const customers = [
      { id: '1', phone: 'call me' },
      { id: '2', phone: 'n/a' },
    ];
    const found = detectDuplicateCustomers(customers);
    expect(found[0].potentialDuplicates[0].reason).toBe('Same phone');
  });

  it('returns an empty list for missing input', () => {
    expect(detectDuplicateCustomers(null)).toEqual([]);
    expect(detectDuplicateCustomers([])).toEqual([]);
  });

  it('runs efficiently on 1000 customers (performance benchmark)', () => {
    const count = 1000;
    const streetTypes = ['St', 'Ave', 'Blvd', 'Rd', 'Ln', 'Dr', 'Way', 'Ct'];
    const towns = ['Kingston', 'New Paltz', 'Poughkeepsie', 'Woodstock', 'Saugerties', 'Rhinebeck'];
    const customers = Array.from({ length: count }, (_, i) => ({
      id: `cust_${i}`,
      phone: i % 5 === 0 ? `(845) 555-${String(i % 50).padStart(4, '0')}` : `(845) 555-${String(i).padStart(4, '0')}`,
      email: i % 7 === 0 ? `user_${i % 40}@example.com` : `user_${i}@example.com`,
      address: `${100 + i} ${streetTypes[i % streetTypes.length]} ${towns[i % towns.length]}`,
    }));

    const start = performance.now();
    const duplicates = detectDuplicateCustomers(customers);
    const duration = performance.now() - start;

    expect(duplicates.length).toBeGreaterThan(0);
    // 1000 customers indexed and matched in under 100ms
    expect(duration).toBeLessThan(100);
  });
});
