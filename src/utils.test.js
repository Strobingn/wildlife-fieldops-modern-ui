import { describe, it, expect } from 'vitest';
import { mergeArrays, filterJobs, searchJobs } from './utils.js';

describe('mergeArrays', () => {
  it('returns copy of local array if server array is invalid or empty', () => {
    const local = [{ id: '1', name: 'Local 1' }];
    expect(mergeArrays(local, null, 'id')).toEqual(local);
    expect(mergeArrays(local, [], 'id')).toEqual(local);
  });

  it('merges new items from server array', () => {
    const local = [{ id: '1', name: 'Item 1' }];
    const server = [{ id: '2', name: 'Item 2' }];
    const result = mergeArrays(local, server, 'id');

    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({ id: '1', name: 'Item 1' });
    expect(result[1]).toEqual({ id: '2', name: 'Item 2' });
  });

  it('updates matching items from server array (server wins)', () => {
    const local = [
      { id: '1', name: 'Item 1 Old' },
      { id: '2', name: 'Item 2' }
    ];
    const server = [{ id: '1', name: 'Item 1 Updated' }];
    const result = mergeArrays(local, server, 'id');

    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({ id: '1', name: 'Item 1 Updated' });
    expect(result[1]).toEqual({ id: '2', name: 'Item 2' });
  });

  it('handles items with missing key field gracefully', () => {
    const local = [{ name: 'No ID Local' }];
    const server = [{ name: 'No ID Server' }];
    const result = mergeArrays(local, server, 'id');

    expect(result).toHaveLength(2);
  });
});

describe('filterJobs', () => {
  const jobs = [
    { id: '1', status: 'active', species: 'Racoon', town: 'Bedford' },
    { id: '2', status: 'completed', species: 'Squirrel', town: 'Bedford' },
    { id: '3', status: 'active', species: 'Bats', town: 'Salem' }
  ];

  it('returns all jobs when filters are empty or null', () => {
    expect(filterJobs(jobs, null)).toEqual(jobs);
    expect(filterJobs(jobs, {})).toEqual(jobs);
    expect(filterJobs(jobs, { status: '', species: '' })).toEqual(jobs);
  });

  it('filters jobs by a single criteria (case-insensitive)', () => {
    const result = filterJobs(jobs, { status: 'ACTIVE' });
    expect(result).toHaveLength(2);
    expect(result.map(j => j.id)).toEqual(['1', '3']);
  });

  it('filters jobs by multiple criteria', () => {
    const result = filterJobs(jobs, { status: 'active', town: 'bedford' });
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('1');
  });

  it('returns empty array if no jobs match filters', () => {
    const result = filterJobs(jobs, { species: 'Snake' });
    expect(result).toEqual([]);
  });

  it('handles non-array inputs gracefully', () => {
    expect(filterJobs(null, { status: 'active' })).toEqual([]);
  });
});

describe('searchJobs', () => {
  const jobs = [
    { id: '1', title: 'Attic Raccoon', customer: 'John Doe', species: 'Raccoon', town: 'Bedford', phone: '555-0199' },
    { id: '2', title: 'Soffit Repair', customer: 'Jane Smith', species: 'Squirrel', town: 'Salem', phone: '555-0200' },
  ];

  it('returns all jobs when query is empty or invalid', () => {
    expect(searchJobs(jobs, '')).toEqual(jobs);
    expect(searchJobs(jobs, null)).toEqual(jobs);
    expect(searchJobs(null, 'test')).toEqual([]);
  });

  it('searches across multiple fields case-insensitively', () => {
    expect(searchJobs(jobs, 'raccoon')).toHaveLength(1);
    expect(searchJobs(jobs, 'JOHN')).toHaveLength(1);
    expect(searchJobs(jobs, '555-0200')).toHaveLength(1);
    expect(searchJobs(jobs, 'soffit')).toHaveLength(1);
  });

  it('returns empty array when no match is found', () => {
    expect(searchJobs(jobs, 'nonexistent')).toEqual([]);
  });
});
