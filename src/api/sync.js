/**
 * @module api/sync
 * @description Offline-first sync queue with conflict resolution,
 * exponential backoff retry, request deduplication, and optimistic updates.
 *
 * Completely replaces the legacy sync.js which had:
 * - Hardcoded Cloudflare Worker URL
 * - No conflict resolution
 * - No retry with backoff
 * - No deduplication
 */

import { supabase, getConnectionState } from './supabaseClient.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const SYNC_QUEUE_KEY = 'ww_fieldops_sync_queue';
const SYNC_METADATA_KEY = 'ww_fieldops_sync_meta';
const DEFAULT_MAX_RETRIES = 3;
const RETRY_DELAYS_MS = [1000, 5000, 15000]; // exponential-ish backoff
const PROCESSING_LOCK_KEY = 'ww_fieldops_sync_processing';
const SYNC_DEBOUNCE_MS = 500;

// ─── Sync Metadata ───────────────────────────────────────────────────────────

function getMetadata() {
  try {
    return JSON.parse(localStorage.getItem(SYNC_METADATA_KEY) || '{}');
  } catch {
    return {};
  }
}

function setMetadata(meta) {
  localStorage.setItem(SYNC_METADATA_KEY, JSON.stringify({ ...getMetadata(), ...meta }));
}

// ─── Unique ID Generator ─────────────────────────────────────────────────────

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// ─── Sync Queue ──────────────────────────────────────────────────────────────

class SyncQueue {
  constructor() {
    this.queue = this._loadQueue();
    this.isProcessing = false;
    this.maxRetries = DEFAULT_MAX_RETRIES;
    this.retryDelays = [...RETRY_DELAYS_MS];
    this._listeners = new Set();
    this._debounceTimer = null;

    // Auto-sync when coming back online
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        console.log('[SyncQueue] Browser online — scheduling sync');
        this._debouncedProcess();
      });
    }
  }

  // ─── Persistence ─────────────────────────────────────────────────────────

  _loadQueue() {
    try {
      const stored = localStorage.getItem(SYNC_QUEUE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch (e) {
      console.error('[SyncQueue] Failed to load queue from localStorage:', e.message);
      return [];
    }
  }

  _persistQueue() {
    try {
      localStorage.setItem(SYNC_QUEUE_KEY, JSON.stringify(this.queue));
    } catch (e) {
      console.error('[SyncQueue] Failed to persist queue:', e.message);
      // If quota exceeded, try clearing old completed items
      if (e.name === 'QuotaExceededError') {
        this._evictOldEntries();
      }
    }
  }

  /**
   * Evict completed entries to free up localStorage space.
   * @private
   */
  _evictOldEntries() {
    const completed = this.queue.filter(op => op.status === 'completed');
    if (completed.length > 10) {
      this.queue = this.queue.filter(op => op.status !== 'completed');
      try {
        localStorage.setItem(SYNC_QUEUE_KEY, JSON.stringify(this.queue));
        console.log(`[SyncQueue] Evicted ${completed.length} completed entries`);
      } catch (e) {
        console.error('[SyncQueue] Still cannot persist after eviction:', e.message);
      }
    }
  }

  // ─── Event Listeners ─────────────────────────────────────────────────────

  /**
   * Subscribe to sync queue events.
   * @param {(event: {type: string, operation?: Object, error?: string}) => void} callback
   * @returns {() => void} Unsubscribe function
   */
  subscribe(callback) {
    this._listeners.add(callback);
    return () => this._listeners.delete(callback);
  }

  _emit(event) {
    this._listeners.forEach(cb => {
      try {
        cb(event);
      } catch (e) {
        console.error('[SyncQueue] Listener error:', e);
      }
    });
  }

  // ─── Deduplication ───────────────────────────────────────────────────────

  /**
   * Generate a deduplication key for an operation.
   * Same entity + same action type within a window = duplicate.
   * @private
   * @param {Object} op
   * @returns {string}
   */
  _dedupeKey(op) {
    const entityId = op.payload?.id || op.payload?.job_id || op.payload?.customer_id || 'none';
    return `${op.table}:${op.action}:${entityId}`;
  }

  /**
   * Check if an equivalent pending operation already exists.
   * If so, replace it with the newer one (keeps latest data).
   * @private
   * @param {Object} operation
   * @returns {boolean} Whether a duplicate was found and replaced
   */
  _deduplicate(operation) {
    const key = this._dedupeKey(operation);
    const duplicateIndex = this.queue.findIndex(op => op.status === 'pending' && this._dedupeKey(op) === key);

    if (duplicateIndex !== -1) {
      // Replace the older operation with the newer one, preserving its ID
      const existingId = this.queue[duplicateIndex].id;
      this.queue[duplicateIndex] = {
        ...operation,
        id: existingId, // Keep original ID for traceability
        createdAt: this.queue[duplicateIndex].createdAt, // Keep original timestamp
        status: 'pending',
        retryCount: 0 // Reset retries since we have fresher data
      };
      console.log(`[SyncQueue] Deduplicated: replaced pending ${key}`);
      return true;
    }
    return false;
  }

  // ─── Public API ──────────────────────────────────────────────────────────

  /**
   * Add an operation to the sync queue.
   * Deduplicates pending operations for the same entity+action.
   * Persists to localStorage immediately.
   *
   * @param {Object} operation
   * @param {string} operation.table - Table name (e.g., 'jobs', 'customers')
   * @param {string} operation.action - Action type: 'insert', 'update', 'delete', 'upsert'
   * @param {Object} operation.payload - Row data to sync
   * @param {Object} [operation.optimistic] - Optimistic local state update
   * @returns {string} Operation ID
   */
  enqueue(operation) {
    if (!operation.table || !operation.action || !operation.payload) {
      throw new Error('SyncQueue.enqueue: operation must have table, action, and payload');
    }

    const validActions = ['insert', 'update', 'delete', 'upsert'];
    if (!validActions.includes(operation.action)) {
      throw new Error(`SyncQueue.enqueue: action must be one of ${validActions.join(', ')}`);
    }

    const normalizedOp = {
      id: generateId(),
      table: operation.table,
      action: operation.action,
      payload: operation.payload,
      optimistic: operation.optimistic || null,
      status: 'pending',
      retryCount: 0,
      createdAt: new Date().toISOString(),
      lastAttempt: null,
      error: null
    };

    // Attempt deduplication first
    const wasDeduped = this._deduplicate(normalizedOp);

    if (!wasDeduped) {
      this.queue.push(normalizedOp);
    }

    this._persistQueue();
    this._emit({ type: 'enqueued', operation: normalizedOp });

    // Auto-process if online
    if (navigator.onLine && getConnectionState()) {
      this._debouncedProcess();
    }

    return normalizedOp.id;
  }

  /**
   * Debounced process trigger to batch rapid operations.
   * @private
   */
  _debouncedProcess() {
    if (this._debounceTimer) {
      clearTimeout(this._debounceTimer);
    }
    this._debounceTimer = setTimeout(() => this.process(), SYNC_DEBOUNCE_MS);
  }

  /**
   * Process all pending operations in the queue.
   * Respects retry limits and backoff timing.
   * Skips if already processing or offline.
   *
   * @returns {Promise<{processed: number, succeeded: number, failed: number}>}
   */
  async process() {
    // Prevent concurrent processing
    if (this.isProcessing) {
      console.log('[SyncQueue] Already processing, skipping');
      return { processed: 0, succeeded: 0, failed: 0 };
    }

    // Check connectivity
    if (!navigator.onLine) {
      console.log('[SyncQueue] Offline — deferring sync');
      this._emit({ type: 'deferred', reason: 'offline' });
      return { processed: 0, succeeded: 0, failed: 0 };
    }

    // Acquire lock
    const lockAcquired = this._acquireLock();
    if (!lockAcquired) {
      console.log('[SyncQueue] Another instance is processing');
      return { processed: 0, succeeded: 0, failed: 0 };
    }

    this.isProcessing = true;
    this._emit({ type: 'processing_start' });

    const pendingOps = this.queue.filter(op => op.status === 'pending');
    let processed = 0;
    let succeeded = 0;
    let failed = 0;

    console.log(`[SyncQueue] Processing ${pendingOps.length} pending operations`);

    for (const op of pendingOps) {
      // Check if we should wait longer before retry
      if (op.retryCount > 0 && op.lastAttempt) {
        const delay = this.retryDelays[Math.min(op.retryCount - 1, this.retryDelays.length - 1)];
        const elapsed = Date.now() - new Date(op.lastAttempt).getTime();
        if (elapsed < delay) {
          console.log(`[SyncQueue] Skipping op ${op.id} — backoff (${delay - elapsed}ms remaining)`);
          continue;
        }
      }

      try {
        await this.processOperation(op);
        op.status = 'completed';
        op.error = null;
        succeeded++;
        this._emit({ type: 'operation_success', operation: op });
      } catch (error) {
        op.retryCount++;
        op.lastAttempt = new Date().toISOString();
        op.error = error.message || 'Unknown error';

        if (op.retryCount >= this.maxRetries) {
          op.status = 'failed';
          failed++;
          console.error(`[SyncQueue] Operation ${op.id} failed permanently after ${op.retryCount} retries:`, op.error);
          this._emit({ type: 'operation_failed', operation: op, error: op.error });
        } else {
          const nextDelay = this.retryDelays[Math.min(op.retryCount - 1, this.retryDelays.length - 1)];
          console.warn(
            `[SyncQueue] Operation ${op.id} failed (retry ${op.retryCount}/${this.maxRetries}), next attempt in ${nextDelay}ms:`,
            op.error
          );
          this._emit({ type: 'operation_retry', operation: op, retryCount: op.retryCount });
        }
      }
      processed++;
    }

    // Clean up completed operations (keep last 50 for history)
    this.queue = this.queue.filter(op => op.status !== 'completed').slice(-100);
    this._persistQueue();

    setMetadata({
      lastSyncAttempt: new Date().toISOString(),
      lastSyncResult: { processed, succeeded, failed }
    });

    this.isProcessing = false;
    this._releaseLock();
    this._emit({ type: 'processing_complete', processed, succeeded, failed });

    console.log(`[SyncQueue] Complete: ${succeeded} succeeded, ${failed} failed, ${processed} processed`);
    return { processed, succeeded, failed };
  }

  /**
   * Execute a single sync operation against Supabase.
   * @param {Object} op
   * @returns {Promise<Object>} Server response data
   */
  async processOperation(op) {
    const { table, action, payload } = op;

    console.log(`[SyncQueue] Executing ${action} on ${table} (op ${op.id})`);

    let result;

    switch (action) {
      case 'insert':
        result = await supabase.from(table).insert(payload).select().single();
        break;

      case 'update': {
        const { id, ...updates } = payload;
        if (!id) throw new Error('Update payload must include an id');
        result = await supabase.from(table).update(updates).eq('id', id).select().single();
        break;
      }

      case 'upsert':
        result = await supabase.from(table).upsert(payload, { onConflict: 'id' }).select().single();
        break;

      case 'delete': {
        const deleteId = payload.id || payload;
        if (!deleteId) throw new Error('Delete payload must include an id');
        result = await supabase.from(table).delete().eq('id', deleteId).select().single();
        break;
      }

      default:
        throw new Error(`Unknown action: ${action}`);
    }

    if (result.error) {
      // Handle specific Supabase errors
      if (result.error.code === '23505') {
        // Unique violation — likely already exists, treat as success
        console.warn(`[SyncQueue] Unique constraint violation on ${table} — treating as success`);
        return result.data;
      }
      if (result.error.code === 'PGRST116') {
        // No rows returned for update/delete — may already be gone
        console.warn(`[SyncQueue] No rows affected on ${table} — may already be processed`);
        return result.data;
      }
      throw new Error(`Supabase ${action} on ${table} failed: ${result.error.message} (code: ${result.error.code})`);
    }

    return result.data;
  }

  // ─── Conflict Resolution ─────────────────────────────────────────────────

  /**
   * Resolve a conflict between local and server data using last-write-wins
   * with timestamp validation.
   *
   * @param {Object} local - Local version of the record
   * @param {Object} server - Server version of the record
   * @param {string} [timestampField='updated_at'] - Field to compare for LWW
   * @returns {{winner: 'local'|'server', data: Object}} The winning record
   */
  resolveConflict(local, server, timestampField = 'updated_at') {
    if (!local || !server) {
      return { winner: local ? 'local' : 'server', data: local || server };
    }

    const localTime = local[timestampField] ? new Date(local[timestampField]).getTime() : 0;
    const serverTime = server[timestampField] ? new Date(server[timestampField]).getTime() : 0;

    // If local is newer or same, prefer local
    if (localTime >= serverTime) {
      console.log(`[SyncQueue] Conflict resolved: local wins (${localTime} >= ${serverTime})`);
      return { winner: 'local', data: { ...server, ...local, [timestampField]: new Date().toISOString() } };
    }

    console.log(`[SyncQueue] Conflict resolved: server wins (${serverTime} > ${localTime})`);
    return { winner: 'server', data: server };
  }

  // ─── Lock Management ─────────────────────────────────────────────────────

  _acquireLock() {
    const now = Date.now();
    const lockData = localStorage.getItem(PROCESSING_LOCK_KEY);
    if (lockData) {
      try {
        const { timestamp } = JSON.parse(lockData);
        // Stale lock check (30 seconds)
        if (now - timestamp < 30000) {
          return false;
        }
      } catch {
        // Invalid lock data, proceed
      }
    }
    localStorage.setItem(PROCESSING_LOCK_KEY, JSON.stringify({ timestamp: now }));
    return true;
  }

  _releaseLock() {
    localStorage.removeItem(PROCESSING_LOCK_KEY);
  }

  // ─── Queue Inspection ────────────────────────────────────────────────────

  /** @returns {number} Count of pending operations */
  getPendingCount() {
    return this.queue.filter(op => op.status === 'pending').length;
  }

  /** @returns {number} Count of failed operations */
  getFailedCount() {
    return this.queue.filter(op => op.status === 'failed').length;
  }

  /** @returns {Array} All pending operations */
  getPendingOps() {
    return this.queue.filter(op => op.status === 'pending');
  }

  /** @returns {Array} All failed operations */
  getFailedOps() {
    return this.queue.filter(op => op.status === 'failed');
  }

  /** @returns {Object|null} Last sync metadata */
  getLastSyncInfo() {
    return getMetadata();
  }

  // ─── Queue Management ────────────────────────────────────────────────────

  /** Clear all operations from the queue. Use with caution. */
  clear() {
    const count = this.queue.length;
    this.queue = [];
    this._persistQueue();
    console.log(`[SyncQueue] Cleared ${count} operations`);
    this._emit({ type: 'cleared', count });
  }

  /**
   * Retry all failed operations by resetting their status to pending.
   * @returns {number} Number of operations retried
   */
  retryAllFailed() {
    let retried = 0;
    this.queue = this.queue.map(op => {
      if (op.status === 'failed') {
        retried++;
        return { ...op, status: 'pending', retryCount: 0, error: null, lastAttempt: null };
      }
      return op;
    });
    this._persistQueue();
    console.log(`[SyncQueue] Retrying ${retried} failed operations`);
    this._emit({ type: 'retry_all', count: retried });
    return retried;
  }

  /**
   * Remove a specific operation from the queue by ID.
   * @param {string} opId
   * @returns {boolean}
   */
  remove(opId) {
    const before = this.queue.length;
    this.queue = this.queue.filter(op => op.id !== opId);
    const removed = before - this.queue.length;
    if (removed > 0) {
      this._persistQueue();
      this._emit({ type: 'removed', operationId: opId });
    }
    return removed > 0;
  }

  // ─── Full Sync ───────────────────────────────────────────────────────────

  /**
   * Perform a full bidirectional sync: push local changes, then fetch server changes.
   * @param {string} table - Table to sync
   * @param {string} [timestampField='updated_at'] - Timestamp column for comparison
   * @returns {Promise<{pushed: number, pulled: number}>}
   */
  async fullSync(table, timestampField = 'updated_at') {
    const meta = getMetadata();
    const lastSync = meta[`lastSync_${table}`];

    // 1. Push pending operations first
    const pushResult = await this.process();

    // 2. Pull server changes since last sync
    let query = supabase.from(table).select('*');
    if (lastSync) {
      query = query.gt(timestampField, lastSync);
    }
    const { data: serverChanges, error } = await query;

    if (error) {
      console.error(`[SyncQueue] Full sync pull failed for ${table}:`, error.message);
      throw error;
    }

    setMetadata({ [`lastSync_${table}`]: new Date().toISOString() });

    console.log(
      `[SyncQueue] Full sync for ${table}: pushed ${pushResult.succeeded}, pulled ${serverChanges?.length || 0}`
    );
    return { pushed: pushResult.succeeded, pulled: serverChanges?.length || 0, serverChanges: serverChanges || [] };
  }
}

// ─── Singleton Export ────────────────────────────────────────────────────────

/** @type {SyncQueue} */
export const syncQueue = new SyncQueue();

// ─── Convenience Exports ─────────────────────────────────────────────────────

/**
 * Enqueue a create operation.
 * @param {string} table
 * @param {Object} payload
 * @param {Object} [optimistic]
 * @returns {string} Operation ID
 */
export function queueInsert(table, payload, optimistic) {
  return syncQueue.enqueue({ table, action: 'insert', payload, optimistic });
}

/**
 * Enqueue an update operation.
 * @param {string} table
 * @param {Object} payload - Must include `id`
 * @param {Object} [optimistic]
 * @returns {string} Operation ID
 */
export function queueUpdate(table, payload, optimistic) {
  return syncQueue.enqueue({ table, action: 'update', payload, optimistic });
}

/**
 * Enqueue a delete operation.
 * @param {string} table
 * @param {string} id
 * @param {Object} [optimistic]
 * @returns {string} Operation ID
 */
export function queueDelete(table, id, optimistic) {
  return syncQueue.enqueue({ table, action: 'delete', payload: { id }, optimistic });
}

/**
 * Enqueue an upsert operation.
 * @param {string} table
 * @param {Object} payload
 * @param {Object} [optimistic]
 * @returns {string} Operation ID
 */
export function queueUpsert(table, payload, optimistic) {
  return syncQueue.enqueue({ table, action: 'upsert', payload, optimistic });
}

/**
 * Process the sync queue. Call this after coming online or on app startup.
 * @returns {Promise<{processed: number, succeeded: number, failed: number}>}
 */
export function processSyncQueue() {
  return syncQueue.process();
}

/**
 * Setup automatic sync on online events and periodic background sync.
 * @returns {() => void} Cleanup function
 */
export function setupSync() {
  // Initial sync if online
  if (navigator.onLine) {
    syncQueue.process().catch(err => {
      console.warn('[SyncQueue] Initial sync error:', err.message);
    });
  }

  // Sync when coming online
  const handleOnline = () => syncQueue.process();
  window.addEventListener('online', handleOnline);

  // Periodic sync every 5 minutes
  const intervalId = setInterval(
    () => {
      if (navigator.onLine) {
        syncQueue.process().catch(() => {});
      }
    },
    5 * 60 * 1000
  );

  // Return cleanup
  return () => {
    window.removeEventListener('online', handleOnline);
    clearInterval(intervalId);
  };
}

export default SyncQueue;
