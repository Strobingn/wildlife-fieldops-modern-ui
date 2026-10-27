import { describe, it, expect } from 'vitest';
import {
  mergeArrays,
  filterJobs,
  money,
  formatDateShort,
  buildJobRelationCounts,
  jobCompletenessScore,
  pageDataChanged,
} from './utils.js';

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

describe('money', () => {
  it('formats USD and treats empty or invalid amounts as zero', () => {
    expect(money(1234.5)).toBe('$1,234.50');
    expect(money(0)).toBe('$0.00');
    expect(money(null)).toBe('$0.00');
    expect(money(undefined)).toBe('$0.00');
    expect(money('nope')).toBe('$0.00');
  });
});

describe('formatDateShort', () => {
  it('returns an em dash for empty or invalid dates', () => {
    expect(formatDateShort(null)).toBe('—');
    expect(formatDateShort(undefined)).toBe('—');
    expect(formatDateShort('')).toBe('—');
    expect(formatDateShort('not-a-date')).toBe('—');
  });

  it('labels today and a date 24 hours ahead', () => {
    const now = new Date();
    expect(formatDateShort(now)).toMatch(/^Today\s+/);
    const ahead = new Date(now.getTime() + 86400000);
    expect(formatDateShort(ahead)).toMatch(/^Tomorrow\s+/);
  });

  it('formats other days with a short month and day', () => {
    const past = new Date(2020, 4, 15, 14, 0);
    expect(formatDateShort(past)).toBe('May 15, 2:00 PM');
  });
});

describe('buildJobRelationCounts', () => {
  it('counts both jobId and job_id and scores from the index', () => {
    const state = {
      visits: [{ jobId: 'a' }, { job_id: 'a' }, { jobId: 'b' }],
      repairs: [{ job_id: 'b' }],
      photos: [{ jobId: 'a' }, { job_id: '' }],
      signatures: [{ jobId: 'a' }],
    };
    const counts = buildJobRelationCounts(state);
    expect(counts.visitCounts.get('a')).toBe(2);
    expect(counts.visitCounts.get('b')).toBe(1);
    expect(counts.repairCounts.get('b')).toBe(1);
    expect(counts.photoCounts.get('a')).toBe(1);
    expect(counts.photoCounts.has('')).toBe(false);
    expect(jobCompletenessScore('a', counts)).toBe(75);
    expect(jobCompletenessScore('b', counts)).toBe(50);
    expect(jobCompletenessScore('missing', counts)).toBe(0);
  });
});

describe('pageDataChanged', () => {
  const base = { page: 'jobs', jobs: ['a'], drawerOpen: false, toast: null, loading: false, syncStatus: 'idle', isOnline: true, lastSyncAt: null };

  it('ignores shell-only updates', () => {
    expect(pageDataChanged(base, { ...base, drawerOpen: true, toast: { message: 'hi' }, loading: true, syncStatus: 'syncing', isOnline: false, lastSyncAt: 'now' })).toBe(false);
  });

  it('notices page data and a missing previous snapshot', () => {
    expect(pageDataChanged(null, base)).toBe(true);
    expect(pageDataChanged(base, { ...base, jobs: ['b'] })).toBe(true);
    expect(pageDataChanged(base, { ...base, searchQuery: 'bat' })).toBe(true);
  });
});
