import { describe, it, expect } from 'vitest';
import { mergeArrays, filterJobs, buildJobCounts, jobScore } from './utils.js';

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

describe('buildJobCounts and jobScore', () => {
  const sampleState = {
    visits: [
      { jobId: 'job-1' },
      { job_id: 'job-1' },
      { jobId: 'job-2' },
    ],
    repairs: [
      { job_id: 'job-1' },
    ],
    photos: [
      { jobId: 'job-1' },
      { jobId: 'job-3' },
    ],
    signatures: [
      { jobId: 'job-1' },
    ],
  };

  it('accurately indexes visit, repair, photo counts and signature presence', () => {
    const counts = buildJobCounts(sampleState);
    expect(counts.visitCounts.get('job-1')).toBe(2);
    expect(counts.visitCounts.get('job-2')).toBe(1);
    expect(counts.repairCounts.get('job-1')).toBe(1);
    expect(counts.photoCounts.get('job-1')).toBe(1);
    expect(counts.photoCounts.get('job-3')).toBe(1);
    expect(counts.signatureSet.has('job-1')).toBe(true);
    expect(counts.signatureSet.has('job-2')).toBe(false);
  });

  it('calculates jobScore correctly using pre-built counts', () => {
    const counts = buildJobCounts(sampleState);

    // job-1 has visits, repairs, photos, signature -> 100
    expect(jobScore('job-1', sampleState, counts)).toBe(100);

    // job-2 has visits -> 25
    expect(jobScore('job-2', sampleState, counts)).toBe(25);

    // job-3 has photos -> 25
    expect(jobScore('job-3', sampleState, counts)).toBe(25);

    // job-4 has nothing -> 0
    expect(jobScore('job-4', sampleState, counts)).toBe(0);
  });

  it('calculates jobScore correctly without counts fallback', () => {
    expect(jobScore('job-1', sampleState)).toBe(100);
    expect(jobScore('job-2', sampleState)).toBe(25);
    expect(jobScore('job-3', sampleState)).toBe(25);
    expect(jobScore('job-4', sampleState)).toBe(0);
  });
});
