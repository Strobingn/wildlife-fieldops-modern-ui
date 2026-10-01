import { describe, it, expect } from 'vitest';
import { buildJobCounts, jobScore } from './main.js';

describe('buildJobCounts and jobScore optimization', () => {
  it('correctly calculates counts across visits, photos, repairs, and signatures', () => {
    const state = {
      visits: [
        { jobId: 'job1' },
        { job_id: 'job1' },
        { jobId: 'job2' },
      ],
      photos: [
        { jobId: 'job1' },
      ],
      repairs: [
        { jobId: 'job2' },
      ],
      signatures: [
        { job_id: 'job1' },
      ],
    };

    const counts = buildJobCounts(state);

    expect(counts.vCounts['job1']).toBe(2);
    expect(counts.vCounts['job2']).toBe(1);
    expect(counts.pCounts['job1']).toBe(1);
    expect(counts.pCounts['job2']).toBeUndefined();
    expect(counts.rCounts['job2']).toBe(1);
    expect(counts.sCounts['job1']).toBe(1);
  });

  it('calculates jobScore correctly using pre-computed counts map', () => {
    const state = {
      visits: [{ jobId: 'job1' }],
      photos: [{ jobId: 'job1' }],
      repairs: [{ jobId: 'job1' }],
      signatures: [{ jobId: 'job1' }],
    };

    const counts = buildJobCounts(state);

    expect(jobScore('job1', counts)).toBe(100);
    expect(jobScore('job2', counts)).toBe(0);
  });

  it('handles empty or missing state properties gracefully in buildJobCounts', () => {
    const counts = buildJobCounts({});
    expect(counts.vCounts).toEqual({});
    expect(counts.pCounts).toEqual({});
    expect(counts.rCounts).toEqual({});
    expect(counts.sCounts).toEqual({});
    expect(jobScore('job1', counts)).toBe(0);
  });
});
