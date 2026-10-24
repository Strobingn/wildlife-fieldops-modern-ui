import { describe, it, expect } from 'vitest';
import { JobList } from './components/JobList.js';
import { Dashboard } from './components/Dashboard.js';
import { MetricsPage } from './components/MetricsPage.js';

describe('JobList Component', () => {
  const sampleState = {
    jobs: [
      { id: 'job-1', title: 'Raccoon Attic Inspection', customer: 'John Doe', status: 'Active', species: 'Raccoon', town: 'Bedford', assigned_tech: 'Tech 1', phone: '555-0101' },
      { id: 'job-2', title: 'Squirrel Entry Removal', customer: 'Jane Smith', status: 'Closed', species: 'Grey Squirrel', town: 'Salem', assigned_tech: 'Tech 2', phone: '555-0202' },
    ],
    visits: [
      { id: 'v1', job_id: 'job-1', created_at: '2025-01-01' },
      { id: 'v2', job_id: 'job-1', created_at: '2025-01-02' },
    ],
    repairs: [
      { id: 'r1', job_id: 'job-1' },
    ],
    photos: [
      { id: 'p1', job_id: 'job-1' },
    ],
    signatures: [
      { id: 'sig1', job_id: 'job-1' },
    ],
  };

  it('renders job list with pre-indexed collection metrics', () => {
    const html = JobList.render(sampleState);
    expect(html).toContain('Raccoon Attic Inspection');
    expect(html).toContain('2 visits');
    expect(html).toContain('1 repairs');
    expect(html).toContain('1 photos');
  });

  it('filters jobs efficiently using status, species, tech, and town criteria', () => {
    JobList._filters = { status: 'Active', species: '', tech: '', town: '' };
    const filtered = JobList._filterJobs(sampleState.jobs);
    expect(filtered).toHaveLength(1);
    expect(filtered[0].id).toBe('job-1');

    JobList.unmount();
  });
});

describe('Dashboard Component', () => {
  const sampleState = {
    currentUser: { name: 'Alice' },
    jobs: [
      { id: 'j1', title: 'Bat Exclusion', species: 'Bat', status: 'Active', estimate: 800 },
    ],
    visits: [{ id: 'v1', job_id: 'j1' }],
    repairs: [],
    photos: [{ id: 'p1', job_id: 'j1' }],
    signatures: [],
  };

  it('renders dashboard with stats and recent job cards', () => {
    const html = Dashboard.render(sampleState);
    expect(html).toContain('Good');
    expect(html).toContain('Bat Exclusion');
    expect(html).toContain('Active Jobs');
  });
});

describe('MetricsPage Component', () => {
  const sampleState = {
    jobs: [
      { id: 'j1', created_at: '2025-01-01T10:00:00Z', status: 'Closed', species: 'Bat', grand_total: 1000 },
    ],
    visits: [
      { id: 'v1', job_id: 'j1', date: '2025-01-03T10:00:00Z' },
    ],
    expenses: [
      { amount: 150 },
    ],
  };

  it('computes response times and revenue metrics efficiently using pre-indexed maps', () => {
    const html = MetricsPage.render(sampleState);
    expect(html).toContain('Business Metrics');
    expect(html).toContain('2.0d'); // 2 days response time
    expect(html).toContain('$1,000');
  });
});
