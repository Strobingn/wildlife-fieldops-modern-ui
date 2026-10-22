import { describe, it, expect } from 'vitest';
import { mergeArrays, filterJobs, buildJobRelationCounts } from './utils.js';

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

describe('buildJobRelationCounts', () => {
  it('handles empty state gracefully', () => {
    const counts = buildJobRelationCounts(null);
    expect(counts.visitCounts.size).toBe(0);
    expect(counts.repairCounts.size).toBe(0);
    expect(counts.photoCounts.size).toBe(0);
    expect(counts.signatureSet.size).toBe(0);
  });

  it('correctly aggregates counts and sets across relations with both jobId and job_id fields', () => {
    const state = {
      visits: [
        { id: 'v1', jobId: 'job-1' },
        { id: 'v2', job_id: 'job-1' },
        { id: 'v3', jobId: 'job-2' },
      ],
      repairs: [
        { id: 'r1', jobId: 'job-1' },
      ],
      photos: [
        { id: 'p1', job_id: 'job-2' },
        { id: 'p2', job_id: 'job-2' },
      ],
      signatures: [
        { id: 'sig1', jobId: 'job-1' },
        { id: 'sig2', job_id: 'job-1' },
      ]
    };

    const counts = buildJobRelationCounts(state);

    expect(counts.visitCounts.get('job-1')).toBe(2);
    expect(counts.visitCounts.get('job-2')).toBe(1);
    expect(counts.visitCounts.get('job-3')).toBeUndefined();

    expect(counts.repairCounts.get('job-1')).toBe(1);
    expect(counts.repairCounts.get('job-2')).toBeUndefined();

    expect(counts.photoCounts.get('job-2')).toBe(2);

    expect(counts.signatureSet.has('job-1')).toBe(true);
    expect(counts.signatureSet.has('job-2')).toBe(false);
  });
});
