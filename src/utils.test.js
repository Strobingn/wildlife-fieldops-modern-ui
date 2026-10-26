import { describe, it, expect } from 'vitest';
import { mergeArrays, filterJobs, buildJobStatsMap } from './utils.js';

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

describe('buildJobStatsMap', () => {
  it('returns empty Map for empty inputs', () => {
    const map = buildJobStatsMap();
    expect(map.size).toBe(0);
  });

  it('correctly aggregates counts and calculates score per job_id', () => {
    const visits = [{ job_id: 'j1' }, { job_id: 'j1' }, { jobId: 'j2' }];
    const repairs = [{ job_id: 'j1' }];
    const photos = [{ jobId: 'j2' }];
    const signatures = [{ job_id: 'j1' }];

    const map = buildJobStatsMap(visits, repairs, photos, signatures);

    expect(map.size).toBe(2);

    const j1 = map.get('j1');
    expect(j1).toEqual({
      visits: 2,
      repairs: 1,
      photos: 0,
      hasSig: true,
      score: 75, // 25 (visits) + 25 (repairs) + 25 (sig)
    });

    const j2 = map.get('j2');
    expect(j2).toEqual({
      visits: 1,
      repairs: 0,
      photos: 1,
      hasSig: false,
      score: 50, // 25 (visits) + 25 (photos)
    });
  });

  it('caps job completeness score at 100', () => {
    const visits = [{ job_id: 'j1' }];
    const repairs = [{ job_id: 'j1' }];
    const photos = [{ job_id: 'j1' }];
    const signatures = [{ job_id: 'j1' }];

    const map = buildJobStatsMap(visits, repairs, photos, signatures);
    expect(map.get('j1').score).toBe(100);
  });
});
