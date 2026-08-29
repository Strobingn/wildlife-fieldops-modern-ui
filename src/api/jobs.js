/**
 * @module api/jobs
 * @description Job CRUD operations with offline-first support, optimistic updates,
 * rollback on failure, and local cache management.
 */

import { supabase, applyJobFilters } from './supabaseClient.js';
import { syncQueue } from './sync.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const LOCAL_JOBS_KEY = 'ww_fieldops_jobs_cache';
const JOB_STATUS = ['Active', 'Scheduled', 'In Progress', 'Needs Follow-up', 'Closed', 'Cancelled'];
const VALID_SPECIES = [
  'Raccoon',
  'Grey Squirrel',
  'Red Squirrel',
  'Flying Squirrel',
  'Bat',
  'Skunk',
  'Groundhog',
  'Bird',
  'Snake',
  'Opossum',
  'Rodent',
  'Carpenter Bee',
  'Other'
];

// ─── Local Cache Helpers ─────────────────────────────────────────────────────

function loadLocalCache() {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_JOBS_KEY) || '[]');
  } catch {
    return [];
  }
}

function saveLocalCache(jobs) {
  localStorage.setItem(LOCAL_JOBS_KEY, JSON.stringify(jobs));
}

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// ─── Validation ──────────────────────────────────────────────────────────────

function validateJobId(id) {
  if (!id || typeof id !== 'string') {
    throw new Error('Job ID is required and must be a string');
  }
}

function validateJobData(data) {
  const errors = [];
  if (!data.customer || data.customer.trim().length < 2) {
    errors.push('Customer name is required (min 2 characters)');
  }
  if (!data.address || data.address.trim().length < 5) {
    errors.push('Address is required (min 5 characters)');
  }
  if (data.phone && !/^\+?[\d\s\-()]{10,15}$/.test(data.phone.replace(/\s/g, ''))) {
    errors.push('Invalid phone number format');
  }
  if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
    errors.push('Invalid email format');
  }
  if (data.status && !JOB_STATUS.includes(data.status)) {
    errors.push(`Status must be one of: ${JOB_STATUS.join(', ')}`);
  }
  if (data.species && !VALID_SPECIES.includes(data.species)) {
    errors.push(`Species must be one of: ${VALID_SPECIES.join(', ')}`);
  }
  if (data.latitude && (isNaN(data.latitude) || Math.abs(parseFloat(data.latitude)) > 90)) {
    errors.push('Invalid latitude');
  }
  if (data.longitude && (isNaN(data.longitude) || Math.abs(parseFloat(data.longitude)) > 180)) {
    errors.push('Invalid longitude');
  }
  return errors;
}

// ─── Core CRUD ───────────────────────────────────────────────────────────────

/**
 * Fetch jobs with optional filters. Falls back to local cache when offline.
 *
 * @param {{status?: string, species?: string, tech?: string, town?: string, dateFrom?: string, dateTo?: string, limit?: number, offset?: number}} filters
 * @returns {Promise<Array>} Jobs array
 */
export async function getJobs(filters = {}) {
  try {
    if (!navigator.onLine) {
      console.log('[jobs] Offline — returning cached jobs');
      const cached = loadLocalCache();
      return applyLocalFilters(cached, filters);
    }

    let query = supabase.from('jobs').select('*, services(*), photos(*), inspections(*)');

    query = applyJobFilters(query, filters);

    if (filters.limit) {
      query = query.limit(filters.limit);
    }
    if (filters.offset) {
      query = query.range(filters.offset, filters.offset + (filters.limit || 50) - 1);
    }

    const { data, error } = await query.order('created_at', { ascending: false });

    if (error) throw error;

    // Update local cache
    if (data) {
      saveLocalCache(data);
    }
    return data || [];
  } catch (err) {
    console.error('[jobs] getJobs error:', err.message);
    // Fallback to cache on error
    const cached = loadLocalCache();
    return applyLocalFilters(cached, filters);
  }
}

/**
 * Fetch a single job by ID.
 *
 * @param {string} id
 * @returns {Promise<Object|null>}
 */
export async function getJobById(id) {
  validateJobId(id);

  try {
    if (!navigator.onLine) {
      const cached = loadLocalCache();
      return cached.find(j => j.id === id) || null;
    }

    const { data, error } = await supabase
      .from('jobs')
      .select('*, services(*), photos(*), inspections(*)')
      .eq('id', id)
      .single();

    if (error) throw error;
    return data;
  } catch (err) {
    console.error(`[jobs] getJobById(${id}) error:`, err.message);
    const cached = loadLocalCache();
    return cached.find(j => j.id === id) || null;
  }
}

/**
 * Create a new job. Optimistic update with sync queue + rollback support.
 *
 * @param {Object} jobData
 * @returns {Promise<Object>} The created job (optimistic or confirmed)
 */
export async function createJob(jobData) {
  const errors = validateJobData(jobData);
  if (errors.length > 0) {
    throw new Error(`Validation failed: ${errors.join('; ')}`);
  }

  const now = new Date().toISOString();
  const tempId = jobData.id || generateId();
  const newJob = {
    id: tempId,
    customer: jobData.customer.trim(),
    phone: jobData.phone?.trim() || null,
    email: jobData.email?.trim().toLowerCase() || null,
    address: jobData.address.trim(),
    town: jobData.town?.trim() || null,
    state: jobData.state?.trim() || null,
    zip: jobData.zip?.trim() || null,
    species: jobData.species || null,
    status: jobData.status || 'Active',
    priority: jobData.priority || 'Normal',
    assigned_tech: jobData.assigned_tech || null,
    notes: jobData.notes?.trim() || null,
    scope: jobData.scope?.trim() || null,
    warranty: jobData.warranty || 'Not set',
    estimate: parseFloat(jobData.estimate) || 0,
    subtotal: parseFloat(jobData.subtotal) || 0,
    tax_rate: parseFloat(jobData.tax_rate) || 0,
    tax_amount: parseFloat(jobData.tax_amount) || 0,
    grand_total: parseFloat(jobData.grand_total) || 0,
    deposit_paid: parseFloat(jobData.deposit_paid) || 0,
    balance_due: parseFloat(jobData.balance_due) || 0,
    latitude: jobData.latitude ? String(jobData.latitude) : null,
    longitude: jobData.longitude ? String(jobData.longitude) : null,
    accuracy: jobData.accuracy || null,
    scheduled_start: jobData.scheduled_start || null,
    scheduled_end: jobData.scheduled_end || null,
    is_recurring: jobData.is_recurring || false,
    recurrence_pattern: jobData.recurrence_pattern || null,
    parent_job_id: jobData.parent_job_id || null,
    created_at: now,
    updated_at: now
  };

  // Optimistic: add to local cache immediately
  const cached = loadLocalCache();
  cached.unshift(newJob);
  saveLocalCache(cached);

  try {
    if (!navigator.onLine) {
      // Offline: queue for later sync
      syncQueue.enqueue({
        table: 'jobs',
        action: 'insert',
        payload: newJob
      });
      return { ...newJob, _syncStatus: 'pending' };
    }

    // Online: try direct Supabase insert
    const { data, error } = await supabase.from('jobs').insert(newJob).select().single();

    if (error) throw error;

    // Update cache with server-confirmed data (has proper UUID, triggers, etc.)
    if (data) {
      const updatedCache = loadLocalCache().map(j => (j.id === tempId ? data : j));
      saveLocalCache(updatedCache);
      return { ...data, _syncStatus: 'synced' };
    }

    return { ...newJob, _syncStatus: 'pending' };
  } catch (err) {
    console.error('[jobs] createJob error:', err.message);
    // Queue for retry and keep optimistic data
    syncQueue.enqueue({
      table: 'jobs',
      action: 'insert',
      payload: newJob
    });
    return { ...newJob, _syncStatus: 'pending', _error: err.message };
  }
}

/**
 * Update an existing job. Optimistic update with rollback on failure.
 *
 * @param {string} id
 * @param {Object} updates
 * @returns {Promise<Object>}
 */
export async function updateJob(id, updates) {
  validateJobId(id);

  if (!updates || Object.keys(updates).length === 0) {
    throw new Error('No update fields provided');
  }

  // Validate any status/species in updates
  const errors = validateJobData(updates);
  if (errors.length > 0) {
    throw new Error(`Validation failed: ${errors.join('; ')}`);
  }

  // Build clean updates
  const cleanUpdates = { ...updates, updated_at: new Date().toISOString() };
  const fieldsToClean = ['customer', 'phone', 'email', 'address', 'town', 'state', 'zip', 'notes', 'scope'];
  fieldsToClean.forEach(f => {
    if (typeof cleanUpdates[f] === 'string') cleanUpdates[f] = cleanUpdates[f].trim();
    if (cleanUpdates[f] === '') cleanUpdates[f] = null;
  });
  if (cleanUpdates.email) cleanUpdates.email = cleanUpdates.email.toLowerCase();

  // Store pre-update state for rollback
  const cached = loadLocalCache();
  const preUpdateJob = cached.find(j => j.id === id);
  const preUpdateState = preUpdateJob ? { ...preUpdateJob } : null;

  // Optimistic: update local cache
  const updatedCache = cached.map(j => (j.id === id ? { ...j, ...cleanUpdates } : j));
  saveLocalCache(updatedCache);

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({
        table: 'jobs',
        action: 'update',
        payload: { id, ...cleanUpdates }
      });
      return { id, ...cleanUpdates, _syncStatus: 'pending' };
    }

    const { data, error } = await supabase.from('jobs').update(cleanUpdates).eq('id', id).select().single();

    if (error) throw error;
    return { ...data, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[jobs] updateJob(${id}) error:`, err.message);

    // Rollback: restore pre-update state
    if (preUpdateState) {
      const rollbackCache = loadLocalCache().map(j => (j.id === id ? preUpdateState : j));
      saveLocalCache(rollbackCache);
      console.log(`[jobs] updateJob(${id}): rolled back optimistic update`);
    }

    // Queue for retry
    syncQueue.enqueue({
      table: 'jobs',
      action: 'update',
      payload: { id, ...cleanUpdates }
    });

    throw new Error(`Failed to update job ${id}: ${err.message}`);
  }
}

/**
 * Soft-delete a job by setting status to 'Cancelled'.
 *
 * @param {string} id
 * @returns {Promise<Object>}
 */
export async function deleteJob(id) {
  validateJobId(id);

  const cached = loadLocalCache();
  const preDeleteJob = cached.find(j => j.id === id);

  // Optimistic: mark as Cancelled in local cache
  const updatedCache = cached.map(j =>
    j.id === id ? { ...j, status: 'Cancelled', updated_at: new Date().toISOString() } : j
  );
  saveLocalCache(updatedCache);

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({
        table: 'jobs',
        action: 'update',
        payload: { id, status: 'Cancelled', updated_at: new Date().toISOString() }
      });
      return { id, status: 'Cancelled', _syncStatus: 'pending' };
    }

    const { data, error } = await supabase
      .from('jobs')
      .update({ status: 'Cancelled', updated_at: new Date().toISOString() })
      .eq('id', id)
      .select()
      .single();

    if (error) throw error;
    return { ...data, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[jobs] deleteJob(${id}) error:`, err.message);

    // Rollback
    if (preDeleteJob) {
      const rollbackCache = loadLocalCache().map(j => (j.id === id ? preDeleteJob : j));
      saveLocalCache(rollbackCache);
    }

    syncQueue.enqueue({
      table: 'jobs',
      action: 'update',
      payload: { id, status: 'Cancelled', updated_at: new Date().toISOString() }
    });

    throw new Error(`Failed to delete job ${id}: ${err.message}`);
  }
}

// ─── Search ──────────────────────────────────────────────────────────────────

/**
 * Full-text search across jobs.
 *
 * @param {string} query
 * @returns {Promise<Array>}
 */
export async function searchJobs(query) {
  if (!query || query.trim().length < 1) {
    return getJobs();
  }

  const term = query.trim().toLowerCase();

  try {
    if (!navigator.onLine) {
      const cached = loadLocalCache();
      return filterBySearchTerm(cached, term);
    }

    // Use Supabase ilike across multiple fields
    const { data, error } = await supabase
      .from('jobs')
      .select('*, services(*), photos(*), inspections(*)')
      .or(
        `customer.ilike.%${term}%,address.ilike.%${term}%,town.ilike.%${term}%,species.ilike.%${term}%,status.ilike.%${term}%,notes.ilike.%${term}%,assigned_tech.ilike.%${term}%`
      )
      .order('created_at', { ascending: false });

    if (error) throw error;
    return data || [];
  } catch (err) {
    console.error(`[jobs] searchJobs("${query}") error:`, err.message);
    const cached = loadLocalCache();
    return filterBySearchTerm(cached, term);
  }
}

// ─── Aggregation Queries ─────────────────────────────────────────────────────

/**
 * Get dashboard statistics.
 *
 * @returns {Promise<Object>}
 */
export async function getJobStats() {
  try {
    if (!navigator.onLine) {
      return computeLocalStats(loadLocalCache());
    }

    // Try the database view first
    const { data, error } = await supabase.from('job_stats').select('*').single();

    if (error) {
      // Fallback: compute locally from fetched data
      const jobs = await getJobs();
      return computeLocalStats(jobs);
    }

    return data || computeLocalStats(loadLocalCache());
  } catch (err) {
    console.error('[jobs] getJobStats error:', err.message);
    return computeLocalStats(loadLocalCache());
  }
}

/**
 * Get jobs grouped by species.
 *
 * @returns {Promise<Array<{species: string, count: number, value: number}>>}
 */
export async function getJobsBySpecies() {
  try {
    const { data, error } = await supabase.from('species_stats').select('*');
    if (error) throw error;
    if (data) {
      return data.map(r => ({ species: r.species, count: r.job_count, value: r.quoted_value }));
    }
  } catch (err) {
    console.error('[jobs] getJobsBySpecies error:', err.message);
  }

  // Fallback: compute locally
  const jobs = loadLocalCache();
  const grouped = {};
  jobs.forEach(j => {
    const s = j.species || 'Unknown';
    if (!grouped[s]) grouped[s] = { species: s, count: 0, value: 0 };
    grouped[s].count++;
    grouped[s].value += parseFloat(j.grand_total) || 0;
  });
  return Object.values(grouped).sort((a, b) => b.count - a.count);
}

/**
 * Get jobs grouped by status.
 *
 * @returns {Promise<Array<{status: string, count: number, value: number}>>}
 */
export async function getJobsByStatus() {
  try {
    if (!navigator.onLine) throw new Error('Offline');

    const { data, error } = await supabase.rpc('get_jobs_by_status');
    if (!error && data) return data;
  } catch {
    // fallthrough to local
  }

  const jobs = loadLocalCache();
  const grouped = {};
  JOB_STATUS.forEach(s => (grouped[s] = { status: s, count: 0, value: 0 }));
  jobs.forEach(j => {
    const s = j.status || 'Active';
    if (!grouped[s]) grouped[s] = { status: s, count: 0, value: 0 };
    grouped[s].count++;
    grouped[s].value += parseFloat(j.grand_total) || 0;
  });
  return Object.values(grouped)
    .filter(g => g.count > 0)
    .sort((a, b) => b.count - a.count);
}

/**
 * Get jobs grouped by town.
 *
 * @returns {Promise<Array<{town: string, count: number, species: Object}>>}
 */
export async function getJobsByTown() {
  try {
    if (!navigator.onLine) throw new Error('Offline');

    const { data, error } = await supabase.from('jobs').select('town, species, grand_total');

    if (error) throw error;
    return aggregateByTown(data || []);
  } catch (err) {
    console.error('[jobs] getJobsByTown error:', err.message);
    return aggregateByTown(loadLocalCache());
  }
}

/**
 * Get revenue grouped by technician.
 *
 * @returns {Promise<Array<{tech: string, jobCount: number, activeJobs: number, revenue: number}>>}
 */
export async function getRevenueByTech() {
  try {
    if (!navigator.onLine) throw new Error('Offline');

    const { data, error } = await supabase.from('tech_stats').select('*');
    if (error) throw error;
    if (data) {
      return data.map(r => ({
        tech: r.assigned_tech,
        jobCount: r.job_count,
        activeJobs: r.active_job_count,
        revenue: r.quoted_value
      }));
    }
  } catch (err) {
    console.error('[jobs] getRevenueByTech error:', err.message);
  }

  // Fallback
  const jobs = loadLocalCache();
  const grouped = {};
  jobs.forEach(j => {
    const t = j.assigned_tech || 'Unassigned';
    if (!grouped[t]) grouped[t] = { tech: t, jobCount: 0, activeJobs: 0, revenue: 0 };
    grouped[t].jobCount++;
    if (j.status !== 'Closed') grouped[t].activeJobs++;
    grouped[t].revenue += parseFloat(j.grand_total) || 0;
  });
  return Object.values(grouped).sort((a, b) => b.revenue - a.revenue);
}

// ─── Local Helpers ───────────────────────────────────────────────────────────

function applyLocalFilters(jobs, filters) {
  return jobs.filter(j => {
    if (filters.status && j.status !== filters.status) return false;
    if (filters.species && j.species !== filters.species) return false;
    if (filters.tech && j.assigned_tech !== filters.tech) return false;
    if (filters.town && !(j.town || '').toLowerCase().includes(filters.town.toLowerCase())) return false;
    if (filters.dateFrom && j.created_at && j.created_at < filters.dateFrom) return false;
    if (filters.dateTo && j.created_at && j.created_at > filters.dateTo) return false;
    return true;
  });
}

function filterBySearchTerm(jobs, term) {
  return jobs.filter(j => {
    const haystack =
      `${j.customer || ''} ${j.address || ''} ${j.town || ''} ${j.species || ''} ${j.status || ''} ${j.notes || ''} ${j.assigned_tech || ''}`.toLowerCase();
    return haystack.includes(term);
  });
}

function computeLocalStats(jobs) {
  const active = jobs.filter(j => j.status !== 'Closed');
  const totalValue = jobs.reduce((sum, j) => sum + (parseFloat(j.grand_total) || 0), 0);
  return {
    total_jobs: jobs.length,
    active_jobs: active.length,
    closed_jobs: jobs.filter(j => j.status === 'Closed').length,
    quoted_value: Math.round(totalValue * 100) / 100
  };
}

function aggregateByTown(jobs) {
  const grouped = {};
  jobs.forEach(j => {
    const t = j.town || 'Unsorted';
    if (!grouped[t]) grouped[t] = { town: t, count: 0, value: 0, species: {} };
    grouped[t].count++;
    grouped[t].value += parseFloat(j.grand_total) || 0;
    const s = j.species || 'Unknown';
    grouped[t].species[s] = (grouped[t].species[s] || 0) + 1;
  });
  return Object.values(grouped).sort((a, b) => b.count - a.count);
}

// ─── Service Operations ──────────────────────────────────────────────────────

/**
 * Add a service line item to a job.
 *
 * @param {string} jobId
 * @param {Object} service - { service, qty, unit_price, notes }
 * @returns {Promise<Object>}
 */
export async function addService(jobId, service) {
  if (!jobId) throw new Error('Job ID is required');
  if (!service.service?.trim()) throw new Error('Service description is required');

  const payload = {
    id: generateId(),
    job_id: jobId,
    service: service.service.trim(),
    qty: parseFloat(service.qty) || 1,
    unit_price: parseFloat(service.unit_price) || 0,
    total: (parseFloat(service.qty) || 1) * (parseFloat(service.unit_price) || 0),
    notes: service.notes?.trim() || null,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'services', action: 'insert', payload });
      return { ...payload, _syncStatus: 'pending' };
    }

    const { data, error } = await supabase.from('services').insert(payload).select().single();
    if (error) throw error;
    return { ...data, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[jobs] addService(${jobId}) error:`, err.message);
    syncQueue.enqueue({ table: 'services', action: 'insert', payload });
    return { ...payload, _syncStatus: 'pending' };
  }
}

/**
 * Remove a service line item.
 *
 * @param {string} serviceId
 * @returns {Promise<Object>}
 */
export async function removeService(serviceId) {
  if (!serviceId) throw new Error('Service ID is required');

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'services', action: 'delete', payload: { id: serviceId } });
      return { id: serviceId, _syncStatus: 'pending' };
    }

    const { data, error } = await supabase.from('services').delete().eq('id', serviceId).select().single();
    if (error) throw error;
    return { ...data, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[jobs] removeService(${serviceId}) error:`, err.message);
    syncQueue.enqueue({ table: 'services', action: 'delete', payload: { id: serviceId } });
    return { id: serviceId, _syncStatus: 'pending' };
  }
}
