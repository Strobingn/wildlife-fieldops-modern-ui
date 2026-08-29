/**
 * Wildlife Whisperer FieldOps — Central State Management
 *
 * Pub/sub store pattern with selector support.
 * All state mutations go through setState() which notifies subscribers.
 *
 * @module state
 * @version 3.0.0
 */

import { STORAGE_KEY, THEME_KEY } from './constants.js';

// ═══════════════════════════════════════════════════
// createStore Factory
// ═══════════════════════════════════════════════════

/**
 * Create a reactive state store.
 *
 * Performance note: Avoid deep cloning state on every read/write to prevent O(N)
 * JSON serialization overhead during state access and subscriber notifications.
 * State immutability is enforced at the top level with Object.freeze.
 *
 * @template T
 * @param {T} initialState - Starting state object
 * @returns {{
 *   getState: () => T,
 *   setState: (updater: Partial<T> | ((state: T) => T)) => void,
 *   subscribe: (fn: (state: T) => void) => () => void,
 *   select: <R>(selector: (state: T) => R) => R
 * }} Store instance
 */
export function createStore(initialState) {
  if (!initialState || typeof initialState !== 'object') {
    throw new TypeError('createStore: initialState must be an object');
  }

  let state = Object.freeze({ ...initialState });
  const listeners = new Set();
  let isNotifying = false;

  return {
    /**
     * Get snapshot of current state (O(1) access).
     * @returns {T}
     */
    getState() {
      return state;
    },

    /**
     * Update state and notify all subscribers.
     * @param {Partial<T> | ((state: T) => T)} updater - Partial update or reducer function
     */
    setState(updater) {
      const prev = state;
      const next = typeof updater === 'function' ? /** @type {any} */ (updater)(prev) : { ...prev, ...updater };
      state = Object.freeze(next);

      // Notify subscribers (copy set to handle mutations during iteration)
      if (!isNotifying) {
        isNotifying = true;
        for (const fn of [...listeners]) {
          try {
            fn(state);
          } catch (err) {
            console.error('Store subscriber error:', err);
          }
        }
        isNotifying = false;
      }
    },

    /**
     * Subscribe to state changes. Returns unsubscribe function.
     * @param {(state: T) => void} fn - Callback invoked on every state change
     * @returns {() => void} Unsubscribe function
     */
    subscribe(fn) {
      if (typeof fn !== 'function') throw new TypeError('subscribe: fn must be a function');
      listeners.add(fn);
      // Immediately invoke with current state so subscriber is in sync
      try {
        fn(state);
      } catch (err) {
        console.error('Store initial subscriber error:', err);
      }
      return () => {
        listeners.delete(fn);
      };
    },

    /**
     * Read a derived value from state via a selector.
     * @template R
     * @param {(state: T) => R} selector - Pure selector function
     * @returns {R}
     */
    select(selector) {
      if (typeof selector !== 'function') throw new TypeError('select: selector must be a function');
      return selector(state);
    }
  };
}

// ═══════════════════════════════════════════════════
// App Initial State
// ═══════════════════════════════════════════════════

/** @returns {import('./types').AppState} */
function buildInitialState() {
  return {
    // ── Collections ──
    jobs: [],
    inspections: [],
    customers: [],
    photos: [],
    services: [],
    expenses: [],
    visits: [],
    repairs: [],
    signatures: [],
    syncQueue: [],
    voiceNotes: [],
    trapLogs: [],
    reminders: [],
    communications: [],
    inventory: [],
    equipment: [],

    // ── Selection ──
    selectedJobId: null,
    selectedInspectionId: null,
    selectedCustomerId: null,

    // ── Auth / User ──
    currentUser: null,
    isAuthenticated: false,

    // ── Connectivity ──
    isOnline: typeof navigator !== 'undefined' ? navigator.onLine : true,
    syncStatus: 'idle', // 'idle' | 'syncing' | 'error' | 'synced'
    lastSyncAt: null,

    // ── UI ──
    theme: localStorage.getItem(THEME_KEY) || 'dark',
    page: 'dashboard',
    previousPage: null,
    loading: false,
    toast: null, // { message: string, type: 'success'|'error'|'warn', duration: number } | null
    searchQuery: '',
    drawerOpen: false,

    // ── Filters ──
    filters: {
      status: '',
      species: '',
      tech: '',
      town: '',
      priority: ''
    },

    // ── Modals ──
    activeModal: null, // string | null — which modal is open
    modalData: null, // any — data passed to the active modal

    // ── GPS ──
    pendingGPS: null, // { lat: number, lng: number, accuracy: number } | null

    // ── AI Assistant ──
    aiInput: '',
    aiResponse: '',
    aiLoading: false,

    // ── Schedule ──
    scheduleYear: new Date().getFullYear(),
    scheduleMonth: new Date().getMonth(),
    scheduleFilterDate: null,

    // ── Weather ──
    weatherCache: null,

    // ── Pagination ──
    jobsPage: 1,
    jobsPerPage: 25
  };
}

// ═══════════════════════════════════════════════════
// Hydrate from localStorage (offline-first)
// ═══════════════════════════════════════════════════

/**
 * Load persisted collections from localStorage.
 * @returns {Partial<ReturnType<typeof buildInitialState>> | null}
 */
function loadPersistedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      jobs: parsed.jobs ?? [],
      inspections: parsed.inspections ?? [],
      customers: parsed.customers ?? [],
      visits: parsed.visits ?? [],
      repairs: parsed.repairs ?? [],
      photos: parsed.photos ?? [],
      signatures: parsed.signatures ?? [],
      services: parsed.services ?? [],
      expenses: parsed.expenses ?? [],
      voiceNotes: parsed.voiceNotes ?? [],
      trapLogs: parsed.trapLogs ?? [],
      reminders: parsed.reminders ?? [],
      communications: parsed.communications ?? [],
      inventory: parsed.inventory ?? [],
      equipment: parsed.equipment ?? [],
      syncQueue: parsed.queue ?? []
    };
  } catch {
    console.warn('Failed to hydrate state from localStorage');
    return null;
  }
}

// ═══════════════════════════════════════════════════
// Create & Export App Store
// ═══════════════════════════════════════════════════

const persisted = loadPersistedState();
const initial = buildInitialState();

/** @type {ReturnType<typeof createStore>} */
export const store = createStore(persisted ? { ...initial, ...persisted } : initial);

// ═══════════════════════════════════════════════════
// Persistence Middleware
// ═══════════════════════════════════════════════════

/** Debounced localStorage persistence to reduce I/O */
let persistTimer = null;

/**
 * Persist collections to localStorage.
 * Only writes every 500ms to batch rapid changes.
 */
export function persistState() {
  clearTimeout(persistTimer);
  persistTimer = setTimeout(() => {
    try {
      const s = store.getState();
      const payload = {
        jobs: s.jobs,
        inspections: s.inspections,
        customers: s.customers,
        visits: s.visits,
        repairs: s.repairs,
        photos: s.photos,
        signatures: s.signatures,
        services: s.services,
        expenses: s.expenses,
        voiceNotes: s.voiceNotes,
        trapLogs: s.trapLogs,
        reminders: s.reminders,
        communications: s.communications,
        inventory: s.inventory,
        equipment: s.equipment,
        queue: s.syncQueue,
        savedAt: new Date().toISOString()
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      localStorage.setItem(`${STORAGE_KEY}_last`, new Date().toISOString());
    } catch (err) {
      console.error('State persistence failed:', err);
    }
  }, 500);
}

// Auto-persist whenever state changes (collections only)
store.subscribe(state => {
  // Only persist if we have data to avoid overwriting with empty state on init
  if (state.jobs?.length >= 0) {
    persistState();
  }
});

// ═══════════════════════════════════════════════════
// Snapshot System (30-second automatic backups)
// ═══════════════════════════════════════════════════

let snapshotTimer = null;

/**
 * Save a snapshot backup of current state.
 */
export function saveSnapshot() {
  try {
    const s = store.getState();
    const snapshot = {
      saved: new Date().toISOString(),
      db: {
        jobs: s.jobs,
        inspections: s.inspections,
        customers: s.customers,
        visits: s.visits,
        repairs: s.repairs,
        photos: s.photos,
        signatures: s.signatures,
        services: s.services,
        expenses: s.expenses,
        voiceNotes: s.voiceNotes,
        trapLogs: s.trapLogs,
        reminders: s.reminders,
        communications: s.communications,
        inventory: s.inventory,
        equipment: s.equipment,
        queue: s.syncQueue
      }
    };
    localStorage.setItem(`${STORAGE_KEY}_bak`, JSON.stringify(snapshot));
  } catch (err) {
    console.error('Snapshot failed:', err);
  }
}

/**
 * Start automatic snapshotting every N milliseconds.
 * @param {number} [intervalMs=30000] - Snapshot interval
 * @returns {() => void} Cleanup function
 */
export function startSnapshots(intervalMs = 30000) {
  stopSnapshots();
  snapshotTimer = setInterval(saveSnapshot, intervalMs);
  return stopSnapshots;
}

/**
 * Stop automatic snapshotting.
 */
export function stopSnapshots() {
  if (snapshotTimer) {
    clearInterval(snapshotTimer);
    snapshotTimer = null;
  }
}

// ═══════════════════════════════════════════════════
// Toast Helper
// ═══════════════════════════════════════════════════

/**
 * Show a toast notification via the store.
 * @param {string} message
 * @param {'success'|'error'|'warn'} [type='success']
 * @param {number} [duration=3000]
 */
export function showToast(message, type = 'success', duration = 3000) {
  store.setState({ toast: { message, type, duration } });
  // Auto-clear toast
  setTimeout(() => {
    store.setState(s => {
      if (s.toast?.message === message) return { ...s, toast: null };
      return s;
    });
  }, duration);
}

/**
 * Show/hide global loading overlay.
 * @param {boolean} isLoading
 * @param {string} [message='Loading...']
 */
export function setLoading(isLoading, message = 'Loading...') {
  store.setState({ loading: isLoading });
}

// ═══════════════════════════════════════════════════
// Navigation Helpers
// ═══════════════════════════════════════════════════

/**
 * Navigate to a page via the store.
 * @param {string} page
 * @param {Record<string, any>} [extra]
 */
export function navigateTo(page, extra = {}) {
  store.setState(s => ({
    ...s,
    previousPage: s.page,
    page,
    ...extra
  }));
}

/**
 * Open a modal.
 * @param {string} modalId
 * @param {any} [data]
 */
export function openModal(modalId, data = null) {
  store.setState({ activeModal: modalId, modalData: data });
}

/**
 * Close the active modal.
 */
export function closeModal() {
  store.setState({ activeModal: null, modalData: null });
}

/**
 * Toggle the navigation drawer.
 */
export function toggleDrawer() {
  store.setState(s => ({ drawerOpen: !s.drawerOpen }));
}
