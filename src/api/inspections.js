/**
 * @module api/inspections
 * @description Inspection scheduling API — full CRUD for pre-job inspections.
 * Offline-first: falls back to localStorage when Supabase is unavailable.
 */

import { supabase } from './supabaseClient.js';
import { config } from '../config.js';
import { id, now } from '../utils.js';

const STORAGE_KEY = 'ww_rockstar_inspections';
const TABLE = 'inspections';

// ═══════════════════════════════════════════════════
// Local Cache (offline-first)
// ═══════════════════════════════════════════════════

function loadLocal() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveLocal(items) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch (e) {
    console.warn('[inspections] Failed to save local cache:', e.message);
  }
}

function generateId() {
  return 'insp_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 7);
}

// ═══════════════════════════════════════════════════
// Create
// ═══════════════════════════════════════════════════

/**
 * Create a new inspection (pre-job scheduling).
 * @param {Object} data
 * @param {string} data.customer_name
 * @param {string} [data.phone]
 * @param {string} [data.address]
 * @param {string} [data.town]
 * @param {string} [data.species]
 * @param {string} [data.priority='Normal']
 * @param {string} [data.status='scheduled']
 * @param {string} [data.scheduled_date]
 * @param {string} [data.notes]
 * @returns {Promise<{data: Object|null, error: Error|null}>}
 */
export async function createInspection(data) {
  const payload = {
    id: generateId(),
    customer_name: data.customer_name || '',
    phone: data.phone || '',
    address: data.address || '',
    town: data.town || '',
    species: data.species || '',
    priority: data.priority || 'Normal',
    status: data.status || 'scheduled',
    scheduled_date: data.scheduled_date || null,
    notes: data.notes || '',
    job_id: null,
    created_at: now(),
    updated_at: now()
  };

  // Save locally first (offline-first)
  const local = loadLocal();
  local.unshift(payload);
  saveLocal(local);

  // Sync to Supabase if available
  if (config.hasSupabase) {
    try {
      const { error } = await supabase.from(TABLE).insert(payload);
      if (error) console.warn('[inspections] Supabase insert warning:', error.message);
    } catch (e) {
      console.warn('[inspections] Supabase insert failed (offline?):', e.message);
    }
  }

  return { data: payload, error: null };
}

// ═══════════════════════════════════════════════════
// Read
// ═══════════════════════════════════════════════════

/**
 * Get all inspections. Optionally filter by status.
 * @param {{status?: string}} [filters={}]
 * @returns {Promise<{data: Array, error: Error|null}>}
 */
export async function getInspections(filters = {}) {
  let items = loadLocal();

  if (config.hasSupabase) {
    try {
      let query = supabase.from(TABLE).select('*').order('scheduled_date', { ascending: true });
      if (filters.status) query = query.eq('status', filters.status);
      const { data, error } = await query;
      if (!error && data) {
        items = data;
        saveLocal(items);
      }
    } catch (e) {
      console.warn('[inspections] Supabase fetch failed, using local:', e.message);
    }
  }

  if (filters.status) {
    items = items.filter(i => i.status === filters.status);
  }

  return { data: items, error: null };
}

/**
 * Get a single inspection by ID.
 * @param {string} inspectionId
 * @returns {Promise<{data: Object|null, error: Error|null}>}
 */
export async function getInspectionById(inspectionId) {
  const local = loadLocal();
  const found = local.find(i => i.id === inspectionId);
  if (found) return { data: found, error: null };

  if (config.hasSupabase) {
    try {
      const { data, error } = await supabase.from(TABLE).select('*').eq('id', inspectionId).single();
      return { data, error };
    } catch (e) {
      return { data: null, error: e };
    }
  }

  return { data: null, error: new Error('Inspection not found') };
}

// ═══════════════════════════════════════════════════
// Update
// ═══════════════════════════════════════════════════

/**
 * Update an inspection.
 * @param {string} inspectionId
 * @param {Object} updates
 * @returns {Promise<{data: Object|null, error: Error|null}>}
 */
export async function updateInspection(inspectionId, updates) {
  const local = loadLocal();
  const idx = local.findIndex(i => i.id === inspectionId);
  if (idx < 0) return { data: null, error: new Error('Inspection not found') };

  local[idx] = { ...local[idx], ...updates, updated_at: now() };
  saveLocal(local);

  if (config.hasSupabase) {
    try {
      const { error } = await supabase.from(TABLE).update(updates).eq('id', inspectionId);
      if (error) console.warn('[inspections] Supabase update warning:', error.message);
    } catch (e) {
      console.warn('[inspections] Supabase update failed (offline?):', e.message);
    }
  }

  return { data: local[idx], error: null };
}

// ═══════════════════════════════════════════════════
// Delete
// ═══════════════════════════════════════════════════

/**
 * Delete an inspection.
 * @param {string} inspectionId
 * @returns {Promise<{error: Error|null}>}
 */
export async function deleteInspection(inspectionId) {
  let local = loadLocal();
  local = local.filter(i => i.id !== inspectionId);
  saveLocal(local);

  if (config.hasSupabase) {
    try {
      const { error } = await supabase.from(TABLE).delete().eq('id', inspectionId);
      if (error) console.warn('[inspections] Supabase delete warning:', error.message);
    } catch (e) {
      console.warn('[inspections] Supabase delete failed (offline?):', e.message);
    }
  }

  return { error: null };
}

// ═══════════════════════════════════════════════════
// Convert Inspection to Job
// ═══════════════════════════════════════════════════

/**
 * Convert an inspection to a job. Updates the inspection status and creates a job record.
 * @param {string} inspectionId
 * @param {Object} [jobOverrides={}] - Additional job fields to merge
 * @returns {Promise<{data: {inspection: Object, job: Object}|null, error: Error|null}>}
 */
export async function convertInspectionToJob(inspectionId, jobOverrides = {}) {
  const { data: inspection, error: fetchErr } = await getInspectionById(inspectionId);
  if (fetchErr || !inspection) return { data: null, error: fetchErr || new Error('Inspection not found') };

  const jobId = 'job_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 7);
  const timestamp = now();

  const job = {
    id: jobId,
    customer: inspection.customer_name || '',
    phone: inspection.phone || '',
    address: inspection.address || '',
    town: inspection.town || '',
    species: inspection.species || '',
    priority: inspection.priority || 'Normal',
    status: 'Active',
    scope: inspection.notes || '',
    scheduled_start: inspection.scheduled_date || null,
    ...jobOverrides,
    created_at: timestamp,
    updated_at: timestamp
  };

  // Update inspection to mark as converted
  const updatedInspection = { ...inspection, status: 'converted', job_id: jobId, updated_at: timestamp };
  const local = loadLocal();
  const idx = local.findIndex(i => i.id === inspectionId);
  if (idx >= 0) {
    local[idx] = updatedInspection;
    saveLocal(local);
  }

  // Save job to localStorage jobs cache
  try {
    const jobsRaw = localStorage.getItem('ww_rockstar') || '{}';
    const jobsStore = JSON.parse(jobsRaw);
    if (!jobsStore.jobs) jobsStore.jobs = [];
    jobsStore.jobs.unshift(job);
    localStorage.setItem('ww_rockstar', JSON.stringify(jobsStore));
  } catch (e) {
    console.warn('[inspections] Failed to save converted job:', e.message);
  }

  // Sync to Supabase
  if (config.hasSupabase) {
    try {
      await supabase.from(TABLE).update({ status: 'converted', job_id: jobId }).eq('id', inspectionId);
    } catch (e) {
      console.warn('[inspections] Supabase convert update failed:', e.message);
    }
  }

  return { data: { inspection: updatedInspection, job }, error: null };
}

// ═══════════════════════════════════════════════════
// Sync (manual trigger)
// ═══════════════════════════════════════════════════

/**
 * Push all local inspections to Supabase.
 * @returns {Promise<{synced: number, errors: number}>}
 */
export async function syncInspections() {
  if (!config.hasSupabase) return { synced: 0, errors: 0 };

  const items = loadLocal();
  let synced = 0;
  let errors = 0;

  for (const item of items) {
    try {
      const { error } = await supabase.from(TABLE).upsert(item, { onConflict: 'id' });
      if (error) {
        errors++;
      } else {
        synced++;
      }
    } catch {
      errors++;
    }
  }

  console.log(`[inspections] Synced ${synced}/${items.length} (${errors} errors)`);
  return { synced, errors };
}
