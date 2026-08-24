/**
 * @module api/supabaseClient
 * @description Supabase client singleton with request/response interceptors,
 * connection state monitoring, health checks, and offline-aware request queue.
 */

import { createClient } from '@supabase/supabase-js';
import { config } from '../config.js';

// ─── Configuration ───────────────────────────────────────────────────────────

const SUPABASE_URL = config.SUPABASE_URL;
const SUPABASE_ANON_KEY = config.SUPABASE_ANON_KEY;

const REQUEST_TIMEOUT_MS = 10000;
const HEALTH_CHECK_INTERVAL_MS = 30000;

// ─── Connection State ────────────────────────────────────────────────────────

let isConnected = navigator.onLine;
let lastHealthCheck = null;
let healthCheckInterval = null;
const connectionListeners = new Set();

/** @returns {boolean} Current connection state */
export function getConnectionState() {
  return isConnected;
}

/** @returns {string|null} ISO timestamp of last successful health check */
export function getLastHealthCheck() {
  return lastHealthCheck;
}

/**
 * Subscribe to connection state changes.
 * @param {(connected: boolean) => void} callback
 * @returns {() => void} Unsubscribe function
 */
export function onConnectionChange(callback) {
  connectionListeners.add(callback);
  return () => connectionListeners.delete(callback);
}

function notifyConnectionChange(connected) {
  isConnected = connected;
  connectionListeners.forEach(cb => {
    try {
      cb(connected);
    } catch (e) {
      console.error('[supabaseClient] Connection listener error:', e);
    }
  });
}

// ─── Custom Fetch with Timeout & Logging ─────────────────────────────────────

/**
 * Wraps the native fetch with timeout support and request/response logging.
 * @param {string} url
 * @param {RequestInit} init
 * @returns {Promise<Response>}
 */
async function interceptedFetch(url, init = {}) {
  const method = init.method || 'GET';
  const requestId = `${method}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 5)}`;
  const startTime = performance.now();

  console.log(`[supabaseClient][${requestId}] → ${method} ${url?.split('?')[0]?.slice(0, 120)}`);

  // AbortController for timeout
  const controller = new AbortController();
  const timeoutId = setTimeout(() => {
    controller.abort();
    console.warn(`[supabaseClient][${requestId}] Request timed out after ${REQUEST_TIMEOUT_MS}ms`);
  }, REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal
    });

    clearTimeout(timeoutId);
    const duration = Math.round(performance.now() - startTime);

    if (!response.ok) {
      console.warn(`[supabaseClient][${requestId}] ← HTTP ${response.status} (${duration}ms)`);
    } else {
      console.log(`[supabaseClient][${requestId}] ← HTTP ${response.status} (${duration}ms)`);
    }

    // Mark as connected on successful response
    if (!isConnected) {
      notifyConnectionChange(true);
      lastHealthCheck = new Date().toISOString();
    }

    return response;
  } catch (error) {
    clearTimeout(timeoutId);
    const duration = Math.round(performance.now() - startTime);

    if (error.name === 'AbortError') {
      console.error(`[supabaseClient][${requestId}] ← TIMEOUT (${duration}ms)`);
      throw new Error(`Request timed out after ${REQUEST_TIMEOUT_MS}ms: ${url}`);
    }

    console.error(`[supabaseClient][${requestId}] ← ERROR (${duration}ms):`, error.message);

    // Mark as disconnected on network error
    if (isConnected && !navigator.onLine) {
      notifyConnectionChange(false);
    }

    throw error;
  }
}

// ─── Supabase Client ─────────────────────────────────────────────────────────

let supabase = null;

if (config.hasSupabase) {
  supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
      storage: localStorage,
      autoRefreshToken: true,
      persistSession: true,
      detectSessionInUrl: true
    },
    realtime: {
      params: {
        eventsPerSecond: 10
      }
    },
    global: {
      fetch: interceptedFetch,
      headers: {
        'X-Client-Name': 'wildlife-fieldops',
        'X-Client-Version': '3.0.0'
      }
    }
  });

  // ─── Realtime Connection Monitor ─────────────────────────────────────────────

  supabase.realtime?.onOpen?.(() => {
    console.log('[supabaseClient] Realtime connection opened');
    notifyConnectionChange(true);
  });

  supabase.realtime?.onClose?.(() => {
    console.warn('[supabaseClient] Realtime connection closed');
  });

  supabase.realtime?.onError?.(error => {
    console.error('[supabaseClient] Realtime error:', error);
    notifyConnectionChange(false);
  });
} else {
  // Safe offline stub when Supabase is not configured
  console.log('[supabaseClient] No valid Supabase config — running in offline mode');
  supabase = {
    from: () => ({
      select: () => ({ data: [], error: null }),
      insert: () => ({ data: null, error: new Error('Supabase not configured') }),
      update: () => ({ data: null, error: new Error('Supabase not configured') }),
      delete: () => ({ data: null, error: new Error('Supabase not configured') }),
      eq: () => ({ data: [], error: null }),
      order: () => ({ data: [], error: null }),
      limit: () => ({ data: [], error: null })
    }),
    storage: {
      from: () => ({
        upload: () => ({ data: null, error: new Error('Supabase not configured') }),
        getPublicUrl: () => ({ data: { publicUrl: '' } }),
        remove: () => ({ data: null, error: null })
      })
    },
    auth: {
      onAuthStateChange: () => ({ data: { subscription: { unsubscribe: () => {} } } }),
      getSession: () => Promise.resolve({ data: { session: null } }),
      signInWithPassword: () => Promise.resolve({ data: null, error: new Error('Supabase not configured') }),
      signOut: () => Promise.resolve({ error: null })
    },
    realtime: {}
  };
}

// ─── Health Check ────────────────────────────────────────────────────────────

/**
 * Performs a lightweight health check against Supabase.
 * @returns {Promise<{healthy: boolean, latencyMs: number, error?: string}>}
 */
export async function checkHealth() {
  const start = performance.now();
  try {
    // Lightweight query: just check the connection
    const { error } = await supabase.from('jobs').select('id', { count: 'exact', head: true }).limit(1);

    const latencyMs = Math.round(performance.now() - start);

    if (error) {
      console.warn('[supabaseClient] Health check failed:', error.message);
      notifyConnectionChange(false);
      return { healthy: false, latencyMs, error: error.message };
    }

    lastHealthCheck = new Date().toISOString();
    if (!isConnected) {
      notifyConnectionChange(true);
    }
    return { healthy: true, latencyMs };
  } catch (err) {
    const latencyMs = Math.round(performance.now() - start);
    console.error('[supabaseClient] Health check exception:', err.message);
    notifyConnectionChange(false);
    return { healthy: false, latencyMs, error: err.message };
  }
}

/**
 * Start periodic health checks.
 * @returns {() => void} Stop function
 */
export function startHealthChecks() {
  if (healthCheckInterval) {
    clearInterval(healthCheckInterval);
  }
  healthCheckInterval = setInterval(() => {
    if (navigator.onLine) {
      checkHealth().catch(err => {
        console.warn('[supabaseClient] Background health check error:', err.message);
      });
    }
  }, HEALTH_CHECK_INTERVAL_MS);

  // Return cleanup function
  return () => {
    if (healthCheckInterval) {
      clearInterval(healthCheckInterval);
      healthCheckInterval = null;
    }
  };
}

// ─── Network Event Listeners ─────────────────────────────────────────────────

window.addEventListener('online', () => {
  console.log('[supabaseClient] Browser went online');
  notifyConnectionChange(true);
  checkHealth().catch(() => {});
});

window.addEventListener('offline', () => {
  console.warn('[supabaseClient] Browser went offline');
  notifyConnectionChange(false);
});

// ─── Helper: Build query with optional filters ───────────────────────────────

/**
 * Applies common filters to a Supabase query builder for the jobs table.
 * @param {Object} query - Supabase query builder
 * @param {{status?: string, species?: string, tech?: string, town?: string, dateFrom?: string, dateTo?: string}} filters
 * @returns {Object} The filtered query builder
 */
export function applyJobFilters(query, filters = {}) {
  if (filters.status) {
    query = query.eq('status', filters.status);
  }
  if (filters.species) {
    query = query.eq('species', filters.species);
  }
  if (filters.tech) {
    query = query.eq('assigned_tech', filters.tech);
  }
  if (filters.town) {
    query = query.ilike('town', `%${filters.town}%`);
  }
  if (filters.dateFrom) {
    query = query.gte('created_at', filters.dateFrom);
  }
  if (filters.dateTo) {
    query = query.lte('created_at', filters.dateTo);
  }
  return query;
}

// ─── Storage helpers ─────────────────────────────────────────────────────────

/**
 * Upload a file to Supabase Storage.
 * @param {string} bucket - Storage bucket name
 * @param {string} path - File path within bucket
 * @param {File|Blob} file - File to upload
 * @param {{contentType?: string}} options
 * @returns {Promise<{path: string, publicUrl: string}|null>}
 */
export async function uploadToStorage(bucket, path, file, options = {}) {
  try {
    const { error } = await supabase.storage.from(bucket).upload(path, file, {
      contentType: options.contentType || file.type || 'application/octet-stream',
      upsert: true
    });

    if (error) {
      console.error(`[supabaseClient] Storage upload error [${bucket}/${path}]:`, error.message);
      throw error;
    }

    const { data: urlData } = supabase.storage.from(bucket).getPublicUrl(path);
    return {
      path,
      publicUrl: urlData?.publicUrl || null
    };
  } catch (err) {
    console.error(`[supabaseClient] Storage upload failed [${bucket}/${path}]:`, err.message);
    throw err;
  }
}

/**
 * Delete a file from Supabase Storage.
 * @param {string} bucket - Storage bucket name
 * @param {string} path - File path within bucket
 * @returns {Promise<boolean>}
 */
export async function deleteFromStorage(bucket, path) {
  try {
    const { error } = await supabase.storage.from(bucket).remove([path]);
    if (error) {
      console.error(`[supabaseClient] Storage delete error [${bucket}/${path}]:`, error.message);
      throw error;
    }
    return true;
  } catch (err) {
    console.error(`[supabaseClient] Storage delete failed [${bucket}/${path}]:`, err.message);
    throw err;
  }
}

export { supabase };
