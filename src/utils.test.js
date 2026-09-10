import { describe, it, expect } from 'vitest';
import {
  deepClone,
  money,
  formatPhone,
  isValidPhone,
  isValidEmail,
  validateJob,
  groupBy,
  sortBy,
  searchJobs,
  filterJobs,
} from './utils.js';

describe('utils.js — deepClone', () => {
  it('handles primitive values and null/undefined', () => {
    expect(deepClone(null)).toBe(null);
    expect(deepClone(undefined)).toBe(undefined);
    expect(deepClone(42)).toBe(42);
    expect(deepClone('hello')).toBe('hello');
    expect(deepClone(true)).toBe(true);
  });

  it('clones Date objects correctly', () => {
    const d = new Date('2025-01-01T00:00:00Z');
    const cloned = deepClone(d);
    expect(cloned).toBeInstanceOf(Date);
    expect(cloned.getTime()).toBe(d.getTime());
    expect(cloned).not.toBe(d);
  });

  it('clones nested objects and arrays without maintaining reference equality', () => {
    const original = {
      name: 'Raccoon Job',
      tags: ['attic', 'exclusion'],
      meta: { priority: 'High', count: 3 },
    };
    const cloned = deepClone(original);

    expect(cloned).toEqual(original);
    expect(cloned).not.toBe(original);
    expect(cloned.tags).not.toBe(original.tags);
    expect(cloned.meta).not.toBe(original.meta);
  });

  it('falls back gracefully for objects containing functions', () => {
    const original = {
      id: 1,
      fn: () => 'test',
    };
    const cloned = deepClone(original);
    expect(cloned.id).toBe(1);
    expect(cloned).not.toBe(original);
  });
});

describe('utils.js — formatters & helpers', () => {
  it('formats currency correctly', () => {
    expect(money(500)).toBe('$500.00');
    expect(money(1234.56)).toBe('$1,234.56');
    expect(money(null)).toBe('$0.00');
  });

  it('formats phone numbers', () => {
    expect(formatPhone('5551234567')).toBe('(555) 123-4567');
    expect(formatPhone('15551234567')).toBe('+1 (555) 123-4567');
  });

  it('validates phone and email', () => {
    expect(isValidPhone('5551234567')).toBe(true);
    expect(isValidPhone('123')).toBe(false);

    expect(isValidEmail('test@example.com')).toBe(true);
    expect(isValidEmail('invalid-email')).toBe(false);
  });

  it('groups and sorts arrays', () => {
    const items = [
      { id: 1, category: 'A', name: 'Zebra' },
      { id: 2, category: 'B', name: 'Apple' },
      { id: 3, category: 'A', name: 'Monkey' },
    ];

    const grouped = groupBy(items, 'category');
    expect(grouped.A).toHaveLength(2);
    expect(grouped.B).toHaveLength(1);

    const sorted = sortBy(items, 'name', 'asc');
    expect(sorted[0].name).toBe('Apple');
    expect(sorted[2].name).toBe('Zebra');
  });

  it('searches and filters jobs', () => {
    const jobs = [
      { id: '1', title: 'Attic Raccoon', customer: 'John Doe', species: 'Raccoon', status: 'Active' },
      { id: '2', title: 'Squirrel Removal', customer: 'Jane Smith', species: 'Squirrel', status: 'Closed' },
    ];

    expect(searchJobs(jobs, 'raccoon')).toHaveLength(1);
    expect(searchJobs(jobs, 'jane')).toHaveLength(1);

    expect(filterJobs(jobs, { status: 'Active' })).toHaveLength(1);
  });
});
