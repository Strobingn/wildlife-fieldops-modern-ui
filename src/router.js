/**
 * Wildlife Whisperer FieldOps — Hash-Based SPA Router
 *
 * Declarative routing with parameterized routes, navigation guards,
 * and before/after hooks. Maps hash fragments to state changes.
 *
 * @module router
 * @version 3.0.0
 * @example
 *   router.add('/', () => navigateTo('dashboard'));
 *   router.add('/jobs/:id', (params) => navigateTo('job-detail', { selectedJobId: params.id }));
 *   router.navigate('/jobs/abc123');
 */

import { store, navigateTo } from './state.js';
import { showToast } from './state.js';

// ═══════════════════════════════════════════════════
// Router Class
// ═══════════════════════════════════════════════════

export class Router {
  /**
   * Create a new Router instance.
   */
  constructor() {
    /** @private @type {Map<string, Function>} */
    this.routes = new Map();
    /** @private @type {Function[]} */
    this.beforeHooks = [];
    /** @private @type {Function[]} */
    this.afterHooks = [];
    /** @private @type {Function|null} */
    this.fallbackHandler = null;
    /** @private @type {string} */
    this.currentPath = '';
    /** @private @type {boolean} */
    this.initialized = false;

    // Bind event listeners (stored for cleanup)
    this._onHashChange = () => this.resolve();
    this._onLoad = () => this.resolve();

    window.addEventListener('hashchange', this._onHashChange);
    window.addEventListener('load', this._onLoad);
  }

  /**
   * Register a route handler.
   * @param {string} path - Route pattern, e.g. '/jobs/:id'
   * @param {Function} handler - (params, query) => void
   * @returns {Router} Fluent interface
   */
  add(path, handler) {
    if (typeof path !== 'string' || !path.startsWith('/')) {
      throw new TypeError(`Router.add: path must be a string starting with "/", got "${path}"`);
    }
    if (typeof handler !== 'function') {
      throw new TypeError('Router.add: handler must be a function');
    }
    this.routes.set(path, handler);
    return this;
  }

  /**
   * Set a fallback handler for unmatched routes.
   * @param {Function} handler
   * @returns {Router}
   */
  setFallback(handler) {
    if (typeof handler !== 'function') throw new TypeError('Router.setFallback: handler must be a function');
    this.fallbackHandler = handler;
    return this;
  }

  /**
   * Register a before-each guard. Return false to cancel navigation.
   * @param {(to: string, from: string) => boolean|Promise<boolean>} fn
   * @returns {Router}
   */
  beforeEach(fn) {
    if (typeof fn !== 'function') throw new TypeError('Router.beforeEach: fn must be a function');
    this.beforeHooks.push(fn);
    return this;
  }

  /**
   * Register an after-each hook.
   * @param {(to: string, from: string) => void} fn
   * @returns {Router}
   */
  afterEach(fn) {
    if (typeof fn !== 'function') throw new TypeError('Router.afterEach: fn must be a function');
    this.afterHooks.push(fn);
    return this;
  }

  /**
   * Navigate to a path by setting window.location.hash.
   * @param {string} path - Target path (e.g. '/jobs/123')
   */
  navigate(path) {
    if (!path || typeof path !== 'string') {
      console.error('Router.navigate: invalid path', path);
      return;
    }
    const target = path.startsWith('/') ? path : '/' + path;
    window.location.hash = target;
    // hashchange event will trigger resolve()
  }

  /**
   * Go back in browser history.
   */
  back() {
    window.history.back();
  }

  /**
   * Replace current hash without adding history entry.
   * @param {string} path
   */
  replace(path) {
    if (!path || typeof path !== 'string') return;
    const target = path.startsWith('/') ? path : '/' + path;
    window.location.replace(window.location.href.split('#')[0] + '#' + target);
    this.resolve();
  }

  /**
   * Resolve current hash and execute matching route.
   * Called automatically on hashchange and load events.
   */
  resolve() {
    const hash = window.location.hash.slice(1) || '/';
    const [pathPart, queryPart] = hash.split('?');
    const path = pathPart || '/';
    const query = this._parseQuery(queryPart);

    const from = this.currentPath;
    this.currentPath = path;

    // ── Run before hooks ──
    for (const hook of this.beforeHooks) {
      try {
        const result = hook(path, from);
        if (result === false) {
          // Navigation cancelled — restore previous hash
          window.location.hash = from || '/';
          this.currentPath = from;
          return;
        }
      } catch (err) {
        console.error('Router beforeEach error:', err);
        showToast('Navigation error', 'error');
        return;
      }
    }

    // ── Find matching route ──
    const match = this._matchRoute(path);
    if (match) {
      try {
        match.handler(match.params, query);
      } catch (err) {
        console.error(`Route error for "${match.path}":`, err);
        showToast('Page load error', 'error');
      }
    } else if (this.fallbackHandler) {
      try {
        this.fallbackHandler({ path }, query);
      } catch (err) {
        console.error('Fallback handler error:', err);
      }
    } else {
      console.warn('No route matched:', path);
      navigateTo('dashboard');
    }

    // ── Run after hooks ──
    for (const hook of this.afterHooks) {
      try {
        hook(path, from);
      } catch (err) {
        console.error('Router afterEach error:', err);
      }
    }
  }

  /**
   * Get current route path.
   * @returns {string}
   */
  getCurrentPath() {
    return this.currentPath;
  }

  /**
   * Remove all event listeners. Call on app teardown.
   */
  destroy() {
    window.removeEventListener('hashchange', this._onHashChange);
    window.removeEventListener('load', this._onLoad);
    this.routes.clear();
    this.beforeHooks = [];
    this.afterHooks = [];
    this.fallbackHandler = null;
    this.initialized = false;
  }

  // ── Private ──

  /**
   * @private
   * Match a path against registered routes. Supports :param segments.
   * @param {string} path
   * @returns {{path: string, params: Record<string, string>, handler: Function}|null}
   */
  _matchRoute(path) {
    // Exact match first
    if (this.routes.has(path)) {
      return { path, params: {}, handler: this.routes.get(path) };
    }

    // Parameterized match
    for (const [routePath, handler] of this.routes) {
      const params = this._extractParams(path, routePath);
      if (params) {
        return { path: routePath, params, handler };
      }
    }
    return null;
  }

  /**
   * @private
   * Extract named parameters from a path using a route pattern.
   * @param {string} path - Actual URL path
   * @param {string} pattern - Route pattern with :param
   * @returns {Record<string, string>|null} Params object or null if no match
   */
  _extractParams(path, pattern) {
    const pathSegments = path.split('/').filter(Boolean);
    const patternSegments = pattern.split('/').filter(Boolean);

    if (pathSegments.length !== patternSegments.length) return null;

    /** @type {Record<string, string>} */
    const params = {};
    for (let i = 0; i < patternSegments.length; i++) {
      const ps = patternSegments[i];
      const val = pathSegments[i];
      if (ps.startsWith(':')) {
        params[ps.slice(1)] = decodeURIComponent(val);
      } else if (ps !== val) {
        return null;
      }
    }
    return params;
  }

  /**
   * @private
   * Parse query string into an object.
   * @param {string} [queryStr]
   * @returns {Record<string, string>}
   */
  _parseQuery(queryStr) {
    if (!queryStr) return {};
    const params = {};
    for (const pair of queryStr.split('&')) {
      const [k, v] = pair.split('=');
      if (k) params[decodeURIComponent(k)] = decodeURIComponent(v || '');
    }
    return params;
  }
}

// ═══════════════════════════════════════════════════
// Singleton Instance
// ═══════════════════════════════════════════════════

/** @type {Router} */
export const router = new Router();

// ═══════════════════════════════════════════════════
// Default Route Mappings
// ═══════════════════════════════════════════════════

/**
 * Register all standard application routes.
 * Call once during app initialization.
 */
export function registerRoutes() {
  router
    .add('/', () => navigateTo('dashboard'))
    .add('/jobs', () => navigateTo('jobs'))
    .add('/jobs/new', () => navigateTo('job-form'))
    .add('/jobs/:id', params => navigateTo('job-detail', { selectedJobId: params.id }))
    .add('/jobs/:id/edit', params => navigateTo('job-form', { selectedJobId: params.id }))
    .add('/customers', () => navigateTo('customers'))
    .add('/customers/new', () => navigateTo('customer-form'))
    .add('/customers/:id', params => navigateTo('customer-form', { selectedCustomerId: params.id }))
    .add('/customers/:id/edit', params => navigateTo('customer-form', { selectedCustomerId: params.id }))
    .add('/estimate', () => navigateTo('estimate'))
    .add('/photos', () => navigateTo('photos'))
    .add('/gps', () => navigateTo('gps'))
    .add('/metrics', () => navigateTo('metrics'))
    .add('/settings', () => navigateTo('settings'))
    .add('/schedule', () => navigateTo('schedule'))
    .add('/route', () => navigateTo('route'))
    .add('/expenses', () => navigateTo('expenses'))
    .add('/inventory', () => navigateTo('inventory'))
    .add('/equipment', () => navigateTo('equipment'))
    .add('/ai', () => navigateTo('ai'))
    .add('/inspections', () => navigateTo('inspections'))
    .add('/inspections/new', () => navigateTo('inspection-form'))
    .add('/inspections/:id', params => navigateTo('inspection-form', { selectedInspectionId: params.id }))
    .add('/inspections/:id/edit', params => navigateTo('inspection-form', { selectedInspectionId: params.id }))
    .setFallback(() => navigateTo('dashboard'));
}
