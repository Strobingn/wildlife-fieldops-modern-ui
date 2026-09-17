import { describe, it, expect } from 'vitest';
import { JobList } from './JobList.js';

describe('JobList component', () => {
  it('renders job list with pre-indexed stats correctly', () => {
    const mockState = {
      jobs: [
        { id: 'job_1', species: 'Raccoon', title: 'Raccoon Removal', status: 'Active', customer: 'John Doe', phone: '555-1234', address: '123 Main St' },
        { id: 'job_2', species: 'Bat', title: 'Bat Exclusion', status: 'Closed', customer: 'Jane Smith', phone: '555-5678', address: '456 Oak Ave' },
      ],
      visits: [{ job_id: 'job_1' }],
      repairs: [{ job_id: 'job_1' }],
      photos: [{ job_id: 'job_1' }, { job_id: 'job_2' }],
      signatures: [{ job_id: 'job_1' }],
    };

    const html = JobList.render(mockState);
    expect(html).toContain('Raccoon Removal');
    expect(html).toContain('Bat Exclusion');
    expect(html).toContain('Score 100%'); // job_1 has visits, repairs, photos, signatures -> 100%
    expect(html).toContain('Score 25%');  // job_2 has only photos -> 25%
  });
});
