import { describe, it, expect } from 'vitest';
import { filterJobs, searchJobs, mergeArrays } from './utils.js';

describe('utils collection helpers', () => {
  describe('filterJobs', () => {
    const jobs = [
      { id: '1', status: 'Active', species: 'Raccoon', town: 'Dallas' },
      { id: '2', status: 'Closed', species: 'Squirrel', town: 'Austin' },
      { id: '3', status: 'Active', species: 'Squirrel', town: 'Dallas' },
    ];

    it('returns original array reference or empty check when no filters are set', () => {
      expect(filterJobs(jobs, {})).toEqual(jobs);
      expect(filterJobs(jobs, { status: '', species: '' })).toEqual(jobs);
      expect(filterJobs(jobs, null)).toEqual(jobs);
    });

    it('filters correctly when single filter is active', () => {
      const active = filterJobs(jobs, { status: 'Active' });
      expect(active).toHaveLength(2);
      expect(active.map((j) => j.id)).toEqual(['1', '3']);
    });

    it('filters correctly when multiple filters are active (case insensitive)', () => {
      const active = filterJobs(jobs, { status: 'active', species: 'RACCOON' });
      expect(active).toHaveLength(1);
      expect(active[0].id).toEqual('1');
    });

    it('returns empty array when no matches found', () => {
      const result = filterJobs(jobs, { status: 'NonExistent' });
      expect(result).toEqual([]);
    });

    it('handles non-array inputs gracefully', () => {
      expect(filterJobs(null, {})).toEqual([]);
    });
  });

  describe('searchJobs', () => {
    const jobs = [
      { id: '1', title: 'Raccoon Removal', customer: 'Alice Smith', address: '123 Main St', town: 'Dallas' },
      { id: '2', title: 'Squirrel Exclusion', customer: 'Bob Jones', address: '456 Oak Ave', town: 'Austin' },
      { id: '3', title: null, customer: 'Charlie Brown', address: '789 Pine Rd', species: 'Bat' },
    ];

    it('returns original array when search query is empty', () => {
      expect(searchJobs(jobs, '')).toEqual(jobs);
      expect(searchJobs(jobs, '   ')).toEqual(jobs);
      expect(searchJobs(jobs, null)).toEqual(jobs);
    });

    it('searches across fields case-insensitively', () => {
      expect(searchJobs(jobs, 'raccoon')).toHaveLength(1);
      expect(searchJobs(jobs, 'alice')).toHaveLength(1);
      expect(searchJobs(jobs, 'DALLAS')).toHaveLength(1);
      expect(searchJobs(jobs, 'oak')).toHaveLength(1);
      expect(searchJobs(jobs, 'bat')).toHaveLength(1);
    });

    it('handles nullish/missing properties without throwing', () => {
      expect(searchJobs(jobs, 'charlie')).toHaveLength(1);
    });
  });

  describe('mergeArrays', () => {
    it('merges local and server arrays with server taking precedence', () => {
      const local = [
        { id: '1', name: 'Item 1', status: 'pending' },
        { id: '2', name: 'Item 2', status: 'pending' },
      ];
      const server = [
        { id: '2', name: 'Item 2 Updated', status: 'synced' },
        { id: '3', name: 'Item 3', status: 'synced' },
      ];

      const merged = mergeArrays(local, server, 'id');
      expect(merged).toHaveLength(3);
      expect(merged.find((i) => i.id === '1')).toEqual({ id: '1', name: 'Item 1', status: 'pending' });
      expect(merged.find((i) => i.id === '2')).toEqual({ id: '2', name: 'Item 2 Updated', status: 'synced' });
      expect(merged.find((i) => i.id === '3')).toEqual({ id: '3', name: 'Item 3', status: 'synced' });
    });

    it('handles empty local or server arrays', () => {
      const local = [{ id: '1' }];
      expect(mergeArrays(local, [], 'id')).toEqual([{ id: '1' }]);
      expect(mergeArrays([], [{ id: '2' }], 'id')).toEqual([{ id: '2' }]);
    });
  });
});
