import { describe, it, expect, beforeEach, beforeAll } from 'vitest';

beforeAll(() => {
  if (typeof globalThis.window === 'undefined') {
    const mockElement = {
      addEventListener: () => {},
      removeEventListener: () => {},
      querySelector: () => null,
      querySelectorAll: () => [],
      style: {},
      classList: { toggle: () => {}, add: () => {}, remove: () => {} },
      setAttribute: () => {},
      appendChild: () => {},
    };

    globalThis.window = {
      addEventListener: () => {},
      removeEventListener: () => {},
      location: { hash: '' },
      open: () => {},
    };
    globalThis.document = {
      addEventListener: () => {},
      removeEventListener: () => {},
      querySelector: () => null,
      querySelectorAll: () => [],
      getElementById: () => null,
      createElement: () => mockElement,
      body: mockElement,
      head: mockElement,
      readyState: 'complete',
    };
    Object.defineProperty(globalThis, 'navigator', {
      value: { onLine: true },
      writable: true,
      configurable: true,
    });
    globalThis.localStorage = {
      getItem: () => null,
      setItem: () => {},
      removeItem: () => {},
    };
  }
});

describe('UI pages rendering with pre-aggregated job counts', () => {
  let pages;
  let sampleState;

  beforeAll(async () => {
    const mainModule = await import('./main.js');
    pages = mainModule.pages;
  });

  beforeEach(() => {
    sampleState = {
      jobs: [
        {
          id: 'job-1',
          customer_name: 'John Doe',
          title: 'Raccoon Removal',
          species: 'Raccoon',
          status: 'Active',
          priority: 'High',
          address: '123 Main St',
          town: 'Springfield',
          phone: '555-123-4567',
          grand_total: 500,
        },
        {
          id: 'job-2',
          customer_name: 'Jane Smith',
          title: 'Bat Exclusion',
          species: 'Bat',
          status: 'Scheduled',
          priority: 'Normal',
          address: '456 Elm St',
          town: 'Springfield',
          phone: '555-987-6543',
          grand_total: 800,
        },
      ],
      visits: [
        { id: 'v1', jobId: 'job-1', type: 'Initial', animals: 1 },
        { id: 'v2', jobId: 'job-1', type: 'Follow-up', animals: 0 },
      ],
      repairs: [
        { id: 'r1', jobId: 'job-1', location: 'Roof', status: 'Completed', severity: 'High' },
      ],
      photos: [
        { id: 'p1', jobId: 'job-1', tag: 'Damage', image_url: 'http://example.com/p1.jpg' },
        { id: 'p2', jobId: 'job-2', tag: 'Entry', image_url: 'http://example.com/p2.jpg' },
      ],
      signatures: [
        { id: 's1', jobId: 'job-1', data: 'data:image/png...' },
      ],
      syncQueue: [],
      reminders: [],
      searchQuery: '',
      filters: { status: '', species: '' },
      weatherCache: null,
    };
  });

  it('renders Dashboard correctly with pre-aggregated counts and score', () => {
    const html = pages.dashboard.render(sampleState);
    expect(html).toContain('John Doe');
    expect(html).toContain('2 visits');
    expect(html).toContain('1 repairs');
    expect(html).toContain('1 photos');
    // job-1 has visits, repairs, photos, signatures -> score = 100%
    expect(html).toContain('Score 100%');
  });

  it('renders JobList correctly with pre-aggregated counts and score', () => {
    const html = pages.jobs.render(sampleState);
    expect(html).toContain('John Doe');
    expect(html).toContain('Jane Smith');
    expect(html).toContain('Score 100%');
    // job-2 has photos only -> score = 25%
    expect(html).toContain('Score 25%');
  });

  it('efficiently renders lists with 100 jobs and 1000 items in collections', () => {
    const manyJobs = [];
    const manyVisits = [];
    const manyPhotos = [];
    const manyRepairs = [];
    const manySignatures = [];

    for (let i = 0; i < 100; i++) {
      const jobId = `job-${i}`;
      manyJobs.push({
        id: jobId,
        customer_name: `Customer ${i}`,
        species: 'Raccoon',
        status: 'Active',
        address: `${i} Main St`,
        phone: '555-000-0000',
        updated_at: new Date().toISOString(),
      });
      manyVisits.push({ id: `v-${i}`, jobId });
      manyPhotos.push({ id: `p-${i}`, jobId });
      manyRepairs.push({ id: `r-${i}`, jobId });
      manySignatures.push({ id: `s-${i}`, jobId });
    }

    const largeState = {
      ...sampleState,
      jobs: manyJobs,
      visits: manyVisits,
      photos: manyPhotos,
      repairs: manyRepairs,
      signatures: manySignatures,
    };

    const start = performance.now();
    const html = pages.jobs.render(largeState);
    const duration = performance.now() - start;

    expect(html).toContain('Customer 99');
    expect(duration).toBeLessThan(100);
  });
});
