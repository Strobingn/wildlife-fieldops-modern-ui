/**
 * @module api/customers
 * @description Customer CRUD operations with job history, duplicate detection,
 * and merge capability. All operations are offline-first with sync queue fallback.
 */

import { supabase } from './supabaseClient.js';
import { syncQueue } from './sync.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const LOCAL_CUSTOMERS_KEY = 'ww_fieldops_customers_cache';

// ─── Local Cache Helpers ─────────────────────────────────────────────────────

function loadCache() {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_CUSTOMERS_KEY) || '[]');
  } catch {
    return [];
  }
}

function saveCache(customers) {
  localStorage.setItem(LOCAL_CUSTOMERS_KEY, JSON.stringify(customers));
}

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// ─── Validation ──────────────────────────────────────────────────────────────

function validateCustomerId(id) {
  if (!id || typeof id !== 'string') throw new Error('Customer ID is required and must be a string');
}

function validateCustomerData(data, isUpdate = false) {
  const errors = [];
  if (!isUpdate && (!data.name || data.name.trim().length < 2)) {
    errors.push('Customer name is required (min 2 characters)');
  }
  if (data.phone && !/^\+?[\d\s\-()]{10,15}$/.test(data.phone.replace(/\s/g, ''))) {
    errors.push('Invalid phone number format');
  }
  if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
    errors.push('Invalid email format');
  }
  if (data.address && data.address.trim().length > 0 && data.address.trim().length < 5) {
    errors.push('Address must be at least 5 characters');
  }
  return errors;
}

// ─── Core CRUD ───────────────────────────────────────────────────────────────

/**
 * Fetch all customers.
 *
 * @param {{limit?: number, offset?: number, orderBy?: string, order?: 'asc'|'desc'}} options
 * @returns {Promise<Array>}
 */
export async function getCustomers(options = {}) {
  try {
    if (!navigator.onLine) {
      console.log('[customers] Offline — returning cached customers');
      return loadCache();
    }

    let query = supabase.from('customers').select('*');

    const orderCol = options.orderBy || 'name';
    const dir = options.order === 'desc' ? { ascending: false } : { ascending: true };
    query = query.order(orderCol, dir);

    if (options.limit) query = query.limit(options.limit);
    if (options.offset && options.limit) {
      query = query.range(options.offset, options.offset + options.limit - 1);
    }

    const { data, error } = await query;
    if (error) throw error;

    if (data) saveCache(data);
    return data || [];
  } catch (err) {
    console.error('[customers] getCustomers error:', err.message);
    return loadCache();
  }
}

/**
 * Fetch a single customer by ID, including their job history.
 *
 * @param {string} id
 * @returns {Promise<Object|null>} Customer object with `jobs` array
 */
export async function getCustomerById(id) {
  validateCustomerId(id);

  try {
    if (!navigator.onLine) {
      const cached = loadCache();
      const customer = cached.find(c => c.id === id) || null;
      if (customer) {
        // Get jobs from local jobs cache
        const jobs = getLocalJobsForCustomer(id);
        return { ...customer, jobs };
      }
      return null;
    }

    // Fetch customer + jobs in parallel
    const [customerResult, jobsResult] = await Promise.all([
      supabase.from('customers').select('*').eq('id', id).single(),
      supabase.from('jobs').select('*').eq('customer_id', id).order('created_at', { ascending: false })
    ]);

    if (customerResult.error) throw customerResult.error;

    return {
      ...customerResult.data,
      jobs: jobsResult.data || []
    };
  } catch (err) {
    console.error(`[customers] getCustomerById(${id}) error:`, err.message);
    const cached = loadCache();
    const customer = cached.find(c => c.id === id) || null;
    if (customer) {
      return { ...customer, jobs: getLocalJobsForCustomer(id) };
    }
    return null;
  }
}

/**
 * Create a new customer.
 *
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export async function createCustomer(data) {
  const errors = validateCustomerData(data);
  if (errors.length > 0) throw new Error(`Validation failed: ${errors.join('; ')}`);

  const now = new Date().toISOString();
  const customer = {
    id: data.id || generateId(),
    name: data.name.trim(),
    phone: data.phone?.trim() || null,
    email: data.email?.trim().toLowerCase() || null,
    address: data.address?.trim() || null,
    town: data.town?.trim() || null,
    state: data.state?.trim() || null,
    zip: data.zip?.trim() || null,
    notes: data.notes?.trim() || null,
    created_at: now,
    updated_at: now
  };

  // Optimistic cache update
  const cached = loadCache();
  cached.unshift(customer);
  saveCache(cached);

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'customers', action: 'insert', payload: customer });
      return { ...customer, _syncStatus: 'pending' };
    }

    const { data: result, error } = await supabase.from('customers').insert(customer).select().single();
    if (error) throw error;

    if (result) {
      const updated = loadCache().map(c => (c.id === customer.id ? result : c));
      saveCache(updated);
    }
    return { ...result, _syncStatus: 'synced' };
  } catch (err) {
    console.error('[customers] createCustomer error:', err.message);
    syncQueue.enqueue({ table: 'customers', action: 'insert', payload: customer });
    return { ...customer, _syncStatus: 'pending', _error: err.message };
  }
}

/**
 * Update a customer.
 *
 * @param {string} id
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export async function updateCustomer(id, data) {
  validateCustomerId(id);

  const errors = validateCustomerData(data, true);
  if (errors.length > 0) throw new Error(`Validation failed: ${errors.join('; ')}`);

  const updates = { ...data, updated_at: new Date().toISOString() };
  const stringFields = ['name', 'phone', 'email', 'address', 'town', 'state', 'zip', 'notes'];
  stringFields.forEach(f => {
    if (typeof updates[f] === 'string') {
      updates[f] = updates[f].trim();
      if (updates[f] === '') updates[f] = null;
    }
  });
  if (updates.email) updates.email = updates.email.toLowerCase();

  // Store rollback state
  const cached = loadCache();
  const preUpdate = cached.find(c => c.id === id);

  // Optimistic update
  const updated = cached.map(c => (c.id === id ? { ...c, ...updates } : c));
  saveCache(updated);

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'customers', action: 'update', payload: { id, ...updates } });
      return { id, ...updates, _syncStatus: 'pending' };
    }

    const { data: result, error } = await supabase.from('customers').update(updates).eq('id', id).select().single();
    if (error) throw error;
    return { ...result, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[customers] updateCustomer(${id}) error:`, err.message);
    // Rollback
    if (preUpdate) {
      const rollback = loadCache().map(c => (c.id === id ? preUpdate : c));
      saveCache(rollback);
    }
    syncQueue.enqueue({ table: 'customers', action: 'update', payload: { id, ...updates } });
    throw new Error(`Failed to update customer ${id}: ${err.message}`);
  }
}

/**
 * Delete a customer. Will fail if customer has associated jobs unless
 * `force` is true (jobs will be orphaned).
 *
 * @param {string} id
 * @param {{force?: boolean, reassignTo?: string}} options
 * @returns {Promise<Object>}
 */
export async function deleteCustomer(id, options = {}) {
  validateCustomerId(id);

  try {
    // Check for associated jobs
    const { data: jobCount, error: countError } = await supabase
      .from('jobs')
      .select('id', { count: 'exact', head: true })
      .eq('customer_id', id);

    if (countError) throw countError;

    if ((jobCount?.length || 0) > 0 && !options.force && !options.reassignTo) {
      throw new Error(
        `Cannot delete: customer has ${jobCount.length} associated job(s). Use force=true to delete anyway, or reassignTo=otherCustomerId to transfer jobs.`
      );
    }

    // Reassign jobs if requested
    if (options.reassignTo) {
      const { error: reassignError } = await supabase
        .from('jobs')
        .update({ customer_id: options.reassignTo, updated_at: new Date().toISOString() })
        .eq('customer_id', id);
      if (reassignError) throw reassignError;
    }

    // Remove from local cache
    const cached = loadCache();
    saveCache(cached.filter(c => c.id !== id));

    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'customers', action: 'delete', payload: { id } });
      return { id, deleted: true, reassigned: !!options.reassignTo, _syncStatus: 'pending' };
    }

    const { data, error } = await supabase.from('customers').delete().eq('id', id).select().single();
    if (error) throw error;
    return { ...data, deleted: true, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[customers] deleteCustomer(${id}) error:`, err.message);
    syncQueue.enqueue({ table: 'customers', action: 'delete', payload: { id } });
    throw new Error(`Failed to delete customer ${id}: ${err.message}`);
  }
}

// ─── Search ──────────────────────────────────────────────────────────────────

/**
 * Search customers by name, phone, email, address, or town.
 *
 * @param {string} query
 * @returns {Promise<Array>}
 */
export async function searchCustomers(query) {
  if (!query || query.trim().length < 1) return getCustomers();

  const term = query.trim();

  try {
    if (!navigator.onLine) {
      return filterCustomersLocally(loadCache(), term);
    }

    const { data, error } = await supabase
      .from('customers')
      .select('*')
      .or(
        `name.ilike.%${term}%,phone.ilike.%${term}%,email.ilike.%${term}%,address.ilike.%${term}%,town.ilike.%${term}%`
      )
      .order('name', { ascending: true });

    if (error) throw error;
    return data || [];
  } catch (err) {
    console.error(`[customers] searchCustomers("${query}") error:`, err.message);
    return filterCustomersLocally(loadCache(), term);
  }
}

// ─── Customer History ────────────────────────────────────────────────────────

/**
 * Get full history for a customer: all jobs, visits, and services.
 *
 * @param {string} customerId
 * @returns {Promise<Object|null>} { customer, jobs, timeline }
 */
export async function getCustomerHistory(customerId) {
  validateCustomerId(customerId);

  try {
    if (!navigator.onLine) {
      const customer = loadCache().find(c => c.id === customerId) || null;
      if (!customer) return null;
      const jobs = getLocalJobsForCustomer(customerId);
      const timeline = buildTimeline(jobs);
      return { customer, jobs, timeline };
    }

    const { data: customer, error: custErr } = await supabase
      .from('customers')
      .select('*')
      .eq('id', customerId)
      .single();

    if (custErr) throw custErr;

    const { data: jobs } = await supabase
      .from('jobs')
      .select('*, services(*), photos(*), inspections(*)')
      .eq('customer_id', customerId)
      .order('created_at', { ascending: false });

    const timeline = buildTimeline(jobs || []);

    return { customer, jobs: jobs || [], timeline };
  } catch (err) {
    console.error(`[customers] getCustomerHistory(${customerId}) error:`, err.message);
    const customer = loadCache().find(c => c.id === customerId) || null;
    if (!customer) return null;
    const jobs = getLocalJobsForCustomer(customerId);
    return { customer, jobs, timeline: buildTimeline(jobs) };
  }
}

// ─── Merge Customers ─────────────────────────────────────────────────────────

/**
 * Merge two customers: copy data from `fromId` to `toId`, reassign all jobs,
 * then delete the source customer.
 *
 * @param {string} fromId - Source customer ID (will be deleted)
 * @param {string} toId - Target customer ID (will be kept)
 * @returns {Promise<Object>}
 */
export async function mergeCustomers(fromId, toId) {
  if (!fromId || !toId) throw new Error('Both fromId and toId are required');
  if (fromId === toId) throw new Error('Cannot merge a customer into itself');

  try {
    // Fetch both customers
    const { data: fromCust, error: fromErr } = await supabase.from('customers').select('*').eq('id', fromId).single();
    if (fromErr) throw fromErr;

    const { data: toCust, error: toErr } = await supabase.from('customers').select('*').eq('id', toId).single();
    if (toErr) throw toErr;

    // Merge fields: prefer target data, fill in missing from source
    const merged = {
      ...toCust,
      phone: toCust.phone || fromCust.phone,
      email: toCust.email || fromCust.email,
      address: toCust.address || fromCust.address,
      town: toCust.town || fromCust.town,
      state: toCust.state || fromCust.state,
      zip: toCust.zip || fromCust.zip,
      notes: [toCust.notes, fromCust.notes].filter(Boolean).join('\n---\n'),
      updated_at: new Date().toISOString()
    };

    // Update target customer
    const { error: updateErr } = await supabase.from('customers').update(merged).eq('id', toId);
    if (updateErr) throw updateErr;

    // Reassign all jobs
    const { data: reassignedJobs, error: reassignErr } = await supabase
      .from('jobs')
      .update({ customer_id: toId, updated_at: new Date().toISOString() })
      .eq('customer_id', fromId)
      .select();
    if (reassignErr) throw reassignErr;

    // Delete source customer
    const { error: deleteErr } = await supabase.from('customers').delete().eq('id', fromId);
    if (deleteErr) throw deleteErr;

    // Update local cache
    const cached = loadCache();
    const filtered = cached.filter(c => c.id !== fromId);
    const updated = filtered.map(c => (c.id === toId ? merged : c));
    saveCache(updated);

    return {
      toId,
      fromId,
      reassignedJobs: reassignedJobs?.length || 0,
      mergedCustomer: merged,
      _syncStatus: 'synced'
    };
  } catch (err) {
    console.error(`[customers] mergeCustomers(${fromId} -> ${toId}) error:`, err.message);
    // Queue the critical operations
    syncQueue.enqueue({ table: 'jobs', action: 'update', payload: { customer_id: toId } }); // This needs refinement
    throw new Error(`Failed to merge customers: ${err.message}`);
  }
}

// ─── Find Duplicates ─────────────────────────────────────────────────────────

/**
 * Find potential duplicate customers by phone, email, or address similarity.
 *
 * @returns {Promise<Array<{customer: Object, potentialDuplicates: Array}>}>}
 */
export async function findDuplicateCustomers() {
  const customers = await getCustomers();
  const duplicates = [];

  for (let i = 0; i < customers.length; i++) {
    const c = customers[i];
    const potentials = [];

    for (let j = 0; j < customers.length; j++) {
      if (i === j) continue;
      const other = customers[j];

      // Match by phone (exact)
      if (c.phone && other.phone && normalizePhone(c.phone) === normalizePhone(other.phone)) {
        potentials.push({ customer: other, reason: 'Same phone' });
        continue;
      }
      // Match by email (exact, case-insensitive)
      if (c.email && other.email && c.email.toLowerCase() === other.email.toLowerCase()) {
        potentials.push({ customer: other, reason: 'Same email' });
        continue;
      }
      // Match by address similarity
      if (c.address && other.address && addressSimilarity(c.address, other.address) > 0.8) {
        potentials.push({ customer: other, reason: 'Similar address' });
        continue;
      }
    }

    if (potentials.length > 0) {
      duplicates.push({ customer: c, potentialDuplicates: potentials });
    }
  }

  return duplicates;
}

// ─── Local Helpers ───────────────────────────────────────────────────────────

function filterCustomersLocally(customers, term) {
  const lower = term.toLowerCase();
  return customers.filter(c => {
    const haystack =
      `${c.name || ''} ${c.phone || ''} ${c.email || ''} ${c.address || ''} ${c.town || ''}`.toLowerCase();
    return haystack.includes(lower);
  });
}

function getLocalJobsForCustomer(customerId) {
  try {
    const jobsCache = JSON.parse(localStorage.getItem('ww_fieldops_jobs_cache') || '[]');
    return jobsCache
      .filter(j => j.customer_id === customerId)
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  } catch {
    return [];
  }
}

function buildTimeline(jobs) {
  const events = [];
  jobs.forEach(job => {
    events.push({
      type: 'job_created',
      date: job.created_at,
      title: `Job created: ${job.species || 'Unknown'}`,
      status: job.status,
      id: job.id
    });
    if (job.completed_at) {
      events.push({
        type: 'job_completed',
        date: job.completed_at,
        title: `Job completed: ${job.species || 'Unknown'}`,
        id: job.id
      });
    }
  });
  return events.sort((a, b) => new Date(b.date) - new Date(a.date));
}

function normalizePhone(phone) {
  return phone.replace(/\D/g, '');
}

function addressSimilarity(a, b) {
  const normA = a.toLowerCase().replace(/[^a-z0-9]/g, '');
  const normB = b.toLowerCase().replace(/[^a-z0-9]/g, '');
  if (normA === normB) return 1;
  // Simple substring similarity
  let matches = 0;
  const minLen = Math.min(normA.length, normB.length);
  for (let i = 0; i < minLen; i++) {
    if (normA[i] === normB[i]) matches++;
  }
  return matches / Math.max(normA.length, normB.length);
}
