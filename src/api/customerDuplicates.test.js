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

  it('correctly handles multiple symmetric pairwise duplicates and maintains ordering', () => {
    const customers = [
      { id: '1', phone: '555-0001', email: 'shared@test.com' },
      { id: '2', phone: '555-0001', email: 'other@test.com' },
      { id: '3', phone: '555-9999', email: 'shared@test.com' },
    ];
    const found = detectDuplicateCustomers(customers);
    expect(found).toHaveLength(3);

    // Customer 1 matches 2 by phone, and 3 by email
    expect(found[0].customer.id).toBe('1');
    expect(found[0].potentialDuplicates).toEqual([
      { customer: customers[1], reason: 'Same phone' },
      { customer: customers[2], reason: 'Same email' },
    ]);

    // Customer 2 matches 1 by phone
    expect(found[1].customer.id).toBe('2');
    expect(found[1].potentialDuplicates).toEqual([
      { customer: customers[0], reason: 'Same phone' },
    ]);

    // Customer 3 matches 1 by email
    expect(found[2].customer.id).toBe('3');
    expect(found[2].potentialDuplicates).toEqual([
      { customer: customers[0], reason: 'Same email' },
    ]);
  });
});
