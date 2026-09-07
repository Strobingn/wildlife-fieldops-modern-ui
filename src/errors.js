/**
 * Wildlife Whisperer FieldOps — Error Handling
 *
 * Global error boundaries, async wrappers with loading/error states,
 * retry logic with exponential backoff, and safe execution helpers.
 *
 * @module errors
 * @version 3.0.0
 */

import { showToast, setLoading } from './state.js';

// ═══════════════════════════════════════════════════
// Global Error Boundary
// ═══════════════════════════════════════════════════

/**
 * Initialize global error handlers.
 * Catches uncaught exceptions and unhandled promise rejections.
 * Call once during app startup.
 *
 * @returns {() => void} Cleanup function to remove listeners
 */
export function initErrorBoundary() {
  /**
   * @param {ErrorEvent} event
   */
  const onError = event => {
    console.error('[Global Error]', event.error);
    showToast(`Error: ${event.message ?? 'Unknown error'}`, 'error', 5000);
    // Prevent default browser error handling in production
    event.preventDefault();
  };

  /**
   * @param {PromiseRejectionEvent} event
   */
  const onUnhandledRejection = event => {
    console.error('[Unhandled Rejection]', event.reason);
    const msg = event.reason instanceof Error ? event.reason.message : String(event.reason ?? 'Unknown async error');
    showToast(`Async Error: ${msg}`, 'error', 5000);
    event.preventDefault();
  };

  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onUnhandledRejection);

  // Cleanup function
  return () => {
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onUnhandledRejection);
  };
}

// ═══════════════════════════════════════════════════
// Async Wrapper
// ═══════════════════════════════════════════════════

/**
 * Wrap an async function with loading state and automatic error toast.
 *
 * @template T
 * @param {(...args: any[]) => Promise<T>} fn - Async function to wrap
 * @param {Object} [options]
 * @param {string} [options.loadingMsg='Loading...'] - Loading message
 * @param {string} [options.errorMsg='Operation failed'] - Default error message
 * @param {string} [options.successMsg] - Optional success toast
 * @returns {(...args: any[]) => Promise<T|null>} Wrapped function
 *
 * @example
 *   const saveJob = asyncWrapper(
 *     async (payload) => { await supabase.from('jobs').insert(payload); },
 *     { loadingMsg: 'Saving job...', successMsg: 'Job saved!' }
 *   );
 */
export function asyncWrapper(fn, { loadingMsg = 'Loading...', errorMsg = 'Operation failed', successMsg } = {}) {
  if (typeof fn !== 'function') throw new TypeError('asyncWrapper: fn must be a function');

  return async function (...args) {
    setLoading(true, loadingMsg);
    try {
      const result = await fn.apply(this, args);
      if (successMsg) showToast(successMsg, 'success');
      return result;
    } catch (err) {
      const message = err?.message ?? err?.error_description ?? String(err);
      console.error(`[asyncWrapper] ${errorMsg}:`, err);
      showToast(`${errorMsg}: ${message}`, 'error', 5000);
      return null;
    } finally {
      setLoading(false);
    }
  };
}

// ═══════════════════════════════════════════════════
// Retry with Exponential Backoff
// ═══════════════════════════════════════════════════

/**
 * Retry an async function with exponential backoff.
 *
 * @template T
 * @param {() => Promise<T>} fn - Async function to retry
 * @param {number} [retries=3] - Maximum retry attempts
 * @param {number} [delay=1000] - Initial delay in ms (doubles each attempt)
 * @param {number} [maxDelay=30000] - Maximum delay cap
 * @returns {Promise<T>} Resolves with fn's return value, rejects after all retries
 *
 * @example
 *   const data = await retry(() => fetchData(), 5, 500);
 */
export async function retry(fn, retries = 3, delay = 1000, maxDelay = 30000) {
  if (typeof fn !== 'function') throw new TypeError('retry: fn must be a function');
  if (retries < 0) retries = 0;
  if (delay < 0) delay = 0;

  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err;
      if (attempt < retries) {
        const backoff = Math.min(delay * Math.pow(2, attempt), maxDelay);
        console.warn(`[retry] Attempt ${attempt + 1}/${retries + 1} failed, retrying in ${backoff}ms...`);
        await sleep(backoff);
      }
    }
  }
  throw lastError ?? new Error('All retry attempts failed');
}

/**
 * Sleep for N milliseconds.
 * @param {number} ms
 * @returns {Promise<void>}
 */
export function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ═══════════════════════════════════════════════════
// Safe Execution
// ═══════════════════════════════════════════════════

/**
 * Execute a function with automatic fallback on error.
 *
 * @template T
 * @param {() => T} fn - Function to execute
 * @param {T} fallback - Value to return if fn throws
 * @param {string} [context=''] - Context for error logging
 * @returns {T}
 *
 * @example
 *   const val = safeExecute(() => JSON.parse(raw), {}, 'parse config');
 */
export function safeExecute(fn, fallback, context = '') {
  if (typeof fn !== 'function') {
    console.warn(`[safeExecute] Expected function, got ${typeof fn}`);
    return fallback;
  }
  try {
    return fn();
  } catch (err) {
    const ctx = context ? ` (${context})` : '';
    console.warn(`[safeExecute] Execution failed${ctx}:`, err);
    return fallback;
  }
}

/**
 * Safely parse JSON with fallback.
 * @param {string} str - JSON string
 * @param {any} fallback - Fallback value on parse error
 * @returns {any}
 */
export function safeJSONParse(str, fallback = null) {
  if (!str || typeof str !== 'string') return fallback;
  try {
    return JSON.parse(str);
  } catch {
    return fallback;
  }
}

/**
 * Safely access localStorage with try/catch (handles quota errors, private mode).
 * @param {string} key
 * @param {string|null} [fallback=null]
 * @returns {string|null}
 */
export function safeLocalStorageGet(key, fallback = null) {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

/**
 * Safely write to localStorage with try/catch.
 * @param {string} key
 * @param {string} value
 * @returns {boolean} Whether the write succeeded
 */
export function safeLocalStorageSet(key, value) {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch (err) {
    console.warn(`[safeLocalStorageSet] Failed to write "${key}":`, err);
    return false;
  }
}

// ═══════════════════════════════════════════════════
// Error Reporting Queue
// ═══════════════════════════════════════════════════

/** @private @type {Array<{message: string, stack?: string, timestamp: string}>} */
const errorLog = [];

/** Maximum number of errors to keep in memory */
const MAX_ERROR_LOG = 50;

/**
 * Log an error to the in-memory error queue for later inspection/export.
 * @param {string|Error} err
 * @param {string} [context='']
 */
export function logError(err, context = '') {
  const entry = {
    message: err instanceof Error ? err.message : String(err),
    stack: err instanceof Error ? err.stack : undefined,
    context,
    timestamp: new Date().toISOString()
  };
  errorLog.unshift(entry);
  if (errorLog.length > MAX_ERROR_LOG) errorLog.length = MAX_ERROR_LOG;
  console.error(`[ErrorLog] ${context ? `[${context}] ` : ''}${entry.message}`, err);
}

/**
 * Get all logged errors.
 * @returns {Array<{message: string, stack?: string, context: string, timestamp: string}>}
 */
export function getErrorLog() {
  return [...errorLog];
}

/**
 * Clear the error log.
 */
export function clearErrorLog() {
  errorLog.length = 0;
}

// ═══════════════════════════════════════════════════
// ValidationError Class
// ═══════════════════════════════════════════════════

/**
 * Structured validation error with field-level details.
 */
export class ValidationError extends Error {
  /**
   * @param {string} message
   * @param {Array<{field: string, message: string}>} [fieldErrors=[]]
   */
  constructor(message, fieldErrors = []) {
    super(message);
    this.name = 'ValidationError';
    this.fieldErrors = fieldErrors;
  }
}

/**
 * Throw a ValidationError if condition is falsy.
 * @param {any} condition
 * @param {string} message
 * @param {string} field
 */
export function assertValid(condition, message, field = '') {
  if (!condition) {
    throw new ValidationError(message, field ? [{ field, message }] : []);
  }
}
