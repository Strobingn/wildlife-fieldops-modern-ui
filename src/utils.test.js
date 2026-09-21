import { describe, it, expect } from 'vitest';
import { searchJobs, filterJobs, sortBy, groupBy, deepClone } from './utils.js';

describe('searchJobs', () => {
  const jobs = [
    { id: '1', title: 'Bat in Attic', customer: 'Alice Smith', address: '10 Oak St', town: 'Bedford', species: 'Bat', scope: 'Exclusion', status: 'Active', phone: '555-0101' },
    { id: '2', title: 'Raccoon Removal', customer: 'Bob Jones', address: '22 Elm St', town: 'Lexington', species: 'Raccoon', scope: 'Trapping', status: 'Scheduled', phone: '555-0202' },
    { id: '3', title: 'Squirrel Control', customer: 'Charlie Brown', address: '33 Pine St', town: 'Bedford', species: 'Grey Squirrel', scope: 'Repair', status: 'Closed', phone: '555-0303' },
  ];

  it('returns all jobs when query is empty or invalid', () => {
    expect(searchJobs(jobs, '')).toEqual(jobs);
    expect(searchJobs(jobs, '  ')).toEqual(jobs);
    expect(searchJobs(null, 'bedford')).toEqual([]);
  });

  it('filters jobs case-insensitively across multiple fields', () => {
    const byTown = searchJobs(jobs, 'bedford');
    expect(byTown).toHaveLength(2);
    expect(byTown.map((j) => j.id)).toEqual(['1', '3']);

    const byCustomer = searchJobs(jobs, 'alice');
    expect(byCustomer).toHaveLength(1);
    expect(byCustomer[0].id).toBe('1');

    const bySpecies = searchJobs(jobs, 'RACCOON');
    expect(bySpecies).toHaveLength(1);
    expect(bySpecies[0].id).toBe('2');
  });

  it('handles null/missing job fields gracefully', () => {
    const incompleteJobs = [
      { id: '10', customer: null, town: 'Bedford' },
      { id: '11', title: undefined },
    ];
    expect(searchJobs(incompleteJobs, 'bedford')).toHaveLength(1);
  });
});

describe('filterJobs', () => {
  const jobs = [
    { id: '1', status: 'Active', species: 'Bat', town: 'Bedford' },
    { id: '2', status: 'Scheduled', species: 'Raccoon', town: 'Lexington' },
    { id: '3', status: 'Active', species: 'Raccoon', town: 'Bedford' },
  ];

  it('returns all jobs when filters object is empty or null', () => {
    expect(filterJobs(jobs, {})).toEqual(jobs);
    expect(filterJobs(jobs, null)).toEqual(jobs);
    expect(filterJobs(null, { status: 'Active' })).toEqual([]);
  });

  it('filters jobs case-insensitively by single and multiple criteria', () => {
    const active = filterJobs(jobs, { status: 'active' });
    expect(active).toHaveLength(2);
    expect(active.map((j) => j.id)).toEqual(['1', '3']);

    const activeRaccoonsInBedford = filterJobs(jobs, {
      status: 'ACTIVE',
      species: 'raccoon',
      town: 'bedford',
    });
    expect(activeRaccoonsInBedford).toHaveLength(1);
    expect(activeRaccoonsInBedford[0].id).toBe('3');
  });

  it('ignores empty filter field values', () => {
    const filtered = filterJobs(jobs, { status: 'Active', species: '', town: null });
    expect(filtered).toHaveLength(2);
  });
});

describe('sortBy & groupBy & deepClone', () => {
  it('sortBy sorts array ascending and descending', () => {
    const list = [{ val: 3 }, { val: 1 }, { val: 2 }];
    expect(sortBy(list, 'val', 'asc').map((x) => x.val)).toEqual([1, 2, 3]);
    expect(sortBy(list, 'val', 'desc').map((x) => x.val)).toEqual([3, 2, 1]);
  });

  it('groupBy groups array items by property', () => {
    const items = [{ cat: 'a', id: 1 }, { cat: 'b', id: 2 }, { cat: 'a', id: 3 }];
    const grouped = groupBy(items, 'cat');
    expect(grouped.a).toHaveLength(2);
    expect(grouped.b).toHaveLength(1);
  });

  it('deepClone clones objects and arrays correctly', () => {
    const orig = { a: [1, 2], b: { c: 'hello' } };
    const cloned = deepClone(orig);
    expect(cloned).toEqual(orig);
    expect(cloned).not.toBe(orig);
    expect(cloned.b).not.toBe(orig.b);
  });
});
