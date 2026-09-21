import { describe, it, expect } from 'vitest';
import { E, groupBy, mergeArrays, searchJobs, filterJobs } from './utils.js';

describe('src/utils.js performance & functionality', () => {
  describe('E() HTML escape', () => {
    it('escapes special characters correctly', () => {
      expect(E('<script>alert("xss") & \'foo\'</script>')).toBe(
        '&lt;script&gt;alert(&quot;xss&quot;) &amp; &#39;foo&#39;&lt;/script&gt;'
      );
    });

    it('handles null, undefined, and numbers safely', () => {
      expect(E(null)).toBe('');
      expect(E(undefined)).toBe('');
      expect(E(123)).toBe('123');
    });
  });

  describe('groupBy()', () => {
    it('groups array of objects by key', () => {
      const input = [
        { id: 1, town: 'Albany' },
        { id: 2, town: 'Troy' },
        { id: 3, town: 'Albany' },
      ];
      const grouped = groupBy(input, 'town');
      expect(grouped).toEqual({
        Albany: [{ id: 1, town: 'Albany' }, { id: 3, town: 'Albany' }],
        Troy: [{ id: 2, town: 'Troy' }],
      });
    });

    it('returns empty object for invalid inputs', () => {
      expect(groupBy(null, 'key')).toEqual({});
      expect(groupBy([], 'key')).toEqual({});
    });
  });

  describe('mergeArrays()', () => {
    it('merges local and server arrays with server precedence', () => {
      const local = [
        { id: '1', title: 'Local 1' },
        { id: '2', title: 'Local 2' },
      ];
      const server = [
        { id: '2', title: 'Server 2 (updated)' },
        { id: '3', title: 'Server 3' },
      ];
      const merged = mergeArrays(local, server, 'id');
      expect(merged).toHaveLength(3);
      expect(merged.find((item) => item.id === '2').title).toBe('Server 2 (updated)');
      expect(merged.find((item) => item.id === '3').title).toBe('Server 3');
    });

    it('preserves items that do not contain the key property', () => {
      const local = [
        { title: 'Local item without key' },
        { id: '1', title: 'Local 1' },
      ];
      const server = [
        { title: 'Server item without key' },
        { id: '1', title: 'Server 1 updated' },
      ];
      const merged = mergeArrays(local, server, 'id');
      expect(merged).toHaveLength(3);
      expect(merged).toContainEqual({ title: 'Local item without key' });
      expect(merged).toContainEqual({ title: 'Server item without key' });
    });
  });

  describe('searchJobs()', () => {
    it('searches across jobs by query term', () => {
      const jobs = [
        { id: '1', customer_name: 'John Doe', species: 'Raccoon' },
        { id: '2', customer_name: 'Jane Smith', species: 'Bat' },
      ];
      expect(searchJobs(jobs, 'john')).toHaveLength(1);
      expect(searchJobs(jobs, 'john')[0].id).toBe('1');
      expect(searchJobs(jobs, 'bat')).toHaveLength(1);
      expect(searchJobs(jobs, 'bat')[0].id).toBe('2');
      expect(searchJobs(jobs, '')).toEqual(jobs);
    });
  });

  describe('filterJobs()', () => {
    it('returns original array when filters are empty or falsy', () => {
      const jobs = [{ id: '1', status: 'Active' }, { id: '2', status: 'Closed' }];
      expect(filterJobs(jobs, {})).toBe(jobs);
      expect(filterJobs(jobs, { status: '', species: '' })).toBe(jobs);
    });

    it('filters jobs based on active filters case-insensitively', () => {
      const jobs = [
        { id: '1', status: 'Active', species: 'Squirrel' },
        { id: '2', status: 'Closed', species: 'Squirrel' },
        { id: '3', status: 'Active', species: 'Raccoon' },
      ];
      const filtered = filterJobs(jobs, { status: 'active', species: 'squirrel' });
      expect(filtered).toHaveLength(1);
      expect(filtered[0].id).toBe('1');
    });
  });
});
