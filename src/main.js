/**
 * Wildlife Whisperer FieldOps — Application Entry Point
 *
 * Initializes the store, router, all services, and manages the component
 * render loop. This is the central hub that wires everything together.
 *
 * Architecture:
 *   - Component-based: each page is { render(), unmount(), afterRender() }
 *   - Store-driven: render loop subscribes to state changes
 *   - Router-driven: hash changes → store.page updates → re-render
 *   - Lifecycle-safe: proper cleanup on every page transition
 *
 * @module main
 * @version 3.0.0
 */

// ─────────────────────────────────────────────────
// Imports
// ─────────────────────────────────────────────────

import { config, isFeatureAvailable, getBuildInfo } from './config.js';
import {
  store,
  navigateTo,
  showToast,
  setLoading,
  toggleDrawer,
  openModal,
  closeModal,
  startSnapshots,
  stopSnapshots
} from './state.js';
import { router, registerRoutes } from './router.js';
import { initErrorBoundary, asyncWrapper, retry, safeExecute, logError, safeJSONParse } from './errors.js';
import { Geolocation } from '@capacitor/geolocation';
import { loadGoogleMaps } from './api/maps.js';
import {
  E,
  money,
  tel,
  id,
  now,
  formatDate,
  formatPhone,
  isValidPhone,
  validateJob,
  validateCustomer,
  compressImage,
  generatePDF,
  deepClone,
  debounce,
  throttle,
  groupBy,
  sortBy,
  searchJobs,
  filterJobs,
  calculateEstimate,
  mergeArrays,
  formatDateShort
} from './utils.js';
import {
  SPECIES,
  SERVICES,
  SPECIES_ICONS,
  SPECIES_HINTS,
  STATUS_STYLES,
  PRIORITIES,
  INSPECTION_STATUSES,
  INSPECTION_STATUS_STYLES,
  VISIT_TYPES,
  REPAIR_STATUSES,
  SEVERITIES,
  PHOTO_TAGS,
  ESTIMATE_TEMPLATES,
  BASE_PRICES,
  SEVERITY_MULTIPLIERS,
  EXPENSE_CATEGORIES,
  INVENTORY_CATEGORIES,
  EQUIPMENT_TYPES,
  COMMUNICATION_TYPES,
  WEATHER_ICONS,
  DEFAULT_CHECKLIST,
  BOTTOM_NAV,
  DRAWER_PAGES,
  STORAGE_KEY,
  WEATHER_CACHE_KEY
} from './constants.js';

// ─────────────────────────────────────────────────
// DOM Helpers (local to this module)
// ─────────────────────────────────────────────────

/** @returns {HTMLElement|null} */
const $ = sel => document.querySelector(sel);
const $$ = (sel, scope) => Array.from((scope || document).querySelectorAll(sel));

/**
 * Create or update the toast element.
 * @param {string} msg
 * @param {'success'|'error'|'warn'} [type='success']
 * @param {number} [duration=3000]
 */
function renderToast(msg, type = 'success', duration = 3000) {
  let t = document.getElementById('toast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'toast';
    t.style.cssText =
      'position:fixed;bottom:90px;left:50%;transform:translateX(-50%) translateY(10px);' +
      'padding:12px 20px;border-radius:12px;font-size:14px;font-weight:500;z-index:10000;' +
      'opacity:0;transition:opacity .3s,transform .3s;pointer-events:none;' +
      'box-shadow:0 4px 12px rgba(0,0,0,0.3);font-family:Inter,sans-serif;';
    document.body.appendChild(t);
  }
  const styles = {
    success: 'background:rgba(34,197,94,0.95);color:#fff;',
    error: 'background:rgba(239,68,68,0.95);color:#fff;',
    warn: 'background:rgba(251,191,36,0.95);color:#000;'
  };
  t.style.cssText =
    'position:fixed;bottom:90px;left:50%;transform:translateX(-50%);padding:12px 20px;' +
    'border-radius:12px;font-size:14px;font-weight:500;z-index:10000;opacity:1;' +
    'transition:opacity .3s,transform .3s;pointer-events:none;' +
    'box-shadow:0 4px 12px rgba(0,0,0,0.3);font-family:Inter,sans-serif;' +
    (styles[type] || styles.success);
  t.textContent = msg;
  clearTimeout(t._timer);
  t._timer = setTimeout(() => {
    t.style.opacity = '0';
  }, duration);
}

/**
 * Create or update the loading overlay.
 * @param {boolean} show
 * @param {string} [msg='Loading...']
 */
function renderLoading(show, msg = 'Loading...') {
  let o = document.getElementById('loading-overlay');
  if (!o) {
    o = document.createElement('div');
    o.id = 'loading-overlay';
    o.style.cssText =
      'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);' +
      'display:none;align-items:center;justify-content:center;flex-direction:column;' +
      'gap:12px;z-index:9998;color:#fff;font-size:14px;font-family:Inter,sans-serif;';
    o.innerHTML =
      '<div id="loading-spinner" style="width:40px;height:40px;border:4px solid rgba(255,255,255,0.2);' +
      'border-top-color:#22c55e;border-radius:50%;animation:spin 1s linear infinite;"></div>' +
      '<span id="loading-msg">Loading...</span>';
    document.body.appendChild(o);
    // Inject spinner keyframes if not already present
    if (!document.getElementById('ww-spin-style')) {
      const s = document.createElement('style');
      s.id = 'ww-spin-style';
      s.textContent = '@keyframes spin{to{transform:rotate(360deg)}}';
      document.head.appendChild(s);
    }
  }
  const msgEl = document.getElementById('loading-msg');
  if (msgEl) msgEl.textContent = msg;
  o.style.display = show ? 'flex' : 'none';
}

/**
 * Create the app shell (nav, drawer, bottom bar, modals).
 */
function buildAppShell() {
  const existing = document.getElementById('app-shell');
  if (existing) return;

  const shell = document.createElement('div');
  shell.id = 'app-shell';
  try {
    shell.innerHTML = `
      <header class="app-bar">
        <button id="menu-btn" class="icon-btn" aria-label="Menu">&#9776;</button>
        <h1 id="page-label" class="page-title">Wildlife Whisperer</h1>
        <div class="app-bar-actions">
          <button id="search-btn" class="icon-btn" aria-label="Search">&#128269;</button>
          <span id="sync-indicator" class="sync-badge idle"></span>
        </div>
      </header>
      <nav id="drawer" class="drawer" aria-hidden="true">
        <div class="drawer-header">
          <h2>🦝 Wildlife Whisperer</h2>
          <button id="drawer-close" class="icon-btn" aria-label="Close">&times;</button>
        </div>
        <div class="drawer-body">
          ${DRAWER_PAGES.map(
            p =>
              `<a class="drawer-link" data-route="${p.id}" href="${p.id === 'dashboard' ? '#/' : `#/${p.id}`}">${p.label}</a>`
          ).join('')}
        </div>
        <div class="drawer-footer"><small id="build-info"></small></div>
      </nav>
      <div id="drawer-backdrop" class="drawer-backdrop"></div>
      <div id="search-overlay" class="search-overlay" style="display:none;">
        <div class="search-box">
          <input id="global-search" type="text" placeholder="Search jobs, customers, addresses..." autocomplete="off">
          <button id="search-close" class="icon-btn">&times;</button>
        </div>
        <div id="search-results" class="search-results"></div>
      </div>
      <main id="app" class="app-container"></main>
      <nav class="bottom-nav">
        ${BOTTOM_NAV.map(
          (item, idx) =>
            `<button data-route="${item.id}" data-idx="${idx}" aria-label="${E(item.label)}">
            <span class="nav-icon">${item.icon}</span>
            <span class="nav-label">${E(item.label)}</span>
          </button>`
        ).join('')}
      </nav>
    `;
  } catch (e) {
    console.error('[AppShell] Failed to build:', e.message);
  }
  document.body.appendChild(shell);

  // Drawer toggle
  $('#menu-btn')?.addEventListener('click', toggleDrawer);
  $('#drawer-close')?.addEventListener('click', toggleDrawer);
  $('#drawer-backdrop')?.addEventListener('click', toggleDrawer);

  // Drawer navigation
  $$('.drawer-link').forEach(link => {
    link.addEventListener('click', e => {
      e.preventDefault();
      const route = link.getAttribute('data-route');
      if (route === 'dashboard') router.navigate('/');
      else router.navigate(`/${route}`);
      toggleDrawer();
    });
  });

  // Search
  $('#search-btn')?.addEventListener('click', openSearch);
  $('#search-close')?.addEventListener('click', closeSearch);

  // Bottom nav
  $$('.bottom-nav button').forEach(btn => {
    btn.addEventListener('click', () => {
      const route = btn.getAttribute('data-route');
      if (route === 'dashboard') router.navigate('/');
      else router.navigate(`/${route}`);
    });
  });

  // Build info
  const buildEl = $('#build-info');
  if (buildEl) buildEl.textContent = getBuildInfo();
}

/**
 * Update bottom nav active state.
 * @param {string} page
 */
function updateBottomNav(page) {
  const map = { dashboard: 0, jobs: 1, inspections: 2, schedule: 3, gps: 4 };
  const idx = map[page] ?? -1;
  $$('.bottom-nav button').forEach((b, i) => {
    b.classList.toggle('active', i === idx);
  });
}

/**
 * Update page label in app bar.
 * @param {string} page
 */
function updatePageLabel(page) {
  const labels = {
    dashboard: '🏠 Dashboard',
    jobs: '🦝 Jobs',
    'job-detail': '📂 Job Detail',
    'job-form': '✏️ Edit Job',
    inspections: '🔍 Inspections',
    'inspection-form': '🔍 New/Edit Inspection',
    customers: '👥 Customers',
    'customer-form': '✏️ Edit Customer',
    schedule: '📅 Schedule',
    estimate: '💵 Estimator',
    photos: '📸 Photos',
    gps: '📍 GPS Map',
    metrics: '📊 Metrics',
    settings: '⚙️ Settings',
    ai: '🧠 AI Assistant',
    route: '🗺️ Route Optimizer',
    expenses: '💰 Expenses',
    inventory: '📦 Inventory',
    equipment: '🔧 Equipment'
  };
  const el = $('#page-label');
  if (el) el.textContent = labels[page] || 'Wildlife Whisperer';
}

/**
 * Update sync indicator badge.
 * @param {'idle'|'syncing'|'error'|'synced'} status
 */
function updateSyncIndicator(status) {
  const el = $('#sync-indicator');
  if (!el) return;
  el.className = `sync-badge ${status}`;
  el.textContent = status === 'syncing' ? '↻' : status === 'error' ? '✗' : status === 'synced' ? '✓' : '';
}

// ─────────────────────────────────────────────────
// Search
// ─────────────────────────────────────────────────

/** @type {Function} */
let searchUnsubscriber = null;

function openSearch() {
  const overlay = $('#search-overlay');
  if (!overlay) return;
  overlay.style.display = 'flex';
  $('#global-search')?.focus();
  store.setState({ searchQuery: '' });
}

function closeSearch() {
  const overlay = $('#search-overlay');
  if (overlay) overlay.style.display = 'none';
  store.setState({ searchQuery: '' });
}

/** @type {Function} */
const onSearchInput = debounce(q => {
  store.setState({ searchQuery: q });
}, config.SEARCH_DEBOUNCE);

/**
 * Render global search results into the search overlay.
 * Searches jobs, customers, and inspections.
 */
function renderSearchResults() {
  const state = store.getState();
  const q = (state.searchQuery || '').toLowerCase().trim();
  const container = $('#search-results');
  if (!container) return;

  if (!q) {
    container.innerHTML = '';
    return;
  }

  // Search jobs
  const matchedJobs = (state.jobs || [])
    .filter(j =>
      ['title', 'customer', 'address', 'town', 'species', 'scope', 'status', 'phone'].some(f =>
        String(j?.[f] ?? '')
          .toLowerCase()
          .includes(q)
      )
    )
    .slice(0, 5);

  // Search customers
  const matchedCustomers = (state.customers || [])
    .filter(c =>
      ['name', 'address', 'town', 'phone', 'email'].some(f =>
        String(c?.[f] ?? '')
          .toLowerCase()
          .includes(q)
      )
    )
    .slice(0, 5);

  // Search inspections
  const matchedInspections = (state.inspections || [])
    .filter(i =>
      ['customer', 'address', 'town', 'species', 'phone', 'notes'].some(f =>
        String(i?.[f] ?? '')
          .toLowerCase()
          .includes(q)
      )
    )
    .slice(0, 5);

  const html = [];

  if (matchedJobs.length) {
    html.push(`<div class="search-section"><h4>🦝 Jobs</h4>`);
    matchedJobs.forEach(j => {
      html.push(`
        <div class="search-result-item" data-action="open-job" data-id="${E(j.id)}">
          <b>${E(j.title || j.species + ' job')}</b>
          <span class="tiny">${E(j.customer_name)} · ${E(j.address)}${j.town ? ', ' + E(j.town) : ''}</span>
        </div>
      `);
    });
    html.push('</div>');
  }

  if (matchedCustomers.length) {
    html.push(`<div class="search-section"><h4>👥 Customers</h4>`);
    matchedCustomers.forEach(c => {
      html.push(`
        <div class="search-result-item" data-action="open-customer" data-id="${E(c.id)}">
          <b>${E(c.name)}</b>
          <span class="tiny">${formatPhone(c.phone)} · ${E(c.address)}${c.town ? ', ' + E(c.town) : ''}</span>
        </div>
      `);
    });
    html.push('</div>');
  }

  if (matchedInspections.length) {
    html.push(`<div class="search-section"><h4>🔍 Inspections</h4>`);
    matchedInspections.forEach(i => {
      html.push(`
        <div class="search-result-item" data-action="open-inspection" data-id="${E(i.id)}">
          <b>${E(i.customer_name || 'Unknown')}</b>
          <span class="tiny">${E(i.species)} · ${E(i.status)}${i.scheduled_start ? ' · ' + formatDate(i.scheduled_start) : ''}</span>
        </div>
      `);
    });
    html.push('</div>');
  }

  if (!matchedJobs.length && !matchedCustomers.length && !matchedInspections.length) {
    html.push(`<div class="card empty">No results for "${E(q)}"</div>`);
  }

  container.innerHTML = html.join('');

  // Attach click handlers
  $$('[data-action="open-job"]', container).forEach(el => {
    el.addEventListener('click', () => {
      closeSearch();
      router.navigate(`/jobs/${el.dataset.id}`);
    });
  });
  $$('[data-action="open-customer"]', container).forEach(el => {
    el.addEventListener('click', () => {
      closeSearch();
      router.navigate(`/customers/${el.dataset.id}`);
    });
  });
  $$('[data-action="open-inspection"]', container).forEach(el => {
    el.addEventListener('click', () => {
      closeSearch();
      navigateTo('inspection-form', { selectedInspectionId: el.dataset.id });
    });
  });
}

// ─────────────────────────────────────────────────
// Component Helpers
// ─────────────────────────────────────────────────

/**
 * Generate option tags for a select element.
 * @param {string[]} arr
 * @param {string} [selected='']
 * @returns {string} HTML string
 */
function O(arr, selected = '') {
  return arr.map(x => `<option value="${E(x)}" ${x === selected ? 'selected' : ''}>${E(x)}</option>`).join('');
}

/**
 * Calculate job completeness score (0-100).
 * @param {string} jobId
 * @returns {number}
 */
function jobScore(jobId) {
  const s = store.getState();
  const hasVisits = s.visits.some(v => v.jobId === jobId || v.job_id === jobId);
  const hasPhotos = s.photos.some(p => p.jobId === jobId || p.job_id === jobId);
  const hasRepairs = s.repairs.some(r => r.jobId === jobId || r.job_id === jobId);
  const hasSig = s.signatures.some(sig => sig.jobId === jobId || sig.job_id === jobId);
  return Math.min(100, (hasVisits ? 25 : 0) + (hasPhotos ? 25 : 0) + (hasRepairs ? 25 : 0) + (hasSig ? 25 : 0));
}

/**
 * Get species hint for AI suggestions.
 * @param {string} species
 * @returns {string}
 */
function hint(species) {
  return SPECIES_HINTS[species] || 'Track behavior, seasonality, recurrence.';
}

/**
 * Lazy-load images using IntersectionObserver.
 * Call afterRender on pages with images.
 */
function initLazyImages() {
  const imgs = $$('img.lazy');
  if (!imgs.length) return () => {};
  const obs = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const img = entry.target;
        if (img.dataset.src) img.src = img.dataset.src;
        img.classList.add('loaded');
        img.classList.remove('lazy');
        obs.unobserve(img);
      }
    });
  });
  imgs.forEach(img => obs.observe(img));
  return () => obs.disconnect();
}

/**
 * Navigate to a Google Maps direction URL.
 * @param {number|null} lat
 * @param {number|null} lng
 * @param {string} address
 */
function navigateToJob(lat, lng, address) {
  if (!lat || !lng) {
    if (address) {
      window.open(
        `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}&travelmode=driving`,
        '_blank'
      );
      return;
    }
    showToast('No GPS data for this job.', 'warn');
    return;
  }
  window.open(`https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`, '_blank');
}

// ─────────────────────────────────────────────────
// Page Components
// ─────────────────────────────────────────────────

/**
 * Each component is an object with:
 *   render(state): string — HTML string
 *   unmount(): void — cleanup (optional)
 *   afterRender(state): void — post-render init (optional)
 */

// ── Dashboard ────────────────────────────────────

const Dashboard = {
  /** @type {Function|null} */
  _cleanup: null,

  render(state) {
    const jobs = state.jobs || [];
    const openJobs = jobs.filter(j => j.status !== 'Closed' && j.status !== 'Cancelled');
    const totalEstimate = openJobs.reduce(
      (sum, j) => sum + (j.grand_total || j.estimate || calculateEstimate(j.species, j.severity) || 0),
      0
    );
    const q = (state.searchQuery || '').toLowerCase();
    const recentJobs = q ? searchJobs(jobs, q).slice(0, 5) : jobs.slice(0, 5);

    const townCounts = groupBy(jobs, 'town');
    const topTowns = Object.entries(townCounts)
      .sort((a, b) => b[1].length - a[1].length)
      .slice(0, 5);

    return `
      <div class="page dashboard-page">
        <!-- Metrics Cards -->
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value">${openJobs.length}</div>
            <div class="metric-label">Open Jobs</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${money(totalEstimate)}</div>
            <div class="metric-label">Pipeline Value</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${state.syncQueue.length}</div>
            <div class="metric-label">Pending Sync</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${state.photos.length}</div>
            <div class="metric-label">Photos</div>
          </div>
        </div>

        <!-- Weather Widget -->
        ${state.weatherCache ? renderWeatherWidget(state.weatherCache) : ''}

        <!-- Follow-up Reminders -->
        ${this._renderDashboardReminders(state)}

        <!-- Quick Actions -->
        <div class="quick-actions">
          <button class="quick-action" data-action="new-job">
            <span>➕</span>
            <span>New Job</span>
          </button>
          <button class="quick-action" data-action="new-customer">
            <span>👤</span>
            <span>Add Customer</span>
          </button>
          <button class="quick-action" data-action="estimate">
            <span>💵</span>
            <span>Estimate</span>
          </button>
          <button class="quick-action" data-action="gps">
            <span>📍</span>
            <span>GPS Map</span>
          </button>
        </div>

        <!-- Recent Jobs -->
        <h2 class="section-title">${q ? 'Search Results' : 'Recent Jobs'}</h2>
        <div class="job-list">
          ${
            recentJobs.length
              ? recentJobs.map(j => this._jobCard(j, state)).join('')
              : '<div class="card empty">No jobs yet.</div>'
          }
        </div>

        <!-- Top Towns -->
        ${
          topTowns.length
            ? `
          <h2 class="section-title">Top Towns</h2>
          <div class="town-grid">
            ${topTowns
              .map(
                ([town, jobs]) => `
              <div class="town-card">
                <b>${E(town || 'Unsorted')}</b>
                <span class="badge">${jobs.length} jobs</span>
              </div>
            `
              )
              .join('')}
          </div>
        `
            : ''
        }


      </div>
    `;
  },

  _renderDashboardReminders(state) {
    const now = new Date();
    const reminders = (state.reminders || [])
      .filter(r => r.status === 'pending' && new Date(r.dueDate) >= new Date(now.getTime() - 7 * 86400000))
      .sort((a, b) => new Date(a.dueDate) - new Date(b.dueDate))
      .slice(0, 5);

    if (!reminders.length) return '';

    const overdueCount = reminders.filter(r => new Date(r.dueDate) < now).length;

    return `
      <div class="card">
        <h3>🔔 Follow-ups ${overdueCount > 0 ? `<span class="pill bad">${overdueCount} overdue</span>` : ''}</h3>
        ${reminders
          .map(r => {
            const isOverdue = new Date(r.dueDate) < now;
            const job = state.jobs.find(j => j.id === r.jobId);
            return `
            <div class="reminder-row ${isOverdue ? 'warn' : ''}" data-action="open-reminder-job" data-job-id="${E(r.jobId)}">
              <span>${isOverdue ? '🔴' : '🟡'} ${formatDate(r.dueDate)}</span>
              <span class="tiny">${job ? E(job.customer_name) : 'Unknown'} — ${E(r.notes || 'Follow-up')}</span>
              <button class="btn-sm" data-action="dismiss-reminder" data-id="${E(r.id)}">✓ Done</button>
            </div>
          `;
          })
          .join('')}
      </div>
    `;
  },

  _jobCard(j, state) {
    const s = jobScore(j.id);
    const icon = SPECIES_ICONS[j.species] || '🐾';
    const sc = STATUS_STYLES[j.status] || 'active';
    const vCount = (state.visits || []).filter(v => (v.jobId || v.job_id) === j.id).length;
    const rCount = (state.repairs || []).filter(r => (r.jobId || r.job_id) === j.id).length;
    const pCount = (state.photos || []).filter(p => (p.jobId || p.job_id) === j.id).length;
    return `
      <div class="card job-card" data-job-id="${E(j.id)}">
        <div class="job-header">
          <span class="species-icon">${icon}</span>
          <h3 class="job-title">${E(j.title || j.species + ' job')}</h3>
          <span class="status-pill ${sc}">${E(j.status)}</span>
        </div>
        <div class="job-meta">${E(j.customer_name)} · ${formatPhone(j.phone)}</div>
        <div class="job-address">${E(j.address)}${j.town ? `, ${E(j.town)}` : ''}</div>
        <div class="job-pills">
          <span class="pill">${E(j.species)}</span>
          <span class="pill">${vCount} visits</span>
          <span class="pill">${rCount} repairs</span>
          <span class="pill">${pCount} photos</span>
          ${j.latitude || j.lat ? '<span class="pill">📍 GPS</span>' : ''}
        </div>
        <div class="progress-bar"><div class="progress-fill" style="width:${s}%"></div></div>
        <div class="job-footer">
          <span class="tiny">Score ${s}% · Est ${money(j.grand_total || j.estimate || calculateEstimate(j.species))}</span>
          <div class="job-actions">
            <button class="btn-sm primary" data-action="open-job" data-id="${E(j.id)}">Open</button>
            <button class="btn-sm" data-action="navigate" data-lat="${j.latitude || j.lat || ''}" data-lng="${j.longitude || j.lng || ''}" data-addr="${E(j.address)}">Navigate</button>
          </div>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    this._cleanup = initLazyImages();

    // Job card clicks
    $$('[data-action="open-job"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate(`/jobs/${btn.dataset.id}`));
    });

    // Navigate buttons
    $$('[data-action="navigate"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const lat = parseFloat(btn.dataset.lat) || null;
        const lng = parseFloat(btn.dataset.lng) || null;
        navigateToJob(lat, lng, btn.dataset.addr || '');
      });
    });

    // Quick action buttons
    $$('[data-action="new-job"]').forEach(b => {
      b.addEventListener('click', () => router.navigate('/jobs/new'));
    });
    $$('[data-action="new-customer"]').forEach(b => {
      b.addEventListener('click', () => router.navigate('/customers/new'));
    });
    $$('[data-action="estimate"]').forEach(b => {
      b.addEventListener('click', () => router.navigate('/estimate'));
    });
    $$('[data-action="gps"]').forEach(b => {
      b.addEventListener('click', () => router.navigate('/gps'));
    });

    // Click on card header to open job
    $$('.job-card').forEach(card => {
      card.addEventListener('click', e => {
        if (e.target.closest('button')) return; // Don't trigger on button clicks
        const id = card.dataset.jobId;
        if (id) router.navigate(`/jobs/${id}`);
      });
    });

    // Reminder actions
    $$('[data-action="dismiss-reminder"]').forEach(btn => {
      btn.addEventListener('click', () => handleDismissReminder(btn.dataset.id));
    });
    $$('[data-action="open-reminder-job"]').forEach(el => {
      el.addEventListener('click', e => {
        if (e.target.closest('button')) return;
        const jobId = el.dataset.jobId;
        if (jobId) router.navigate(`/jobs/${jobId}`);
      });
    });
  },

  unmount() {
    if (this._cleanup) {
      this._cleanup();
      this._cleanup = null;
    }
  }
};

// ── Job List ─────────────────────────────────────

const JobList = {
  _cleanup: null,

  render(state) {
    const q = (state.searchQuery || '').toLowerCase();
    let jobs = q ? searchJobs(state.jobs, q) : [...state.jobs];
    jobs = filterJobs(jobs, state.filters);
    jobs = sortBy(jobs, 'updated_at', 'desc');

    return `
      <div class="page job-list-page">
        <div class="list-toolbar">
          <input type="text" id="job-search" class="search-input" placeholder="Search jobs..."
            value="${E(state.searchQuery)}" autocomplete="off">
          <select id="filter-status" class="filter-select">
            <option value="">All Statuses</option>
            ${Object.keys(STATUS_STYLES)
              .map(s => `<option value="${E(s)}" ${state.filters.status === s ? 'selected' : ''}>${E(s)}</option>`)
              .join('')}
          </select>
          <select id="filter-species" class="filter-select">
            <option value="">All Species</option>
            ${SPECIES.map(s => `<option value="${E(s)}" ${state.filters.species === s ? 'selected' : ''}>${E(s)}</option>`).join('')}
          </select>
          <button class="btn primary" data-action="new-job">➕ New Job</button>
        </div>
        <div class="job-list">
          ${
            jobs.length
              ? jobs.map(j => this._jobCard(j, state)).join('')
              : `<div class="card empty">${q ? 'No matching jobs.' : 'No jobs yet.'}</div>`
          }
        </div>
      </div>
    `;
  },

  _jobCard(j, state) {
    const s = jobScore(j.id);
    const icon = SPECIES_ICONS[j.species] || '🐾';
    const sc = STATUS_STYLES[j.status] || 'active';
    return `
      <div class="card job-card" data-job-id="${E(j.id)}">
        <div class="job-header">
          <span class="species-icon">${icon}</span>
          <h3 class="job-title">${E(j.title || j.species + ' job')}</h3>
          <span class="status-pill ${sc}">${E(j.status)}</span>
        </div>
        <div class="job-meta">${E(j.customer_name)} · ${formatPhone(j.phone)}</div>
        <div class="job-address">${E(j.address)}${j.town ? `, ${E(j.town)}` : ''}</div>
        <div class="job-pills">
          <span class="pill">${E(j.species)}</span>
          <span class="pill">${E(j.priority || 'Normal')}</span>
          ${j.latitude || j.lat ? '<span class="pill">📍 GPS</span>' : ''}
        </div>
        <div class="progress-bar"><div class="progress-fill" style="width:${s}%"></div></div>
        <div class="job-footer">
          <span class="tiny">Score ${s}% · Est ${money(j.grand_total || j.estimate || 0)}</span>
          <div class="job-actions">
            <button class="btn-sm primary" data-action="open-job" data-id="${E(j.id)}">Open</button>
            <button class="btn-sm" data-action="edit-job" data-id="${E(j.id)}">Edit</button>
          </div>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    this._cleanup = initLazyImages();

    // Search
    const searchEl = $('#job-search');
    if (searchEl) {
      searchEl.addEventListener('input', e => onSearchInput(e.target.value));
    }

    // Filters
    $('#filter-status')?.addEventListener('change', e => {
      store.setState({ filters: { ...state.filters, status: e.target.value } });
    });
    $('#filter-species')?.addEventListener('change', e => {
      store.setState({ filters: { ...state.filters, species: e.target.value } });
    });

    // Buttons
    $$('[data-action="open-job"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate(`/jobs/${btn.dataset.id}`));
    });
    $$('[data-action="edit-job"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate(`/jobs/${btn.dataset.id}/edit`));
    });
    $$('[data-action="new-job"]').forEach(b => {
      b.addEventListener('click', () => router.navigate('/jobs/new'));
    });

    $$('.job-card').forEach(card => {
      card.addEventListener('click', e => {
        if (e.target.closest('button')) return;
        const id = card.dataset.jobId;
        if (id) router.navigate(`/jobs/${id}`);
      });
    });
  },

  unmount() {
    if (this._cleanup) {
      this._cleanup();
      this._cleanup = null;
    }
  }
};

// ── Job Detail ───────────────────────────────────

const JobDetail = {
  _cleanup: null,
  _timerInterval: null,

  render(state) {
    const jobId = state.selectedJobId;
    if (!jobId) return `<div class="page"><div class="card empty">No job selected.</div></div>`;

    const job = state.jobs.find(j => j.id === jobId);
    if (!job) return `<div class="page"><div class="card empty">Job not found.</div></div>`;

    const icon = SPECIES_ICONS[job.species] || '🐾';
    const sc = STATUS_STYLES[job.status] || 'active';
    const s = jobScore(jobId);
    const jobVisits = (state.visits || []).filter(v => (v.jobId || v.job_id) === jobId);
    const jobRepairs = (state.repairs || []).filter(r => (r.jobId || r.job_id) === jobId);
    const jobPhotos = (state.photos || []).filter(p => (p.jobId || p.job_id) === jobId);
    const jobServices = (state.services || []).filter(sv => (sv.jobId || sv.job_id) === jobId);

    return `
      <div class="page job-detail-page">
        <!-- Header Card -->
        <div class="card detail-header">
          <div class="job-header">
            <span class="species-icon large">${icon}</span>
            <div class="detail-title-block">
              <h2>${E(job.title || job.species + ' job')}</h2>
              <span class="status-pill ${sc}">${E(job.status)}</span>
              <span class="priority-pill ${job.priority?.toLowerCase() || 'normal'}">${E(job.priority || 'Normal')}</span>
            </div>
          </div>
          <div class="detail-meta">
            <div><strong>${E(job.customer_name)}</strong> · <a href="${tel(job.phone)}" class="phone-link">${formatPhone(job.phone)}</a></div>
            <div class="tiny">${E(job.address)}${job.town ? `, ${E(job.town)}` : ''}</div>
            ${job.email ? `<div class="tiny">${E(job.email)}</div>` : ''}
          </div>
          <div class="progress-bar"><div class="progress-fill" style="width:${s}%"></div></div>
          <div class="tiny">Score ${s}%</div>
        </div>

        <!-- Financials -->
        <div class="card">
          <h3>💰 Financials</h3>
          <div class="financial-grid">
            <div><label>Estimate</label><div class="fin-val">${money(job.estimate)}</div></div>
            <div><label>Subtotal</label><div class="fin-val">${money(job.subtotal)}</div></div>
            <div><label>Tax</label><div class="fin-val">${money(job.tax_amount)}</div></div>
            <div><label>Grand Total</label><div class="fin-val bold">${money(job.grand_total)}</div></div>
            <div><label>Deposit</label><div class="fin-val">${money(job.deposit_paid)}</div></div>
            <div><label>Balance Due</label><div class="fin-val ${(job.balance_due || 0) > 0 ? 'warn' : ''}">${money(job.balance_due)}</div></div>
          </div>
          ${
            (job.balance_due || 0) > 0
              ? `
            <div class="payment-row">
              <input type="number" id="payment-amount" placeholder="Payment amount" min="0" step="0.01">
              <button class="btn primary" data-action="add-payment" data-id="${E(job.id)}">💳 Add Payment</button>
            </div>
          `
              : ''
          }
        </div>

        <!-- Timer -->
        <div class="card">
          <h3>⏱️ Timer</h3>
          <div class="timer-display" id="timer-display">
            ${job.timer_start ? '⏱️ Running...' : `Total: ${job.timer_total || 0} min`}
          </div>
          <div class="timer-actions">
            ${
              !job.timer_start
                ? `<button class="btn primary" data-action="timer-start" data-id="${E(job.id)}">▶️ Start</button>`
                : `<button class="btn warn" data-action="timer-stop" data-id="${E(job.id)}">⏹️ Stop (+${Math.ceil((Date.now() - new Date(job.timer_start)) / 60000)} min)</button>`
            }
          </div>
        </div>

        <!-- Scope & Notes -->
        <div class="card">
          <h3>📝 Scope & Notes</h3>
          <p>${E(job.scope) || '<em>No scope defined.</em>'}</p>
          ${job.notes ? `<div class="notes-box"><strong>Field Notes:</strong><p>${E(job.notes)}</p></div>` : ''}
          ${job.ai_notes ? `<div class="notes-box ai"><strong>AI Notes:</strong><p>${E(job.ai_notes)}</p></div>` : ''}
          <div class="warranty-line">Warranty: ${E(job.warranty || 'Not set')}</div>
        </div>

        <!-- Assigned -->
        <div class="card">
          <h3>👤 Assignment</h3>
          <div class="detail-row">
            <span>Tech: ${E(job.assigned_tech || 'Unassigned')}</span>
          </div>
        </div>

        <!-- Services -->
        ${
          jobServices.length
            ? `
          <div class="card">
            <h3>🔧 Services (${jobServices.length})</h3>
            ${jobServices
              .map(
                sv => `
              <div class="service-line">
                <span>${E(sv.service || sv.name)}</span>
                <span>${sv.qty || 1} x ${money(sv.unit_price || sv.price)} = ${money(sv.total || (sv.qty || 1) * (sv.unit_price || sv.price || 0))}</span>
              </div>
            `
              )
              .join('')}
          </div>
        `
            : ''
        }

        <!-- Visits -->
        <h3 class="section-title">📝 Visits (${jobVisits.length})</h3>
        ${
          jobVisits.length
            ? jobVisits
                .map(
                  v => `
          <div class="card visit-card">
            <b>${E(v.type)}</b>
            <div class="tiny">${E(v.date || v.created_at)} · Animals: ${v.animals || 0}</div>
            <div>${E(v.note)}</div>
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No visits yet.</div>'
        }

        <!-- Repairs -->
        <h3 class="section-title">🔨 Repairs (${jobRepairs.length})</h3>
        ${
          jobRepairs.length
            ? jobRepairs
                .map(
                  r => `
          <div class="card repair-card">
            <b>${E(r.location)}</b>
            <span class="pill">${E(r.status)}</span>
            <span class="pill">${E(r.severity)}</span>
            <div class="tiny">${E(r.materials || '')}</div>
            <div>${E(r.note)}</div>
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No repairs yet.</div>'
        }

        <!-- Photos -->
        <h3 class="section-title">📸 Photos (${jobPhotos.length})</h3>
        ${
          jobPhotos.length
            ? jobPhotos
                .map(
                  p => `
          <div class="card photo-card">
            <img class="photo lazy" data-src="${E(p.image_url || p.data)}" alt="${E(p.tag || 'Photo')}" loading="lazy">
            <div class="photo-meta">
              <b>${E(p.tag || 'Photo')}</b>
              <div class="tiny">${E(p.notes || '')} · ${formatDate(p.created_at || p.date)}</div>
            </div>
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No photos yet.</div>'
        }

        <!-- Job Completion Checklist -->
        <div class="card">
          <h3>✅ Completion Checklist</h3>
          ${this._renderChecklist(job, state)}
        </div>

        <!-- Voice Notes -->
        <div class="card">
          <h3>🎤 Voice Notes</h3>
          ${this._renderVoiceNotes(job.id, state)}
          <button class="btn" data-action="record-voice" data-job-id="${E(job.id)}" id="record-btn">🎙️ Record Voice Note</button>
          <span id="recording-status" class="tiny" style="display:none;">🔴 Recording...</span>
        </div>

        <!-- Trap Log -->
        <div class="card">
          <h3>🪤 Trap Log</h3>
          ${this._renderTrapLog(job.id, state)}
          <form id="trap-log-form" class="form-row">
            <div class="two-col">
              <div><label>Date</label><input type="date" id="trap-date" value="${new Date().toISOString().slice(0, 10)}"></div>
              <div><label>Location</label><input type="text" id="trap-location" placeholder="e.g. Attic, Garage"></div>
            </div>
            <div class="two-col">
              <div><label>Species Caught</label><select id="trap-species">${O(SPECIES, job.species || 'Raccoon')}</select></div>
              <div><label>Count</label><input type="number" id="trap-count" value="1" min="0"></div>
            </div>
            <div><label>Bait Used</label><input type="text" id="trap-bait" placeholder="e.g. Peanut butter, sardines"></div>
            <div><label>Notes</label><textarea id="trap-notes" rows="2" placeholder="Any observations..."></textarea></div>
            <button type="submit" class="btn primary">➕ Log Trap Check</button>
          </form>
        </div>

        <!-- Communication Log -->
        <div class="card">
          <h3>📞 Communication Log</h3>
          ${this._renderCommunications(job, state)}
          <form id="comm-form" class="form-row">
            <div class="two-col">
              <div><label>Type</label><select id="comm-type">${O(COMMUNICATION_TYPES, 'Call')}</select></div>
              <div><label>Direction</label><select id="comm-direction"><option value="outbound">Outbound</option><option value="inbound">Inbound</option></select></div>
            </div>
            <div><label>Notes</label><textarea id="comm-notes" rows="2" placeholder="What was discussed..."></textarea></div>
            <button type="submit" class="btn primary">➕ Log Communication</button>
          </form>
        </div>

        <!-- Follow-up Reminders -->
        ${job.status === 'Closed' ? this._renderReminders(job.id, state) : ''}

        <!-- Detail Map -->
        ${
          job.latitude || job.lat
            ? `
          <h3 class="section-title">📍 Location</h3>
          <div id="detail-map" class="detail-map"></div>
        `
            : ''
        }

        <!-- Actions -->
        <div class="detail-actions">
          <button class="btn primary" data-action="edit-job" data-id="${E(job.id)}">✏️ Edit Job</button>
          <button class="btn" data-action="generate-pdf" data-id="${E(job.id)}">📄 PDF Report</button>
          <button class="btn" data-action="add-calendar" data-id="${E(job.id)}">📅 Calendar</button>
          <button class="btn" data-action="quick-photo" data-id="${E(job.id)}">📸 Quick Photo</button>
          <button class="btn" data-action="navigate" data-lat="${job.latitude || job.lat || ''}" data-lng="${job.longitude || job.lng || ''}" data-addr="${E(job.address)}">🗺️ Navigate</button>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    this._cleanup = initLazyImages();

    // Timer display
    const jobId = state.selectedJobId;
    const job = state.jobs.find(j => j.id === jobId);
    if (job?.timer_start) {
      this._timerInterval = setInterval(() => {
        const display = $('#timer-display');
        if (display) {
          const mins = Math.ceil((Date.now() - new Date(job.timer_start)) / 60000);
          display.textContent = `⏱️ Running... (+${mins} min)`;
        }
      }, 30000);
    }

    // Detail map
    if (job?.latitude || job?.lat) {
      setTimeout(() => this._initDetailMap(job), 100);
    }

    // Event listeners
    $$('[data-action="edit-job"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate(`/jobs/${btn.dataset.id}/edit`));
    });
    $$('[data-action="add-payment"]').forEach(btn => {
      btn.addEventListener('click', () => handleAddPayment(btn.dataset.id));
    });
    $$('[data-action="timer-start"]').forEach(btn => {
      btn.addEventListener('click', () => handleTimerStart(btn.dataset.id));
    });
    $$('[data-action="timer-stop"]').forEach(btn => {
      btn.addEventListener('click', () => handleTimerStop(btn.dataset.id));
    });
    $$('[data-action="generate-pdf"]').forEach(btn => {
      btn.addEventListener('click', () => handleGeneratePDF(btn.dataset.id));
    });
    $$('[data-action="navigate"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const lat = parseFloat(btn.dataset.lat) || null;
        const lng = parseFloat(btn.dataset.lng) || null;
        navigateToJob(lat, lng, btn.dataset.addr || '');
      });
    });
    $$('[data-action="quick-photo"]').forEach(btn => {
      btn.addEventListener('click', () => handleQuickPhoto(btn.dataset.id));
    });
    $$('[data-action="add-calendar"]').forEach(btn => {
      btn.addEventListener('click', () => handleAddToCalendar(btn.dataset.id));
    });

    // Checklist toggles
    $$('[data-checklist-id]').forEach(chk => {
      chk.addEventListener('change', () => handleToggleChecklist(jobId, chk.dataset.checklistId, chk.checked));
    });

    // Voice recording
    $$('[data-action="record-voice"]').forEach(btn => {
      btn.addEventListener('click', () => handleToggleVoiceRecording(btn.dataset.jobId));
    });

    // Delete voice notes
    $$('[data-action="delete-voice-note"]').forEach(btn => {
      btn.addEventListener('click', () => handleDeleteVoiceNote(btn.dataset.id));
    });

    // Trap log form
    const trapForm = $('#trap-log-form');
    if (trapForm) {
      const onTrapSubmit = e => {
        e.preventDefault();
        handleAddTrapLog(jobId);
      };
      trapForm.addEventListener('submit', onTrapSubmit);
    }

    // Communication form
    const commForm = $('#comm-form');
    if (commForm) {
      const onCommSubmit = e => {
        e.preventDefault();
        handleAddCommunication(jobId);
      };
      commForm.addEventListener('submit', onCommSubmit);
    }

    // Reminder form
    const reminderForm = $('#reminder-form');
    if (reminderForm) {
      const onReminderSubmit = e => {
        e.preventDefault();
        handleAddReminder(jobId);
      };
      reminderForm.addEventListener('submit', onReminderSubmit);
    }
  },

  _initDetailMap(job) {
    if (!window.google?.maps) return;
    const container = $('#detail-map');
    if (!container) return;
    const lat = parseFloat(job.latitude || job.lat);
    const lng = parseFloat(job.longitude || job.lng);
    if (Number.isNaN(lat) || Number.isNaN(lng)) return;

    const map = new google.maps.Map(container, {
      zoom: config.DEFAULT_MAP_ZOOM,
      center: { lat, lng }
    });
    new google.maps.Marker({ position: { lat, lng }, map, title: job.customer_name });
  },

  _renderChecklist(job, state) {
    const checklist = job.checklist || initChecklist();
    const completed = checklist.filter(c => c.done).length;
    const pct = checklist.length ? Math.round((completed / checklist.length) * 100) : 0;
    return `
      <div class="checklist-progress">
        <div class="progress-bar"><div class="progress-fill" style="width:${pct}%"></div></div>
        <div class="tiny">${completed}/${checklist.length} completed (${pct}%)</div>
      </div>
      <div class="checklist-items">
        ${checklist
          .map(
            item => `
          <label class="checklist-row">
            <input type="checkbox" data-checklist-id="${E(item.id)}" ${item.done ? 'checked' : ''}>
            <span class="${item.done ? 'done' : ''}">${E(item.label)}</span>
          </label>
        `
          )
          .join('')}
      </div>
    `;
  },

  _renderVoiceNotes(jobId, state) {
    const notes = (state.voiceNotes || [])
      .filter(v => v.jobId === jobId)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    if (!notes.length) return '<p class="empty">No voice notes yet.</p>';
    return `
      <div class="voice-notes-list">
        ${notes
          .map(
            v => `
          <div class="voice-note-item">
            <audio controls src="${E(v.audioData)}" style="flex:1;min-width:0;"></audio>
            <span class="tiny">${Math.round(v.duration || 0)}s · ${formatDate(v.createdAt)}</span>
            <button class="btn-sm danger" data-action="delete-voice-note" data-id="${E(v.id)}">🗑️</button>
          </div>
        `
          )
          .join('')}
      </div>
    `;
  },

  _renderTrapLog(jobId, state) {
    const logs = (state.trapLogs || [])
      .filter(t => t.jobId === jobId)
      .sort((a, b) => new Date(b.date) - new Date(a.date));
    if (!logs.length) return '<p class="empty">No trap checks logged yet.</p>';

    // Running totals per species
    const speciesTotals = {};
    logs.forEach(l => {
      speciesTotals[l.species] = (speciesTotals[l.species] || 0) + (l.count || 0);
    });

    return `
      <div class="trap-totals">
        ${Object.entries(speciesTotals)
          .map(
            ([sp, count]) => `
          <span class="pill">${SPECIES_ICONS[sp] || '🐾'} ${E(sp)}: ${count}</span>
        `
          )
          .join('')}
      </div>
      ${logs
        .map(
          t => `
        <div class="trap-log-entry">
          <div class="trap-log-header">
            <b>${formatDate(t.date)}</b>
            <span class="pill">${SPECIES_ICONS[t.species] || '🐾'} ${t.count || 0} ${E(t.species)}</span>
          </div>
          <div class="tiny">${E(t.location)}${t.bait ? ' · Bait: ' + E(t.bait) : ''}</div>
          ${t.notes ? `<div class="tiny">${E(t.notes)}</div>` : ''}
        </div>
      `
        )
        .join('')}
    `;
  },

  _renderCommunications(job, state) {
    const customer = job.customer_name;
    const logs = (state.communications || [])
      .filter(c => c.customerId === customer)
      .sort((a, b) => new Date(b.date) - new Date(a.date));
    if (!logs.length) return '<p class="empty">No communications logged yet.</p>';
    return `
      <div class="comm-log">
        ${logs
          .map(
            c => `
          <div class="comm-entry">
            <div class="comm-header">
              <span class="pill ${c.direction === 'inbound' ? 'info' : ''}">${c.direction === 'inbound' ? '📥' : '📤'} ${E(c.type)}</span>
              <span class="tiny">${formatDate(c.date)}</span>
            </div>
            ${c.notes ? `<div class="tiny">${E(c.notes)}</div>` : ''}
          </div>
        `
          )
          .join('')}
      </div>
    `;
  },

  _renderReminders(jobId, state) {
    const reminders = (state.reminders || []).filter(r => r.jobId === jobId);
    return `
      <div class="card">
        <h3>🔔 Follow-up Reminders</h3>
        ${
          reminders.length
            ? reminders
                .map(
                  r => `
          <div class="reminder-item ${r.status === 'overdue' ? 'warn' : r.status === 'completed' ? 'done' : ''}">
            <span>${formatDate(r.dueDate)} — ${E(r.notes || 'Follow-up')}</span>
            <span class="pill">${E(r.status)}</span>
          </div>
        `
                )
                .join('')
            : '<p class="empty">No reminders set.</p>'
        }
        <form id="reminder-form" class="form-row">
          <div class="two-col">
            <div><label>Follow-up Date</label><input type="date" id="reminder-date" value="${new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)}"></div>
            <div><label>Type</label><select id="reminder-type"><option value="warranty">Warranty Check</option><option value="followup">General Follow-up</option><option value="seasonal">Seasonal</option></select></div>
          </div>
          <div><label>Notes</label><input type="text" id="reminder-notes" placeholder="e.g. Check if raccoon returned..."></div>
          <button type="submit" class="btn primary">➕ Add Reminder</button>
        </form>
      </div>
    `;
  },

  unmount() {
    if (this._cleanup) {
      this._cleanup();
      this._cleanup = null;
    }
    if (this._timerInterval) {
      clearInterval(this._timerInterval);
      this._timerInterval = null;
    }
  }
};

// ── Job Form ─────────────────────────────────────

const JobForm = {
  _listeners: [],

  render(state) {
    const jobId = state.selectedJobId;
    const isEdit = Boolean(jobId);
    const job = isEdit ? state.jobs.find(j => j.id === jobId) : null;

    return `
      <div class="page job-form-page">
        <h2>${isEdit ? '✏️ Edit Job' : '➕ New Job'}</h2>
        <form id="job-form" class="card form-card">
          <div class="form-row">
            <label>Customer Name *</label>
            <input type="text" id="form-customer" value="${E(job?.customer_name || '')}" required placeholder="Full name">
          </div>
          <div class="form-row">
            <label>Phone</label>
            <input type="tel" id="form-phone" value="${E(job?.phone || '')}" placeholder="(555) 555-5555">
          </div>
          <div class="form-row">
            <label>Email</label>
            <input type="email" id="form-email" value="${E(job?.email || '')}" placeholder="customer@email.com">
          </div>
          <div class="form-row">
            <label>Address *</label>
            <input type="text" id="form-address" value="${E(job?.address || '')}" required placeholder="123 Main St">
          </div>
          <div class="form-row two-col">
            <div>
              <label>Town</label>
              <input type="text" id="form-town" value="${E(job?.town || '')}" placeholder="City/Town">
            </div>
            <div>
              <label>State</label>
              <input type="text" id="form-state" value="${E(job?.state || 'NY')}" placeholder="NY">
            </div>
          </div>
          <div class="form-row two-col">
            <div>
              <label>ZIP</label>
              <input type="text" id="form-zip" value="${E(job?.zip || '')}" placeholder="12345">
            </div>
            <div>
              <label>Species</label>
              <select id="form-species">${O(SPECIES, job?.species || 'Raccoon')}</select>
            </div>
          </div>
          <div class="form-row">
            <label>Title</label>
            <input type="text" id="form-title" value="${E(job?.title || '')}" placeholder="e.g. Attic raccoon removal">
          </div>
          <div class="form-row">
            <label>Status</label>
            <select id="form-status">
              ${Object.keys(STATUS_STYLES)
                .map(
                  s => `<option value="${E(s)}" ${(job?.status || 'Active') === s ? 'selected' : ''}>${E(s)}</option>`
                )
                .join('')}
            </select>
          </div>
          <div class="form-row">
            <label>Priority</label>
            <select id="form-priority">${O(PRIORITIES, job?.priority || 'Normal')}</select>
          </div>
          <div class="form-row">
            <label>Assigned Tech</label>
            <input type="text" id="form-tech" value="${E(job?.assigned_tech || '')}" placeholder="Technician name">
          </div>
          <div class="form-row two-col">
            <div>
              <label>📅 Schedule Date</label>
              <input type="date" id="form-scheduled-start" value="${job?.scheduled_start ? new Date(job.scheduled_start).toISOString().slice(0, 10) : ''}">
            </div>
            <div>
              <label>🕐 Schedule Time</label>
              <input type="time" id="form-scheduled-time" value="${job?.scheduled_start ? new Date(job.scheduled_start).toTimeString().slice(0, 5) : ''}">
            </div>
          </div>
          <div class="form-row">
            <label>Scope</label>
            <textarea id="form-scope" rows="3" placeholder="Describe the work scope...">${E(job?.scope || '')}</textarea>
          </div>
          <div class="form-row">
            <label>Notes</label>
            <textarea id="form-notes" rows="2" placeholder="Private notes...">${E(job?.notes || '')}</textarea>
          </div>
          <div class="form-row">
            <label>Warranty</label>
            <input type="text" id="form-warranty" value="${E(job?.warranty || 'Not set')}">
          </div>
          <div class="form-row">
            <label>Estimate ($)</label>
            <input type="number" id="form-estimate" value="${job?.estimate || ''}" min="0" step="0.01" placeholder="Auto-calculated if blank">
          </div>
          <div class="form-row">
            <label>Tax Rate</label>
            <input type="number" id="form-tax-rate" value="${(job?.tax_rate ?? config.DEFAULT_TAX_RATE) * 100}" min="0" max="20" step="0.001">%
          </div>

          <!-- GPS Capture -->
          <div class="form-row gps-row">
            <button type="button" class="btn" data-action="capture-gps">📍 Capture GPS</button>
            <span id="gps-status">${state.pendingGPS ? `Captured: ${state.pendingGPS.lat}, ${state.pendingGPS.lng}` : 'No GPS captured'}</span>
          </div>

          <div class="form-actions">
            <button type="submit" class="btn primary">${isEdit ? '💾 Update Job' : '➕ Create Job'}</button>
            <button type="button" class="btn" data-action="cancel-form">Cancel</button>
          </div>
        </form>
      </div>
    `;
  },

  afterRender(state) {
    const form = $('#job-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleSaveJob(state.selectedJobId);
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }

    $$('[data-action="cancel-form"]').forEach(btn => {
      btn.addEventListener('click', () => {
        if (state.previousPage) router.back();
        else router.navigate('/jobs');
      });
    });

    $$('[data-action="capture-gps"]').forEach(btn => {
      btn.addEventListener('click', handleCaptureGPS);
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ── Customer List ────────────────────────────────

const CustomerList = {
  render(state) {
    const q = (state.searchQuery || '').toLowerCase();
    let customers = [...state.customers];
    if (q) {
      customers = customers.filter(c => `${c.name} ${c.address} ${c.phone} ${c.town}`.toLowerCase().includes(q));
    }

    return `
      <div class="page customer-list-page">
        <div class="list-toolbar">
          <input type="text" id="customer-search" class="search-input" placeholder="Search customers..." value="${E(state.searchQuery)}">
          <button class="btn primary" data-action="new-customer">➕ Add Customer</button>
        </div>
        ${
          customers.length
            ? customers
                .map(
                  c => `
          <div class="card customer-card" data-customer-id="${E(c.id)}">
            <h4>${E(c.name)}</h4>
            <div class="tiny">${formatPhone(c.phone)} · ${E(c.email || '')}</div>
            <div class="tiny">${E(c.address)}${c.town ? `, ${E(c.town)}` : ''}</div>
            ${c.notes ? `<div class="tiny">${E(c.notes)}</div>` : ''}
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No customers yet.</div>'
        }
      </div>
    `;
  },

  afterRender(state) {
    $('#customer-search')?.addEventListener('input', e => onSearchInput(e.target.value));

    $$('[data-action="new-customer"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate('/customers/new'));
    });

    $$('.customer-card').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.dataset.customerId;
        if (id) router.navigate(`/customers/${id}`);
      });
    });
  },

  unmount() {}
};

// ── Customer Form ────────────────────────────────

const CustomerForm = {
  _listeners: [],

  render(state) {
    const customerId = state.selectedCustomerId;
    const isEdit = Boolean(customerId);
    const customer = isEdit ? state.customers.find(c => c.id === customerId) : null;

    return `
      <div class="page customer-form-page">
        <h2>${isEdit ? '✏️ Edit Customer' : '➕ New Customer'}</h2>
        <form id="customer-form" class="card form-card">
          <div class="form-row">
            <label>Name *</label>
            <input type="text" id="cform-name" value="${E(customer?.name || '')}" required>
          </div>
          <div class="form-row">
            <label>Phone</label>
            <input type="tel" id="cform-phone" value="${E(customer?.phone || '')}">
          </div>
          <div class="form-row">
            <label>Email</label>
            <input type="email" id="cform-email" value="${E(customer?.email || '')}">
          </div>
          <div class="form-row">
            <label>Address</label>
            <input type="text" id="cform-address" value="${E(customer?.address || '')}">
          </div>
          <div class="form-row two-col">
            <div>
              <label>Town</label>
              <input type="text" id="cform-town" value="${E(customer?.town || '')}">
            </div>
            <div>
              <label>State</label>
              <input type="text" id="cform-state" value="${E(customer?.state || 'NY')}">
            </div>
          </div>
          <div class="form-row">
            <label>ZIP</label>
            <input type="text" id="cform-zip" value="${E(customer?.zip || '')}">
          </div>
          <div class="form-row">
            <label>Notes</label>
            <textarea id="cform-notes" rows="3">${E(customer?.notes || '')}</textarea>
          </div>
          <div class="form-actions">
            <button type="submit" class="btn primary">${isEdit ? '💾 Update' : '➕ Create'}</button>
            <button type="button" class="btn" data-action="cancel-form">Cancel</button>
          </div>
        </form>
      </div>
    `;
  },

  afterRender(state) {
    const form = $('#customer-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleSaveCustomer(state.selectedCustomerId);
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }
    $$('[data-action="cancel-form"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate('/customers'));
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ═══════════════════════════════════════════════════
// Inspections List
// ═══════════════════════════════════════════════════

const InspectionList = {
  _listeners: [],

  render(state) {
    const inspections = state.inspections || [];
    const search = (state.searchQuery || '').toLowerCase();
    const filtered = search
      ? inspections.filter(
          i =>
            (i.customer_name || '').toLowerCase().includes(search) ||
            (i.address || '').toLowerCase().includes(search) ||
            (i.phone || '').toLowerCase().includes(search) ||
            (i.species || '').toLowerCase().includes(search)
        )
      : inspections;

    const byStatus = {};
    INSPECTION_STATUSES.forEach(s => (byStatus[s] = []));
    filtered.forEach(i => {
      const s = i.status || 'Pending';
      if (!byStatus[s]) byStatus[s] = [];
      byStatus[s].push(i);
    });

    return `
      <div class="page">
        <h1 class="section-title">🔍 Inspections</h1>
        <div class="list-toolbar">
          <input type="text" class="search-input" id="inspect-search" placeholder="Search inspections..." value="${E(search)}">
          <button class="btn primary" data-action="new-inspection">➕ New Inspection</button>
        </div>
        ${INSPECTION_STATUSES.map(status => {
          const items = byStatus[status] || [];
          if (!items.length) return '';
          return `
            <h3 class="section-subtitle">${E(status)} (${items.length})</h3>
            <div class="card-flat">
              ${items
                .map(
                  i => `
                <div class="job-row" data-id="${i.id}" data-type="inspection">
                  <span class="job-row-date">${i.scheduled_start ? formatDateShort(i.scheduled_start) : 'No date'}</span>
                  <span class="job-row-title">${SPECIES_ICONS[i.species] || '🔍'} ${E(i.customer_name || 'Unknown')}</span>
                  <span class="status-badge ${(INSPECTION_STATUS_STYLES[i.status] || 'pending').replace(/\s+/g, '-')}">${E(i.status || 'Pending')}</span>
                </div>
              `
                )
                .join('')}
            </div>
          `;
        }).join('')}
        ${!filtered.length ? '<p class="empty">No inspections yet. Tap "New Inspection" to schedule one.</p>' : ''}
      </div>`;
  },

  afterRender(state) {
    const searchEl = $('#inspect-search');
    if (searchEl) {
      const onInput = debounce(() => store.setState({ searchQuery: searchEl.value }), 250);
      searchEl.addEventListener('input', onInput);
      this._listeners.push(() => searchEl.removeEventListener('input', onInput));
    }
    $$('[data-action="new-inspection"]').forEach(btn => {
      const fn = () => navigateTo('inspection-form');
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
    $$('.job-row[data-type="inspection"]').forEach(row => {
      const fn = () => navigateTo('inspection-form', { selectedInspectionId: row.dataset.id });
      row.addEventListener('click', fn);
      this._listeners.push(() => row.removeEventListener('click', fn));
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ═══════════════════════════════════════════════════
// Inspection Form
// ═══════════════════════════════════════════════════

const InspectionForm = {
  _listeners: [],

  render(state) {
    const inspectionId = state.selectedInspectionId;
    const isEdit = Boolean(inspectionId);
    const inspections = state.inspections || [];
    const inspection = isEdit ? inspections.find(i => i.id === inspectionId) : null;

    return `
      <div class="page">
        <h1 class="section-title">${isEdit ? '✏️ Edit Inspection' : '🔍 New Inspection'}</h1>
        <form id="inspection-form" class="card form-card">
          <div class="form-row">
            <label>Customer Name *</label>
            <input type="text" id="iform-customer" value="${E(inspection?.customer_name || '')}" required placeholder="Customer name">
          </div>
          <div class="form-row">
            <label>Phone</label>
            <input type="tel" id="iform-phone" value="${E(inspection?.phone || '')}" placeholder="(555) 555-5555">
          </div>
          <div class="form-row">
            <label>Address</label>
            <input type="text" id="iform-address" value="${E(inspection?.address || '')}" placeholder="123 Main St">
          </div>
          <div class="form-row two-col">
            <div>
              <label>Town</label>
              <input type="text" id="iform-town" value="${E(inspection?.town || '')}">
            </div>
            <div>
              <label>State</label>
              <input type="text" id="iform-state" value="${E(inspection?.state || 'NY')}">
            </div>
          </div>
          <div class="form-row">
            <label>Species</label>
            <select id="iform-species">${O(SPECIES, inspection?.species || '')}</select>
          </div>
          <div class="form-row two-col">
            <div>
              <label>📅 Inspection Date</label>
              <input type="date" id="iform-date" value="${inspection?.scheduled_start ? new Date(inspection.scheduled_start).toISOString().slice(0, 10) : ''}">
            </div>
            <div>
              <label>🕐 Time</label>
              <input type="time" id="iform-time" value="${inspection?.scheduled_start ? new Date(inspection.scheduled_start).toTimeString().slice(0, 5) : '09:00'}">
            </div>
          </div>
          <div class="form-row">
            <label>Status</label>
            <select id="iform-status">${O(INSPECTION_STATUSES, inspection?.status || 'Pending')}</select>
          </div>
          <div class="form-row">
            <label>Notes</label>
            <textarea id="iform-notes" rows="3" placeholder="What the customer reported, access instructions, etc.">${E(inspection?.notes || '')}</textarea>
          </div>
          <div class="form-actions">
            <button type="submit" class="btn primary">${isEdit ? '💾 Update' : '➕ Schedule Inspection'}</button>
            ${isEdit ? '<button type="button" class="btn" data-action="convert-inspection">🦝 Convert to Job</button>' : ''}
            <button type="button" class="btn" data-action="cancel-inspection">Cancel</button>
          </div>
        </form>
      </div>`;
  },

  afterRender(state) {
    const form = $('#inspection-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleSaveInspection(state.selectedInspectionId);
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }
    $$('[data-action="cancel-inspection"]').forEach(btn => {
      const fn = () => navigateTo('inspections');
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
    $$('[data-action="convert-inspection"]').forEach(btn => {
      const fn = () => {
        const inspection = (state.inspections || []).find(i => i.id === state.selectedInspectionId);
        if (inspection) handleConvertInspection(inspection);
      };
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ── Estimate Calculator ──────────────────────────

const EstimateCalc = {
  render(state) {
    return `
      <div class="page estimate-page">
        <h2>💵 Estimate Calculator</h2>
        <div class="card">
          <div class="form-row">
            <label>Template</label>
            <select id="est-template">
              <option value="">— Select template —</option>
              ${Object.entries(ESTIMATE_TEMPLATES)
                .map(([k, t]) => `<option value="${E(k)}">${E(t.species)} — ${E(t.service)}</option>`)
                .join('')}
            </select>
          </div>
          <div class="form-row">
            <label>Species</label>
            <select id="est-species">${O(SPECIES, 'Raccoon')}</select>
          </div>
          <div class="form-row">
            <label>Issue Description</label>
            <textarea id="est-issue" rows="2" placeholder="Describe the issue..."></textarea>
          </div>
          <div class="form-row">
            <label>Service</label>
            <select id="est-service">
              <option value="">— Select service —</option>
              ${SERVICES.map(s => `<option value="${E(s.name)}" data-price="${s.price}">${E(s.name)} — ${money(s.price)}</option>`).join('')}
            </select>
          </div>
          <div class="form-row two-col">
            <div>
              <label>Unit Price ($)</label>
              <input type="number" id="est-price" min="0" step="0.01">
            </div>
            <div>
              <label>Quantity</label>
              <input type="number" id="est-qty" value="1" min="1">
            </div>
          </div>
          <div class="form-row">
            <label>Severity</label>
            <select id="est-severity">${O(SEVERITIES, 'Medium')}</select>
          </div>
          <div class="form-row">
            <label>Tax Rate</label>
            <input type="number" id="est-tax" value="${config.DEFAULT_TAX_RATE * 100}" min="0" max="20" step="0.001">%
          </div>
          <!-- Estimate Total Breakdown -->
          <div class="estimate-total-section">
            <div class="estimate-line">
              <span>Subtotal</span>
              <span id="est-subtotal">$0.00</span>
            </div>
            <div class="estimate-line">
              <span>Tax <small id="est-tax-label">(8.875%)</small></span>
              <span id="est-tax-amount">$0.00</span>
            </div>
            <div class="estimate-line total">
              <span>Grand Total</span>
              <span id="est-grand-total">$0.00</span>
            </div>
          </div>
          <div style="margin-top:var(--space-md);display:flex;gap:var(--space-sm);flex-wrap:wrap;">
            <button class="btn primary" data-action="calc-estimate">🧮 Calculate Full Breakdown</button>
            <button class="btn" data-action="email-estimate">📧 Email</button>
            <button class="btn" data-action="convert-job">➕ Convert to Job</button>
          </div>
          <pre id="estimate-output" class="estimate-output"></pre>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    // Template selector
    $('#est-template')?.addEventListener('change', e => {
      const t = ESTIMATE_TEMPLATES[e.target.value];
      if (!t) return;
      $('#est-species').value = t.species;
      $('#est-issue').value = t.issue;
      // Match service by name
      const svcSelect = $('#est-service');
      for (let i = 0; i < svcSelect.options.length; i++) {
        if (svcSelect.options[i].value === t.service) {
          svcSelect.selectedIndex = i;
          break;
        }
      }
      $('#est-price').value = t.price;
      $('#est-qty').value = t.qty;
    });

    // Auto-fill price when service changes
    $('#est-service')?.addEventListener('change', e => {
      const opt = e.target.selectedOptions[0];
      if (opt?.dataset?.price) $('#est-price').value = opt.dataset.price;
      updateEstimateTotal();
    });

    // Live total on every input change
    const estInputs = ['est-species', 'est-severity', 'est-price', 'est-qty', 'est-tax', 'est-service'];
    estInputs.forEach(id => {
      $('#' + id)?.addEventListener('input', updateEstimateTotal);
      $('#' + id)?.addEventListener('change', updateEstimateTotal);
    });
    updateEstimateTotal(); // Initial calc

    $$('[data-action="calc-estimate"]').forEach(btn => {
      btn.addEventListener('click', handleCalcEstimate);
    });
    $$('[data-action="email-estimate"]').forEach(btn => {
      btn.addEventListener('click', handleEmailEstimate);
    });
    $$('[data-action="convert-job"]').forEach(btn => {
      btn.addEventListener('click', handleConvertToJob);
    });
  },

  unmount() {}
};

// ── Photo Gallery ────────────────────────────────

const PhotoGallery = {
  render(state) {
    const q = (state.searchQuery || '').toLowerCase();
    let photos = [...state.photos];
    if (q) {
      photos = photos.filter(p => (p.tag || '').toLowerCase().includes(q) || (p.notes || '').toLowerCase().includes(q));
    }

    return `
      <div class="page photos-page">
        <div class="list-toolbar">
          <input type="text" id="photo-search" class="search-input" placeholder="Search photos..." value="${E(state.searchQuery)}">
        </div>
        ${
          photos.length
            ? `
          <div class="photo-gallery">
            ${photos
              .map(
                p => `
              <div class="photo-item">
                <img class="photo lazy" data-src="${E(p.image_url || p.data)}" alt="${E(p.tag || 'Photo')}" loading="lazy">
                <div class="photo-overlay">
                  <span class="photo-tag">${E(p.tag || '')}</span>
                </div>
              </div>
            `
              )
              .join('')}
          </div>
        `
            : '<div class="card empty">No photos yet.</div>'
        }
      </div>
    `;
  },

  afterRender() {
    this._cleanup = initLazyImages();
    $('#photo-search')?.addEventListener('input', e => onSearchInput(e.target.value));
  },

  unmount() {
    if (this._cleanup) {
      this._cleanup();
      this._cleanup = null;
    }
  }
};

// ── GPS Map ──────────────────────────────────────

const GPSMap = {
  _map: null,
  _markers: [],

  render(state) {
    return `
      <div class="page gps-page">
        <div class="gps-toolbar">
          <button class="btn primary" data-action="capture-gps">📍 My Location</button>
          <button class="btn" data-action="refresh-map">🔄 Refresh</button>
        </div>
        <div id="map-container" class="map-container">
          <div class="card empty">Loading map...</div>
        </div>
        <div id="gps-status" class="gps-status"></div>
      </div>
    `;
  },

  afterRender(state) {
    // Initialize map
    setTimeout(() => this._initMap(state), 200);

    $$('[data-action="capture-gps"]').forEach(btn => {
      btn.addEventListener('click', handleCaptureGPS);
    });
    $$('[data-action="refresh-map"]').forEach(btn => {
      btn.addEventListener('click', () => this._refreshMap(state));
    });
  },

  _initMap(state) {
    if (!window.google?.maps) {
      const container = $('#map-container');
      if (container) container.innerHTML = '<div class="card empty">Google Maps not configured.</div>';
      return;
    }
    const container = $('#map-container');
    if (!container) return;

    this._map = new google.maps.Map(container, {
      zoom: config.DEFAULT_MAP_ZOOM,
      center: config.DEFAULT_MAP_CENTER
    });

    this._refreshMap(state);
  },

  _refreshMap(state) {
    if (!this._map) return;
    const jobs = (state.jobs || []).filter(j => (j.latitude || j.lat) && (j.longitude || j.lng));
    if (!jobs.length) {
      const container = $('#map-container');
      if (container && !this._map) container.innerHTML = '<div class="card empty">No GPS jobs yet.</div>';
      return;
    }

    // Clear markers
    this._markers.forEach(m => m.setMap(null));
    this._markers = [];

    const bounds = new google.maps.LatLngBounds();
    jobs.forEach(j => {
      const pos = {
        lat: parseFloat(j.latitude || j.lat),
        lng: parseFloat(j.longitude || j.lng)
      };
      const marker = new google.maps.Marker({
        position: pos,
        map: this._map,
        title: `${E(j.species)} — ${E(j.customer_name)}`,
        animation: google.maps.Animation.DROP
      });
      marker.addListener('click', () => router.navigate(`/jobs/${j.id}`));
      this._markers.push(marker);
      bounds.extend(pos);
    });

    if (this._markers.length > 1) this._map.fitBounds(bounds);
    else if (this._markers.length === 1) {
      this._map.setCenter(this._markers[0].getPosition());
      this._map.setZoom(15);
    }
  },

  unmount() {
    this._markers.forEach(m => m.setMap(null));
    this._markers = [];
    this._map = null;
  }
};

// ── Metrics ──────────────────────────────────────

const MetricsPage = {
  render(state) {
    const allJobs = state.jobs || [];
    const openJobs = allJobs.filter(j => j.status !== 'Closed' && j.status !== 'Cancelled');
    const closedJobs = allJobs.filter(j => j.status === 'Closed');
    const totalRevenue = closedJobs.reduce((sum, j) => sum + (j.grand_total || j.estimate || 0), 0);
    const avgJobValue = closedJobs.length ? totalRevenue / closedJobs.length : 0;

    const bySpecies = groupBy(allJobs, 'species');
    const speciesRanking = Object.entries(bySpecies).sort((a, b) => b[1].length - a[1].length);

    const byTown = groupBy(state.jobs, 'town');
    const townRanking = Object.entries(byTown)
      .sort((a, b) => b[1].length - a[1].length)
      .slice(0, 10);

    const byStatus = groupBy(state.jobs, 'status');

    return `
      <div class="page metrics-page">
        <h2>📊 Business Metrics</h2>
        <div class="metrics-grid four-col">
          <div class="metric-card"><div class="metric-value">${state.jobs.length}</div><div class="metric-label">Total Jobs</div></div>
          <div class="metric-card"><div class="metric-value">${openJobs.length}</div><div class="metric-label">Open</div></div>
          <div class="metric-card"><div class="metric-value">${money(totalRevenue)}</div><div class="metric-label">Revenue</div></div>
          <div class="metric-card"><div class="metric-value">${money(avgJobValue)}</div><div class="metric-label">Avg Job</div></div>
        </div>

        <h3 class="section-title">By Species</h3>
        ${
          speciesRanking.length
            ? speciesRanking
                .map(
                  ([sp, jobs]) => `
          <div class="card metric-row">
            <span>${SPECIES_ICONS[sp] || '🐾'} ${E(sp)}</span>
            <span class="metric-bar"><span class="metric-fill" style="width:${Math.min(100, (jobs.length / state.jobs.length) * 100)}%"></span></span>
            <span class="badge">${jobs.length}</span>
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No data.</div>'
        }

        <h3 class="section-title">By Town</h3>
        ${
          townRanking.length
            ? townRanking
                .map(
                  ([town, jobs]) => `
          <div class="card metric-row">
            <span>${E(town || 'Unsorted')}</span>
            <span class="metric-bar"><span class="metric-fill" style="width:${Math.min(100, (jobs.length / state.jobs.length) * 100)}%"></span></span>
            <span class="badge">${jobs.length}</span>
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No data.</div>'
        }

        <h3 class="section-title">By Status</h3>
        ${Object.entries(byStatus)
          .map(
            ([st, jobs]) => `
          <div class="card metric-row">
            <span class="status-pill ${STATUS_STYLES[st] || 'active'}">${E(st)}</span>
            <span class="badge">${jobs.length}</span>
          </div>
        `
          )
          .join('')}
      </div>
    `;
  },

  afterRender() {},
  unmount() {}
};

// ── Settings ─────────────────────────────────────

const SettingsPage = {
  render(state) {
    const syncUrl = localStorage.getItem(`${STORAGE_KEY}_syncUrl`) || '';
    const lastSave = localStorage.getItem(`${STORAGE_KEY}_last`);
    const theme = state.theme;

    return `
      <div class="page settings-page">
        <h2>⚙️ Settings</h2>

        <div class="card">
          <h3>🎨 Appearance</h3>
          <div class="form-row">
            <label>Theme</label>
            <select id="theme-select">
              <option value="dark" ${theme === 'dark' ? 'selected' : ''}>Dark</option>
              <option value="light" ${theme === 'light' ? 'selected' : ''}>Light</option>
            </select>
          </div>
        </div>

        <div class="card">
          <h3>☁️ Sync</h3>
          <div class="form-row">
            <label>Sync Endpoint URL</label>
            <input type="url" id="sync-url" value="${E(syncUrl)}" placeholder="https://your-endpoint.com/sync">
          </div>
          <div class="form-actions">
            <button class="btn primary" data-action="save-sync">💾 Save Endpoint</button>
            <button class="btn" data-action="sync-now">↻ Sync Now</button>
          </div>
          <div id="sync-log" class="sync-log"></div>
        </div>

        <div class="card">
          <h3>💾 Data</h3>
          <div class="form-actions">
            <button class="btn" data-action="export-data">⬇️ Export JSON</button>
            <button class="btn warn" data-action="recover-data">♻️ Recover Snapshot</button>
            <button class="btn danger" data-action="wipe-data">🗑️ Wipe All Data</button>
          </div>
          <div class="form-row">
            <label>Import JSON</label>
            <textarea id="import-box" rows="4" placeholder="Paste exported JSON here..."></textarea>
          </div>
          <button class="btn primary" data-action="import-data">⬆️ Import</button>
        </div>

        <div class="card">
          <h3>ℹ️ About</h3>
          <div class="about-text">
            <p><strong>Wildlife Whisperer FieldOps</strong> v${config.APP_VERSION}</p>
            <p>${getBuildInfo()}</p>
            <p>Last saved: ${lastSave ? new Date(lastSave).toLocaleString() : 'never'}</p>
          </div>
        </div>
      </div>
    `;
  },

  afterRender() {
    $('#theme-select')?.addEventListener('change', e => {
      store.setState({ theme: e.target.value });
      localStorage.setItem('ww_theme', e.target.value);
      document.body.setAttribute('data-theme', e.target.value);
    });

    $$('[data-action="save-sync"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const url = $('#sync-url')?.value?.trim();
        if (url) localStorage.setItem(`${STORAGE_KEY}_syncUrl`, url);
        showToast('Endpoint saved');
      });
    });
    $$('[data-action="sync-now"]').forEach(btn => {
      btn.addEventListener('click', handleSyncNow);
    });
    $$('[data-action="export-data"]').forEach(btn => {
      btn.addEventListener('click', handleExportData);
    });
    $$('[data-action="import-data"]').forEach(btn => {
      btn.addEventListener('click', handleImportData);
    });
    $$('[data-action="recover-data"]').forEach(btn => {
      btn.addEventListener('click', handleRecoverData);
    });
    $$('[data-action="wipe-data"]').forEach(btn => {
      btn.addEventListener('click', handleWipeData);
    });
  },

  unmount() {}
};

// ── AI Assistant ─────────────────────────────────

const AIModal = {
  render(state) {
    return `
      <div class="page ai-page">
        <h2>🧠 AI Assistant</h2>
        <div class="card">
          <div class="form-row">
            <label>Species</label>
            <select id="ai-species">${O(SPECIES, 'Raccoon')}</select>
          </div>
          <div class="form-row">
            <label>Season</label>
            <select id="ai-season">
              <option>Spring</option><option>Summer</option><option>Fall</option><option>Winter</option>
            </select>
          </div>
          <div class="form-row">
            <label>Observations</label>
            <textarea id="ai-obs" rows="4" placeholder="Describe what you see/hear..."></textarea>
          </div>
          <button class="btn primary" data-action="ai-suggest">💡 Get Suggestions</button>
          <button class="btn" data-action="ai-dictate">🎤 Dictate</button>
          <pre id="ai-output" class="ai-output">${E(state.aiResponse)}</pre>
        </div>
      </div>
    `;
  },

  afterRender() {
    $$('[data-action="ai-suggest"]').forEach(btn => {
      btn.addEventListener('click', handleAISuggest);
    });
    $$('[data-action="ai-dictate"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const el = $('#ai-obs');
        if (el) handleDictate(el);
      });
    });
  },

  unmount() {}
};

// ── Route Optimizer ──────────────────────────────

const RouteOptimizer = {
  _cleanup: null,

  render(state) {
    const jobsWithGPS = (state.jobs || []).filter(
      j => (j.latitude || j.lat) && (j.longitude || j.lng) && j.status !== 'Closed' && j.status !== 'Cancelled'
    );

    if (!jobsWithGPS.length) {
      return `<div class="page"><div class="card empty">No jobs with GPS coordinates found.</div></div>`;
    }

    // Calculate optimal route using nearest-neighbor from current location
    const route = calculateOptimalRoute(jobsWithGPS, state.pendingGPS);

    return `
      <div class="page route-page">
        <h2>🗺️ Route Optimizer</h2>
        <p class="tiny">${route.length} stops optimized for shortest driving distance.</p>
        <div class="route-list">
          ${route
            .map(
              (stop, idx) => `
            <div class="card route-stop">
              <div class="route-stop-header">
                <span class="route-number">${idx + 1}</span>
                <div class="route-stop-info">
                  <b>${E(stop.job.customer_name)}</b>
                  <span class="tiny">${E(stop.job.address)}${stop.job.town ? ', ' + E(stop.job.town) : ''}</span>
                </div>
                <span class="route-species">${SPECIES_ICONS[stop.job.species] || '🐾'}</span>
              </div>
              ${idx > 0 ? `<div class="route-leg tiny">↳ ${stop.distance.toFixed(1)} mi from previous</div>` : '<div class="route-leg tiny">📍 Starting point</div>'}
              <div class="route-actions">
                <button class="btn-sm" data-action="navigate-route" data-lat="${stop.job.latitude || stop.job.lat}" data-lng="${stop.job.longitude || stop.job.lng}" data-addr="${E(stop.job.address)}">🗺️ Directions</button>
                <button class="btn-sm" data-action="open-job" data-id="${E(stop.job.id)}">📂 Open Job</button>
              </div>
            </div>
          `
            )
            .join('')}
        </div>
        <div class="card" style="margin-top:var(--space-md);">
          <div class="tiny">Total estimated driving distance: <b>${route.reduce((sum, s, i) => sum + (i > 0 ? s.distance : 0), 0).toFixed(1)} miles</b></div>
          <div class="tiny">At 30 mph average: ~${Math.ceil((route.reduce((sum, s, i) => sum + (i > 0 ? s.distance : 0), 0) / 30) * 60)} min driving time</div>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    $$('[data-action="navigate-route"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const lat = parseFloat(btn.dataset.lat) || null;
        const lng = parseFloat(btn.dataset.lng) || null;
        navigateToJob(lat, lng, btn.dataset.addr || '');
      });
    });
    $$('[data-action="open-job"]').forEach(btn => {
      btn.addEventListener('click', () => router.navigate(`/jobs/${btn.dataset.id}`));
    });
  },

  unmount() {
    if (this._cleanup) {
      this._cleanup();
      this._cleanup = null;
    }
  }
};

// ── Expense Tracker ──────────────────────────────

const ExpenseTracker = {
  _listeners: [],

  render(state) {
    const q = (state.searchQuery || '').toLowerCase();
    let expenses = [...(state.expenses || [])];
    if (q) {
      expenses = expenses.filter(
        e => (e.description || '').toLowerCase().includes(q) || (e.category || '').toLowerCase().includes(q)
      );
    }
    expenses = sortBy(expenses, 'date', 'desc');

    // Totals by category
    const byCategory = {};
    EXPENSE_CATEGORIES.forEach(c => (byCategory[c] = 0));
    expenses.forEach(e => {
      byCategory[e.category] = (byCategory[e.category] || 0) + (e.amount || 0);
    });
    const totalExpenses = expenses.reduce((sum, e) => sum + (e.amount || 0), 0);

    return `
      <div class="page expense-page">
        <h2>💰 Expense Tracker</h2>

        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value">${money(totalExpenses)}</div>
            <div class="metric-label">Total Expenses</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">${expenses.length}</div>
            <div class="metric-label">Entries</div>
          </div>
        </div>

        <div class="card">
          <h3>Add Expense</h3>
          <form id="expense-form">
            <div class="form-row two-col">
              <div><label>Date</label><input type="date" id="exp-date" value="${new Date().toISOString().slice(0, 10)}"></div>
              <div><label>Amount ($)</label><input type="number" id="exp-amount" min="0" step="0.01" placeholder="0.00"></div>
            </div>
            <div class="form-row two-col">
              <div><label>Category</label><select id="exp-category">${O(EXPENSE_CATEGORIES)}</select></div>
              <div><label>Linked Job (optional)</label><select id="exp-job"><option value="">— None —</option>${(state.jobs || []).map(j => `<option value="${E(j.id)}">${E(j.customer_name)}</option>`).join('')}</select></div>
            </div>
            <div class="form-row"><label>Description</label><input type="text" id="exp-desc" placeholder="What was this for?"></div>
            <button type="submit" class="btn primary">➕ Add Expense</button>
          </form>
        </div>

        <h3 class="section-title">By Category</h3>
        <div class="card-flat">
          ${
            Object.entries(byCategory)
              .filter(([, v]) => v > 0)
              .map(
                ([cat, amt]) => `
            <div class="expense-category-row">
              <span>${E(cat)}</span>
              <span class="fin-val">${money(amt)}</span>
            </div>
          `
              )
              .join('') || '<p class="empty">No expenses yet.</p>'
          }
        </div>

        <h3 class="section-title">Recent Entries</h3>
        ${
          expenses.length
            ? expenses
                .slice(0, 50)
                .map(
                  e => `
          <div class="card expense-card">
            <div class="expense-header">
              <span class="pill">${E(e.category)}</span>
              <span class="fin-val bold">${money(e.amount)}</span>
            </div>
            <div class="tiny">${formatDate(e.date)} · ${E(e.description || 'No description')}</div>
            ${e.jobId ? `<div class="tiny">Linked: ${E((state.jobs || []).find(j => j.id === e.jobId)?.customer_name || 'Unknown')}</div>` : ''}
          </div>
        `
                )
                .join('')
            : '<div class="card empty">No expenses yet.</div>'
        }
      </div>
    `;
  },

  afterRender(state) {
    const form = $('#expense-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleAddExpense();
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ── Inventory Tracker ────────────────────────────

const InventoryTracker = {
  _listeners: [],

  render(state) {
    const items = [...(state.inventory || [])];
    const lowStock = items.filter(i => (i.quantity || 0) <= (i.minQuantity || 0));

    return `
      <div class="page inventory-page">
        <h2>📦 Inventory Tracker</h2>

        ${
          lowStock.length
            ? `
          <div class="card warn">
            <h3>⚠️ Low Stock Alert (${lowStock.length})</h3>
            ${lowStock.map(i => `<div class="tiny">${E(i.name)} — Only ${i.quantity} ${E(i.unit)} left (min: ${i.minQuantity})</div>`).join('')}
          </div>
        `
            : ''
        }

        <div class="card">
          <h3>Add Item</h3>
          <form id="inventory-form">
            <div class="form-row two-col">
              <div><label>Name</label><input type="text" id="inv-name" placeholder="e.g. Live Trap - Large"></div>
              <div><label>Category</label><select id="inv-category">${O(INVENTORY_CATEGORIES)}</select></div>
            </div>
            <div class="form-row three-col">
              <div><label>Quantity</label><input type="number" id="inv-qty" min="0" value="0"></div>
              <div><label>Min Qty</label><input type="number" id="inv-min" min="0" value="0"></div>
              <div><label>Unit</label><input type="text" id="inv-unit" placeholder="ea, ft, lb..."></div>
            </div>
            <button type="submit" class="btn primary">➕ Add Item</button>
          </form>
        </div>

        <h3 class="section-title">Inventory (${items.length})</h3>
        ${
          items.length
            ? items
                .map(i => {
                  const isLow = (i.quantity || 0) <= (i.minQuantity || 0);
                  return `
            <div class="card inventory-card ${isLow ? 'warn' : ''}">
              <div class="inventory-header">
                <b>${E(i.name)}</b>
                <span class="pill ${isLow ? 'warn' : ''}">${i.quantity || 0} ${E(i.unit)}</span>
              </div>
              <div class="tiny">${E(i.category)} · Min: ${i.minQuantity || 0} ${E(i.unit)} · Last restocked: ${formatDate(i.lastRestocked)}</div>
              <div class="inventory-actions">
                <button class="btn-sm" data-action="inv-restock" data-id="${E(i.id)}">📥 Restock</button>
                <button class="btn-sm" data-action="inv-use" data-id="${E(i.id)}">📤 Use</button>
              </div>
            </div>
          `;
                })
                .join('')
            : '<div class="card empty">No inventory items yet.</div>'
        }
      </div>
    `;
  },

  afterRender(state) {
    const form = $('#inventory-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleAddInventoryItem();
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }
    $$('[data-action="inv-restock"]').forEach(btn => {
      const fn = () => handleRestockItem(btn.dataset.id);
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
    $$('[data-action="inv-use"]').forEach(btn => {
      const fn = () => handleUseItem(btn.dataset.id);
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ── Equipment Tracker ────────────────────────────

const EquipmentTracker = {
  _listeners: [],

  render(state) {
    const items = [...(state.equipment || [])];
    const now = new Date();
    const overdue = items.filter(i => i.nextMaintenanceDue && new Date(i.nextMaintenanceDue) < now);

    return `
      <div class="page equipment-page">
        <h2>🔧 Equipment Maintenance</h2>

        ${
          overdue.length
            ? `
          <div class="card warn">
            <h3>⚠️ Maintenance Due (${overdue.length})</h3>
            ${overdue.map(i => `<div class="tiny">${E(i.name)} — was due ${formatDate(i.nextMaintenanceDue)}</div>`).join('')}
          </div>
        `
            : ''
        }

        <div class="card">
          <h3>Add Equipment</h3>
          <form id="equipment-form">
            <div class="form-row two-col">
              <div><label>Name</label><input type="text" id="eq-name" placeholder="e.g. Work Truck"></div>
              <div><label>Type</label><select id="eq-type">${O(EQUIPMENT_TYPES)}</select></div>
            </div>
            <div class="form-row two-col">
              <div><label>Purchase Date</label><input type="date" id="eq-purchase"></div>
              <div><label>Next Maintenance</label><input type="date" id="eq-next"></div>
            </div>
            <div class="form-row"><label>Notes</label><textarea id="eq-notes" rows="2"></textarea></div>
            <button type="submit" class="btn primary">➕ Add Equipment</button>
          </form>
        </div>

        <h3 class="section-title">Equipment (${items.length})</h3>
        ${
          items.length
            ? items
                .map(i => {
                  const isOverdue = i.nextMaintenanceDue && new Date(i.nextMaintenanceDue) < now;
                  return `
            <div class="card equipment-card ${isOverdue ? 'warn' : ''}">
              <div class="equipment-header">
                <b>${E(i.name)}</b>
                <span class="pill">${E(i.type)}</span>
              </div>
              <div class="tiny">Purchased: ${formatDate(i.purchaseDate)} · Last maintained: ${formatDate(i.lastMaintenance)}</div>
              <div class="tiny ${isOverdue ? 'warn' : ''}">Next maintenance: ${formatDate(i.nextMaintenanceDue)}</div>
              ${i.notes ? `<div class="tiny">${E(i.notes)}</div>` : ''}
              <div class="equipment-actions">
                <button class="btn-sm" data-action="eq-maintain" data-id="${E(i.id)}">🔧 Mark Maintained</button>
              </div>
            </div>
          `;
                })
                .join('')
            : '<div class="card empty">No equipment tracked yet.</div>'
        }
      </div>
    `;
  },

  afterRender(state) {
    const form = $('#equipment-form');
    if (form) {
      const onSubmit = e => {
        e.preventDefault();
        handleAddEquipment();
      };
      form.addEventListener('submit', onSubmit);
      this._listeners.push(() => form.removeEventListener('submit', onSubmit));
    }
    $$('[data-action="eq-maintain"]').forEach(btn => {
      const fn = () => handleMarkMaintained(btn.dataset.id);
      btn.addEventListener('click', fn);
      this._listeners.push(() => btn.removeEventListener('click', fn));
    });
  },

  unmount() {
    this._listeners.forEach(fn => fn());
    this._listeners = [];
  }
};

// ─────────────────────────────────────────────────
// Page Registry
// ─────────────────────────────────────────────────

/** @type {Record<string, {render: Function, afterRender?: Function, unmount?: Function}>} */
// ═══════════════════════════════════════════════════
// Schedule / Calendar Page
// ═══════════════════════════════════════════════════

const SchedulePage = {
  render(state) {
    const jobs = state.jobs || [];
    const inspections = state.inspections || [];
    const now = new Date();
    const year = state.scheduleYear || now.getFullYear();
    const month = state.scheduleMonth !== undefined ? state.scheduleMonth : now.getMonth();
    const monthNames = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December'
    ];
    const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDayOfWeek = firstDay.getDay();
    const daysInMonth = lastDay.getDate();

    // Get scheduled jobs for this month
    const scheduledJobs = jobs.filter(j => {
      if (!j.scheduled_start) return false;
      const d = new Date(j.scheduled_start);
      return d.getFullYear() === year && d.getMonth() === month;
    });

    // Get scheduled inspections for this month
    const scheduledInspections = inspections.filter(i => {
      if (!i.scheduled_start) return false;
      const d = new Date(i.scheduled_start);
      return d.getFullYear() === year && d.getMonth() === month;
    });

    // Combine by day
    const itemsByDay = {};
    scheduledJobs.forEach(j => {
      const d = new Date(j.scheduled_start);
      const day = d.getDate();
      if (!itemsByDay[day]) itemsByDay[day] = { jobs: [], inspections: [] };
      itemsByDay[day].jobs.push(j);
    });
    scheduledInspections.forEach(i => {
      const d = new Date(i.scheduled_start);
      const day = d.getDate();
      if (!itemsByDay[day]) itemsByDay[day] = { jobs: [], inspections: [] };
      itemsByDay[day].inspections.push(i);
    });

    let calendarHTML = '';
    for (let i = 0; i < startDayOfWeek; i++) {
      calendarHTML += '<div class="cal-day cal-empty"></div>';
    }
    for (let day = 1; day <= daysInMonth; day++) {
      const isToday = day === now.getDate() && month === now.getMonth() && year === now.getFullYear();
      const dayItems = itemsByDay[day] || { jobs: [], inspections: [] };
      const totalItems = dayItems.jobs.length + dayItems.inspections.length;
      const dots = [];
      // Job dots (green/yellow)
      dayItems.jobs.forEach(j => {
        const color = j.status === 'Active' ? '#22c55e' : j.status === 'Scheduled' ? '#eab308' : '#6b7280';
        dots.push(
          `<span class="cal-dot" style="background:${color}" title="Job: ${E(j.customer_name || 'Unknown')}"></span>`
        );
      });
      // Inspection dots (blue)
      dayItems.inspections.forEach(i => {
        const color =
          i.status === 'Pending'
            ? '#3b82f6'
            : i.status === 'Scheduled'
              ? '#8b5cf6'
              : i.status === 'Completed'
                ? '#10b981'
                : '#6b7280';
        dots.push(
          `<span class="cal-dot" style="background:${color}" title="Inspection: ${E(i.customer_name || 'Unknown')}"></span>`
        );
      });
      calendarHTML += `
        <div class="cal-day ${isToday ? 'cal-today' : ''} ${totalItems ? 'cal-has-items' : ''}" data-day="${day}">
          <span class="cal-day-num">${day}</span>
          <div class="cal-dots">${dots.join('')}</div>
          ${totalItems ? `<span class="cal-count">${totalItems}</span>` : ''}
        </div>`;
    }

    // Upcoming items list (both jobs + inspections)
    const filterDate = state.scheduleFilterDate;
    const upcomingJobs = scheduledJobs
      .filter(j => {
        const d = new Date(j.scheduled_start);
        if (filterDate) {
          return d.toISOString().slice(0, 10) === filterDate;
        }
        return d >= now;
      })
      .map(j => ({ ...j, itemType: 'job' }));
    const upcomingInspections = scheduledInspections
      .filter(i => {
        const d = new Date(i.scheduled_start);
        if (filterDate) {
          return d.toISOString().slice(0, 10) === filterDate;
        }
        return d >= now;
      })
      .map(i => ({ ...i, itemType: 'inspection' }));
    const upcoming = [...upcomingJobs, ...upcomingInspections]
      .sort((a, b) => new Date(a.scheduled_start) - new Date(b.scheduled_start))
      .slice(0, 10);

    return `
      <div class="page">
        <h1 class="section-title">📅 Schedule</h1>
        <div class="cal-header">
          <button class="icon-btn" data-cal="prev" aria-label="Previous month">◀</button>
          <h2>${monthNames[month]} ${year}</h2>
          <button class="icon-btn" data-cal="next" aria-label="Next month">▶</button>
        </div>
        <div class="cal-grid">
          ${dayNames.map(d => `<div class="cal-day-name">${d}</div>`).join('')}
          ${calendarHTML}
        </div>
        <div style="display:flex;gap:16px;flex-wrap:wrap;margin:16px 0;font-size:12px;">
          <span><span class="cal-dot" style="background:#22c55e;display:inline-block;"></span> Job</span>
          <span><span class="cal-dot" style="background:#3b82f6;display:inline-block;"></span> Inspection</span>
        </div>
        ${
          filterDate
            ? `
          <div class="card" style="display:flex;justify-content:space-between;align-items:center;">
            <span>Filtered: <b>${formatDate(filterDate)}</b></span>
            <button class="btn-sm" data-action="clear-date-filter">✕ Clear Filter</button>
          </div>
        `
            : ''
        }
        <h2 class="section-title">📋 ${filterDate ? 'Items for Selected Date' : 'Upcoming'}</h2>
        <div class="card-flat">
          ${
            upcoming.length
              ? upcoming
                  .map(
                    item => `
            <div class="job-row" data-id="${item.id}" data-type="${item.itemType}">
              <span class="job-row-date">${formatDateShort(item.scheduled_start)}</span>
              <span class="job-row-title">${item.itemType === 'inspection' ? '🔍' : SPECIES_ICONS[item.species] || '🔧'} ${E(item.customer_name || 'Unknown')}</span>
              <span class="status-badge ${(item.status || '').toLowerCase().replace(/\s+/g, '-')}">${item.itemType === 'inspection' ? 'INSP: ' : ''}${E(item.status || 'Active')}</span>
            </div>
          `
                  )
                  .join('')
              : '<p class="empty">No upcoming items scheduled.</p>'
          }
        </div>
      </div>`;
  },

  afterRender(state) {
    $$('[data-cal]').forEach(btn => {
      btn.addEventListener('click', () => {
        const dir = btn.dataset.cal;
        const s = store.getState();
        let m = s.scheduleMonth !== undefined ? s.scheduleMonth : new Date().getMonth();
        let y = s.scheduleYear || new Date().getFullYear();
        if (dir === 'prev') {
          m--;
          if (m < 0) {
            m = 11;
            y--;
          }
        }
        if (dir === 'next') {
          m++;
          if (m > 11) {
            m = 0;
            y++;
          }
        }
        store.setState({ scheduleMonth: m, scheduleYear: y });
      });
    });
    $$('.cal-day.cal-has-items').forEach(day => {
      day.addEventListener('click', () => {
        const d = parseInt(day.dataset.day);
        const s = store.getState();
        const y = s.scheduleYear || new Date().getFullYear();
        const m = s.scheduleMonth !== undefined ? s.scheduleMonth : new Date().getMonth();
        const dateStr = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        navigateTo('schedule', { scheduleFilterDate: dateStr });
      });
    });
    $$('[data-action="clear-date-filter"]').forEach(btn => {
      btn.addEventListener('click', () => store.setState({ scheduleFilterDate: null }));
    });
    $$('.job-row[data-id]').forEach(row => {
      row.addEventListener('click', () => {
        const type = row.dataset.type;
        if (type === 'inspection') navigateTo('inspection-form', { selectedInspectionId: row.dataset.id });
        else navigateTo('job-detail', { selectedJobId: row.dataset.id });
      });
    });
  }
};

const pages = {
  dashboard: Dashboard,
  jobs: JobList,
  'job-detail': JobDetail,
  'job-form': JobForm,
  customers: CustomerList,
  'customer-form': CustomerForm,
  inspections: InspectionList,
  'inspection-form': InspectionForm,
  estimate: EstimateCalc,
  photos: PhotoGallery,
  gps: GPSMap,
  schedule: SchedulePage,
  metrics: MetricsPage,
  settings: SettingsPage,
  ai: AIModal,
  route: RouteOptimizer,
  expenses: ExpenseTracker,
  inventory: InventoryTracker,
  equipment: EquipmentTracker
};

// ═══════════════════════════════════════════════════
// Action Handlers
// ═══════════════════════════════════════════════════

/** @param {string|null} jobId */
function handleSaveInspection(inspectionId) {
  const customer = $('#iform-customer')?.value?.trim();
  if (!customer) {
    showToast('Customer name is required', 'error');
    return;
  }

  const dateVal = $('#iform-date')?.value;
  const timeVal = $('#iform-time')?.value || '09:00';
  const scheduledStart = dateVal ? new Date(`${dateVal}T${timeVal}`).toISOString() : null;

  const inspection = {
    id: inspectionId || id(),
    customer_name: customer,
    phone: $('#iform-phone')?.value?.trim() || '',
    address: $('#iform-address')?.value?.trim() || '',
    town: $('#iform-town')?.value?.trim() || '',
    state: $('#iform-state')?.value?.trim() || 'NY',
    species: $('#iform-species')?.value || 'General',
    status: $('#iform-status')?.value || 'Pending',
    notes: $('#iform-notes')?.value?.trim() || '',
    scheduled_start: scheduledStart,
    created_at: inspectionId
      ? (store.getState().inspections || []).find(i => i.id === inspectionId)?.created_at || now()
      : now(),
    updated_at: now()
  };

  store.setState(prev => {
    const inspections = [...(prev.inspections || [])];
    const idx = inspections.findIndex(i => i.id === inspection.id);
    if (idx >= 0) {
      inspections[idx] = inspection;
    } else {
      inspections.push(inspection);
    }
    return { inspections, selectedInspectionId: null };
  });

  showToast(`Inspection ${inspectionId ? 'updated' : 'scheduled'} for ${customer}`, 'success');
  navigateTo('inspections');
}

function handleConvertInspection(inspection) {
  const job = {
    id: id(),
    customer: inspection.customer_name,
    phone: inspection.phone,
    address: inspection.address,
    town: inspection.town,
    state: inspection.state,
    species: inspection.species,
    status: 'Active',
    priority: 'Normal',
    assigned_tech: '',
    notes: `Converted from inspection.\n${inspection.notes || ''}`,
    ai_notes: '',
    scope: '',
    warranty: '90 days',
    estimate: 0,
    subtotal: 0,
    tax_rate: config.DEFAULT_TAX_RATE,
    tax_amount: 0,
    grand_total: 0,
    deposit_paid: 0,
    balance_due: 0,
    latitude: '',
    longitude: '',
    accuracy: 0,
    scheduled_start: inspection.scheduled_start,
    timer_total: 0,
    is_recurring: false,
    created_at: now(),
    updated_at: now()
  };

  store.setState(prev => {
    const jobs = [...(prev.jobs || []), job];
    const inspections = (prev.inspections || []).map(i => (i.id === inspection.id ? { ...i, status: 'Converted' } : i));
    return { jobs, inspections, selectedJobId: job.id };
  });

  showToast(`Converted inspection for ${inspection.customer_name} to a job`, 'success');
  navigateTo('job-detail', { selectedJobId: job.id });
}

// ── Route Optimizer Helpers ──────────────────────

/**
 * Calculate the great-circle distance between two lat/lng points (Haversine formula).
 * @param {number} lat1
 * @param {number} lng1
 * @param {number} lat2
 * @param {number} lng2
 * @returns {number} Distance in miles
 */
function haversine(lat1, lng1, lat2, lng2) {
  const R = 3959; // Earth radius in miles
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Calculate optimal route using nearest-neighbor algorithm.
 * @param {Array} jobs - Jobs with GPS coordinates
 * @param {{lat:number,lng:number}|null} startGPS - Starting position
 * @returns {Array<{job:object, distance:number}>}
 */
function calculateOptimalRoute(jobs, startGPS) {
  if (!jobs.length) return [];
  const unvisited = [...jobs];
  const route = [];
  let currentLat = startGPS?.lat || parseFloat(unvisited[0].latitude || unvisited[0].lat);
  let currentLng = startGPS?.lng || parseFloat(unvisited[0].longitude || unvisited[0].lng);

  while (unvisited.length > 0) {
    let nearestIdx = 0;
    let nearestDist = Infinity;
    unvisited.forEach((job, idx) => {
      const jLat = parseFloat(job.latitude || job.lat);
      const jLng = parseFloat(job.longitude || job.lng);
      const dist = haversine(currentLat, currentLng, jLat, jLng);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearestIdx = idx;
      }
    });
    const nearest = unvisited.splice(nearestIdx, 1)[0];
    route.push({ job: nearest, distance: nearestDist === Infinity ? 0 : nearestDist });
    currentLat = parseFloat(nearest.latitude || nearest.lat);
    currentLng = parseFloat(nearest.longitude || nearest.lng);
  }
  return route;
}

// ── Expense Handlers ─────────────────────────────

function handleAddExpense() {
  const date = $('#exp-date')?.value;
  const amount = parseFloat($('#exp-amount')?.value || 0);
  const category = $('#exp-category')?.value;
  const jobId = $('#exp-job')?.value || null;
  const description = $('#exp-desc')?.value?.trim() || '';

  if (!amount || amount <= 0) {
    showToast('Enter a valid amount', 'error');
    return;
  }

  const expense = {
    id: id(),
    date: date || new Date().toISOString().slice(0, 10),
    category: category || 'Other',
    amount,
    description,
    jobId,
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ expenses: [expense, ...s.expenses] }));
  showToast('Expense added');

  // Reset form
  $('#exp-amount').value = '';
  $('#exp-desc').value = '';
}

// ── Inventory Handlers ───────────────────────────

function handleAddInventoryItem() {
  const name = $('#inv-name')?.value?.trim();
  const category = $('#inv-category')?.value;
  const quantity = parseInt($('#inv-qty')?.value || 0);
  const minQuantity = parseInt($('#inv-min')?.value || 0);
  const unit = $('#inv-unit')?.value?.trim() || 'ea';

  if (!name) {
    showToast('Item name is required', 'error');
    return;
  }

  const item = {
    id: id(),
    name,
    category: category || 'Other',
    quantity,
    minQuantity,
    unit,
    lastRestocked: new Date().toISOString(),
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ inventory: [item, ...s.inventory] }));
  showToast(`Added ${name} to inventory`);
  $('#inv-name').value = '';
  $('#inv-qty').value = '0';
}

/** @param {string} itemId */
function handleRestockItem(itemId) {
  const qty = parseInt(prompt('How many to add?', '1') || '0');
  if (!qty || qty <= 0) return;
  store.setState(s => ({
    inventory: s.inventory.map(i =>
      i.id === itemId ? { ...i, quantity: (i.quantity || 0) + qty, lastRestocked: new Date().toISOString() } : i
    )
  }));
  showToast(`Restocked ${qty} units`);
}

/** @param {string} itemId */
function handleUseItem(itemId) {
  const qty = parseInt(prompt('How many used?', '1') || '0');
  if (!qty || qty <= 0) return;
  store.setState(s => ({
    inventory: s.inventory.map(i => (i.id === itemId ? { ...i, quantity: Math.max(0, (i.quantity || 0) - qty) } : i))
  }));
  showToast(`Recorded ${qty} units used`);
}

// ── Equipment Handlers ───────────────────────────

function handleAddEquipment() {
  const name = $('#eq-name')?.value?.trim();
  const type = $('#eq-type')?.value;
  const purchaseDate = $('#eq-purchase')?.value || null;
  const nextMaintenance = $('#eq-next')?.value || null;
  const notes = $('#eq-notes')?.value?.trim() || '';

  if (!name) {
    showToast('Equipment name is required', 'error');
    return;
  }

  const item = {
    id: id(),
    name,
    type: type || 'Other',
    purchaseDate,
    lastMaintenance: null,
    nextMaintenanceDue: nextMaintenance,
    notes,
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ equipment: [item, ...s.equipment] }));
  showToast(`Added ${name}`);
  $('#eq-name').value = '';
  $('#eq-notes').value = '';
}

/** @param {string} itemId */
function handleMarkMaintained(itemId) {
  store.setState(s => ({
    equipment: s.equipment.map(i =>
      i.id === itemId ? { ...i, lastMaintenance: new Date().toISOString(), nextMaintenanceDue: null } : i
    )
  }));
  showToast('Maintenance recorded');
}

// ── Voice Note Handlers ──────────────────────────

/** @type {MediaRecorder|null} */
let _mediaRecorder = null;
/** @type {Blob[]} */
let _recordedChunks = [];
/** @type {number|null} */
let _recordingStartTime = null;
/** @type {string|null} */
let _recordingJobId = null;

/**
 * Start or stop voice recording.
 * @param {string} jobId
 */
async function handleToggleVoiceRecording(jobId) {
  if (_mediaRecorder && _mediaRecorder.state === 'recording') {
    _mediaRecorder.stop();
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    _mediaRecorder = new MediaRecorder(stream);
    _recordedChunks = [];
    _recordingStartTime = Date.now();
    _recordingJobId = jobId;

    _mediaRecorder.ondataavailable = e => {
      if (e.data.size > 0) _recordedChunks.push(e.data);
    };

    _mediaRecorder.onstop = () => {
      const duration = Math.round((Date.now() - _recordingStartTime) / 1000);
      const blob = new Blob(_recordedChunks, { type: 'audio/webm' });
      const reader = new FileReader();
      reader.onloadend = () => {
        const note = {
          id: id(),
          jobId: _recordingJobId,
          audioData: reader.result,
          duration,
          createdAt: new Date().toISOString()
        };
        store.setState(s => ({ voiceNotes: [note, ...s.voiceNotes] }));
        showToast(`Voice note saved (${duration}s)`);
        _recordingJobId = null;
      };
      reader.readAsDataURL(blob);

      // Stop all tracks
      stream.getTracks().forEach(t => t.stop());

      // Update UI
      const statusEl = $('#recording-status');
      const btn = $('#record-btn');
      if (statusEl) statusEl.style.display = 'none';
      if (btn) btn.textContent = '🎙️ Record Voice Note';
      _mediaRecorder = null;
    };

    _mediaRecorder.onerror = () => {
      showToast('Recording error', 'error');
      stream.getTracks().forEach(t => t.stop());
      _mediaRecorder = null;
    };

    _mediaRecorder.start();
    showToast('Recording started', 'success', 1500);
    const statusEl = $('#recording-status');
    const btn = $('#record-btn');
    if (statusEl) statusEl.style.display = 'inline';
    if (btn) btn.textContent = '⏹️ Stop Recording';
  } catch (err) {
    showToast('Microphone access denied', 'error');
    console.error('Voice recording error:', err);
  }
}

/** @param {string} noteId */
function handleDeleteVoiceNote(noteId) {
  if (!confirm('Delete this voice note?')) return;
  store.setState(s => ({ voiceNotes: s.voiceNotes.filter(v => v.id !== noteId) }));
  showToast('Voice note deleted');
}

// ── Checklist Helpers ────────────────────────────

/**
 * Get or initialize a job's checklist.
 * @param {object} job
 * @returns {Array<{id:string, label:string, done:boolean}>}
 */
function initChecklist() {
  return deepClone(DEFAULT_CHECKLIST);
}

/**
 * Toggle a checklist item.
 * @param {string} jobId
 * @param {string} checklistId
 * @param {boolean} done
 */
function handleToggleChecklist(jobId, checklistId, done) {
  store.setState(s => ({
    jobs: s.jobs.map(j => {
      if (j.id !== jobId) return j;
      const checklist = j.checklist ? [...j.checklist] : initChecklist();
      const item = checklist.find(c => c.id === checklistId);
      if (item) item.done = done;
      return { ...j, checklist };
    })
  }));
}

// ── Trap Log Handlers ────────────────────────────

/** @param {string} jobId */
function handleAddTrapLog(jobId) {
  const date = $('#trap-date')?.value;
  const location = $('#trap-location')?.value?.trim();
  const species = $('#trap-species')?.value;
  const count = parseInt($('#trap-count')?.value || 0);
  const bait = $('#trap-bait')?.value?.trim();
  const notes = $('#trap-notes')?.value?.trim();

  if (!location) {
    showToast('Location is required', 'error');
    return;
  }

  const entry = {
    id: id(),
    jobId,
    date: date || new Date().toISOString().slice(0, 10),
    location,
    species: species || 'Other',
    count,
    bait,
    notes,
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ trapLogs: [entry, ...s.trapLogs] }));
  showToast('Trap check logged');
  $('#trap-location').value = '';
  $('#trap-count').value = '1';
  $('#trap-bait').value = '';
  $('#trap-notes').value = '';
}

// ── Communication Log Handlers ───────────────────

/** @param {string} jobId */
function handleAddCommunication(jobId) {
  const st = store.getState();
  const job = st.jobs.find(j => j.id === jobId);
  if (!job) return;

  const type = $('#comm-type')?.value;
  const direction = $('#comm-direction')?.value;
  const notes = $('#comm-notes')?.value?.trim();

  if (!notes) {
    showToast('Notes are required', 'error');
    return;
  }

  const entry = {
    id: id(),
    customerId: job.customer_name,
    jobId,
    type: type || 'Call',
    direction: direction || 'outbound',
    date: new Date().toISOString(),
    notes
  };

  store.setState(s => ({ communications: [entry, ...s.communications] }));
  showToast('Communication logged');
  $('#comm-notes').value = '';
}

// ── Reminder Handlers ────────────────────────────

/**
 * Prompt for follow-up date when closing a job.
 * @param {string} jobId
 */
function promptFollowUpOnClose(jobId) {
  const st = store.getState();
  const job = st.jobs.find(j => j.id === jobId);
  if (!job) return;

  // Check if we already have a reminder for this job
  const existing = (st.reminders || []).filter(r => r.jobId === jobId);
  if (existing.length > 0) return; // Already has reminder

  const defaultDate = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  const dueDate = prompt('Set follow-up reminder date:', defaultDate);
  if (!dueDate) return;

  const reminder = {
    id: id(),
    jobId,
    dueDate,
    type: 'warranty',
    notes: `Follow-up for ${job.customer_name} — ${job.species}`,
    status: 'pending',
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ reminders: [reminder, ...s.reminders] }));
  showToast(`Follow-up set for ${formatDate(dueDate)}`);
}

/** @param {string} jobId */
function handleAddReminder(jobId) {
  const dueDate = $('#reminder-date')?.value;
  const type = $('#reminder-type')?.value;
  const notes = $('#reminder-notes')?.value?.trim();

  if (!dueDate) {
    showToast('Date is required', 'error');
    return;
  }

  const reminder = {
    id: id(),
    jobId,
    dueDate,
    type: type || 'followup',
    notes: notes || 'Follow-up',
    status: 'pending',
    createdAt: new Date().toISOString()
  };

  store.setState(s => ({ reminders: [reminder, ...s.reminders] }));
  showToast('Reminder added');
  $('#reminder-notes').value = '';
}

/** @param {string} reminderId */
function handleDismissReminder(reminderId) {
  store.setState(s => ({
    reminders: s.reminders.map(r => (r.id === reminderId ? { ...r, status: 'completed' } : r))
  }));
  showToast('Reminder marked done');
}

// ── Weather Widget ───────────────────────────────

/**
 * Initialize the weather widget on the dashboard.
 */
async function initWeatherWidget() {
  // Check cache first
  try {
    const cached = localStorage.getItem(WEATHER_CACHE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (parsed.expiresAt && new Date(parsed.expiresAt) > new Date()) {
        store.setState({ weatherCache: parsed.data });
        return;
      }
    }
  } catch {
    /* ignore cache errors */
  }

  await fetchWeather();
}

/**
 * Fetch weather data from OpenWeatherMap API.
 */
async function fetchWeather() {
  if (!config.hasWeather) return;

  try {
    // Get current position
    let lat = config.DEFAULT_MAP_CENTER.lat;
    let lng = config.DEFAULT_MAP_CENTER.lng;

    if ('geolocation' in navigator) {
      const pos = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 10000, maximumAge: 300000 });
      });
      lat = pos.coords.latitude;
      lng = pos.coords.longitude;
    }

    const url = `https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lng}&appid=${config.OPENWEATHER_API_KEY}&units=imperial`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    const weatherData = {
      temp: Math.round(data.main?.temp || 0),
      feelsLike: Math.round(data.main?.feels_like || 0),
      condition: data.weather?.[0]?.main || 'Unknown',
      description: data.weather?.[0]?.description || '',
      humidity: data.main?.humidity || 0,
      windSpeed: Math.round(data.wind?.speed || 0),
      icon: data.weather?.[0]?.icon || '',
      location: data.name || 'Unknown',
      updatedAt: new Date().toISOString()
    };

    // Cache for 30 minutes
    const cacheEntry = {
      data: weatherData,
      expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString()
    };
    localStorage.setItem(WEATHER_CACHE_KEY, JSON.stringify(cacheEntry));
    store.setState({ weatherCache: weatherData });
  } catch (err) {
    console.warn('Weather fetch failed:', err.message);
    // Gracefully degrade — no error toast, just no weather shown
  }
}

/**
 * Render weather widget HTML.
 * @param {object|null} weather
 * @returns {string}
 */
function renderWeatherWidget(weather) {
  if (!weather) return '';
  const icon = WEATHER_ICONS[weather.condition] || '🌡️';
  let warning = '';
  if (weather.condition === 'Rain' || weather.condition === 'Thunderstorm' || weather.condition === 'Drizzle') {
    warning = '<div class="weather-warning">🌧️ Rain expected — bring waterproof gear</div>';
  } else if (weather.condition === 'Snow') {
    warning = '<div class="weather-warning">🌨️ Snow expected — drive carefully</div>';
  } else if (weather.windSpeed > 20) {
    warning = '<div class="weather-warning">💨 High winds — secure ladders and traps</div>';
  } else if (weather.temp > 90) {
    warning = '<div class="weather-warning">☀️ Extreme heat — stay hydrated</div>';
  } else if (weather.temp < 20) {
    warning = '<div class="weather-warning">❄️ Freezing temps — check trap batteries</div>';
  }

  return `
    <div class="weather-card">
      <span class="weather-icon">${icon}</span>
      <div class="weather-info">
        <div class="weather-temp">${weather.temp}°F <small>(feels ${weather.feelsLike}°F)</small></div>
        <div class="weather-desc">${E(weather.description)} · ${E(weather.location)}</div>
        <div class="tiny">💨 ${weather.windSpeed} mph · 💧 ${weather.humidity}% humidity</div>
      </div>
    </div>
    ${warning}
  `;
}

function handleSaveJob(jobId) {
  const customer = $('#form-customer')?.value?.trim();
  const phone = $('#form-phone')?.value?.trim();
  const email = $('#form-email')?.value?.trim();
  const address = $('#form-address')?.value?.trim();
  const town = $('#form-town')?.value?.trim();
  const state_val = $('#form-state')?.value?.trim();
  const zip = $('#form-zip')?.value?.trim();
  const species = $('#form-species')?.value;
  const title = $('#form-title')?.value?.trim();
  const status = $('#form-status')?.value;
  const priority = $('#form-priority')?.value;
  const tech = $('#form-tech')?.value?.trim();
  const dateVal = $('#form-scheduled-start')?.value;
  const timeVal = $('#form-scheduled-time')?.value || '09:00';
  const scheduledStart = dateVal ? new Date(`${dateVal}T${timeVal}`).toISOString() : null;
  const scope = $('#form-scope')?.value?.trim();
  const notes = $('#form-notes')?.value?.trim();
  const warranty = $('#form-warranty')?.value?.trim() || 'Not set';
  const estimate = parseFloat($('#form-estimate')?.value) || 0;
  const taxRate = parseFloat($('#form-tax-rate')?.value) / 100 || config.DEFAULT_TAX_RATE;

  const s = store.getState();
  const pendingGPS = s.pendingGPS;

  const payload = {
    customer,
    phone,
    email,
    address,
    town: town || 'Unsorted',
    state: state_val || 'NY',
    zip,
    species,
    title: title || `${species} job`,
    status: status || 'Active',
    priority: priority || 'Normal',
    assigned_tech: tech,
    scheduled_start: scheduledStart,
    scope,
    notes,
    warranty,
    estimate,
    tax_rate: taxRate,
    updated_at: new Date().toISOString()
  };

  if (pendingGPS) {
    payload.latitude = String(pendingGPS.lat);
    payload.longitude = String(pendingGPS.lng);
    payload.accuracy = pendingGPS.accuracy;
  }

  const errs = validateJob(payload);
  if (errs.length) {
    showToast(errs.join('; '), 'error', 5000);
    return;
  }

  if (jobId) {
    // Update
    const oldJob = store.getState().jobs.find(j => j.id === jobId);
    const wasNotClosed = oldJob?.status !== 'Closed';
    store.setState(st => ({
      ...st,
      jobs: st.jobs.map(j => (j.id === jobId ? { ...j, ...payload } : j)),
      pendingGPS: null
    }));
    // Prompt for follow-up if job was just closed
    if (wasNotClosed && payload.status === 'Closed') {
      setTimeout(() => promptFollowUpOnClose(jobId), 500);
    }
    showToast('Job updated');
    router.navigate(`/jobs/${jobId}`);
  } else {
    // Create
    const newJob = {
      id: id(),
      created_at: new Date().toISOString(),
      ...payload
    };
    store.setState(st => ({
      ...st,
      jobs: [newJob, ...st.jobs],
      pendingGPS: null,
      syncQueue: [...st.syncQueue, { id: id(), action: 'job:create', jobId: newJob.id, at: new Date().toISOString() }]
    }));
    showToast('Job created');
    router.navigate(`/jobs/${newJob.id}`);
  }
}

/** @param {string|null} customerId */
function handleSaveCustomer(customerId) {
  const name = $('#cform-name')?.value?.trim();
  const phone = $('#cform-phone')?.value?.trim();
  const email = $('#cform-email')?.value?.trim();
  const address = $('#cform-address')?.value?.trim();
  const town = $('#cform-town')?.value?.trim();
  const state_val = $('#cform-state')?.value?.trim();
  const zip = $('#cform-zip')?.value?.trim();
  const notes = $('#cform-notes')?.value?.trim();

  const payload = {
    name,
    phone,
    email,
    address,
    town,
    state: state_val || 'NY',
    zip,
    notes,
    updated_at: new Date().toISOString()
  };

  const errs = validateCustomer(payload);
  if (errs.length) {
    showToast(errs.join('; '), 'error', 5000);
    return;
  }

  if (customerId) {
    store.setState(st => ({
      ...st,
      customers: st.customers.map(c => (c.id === customerId ? { ...c, ...payload } : c))
    }));
    showToast('Customer updated');
    router.navigate('/customers');
  } else {
    const newCustomer = { id: id(), created_at: new Date().toISOString(), ...payload };
    store.setState(st => ({
      ...st,
      customers: [newCustomer, ...st.customers]
    }));
    showToast('Customer created');
    router.navigate('/customers');
  }
}

/** @param {string} jobId */
function handleAddPayment(jobId) {
  const amount = parseFloat($('#payment-amount')?.value || 0);
  if (!amount || amount <= 0) {
    showToast('Enter a valid amount', 'error');
    return;
  }

  store.setState(st => ({
    ...st,
    jobs: st.jobs.map(j => {
      if (j.id !== jobId) return j;
      const newDeposit = (j.deposit_paid || 0) + amount;
      const newBalance = (j.grand_total || j.estimate || 0) - newDeposit;
      return {
        ...j,
        deposit_paid: newDeposit,
        balance_due: Math.max(0, newBalance),
        updated_at: new Date().toISOString()
      };
    })
  }));
  showToast(`Payment recorded: ${money(amount)}`);
}

/** @param {string} jobId */
function handleTimerStart(jobId) {
  const now = new Date().toISOString();
  store.setState(st => ({
    ...st,
    jobs: st.jobs.map(j => (j.id === jobId ? { ...j, timer_start: now, updated_at: now } : j))
  }));
  showToast('Timer started');
}

/** @param {string} jobId */
function handleTimerStop(jobId) {
  const st = store.getState();
  const job = st.jobs.find(j => j.id === jobId);
  if (!job?.timer_start) {
    showToast('Timer not started', 'warn');
    return;
  }

  const start = new Date(job.timer_start);
  const now = new Date();
  const mins = Math.ceil((now - start) / (1000 * 60));
  const total = (job.timer_total || 0) + mins;

  store.setState(s => ({
    ...s,
    jobs: s.jobs.map(j =>
      j.id === jobId ? { ...j, timer_start: null, timer_total: total, updated_at: now.toISOString() } : j
    )
  }));
  showToast(`Timer stopped: +${mins} min`);
}

/** @param {string} jobId */
function handleGeneratePDF(jobId) {
  const st = store.getState();
  const job = st.jobs.find(j => j.id === jobId);
  if (!job) {
    showToast('Job not found', 'error');
    return;
  }
  const jobServices = st.services.filter(s => (s.jobId || s.job_id) === jobId);
  const jobPhotos = st.photos.filter(p => (p.jobId || p.job_id) === jobId);
  try {
    const dataUrl = generatePDF(job, jobServices, jobPhotos);
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `job-${String(job.customer_name).replace(/[^a-z0-9]/gi, '_')}.pdf`;
    a.click();
    showToast('PDF generated');
  } catch (err) {
    logError(err, 'PDF generation');
    showToast('PDF generation failed', 'error');
  }
}

/** @param {string} jobId */
function handleQuickPhoto(jobId) {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.capture = 'environment';
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file) return;
    renderLoading(true, 'Compressing...');
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const compressed = await compressImage(reader.result, config.IMAGE_MAX_WIDTH, config.IMAGE_QUALITY);
        store.setState(st => ({
          ...st,
          photos: [
            {
              id: id(),
              job_id: jobId,
              image_url: compressed,
              tag: 'Quick Capture',
              notes: 'Quick photo capture',
              created_at: new Date().toISOString()
            },
            ...st.photos
          ]
        }));
        showToast('Photo saved');
      } catch (err) {
        logError(err, 'quick photo');
        showToast('Photo save failed', 'error');
      } finally {
        renderLoading(false);
      }
    };
    reader.readAsDataURL(file);
  };
  input.click();
}

/** @param {string} jobId */
function handleAddToCalendar(jobId) {
  const st = store.getState();
  const job = st.jobs.find(j => j.id === jobId);
  if (!job) return;

  const subject = encodeURIComponent('Wildlife Whisperer LLC Job');
  const body = encodeURIComponent(
    `Customer: ${job.customer_name}\nPhone: ${job.phone}\nAddress: ${job.address}\nSpecies: ${job.species}\nScope: ${job.scope || ''}`
  );
  const location = encodeURIComponent(job.address);
  const start = new Date();
  const end = new Date(start.getTime() + 3600000);
  const fmt = d => d.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';

  const url = `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${subject}&details=${body}&location=${location}&dates=${fmt(start)}/${fmt(end)}`;
  window.open(url, '_blank');
  showToast('Opening Google Calendar...');
}

function updateEstimateTotal() {
  const species = $('#est-species')?.value || 'General';
  const severity = $('#est-severity')?.value || 'Medium';
  const price = parseFloat($('#est-price')?.value || '0');
  const qty = parseFloat($('#est-qty')?.value || '1');
  const taxRate = parseFloat($('#est-tax')?.value || '8.875') / 100;

  const basePrice = BASE_PRICES[species] || 500;
  const mult = SEVERITY_MULTIPLIERS[severity] || 1.35;
  const subtotal = basePrice * mult + price * qty;
  const tax = subtotal * taxRate;
  const total = subtotal + tax;

  const fmt = n => '$' + n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  const subEl = $('#est-subtotal');
  const taxEl = $('#est-tax-amount');
  const totEl = $('#est-grand-total');
  const taxLbl = $('#est-tax-label');
  if (subEl) subEl.textContent = fmt(subtotal);
  if (taxEl) taxEl.textContent = fmt(tax);
  if (totEl) totEl.textContent = fmt(total);
  if (taxLbl) taxLbl.textContent = `(${(taxRate * 100).toFixed(3)}%)`;
}

function handleCalcEstimate() {
  const species = $('#est-species')?.value;
  const severity = $('#est-severity')?.value;
  const service = $('#est-service')?.value;
  const price = parseFloat($('#est-price')?.value) || 0;
  const qty = parseInt($('#est-qty')?.value) || 1;
  const taxRate = parseFloat($('#est-tax')?.value) / 100 || config.DEFAULT_TAX_RATE;
  const issue = $('#est-issue')?.value?.trim();

  const base = calculateEstimate(species, severity);
  const svcTotal = price * qty;
  const subtotal = Math.max(base, svcTotal);
  const tax = Math.round(subtotal * taxRate * 100) / 100;
  const grandTotal = subtotal + tax;

  const output = [
    '═══════════════════════════════════════',
    `  WILDLIFE WHISPERER ESTIMATE`,
    '═══════════════════════════════════════',
    `Species:     ${species}`,
    `Severity:    ${severity}`,
    `Service:     ${service || 'N/A'}`,
    `Issue:       ${issue || 'N/A'}`,
    '',
    `Base/Calc:   ${money(subtotal)}`,
    `Tax (${(taxRate * 100).toFixed(3)}%): ${money(tax)}`,
    `───────────────────────────────────────`,
    `TOTAL:       ${money(grandTotal)}`,
    '',
    'Includes inspection/travel, exclusion',
    'complexity, and profit buffer.'
  ].join('\n');

  $('#estimate-output').textContent = output;
  return { species, issue, service, price, qty, subtotal, tax, grandTotal };
}

function handleEmailEstimate() {
  const out = $('#estimate-output')?.textContent;
  if (!out) {
    handleCalcEstimate();
  }
  const subject = encodeURIComponent('Wildlife Whisperer LLC Estimate');
  const body = encodeURIComponent($('#estimate-output')?.textContent || '');
  window.location.href = `mailto:?subject=${subject}&body=${body}`;
}

function handleConvertToJob() {
  const est = handleCalcEstimate();
  if (!est) return;
  const { species, issue, service, price, qty, subtotal } = est;

  const job = {
    id: id(),
    customer: 'TBD — From Estimate',
    phone: '',
    address: '',
    town: '',
    species,
    title: `${species} — ${issue?.slice(0, 40) || service || 'New job'}`,
    scope: issue || '',
    status: 'Active',
    priority: 'Normal',
    estimate: subtotal,
    tax_rate: config.DEFAULT_TAX_RATE,
    tax_amount: 0,
    grand_total: subtotal,
    deposit_paid: 0,
    balance_due: subtotal,
    warranty: 'Not set',
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  store.setState(st => ({
    ...st,
    jobs: [job, ...st.jobs],
    services:
      service && price > 0
        ? [
            {
              id: id(),
              job_id: job.id,
              service,
              qty,
              unit_price: price,
              total: subtotal,
              created_at: new Date().toISOString()
            },
            ...st.services
          ]
        : st.services,
    selectedJobId: job.id
  }));

  showToast('Estimate converted to job!');
  router.navigate(`/jobs/${job.id}`);
}

/**
 * Show a help dialog when GPS permission is denied.
 * Explains how to enable location permissions on Android/web.
 */
function showGPSHelp() {
  const existing = document.getElementById('gps-help-modal');
  if (existing) existing.remove();

  const modal = document.createElement('div');
  modal.id = 'gps-help-modal';
  modal.innerHTML = `
    <div class="modal-backdrop" style="display:flex;z-index:10001;">
      <div class="modal-content" style="max-width:380px;width:90%;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
          <h3 style="margin:0;font-size:18px;">📍 Location Access</h3>
          <button class="icon-btn" data-close="gps-help" style="font-size:22px;">&times;</button>
        </div>
        <div style="font-size:14px;line-height:1.6;color:var(--text);opacity:0.9;">
          <p style="margin:0 0 10px 0;"><strong>Why this happens:</strong></p>
          <p style="margin:0 0 12px 0;">Your browser or device blocked location access. Here's how to fix it:</p>
          <ol style="margin:0 0 14px 18px;padding:0;">
            <li style="margin-bottom:6px;"><strong>Android Chrome:</strong> Tap the lock icon in the address bar → Site settings → Location → Allow.</li>
            <li style="margin-bottom:6px;"><strong>App:</strong> Go to Android Settings → Apps → Wildlife Whisperer → Permissions → Location → Allow all the time.</li>
            <li style="margin-bottom:6px;"><strong>Clear denial:</strong> You may need to clear the site's data and reload the page.</li>
          </ol>
          <p style="margin:0 0 10px 0;">Or you can enter coordinates manually below.</p>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px;">
          <button class="btn primary" data-close="gps-help" style="flex:1;">Got it</button>
          <button class="btn" data-action="manual-gps" style="flex:1;">Enter Manually</button>
        </div>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
  $$('[data-close="gps-help"]').forEach(el => el.addEventListener('click', () => modal.remove()));
  $$('[data-action="manual-gps"]', modal).forEach(el =>
    el.addEventListener('click', () => {
      modal.remove();
      openManualGPS();
    })
  );
}

/**
 * Open a dialog for manual GPS coordinate entry.
 */
function openManualGPS() {
  const existing = document.getElementById('manual-gps-modal');
  if (existing) existing.remove();

  const modal = document.createElement('div');
  modal.id = 'manual-gps-modal';
  modal.innerHTML = `
    <div class="modal-backdrop" style="display:flex;z-index:10001;">
      <div class="modal-content" style="max-width:360px;width:90%;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
          <h3 style="margin:0;font-size:18px;">📍 Enter Coordinates</h3>
          <button class="icon-btn" data-close="manual-gps" style="font-size:22px;">&times;</button>
        </div>
        <div style="font-size:14px;">
          <div class="form-row">
            <label>Latitude (e.g. 40.7128)</label>
            <input type="number" id="manual-lat" step="any" placeholder="40.7128" style="width:100%;">
          </div>
          <div class="form-row">
            <label>Longitude (e.g. -74.0060)</label>
            <input type="number" id="manual-lng" step="any" placeholder="-74.0060" style="width:100%;">
          </div>
          <p style="font-size:12px;color:var(--text);opacity:0.6;margin:4px 0 12px;">
            Find coordinates on Google Maps: long-press a spot → the numbers at the bottom.
          </p>
        </div>
        <div style="display:flex;gap:8px;">
          <button class="btn primary" data-action="save-manual-gps" style="flex:1;">Save Coordinates</button>
          <button class="btn" data-close="manual-gps" style="flex:1;">Cancel</button>
        </div>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
  $$('[data-close="manual-gps"]').forEach(el => el.addEventListener('click', () => modal.remove()));
  $$('[data-action="save-manual-gps"]', modal).forEach(el =>
    el.addEventListener('click', () => {
      const lat = parseFloat($('#manual-lat')?.value);
      const lng = parseFloat($('#manual-lng')?.value);
      if (!lat || !lng || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
        showToast('Enter valid latitude (-90 to 90) and longitude (-180 to 180)', 'error');
        return;
      }
      const gps = { lat: +lat.toFixed(6), lng: +lng.toFixed(6), accuracy: 0 };
      store.setState({ pendingGPS: gps });
      modal.remove();
      showToast(`GPS saved: ${gps.lat}, ${gps.lng}`);
    })
  );
}

/**
 * Capture GPS using Capacitor Geolocation plugin (handles Android
 * runtime permissions automatically) with browser fallback.
 */
async function handleCaptureGPS() {
  showToast('Getting location...', 'success', 2000);

  // Try Capacitor Geolocation first (proper Android permission handling)
  try {
    // Check/request permission (Android 6+ requires runtime permission)
    const perm = await Geolocation.checkPermissions();
    if (perm.location !== 'granted') {
      const req = await Geolocation.requestPermissions();
      if (req.location !== 'granted') {
        showToast('Location permission denied', 'error');
        showGPSHelp();
        return;
      }
    }

    const pos = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: config.GPS_TIMEOUT,
      maximumAge: 0
    });

    const gps = {
      lat: +pos.coords.latitude.toFixed(6),
      lng: +pos.coords.longitude.toFixed(6),
      accuracy: Math.round(pos.coords.accuracy || 0)
    };
    store.setState({ pendingGPS: gps });
    showToast(`GPS: ${gps.lat}, ${gps.lng} (\u00b1${gps.accuracy}m)`);
    return;
  } catch (err) {
    // Capacitor failed — try browser fallback (for web/PWA)
    console.warn('Capacitor GPS failed, trying browser fallback:', err.message);
  }

  // Browser fallback for PWA/web use
  if (!('geolocation' in navigator)) {
    showToast('GPS not supported on this device', 'warn');
    openManualGPS();
    return;
  }

  navigator.geolocation.getCurrentPosition(
    pos => {
      const gps = {
        lat: +pos.coords.latitude.toFixed(6),
        lng: +pos.coords.longitude.toFixed(6),
        accuracy: Math.round(pos.coords.accuracy)
      };
      store.setState({ pendingGPS: gps });
      showToast(`GPS: ${gps.lat}, ${gps.lng} (\u00b1${gps.accuracy}m)`);
    },
    err => {
      if (err.code === 1) {
        showToast('Location permission denied', 'error');
        showGPSHelp();
      } else if (err.code === 2) {
        showToast('GPS signal unavailable. Try outdoors.', 'warn');
        setTimeout(openManualGPS, 800);
      } else if (err.code === 3) {
        showToast('GPS timed out', 'warn');
        openManualGPS();
      } else {
        showToast(`GPS error: ${err.message}`, 'error');
        openManualGPS();
      }
    },
    { enableHighAccuracy: true, timeout: config.GPS_TIMEOUT, maximumAge: 0 }
  );
}

function handleAISuggest() {
  const species = $('#ai-species')?.value;
  const season = $('#ai-season')?.value;
  const obs = ($('#ai-obs')?.value || '').toLowerCase();

  const tips = [`Species: ${species}`, `Season: ${season}`, `Hint: ${hint(species)}`];

  if (obs.includes('night'))
    tips.push('Night activity points toward flying squirrel, bat, raccoon, or mice depending on sound.');
  if (obs.includes('soffit') || obs.includes('fascia'))
    tips.push('Inspect soffit returns, fascia corners, roof-to-wall joints.');
  if (obs.includes('attic'))
    tips.push('Check insulation trails, nesting zones, rub marks, urine staining, secondary exits.');
  if (obs.includes('chew') || obs.includes('gnaw'))
    tips.push('Look for entry gaps >1/4 inch; squirrels and rodents require different sealing.');
  if (obs.includes('droppings') || obs.includes('guano'))
    tips.push('Fresh droppings indicate active infestation; note color/consistency for species ID.');

  const output = `— ${tips.join('\n— ')}`;
  store.setState({ aiResponse: output });
}

/** @param {HTMLTextAreaElement} el */
function handleDictate(el) {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) {
    showToast('Speech recognition not supported. Try Chrome.', 'warn');
    return;
  }

  const recog = new SR();
  recog.lang = 'en-US';
  recog.continuous = true;
  recog.interimResults = true;
  recog.onresult = e => {
    const transcript = e.results[e.results.length - 1][0].transcript;
    el.value += (el.value ? ' ' : '') + transcript;
    showToast('Dictating...', 'success', 1500);
  };
  recog.onerror = e => {
    showToast(`Dictation error: ${e.error}`, 'error');
  };
  recog.start();
  showToast('Dictation started', 'success', 2000);
}

// ── Settings Handlers ────────────────────────────

async function handleSyncNow() {
  const url = localStorage.getItem(`${STORAGE_KEY}_syncUrl`);
  if (!url) {
    showToast('Add sync endpoint first', 'warn');
    return;
  }

  store.setState({ syncStatus: 'syncing' });
  updateSyncIndicator('syncing');
  renderLoading(true, 'Syncing...');

  try {
    const st = store.getState();
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        device: 'wildlife-fieldops-v3',
        db: {
          jobs: st.jobs,
          visits: st.visits,
          repairs: st.repairs,
          photos: st.photos,
          signatures: st.signatures
        },
        queue: st.syncQueue,
        timestamp: new Date().toISOString()
      })
    });

    const text = await res.text();
    const logEl = $('#sync-log');
    if (logEl) logEl.textContent = `Sync: HTTP ${res.status}\n${text.slice(0, 500)}`;

    if (res.ok) {
      const serverData = safeJSONParse(text, null);
      if (serverData?.db) {
        store.setState(s => ({
          ...s,
          jobs: mergeArrays(s.jobs, serverData.db.jobs || [], 'id'),
          visits: mergeArrays(s.visits, serverData.db.visits || [], 'id'),
          repairs: mergeArrays(s.repairs, serverData.db.repairs || [], 'id'),
          photos: mergeArrays(s.photos, serverData.db.photos || [], 'id'),
          signatures: mergeArrays(s.signatures, serverData.db.signatures || [], 'id'),
          syncQueue: []
        }));
      }
      store.setState({ syncStatus: 'synced', lastSyncAt: new Date().toISOString() });
      updateSyncIndicator('synced');
      showToast('Sync complete');
      setTimeout(() => {
        store.setState({ syncStatus: 'idle' });
        updateSyncIndicator('idle');
      }, 3000);
    } else {
      store.setState({ syncStatus: 'error' });
      updateSyncIndicator('error');
      showToast(`Sync failed: ${res.status}`, 'error');
    }
  } catch (err) {
    store.setState({ syncStatus: 'error' });
    updateSyncIndicator('error');
    logError(err, 'sync');
    showToast(`Sync error: ${err.message}`, 'error');
  } finally {
    renderLoading(false);
  }
}

function handleExportData() {
  try {
    const st = store.getState();
    const payload = {
      jobs: st.jobs,
      customers: st.customers,
      visits: st.visits,
      repairs: st.repairs,
      photos: st.photos,
      signatures: st.signatures,
      services: st.services,
      expenses: st.expenses,
      exportedAt: new Date().toISOString(),
      version: config.APP_VERSION
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `wildlife-fieldops-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    showToast('Export downloaded');
  } catch (err) {
    logError(err, 'export');
    showToast('Export failed', 'error');
  }
}

function handleImportData() {
  try {
    const raw = $('#import-box')?.value;
    if (!raw) {
      showToast('Paste JSON first', 'warn');
      return;
    }
    const data = JSON.parse(raw);
    if (!data.jobs || !Array.isArray(data.jobs)) {
      showToast('Invalid format: missing jobs array', 'error');
      return;
    }
    store.setState(st => ({
      ...st,
      jobs: data.jobs || st.jobs,
      customers: data.customers || st.customers,
      visits: data.visits || st.visits,
      repairs: data.repairs || st.repairs,
      photos: data.photos || st.photos,
      signatures: data.signatures || st.signatures,
      services: data.services || st.services,
      expenses: data.expenses || st.expenses
    }));
    showToast('Import successful');
  } catch (err) {
    logError(err, 'import');
    showToast('Import failed: invalid JSON', 'error');
  }
}

function handleRecoverData() {
  try {
    const raw = localStorage.getItem(`${STORAGE_KEY}_bak`);
    if (!raw) {
      showToast('No snapshot found', 'warn');
      return;
    }
    const snap = JSON.parse(raw);
    if (!snap.db) {
      showToast('Invalid snapshot', 'error');
      return;
    }
    if (confirm(`Recover snapshot from ${new Date(snap.saved).toLocaleString()}?`)) {
      store.setState(st => ({
        ...st,
        jobs: snap.db.jobs || st.jobs,
        customers: snap.db.customers || st.customers,
        visits: snap.db.visits || st.visits,
        repairs: snap.db.repairs || st.repairs,
        photos: snap.db.photos || st.photos,
        signatures: snap.db.signatures || st.signatures
      }));
      showToast('Recovered from snapshot');
    }
  } catch (err) {
    logError(err, 'recover');
    showToast('Recovery failed', 'error');
  }
}

function handleWipeData() {
  if (!confirm('⚠️ Delete ALL data? This cannot be undone!')) return;
  if (!confirm('Really? All jobs, customers, photos, everything?')) return;
  store.setState(st => ({
    ...st,
    jobs: [],
    customers: [],
    visits: [],
    repairs: [],
    photos: [],
    signatures: [],
    services: [],
    expenses: [],
    syncQueue: []
  }));
  showToast('All data wiped');
}

// ═══════════════════════════════════════════════════
// Main Render Loop
// ═══════════════════════════════════════════════════

/** @type {Function|null} Current page unmount function */
let currentUnmount = null;

/**
 * Main render function. Subscribes to store changes
 * and renders the active page component.
 */
function renderApp() {
  store.subscribe(state => {
    const pageKey = state.page;
    const page = pages[pageKey];
    const appEl = $('#app');
    if (!appEl) return;

    // ── Unmount previous page ──
    if (currentUnmount) {
      try {
        currentUnmount();
      } catch (e) {
        console.error('Unmount error:', e);
      }
      currentUnmount = null;
    }

    // ── Render new page ──
    if (page) {
      appEl.innerHTML = page.render(state);

      // ── Update shell UI ──
      updateBottomNav(pageKey);
      updatePageLabel(pageKey);
      updateSyncIndicator(state.syncStatus);

      // ── Post-render initialization ──
      if (page.afterRender) {
        // Use requestAnimationFrame for DOM to settle
        requestAnimationFrame(() => {
          try {
            page.afterRender(state);
          } catch (e) {
            console.error('afterRender error:', e);
          }
        });
      }

      // ── Store unmount reference ──
      currentUnmount = page.unmount ? () => page.unmount() : null;
    } else {
      appEl.innerHTML = `<div class="page"><div class="card empty">Page "${E(pageKey)}" not found.</div></div>`;
    }

    // ── Sync drawer state ──
    const drawer = $('#drawer');
    const backdrop = $('#drawer-backdrop');
    if (drawer) {
      drawer.classList.toggle('open', state.drawerOpen);
      drawer.setAttribute('aria-hidden', String(!state.drawerOpen));
    }
    if (backdrop) backdrop.classList.toggle('open', state.drawerOpen);

    // ── Toast ──
    if (state.toast) {
      renderToast(state.toast.message, state.toast.type, state.toast.duration);
    }

    // ── Loading ──
    renderLoading(state.loading);

    // ── Search Results ──
    renderSearchResults();
  });
}

// ═══════════════════════════════════════════════════
// Online/Offline Detection
// ═══════════════════════════════════════════════════

/** @type {Function|null} Cleanup for connectivity listeners */
let connectivityCleanup = null;

function initConnectivity() {
  const onOnline = () => {
    store.setState({ isOnline: true });
    showToast('Back online', 'success', 2000);
    // Trigger sync
    handleSyncNow();
  };
  const onOffline = () => {
    store.setState({ isOnline: false });
    showToast('Offline mode', 'warn', 3000);
  };

  window.addEventListener('online', onOnline);
  window.addEventListener('offline', onOffline);

  connectivityCleanup = () => {
    window.removeEventListener('online', onOnline);
    window.removeEventListener('offline', onOffline);
  };
}

// ═══════════════════════════════════════════════════
// Keyboard Shortcuts
// ═══════════════════════════════════════════════════

/** @type {Function|null} Cleanup for keyboard listener */
let keyboardCleanup = null;

function initKeyboardShortcuts() {
  const onKey = e => {
    // Ctrl+/ or Cmd+/ → open search
    if ((e.ctrlKey || e.metaKey) && e.key === '/') {
      e.preventDefault();
      openSearch();
      return;
    }

    // Escape → close search or modal or drawer
    if (e.key === 'Escape') {
      const searchOverlay = $('#search-overlay');
      if (searchOverlay?.style.display !== 'none') {
        closeSearch();
        return;
      }
      const st = store.getState();
      if (st.drawerOpen) {
        toggleDrawer();
        return;
      }
      if (st.activeModal) {
        closeModal();
        return;
      }
    }
  };

  document.addEventListener('keydown', onKey);
  keyboardCleanup = () => document.removeEventListener('keydown', onKey);
}

// ═══════════════════════════════════════════════════
// Service Worker Registration
// ═══════════════════════════════════════════════════

/** @type {ServiceWorkerRegistration|null} */
let swRegistration = null;

async function initServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  try {
    const reg = await navigator.serviceWorker.register('/sw.js');
    swRegistration = reg;
    console.log('[SW] Registered:', reg.scope);

    reg.addEventListener('updatefound', () => {
      const newWorker = reg.installing;
      newWorker?.addEventListener('statechange', () => {
        if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
          showToast('Update available — reloading', 'success');
          setTimeout(() => location.reload(), 1500);
        }
      });
    });
  } catch (err) {
    console.warn('[SW] Registration failed:', err);
  }
}

// ═══════════════════════════════════════════════════
// Splash Screen
// ═══════════════════════════════════════════════════

function initSplashScreen() {
  const splash = document.getElementById('splash');
  if (!splash) return;

  // Fade out after a short delay
  splash.style.transition = 'opacity 0.5s ease';
  requestAnimationFrame(() => {
    setTimeout(() => {
      splash.style.opacity = '0';
      setTimeout(() => {
        splash.style.display = 'none';
      }, 500);
    }, 800);
  });
}

// ═══════════════════════════════════════════════════
// Periodic Sync
// ═══════════════════════════════════════════════════

/** @type {number|null} */
let syncTimerId = null;

function startPeriodicSync() {
  if (syncTimerId) clearInterval(syncTimerId);
  syncTimerId = setInterval(() => {
    const st = store.getState();
    if (st.isOnline && st.syncQueue.length > 0) {
      handleSyncNow();
    }
  }, config.SYNC_INTERVAL);
}

function stopPeriodicSync() {
  if (syncTimerId) {
    clearInterval(syncTimerId);
    syncTimerId = null;
  }
}

// ═══════════════════════════════════════════════════
// App Initialization
// ═══════════════════════════════════════════════════

/** @type {Function[]} Cleanup functions to run on unload */
const cleanupFns = [];

/**
 * Initialize the application.
 * Call once when DOM is ready.
 */
export function initApp() {
  const cleanupErrors = initErrorBoundary();
  cleanupFns.push(cleanupErrors);

  buildAppShell();

  const theme = store.getState().theme;
  document.body.setAttribute('data-theme', theme);

  registerRoutes();

  renderApp();

  initConnectivity();

  initKeyboardShortcuts();

  initServiceWorker();

  loadGoogleMaps();

  const stopSnap = startSnapshots(config.SNAPSHOT_INTERVAL);
  cleanupFns.push(stopSnap);

  startPeriodicSync();

  initWeatherWidget();

  initSplashScreen();

  router.resolve();

  console.log(`[FieldOps v${config.APP_VERSION}] Initialized`);
}

/**
 * Tear down the application. Call on logout / app destroy.
 */
export function destroyApp() {
  // Unmount current page
  if (currentUnmount) {
    try {
      currentUnmount();
    } catch (e) {
      /* ignore */
    }
    currentUnmount = null;
  }

  // Run all cleanup functions
  cleanupFns.forEach(fn => {
    try {
      fn();
    } catch (e) {
      /* ignore */
    }
  });
  cleanupFns.length = 0;

  // Stop timers
  stopPeriodicSync();
  stopSnapshots();

  // Remove router
  router.destroy();

  // Connectivity
  if (connectivityCleanup) {
    connectivityCleanup();
    connectivityCleanup = null;
  }

  // Keyboard
  if (keyboardCleanup) {
    keyboardCleanup();
    keyboardCleanup = null;
  }

  console.log('[FieldOps] Destroyed');
}

// ═══════════════════════════════════════════════════
// Auto-initialize on DOM ready
// ═══════════════════════════════════════════════════

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

// ═══════════════════════════════════════════════════
// Exports (for testing / external access)
// ═══════════════════════════════════════════════════

export { pages };
