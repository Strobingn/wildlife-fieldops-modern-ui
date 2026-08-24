/**
 * Dashboard.js — Dashboard page component
 * Hero greeting, animated stat cards, quick actions, recent jobs, weather, alerts
 */

import { SPECIES_ICONS, STATUS_STYLES, APP_VERSION } from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function money(n) {
  return '$' + Math.round(n || 0).toLocaleString();
}

function estimateJob(j) {
  const base =
    {
      Bat: 950,
      Raccoon: 650,
      'Grey Squirrel': 550,
      'Red Squirrel': 575,
      'Flying Squirrel': 750,
      Skunk: 450,
      Groundhog: 450,
      'Carpenter Bee': 350
    }[j.species] || 500;
  return Math.round(base * 1.35);
}

function scoreJob(j, visits, repairs, photos, signatures) {
  const v = visits.some(x => x.job_id === j.id);
  const p = photos.some(x => x.job_id === j.id);
  const rr = repairs.filter(x => x.job_id === j.id);
  const sig = signatures.some(x => x.job_id === j.id);
  return Math.min(100, (v ? 25 : 0) + (p ? 25 : 0) + (rr.length ? 25 : 0) + (sig ? 25 : 0));
}

function tel(p) {
  return 'tel:' + String(p || '').replace(/[^\d+]/g, '');
}

export const Dashboard = {
  _listeners: [],
  _countUpDone: false,

  render(state) {
    const jobs = state.jobs || [];
    const visits = state.visits || [];
    const repairs = state.repairs || [];
    const photos = state.photos || [];
    const signatures = state.signatures || [];
    const activeJobs = jobs.filter(j => j.status !== 'Closed' && j.status !== 'Cancelled');
    const totalRevenue = activeJobs.reduce((a, j) => a + estimateJob(j), 0);
    const pendingSync = jobs.filter(j => j._pending).length;
    const photoCount = photos.length;
    const greeting = this._getGreeting();
    const userName = state.currentUser?.name || 'Tech';

    // Weather from first GPS job if available
    const weatherWidget = state.weather ? this._renderWeather(state.weather) : '';

    // Alerts
    const alerts = this._renderAlerts(state, activeJobs);

    return /* html */ `
      <!-- Hero Section -->
      <div class="hero reveal">
        <h1>${greeting}, ${E(userName)}! 👋</h1>
        <p>${activeJobs.length} active jobs &middot; ${jobs.length} total &middot; v${APP_VERSION}</p>
      </div>

      <!-- Alerts -->
      ${alerts}

      <!-- Stat Cards -->
      <div class="grid reveal delay-1">
        <div class="card stat-card" data-count="${activeJobs.length}" data-prefix="" data-suffix="">
          <div class="stat-icon" aria-hidden="true">🦝</div>
          <div class="stat-label">Active Jobs</div>
          <div class="stat" data-target="${activeJobs.length}">${activeJobs.length}</div>
        </div>
        <div class="card stat-card" data-count="${totalRevenue}" data-prefix="$" data-suffix="">
          <div class="stat-icon" aria-hidden="true">💰</div>
          <div class="stat-label">Revenue</div>
          <div class="stat" data-target="${totalRevenue}">${money(totalRevenue)}</div>
        </div>
        <div class="card stat-card" data-count="${pendingSync}" data-prefix="" data-suffix="">
          <div class="stat-icon" aria-hidden="true">☁️</div>
          <div class="stat-label">Pending Sync</div>
          <div class="stat" data-target="${pendingSync}">${pendingSync}</div>
        </div>
        <div class="card stat-card" data-count="${photoCount}" data-prefix="" data-suffix="">
          <div class="stat-icon" aria-hidden="true">📸</div>
          <div class="stat-label">Photos</div>
          <div class="stat" data-target="${photoCount}">${photoCount}</div>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="section-title reveal">Quick Actions</div>
      <div class="quick-actions reveal delay-1">
        <button class="quick-action" data-action="new-job" aria-label="Create new job">
          🆕
          <span>New Job</span>
        </button>
        <button class="quick-action" data-action="estimate" aria-label="Open estimate calculator">
          💵
          <span>Estimate</span>
        </button>
        <button class="quick-action" data-action="customers" aria-label="View customers">
          👥
          <span>Techs</span>
        </button>
        <button class="quick-action" data-action="metrics" aria-label="View business metrics">
          📊
          <span>Metrics</span>
        </button>
      </div>

      ${weatherWidget}

      <!-- Recent Jobs -->
      <div class="section-title reveal">Recent Jobs</div>
      <div id="recentJobs" class="reveal delay-2">
        ${
          jobs.slice(0, 5).length
            ? jobs
                .slice(0, 5)
                .map(j => this._jobCard(j, visits, repairs, photos, signatures))
                .join('')
            : `<div class="empty-state">
              <div class="empty-icon" aria-hidden="true">🦝</div>
              <h4>No jobs yet</h4>
              <p>Tap the + button or "New Job" to create your first job.</p>
             </div>`
        }
      </div>

      ${
        jobs.length > 5
          ? `<div class="text-center" style="margin-top:12px;">
            <button class="action dark" data-action="view-all-jobs" style="margin-top:0;width:auto;padding:10px 20px;">View all ${jobs.length} jobs</button>
           </div>`
          : ''
      }
    `;
  },

  afterRender(state) {
    // Count-up animation for stat numbers
    if (!this._countUpDone) {
      this._animateCountUp();
      this._countUpDone = true;
    }

    // Quick action handlers
    document.querySelectorAll('.quick-action[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;
        if (action === 'new-job' && state.navigate) state.navigate('jobs/new');
        if (action === 'estimate' && state.navigate) state.navigate('estimate');
        if (action === 'customers' && state.navigate) state.navigate('customers');
        if (action === 'metrics' && state.navigate) state.navigate('metrics');
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // View all jobs
    const viewAllBtn = document.querySelector('[data-action="view-all-jobs"]');
    if (viewAllBtn) {
      const handler = () => state.navigate?.('jobs');
      viewAllBtn.addEventListener('click', handler);
      this._listeners.push({ el: viewAllBtn, type: 'click', fn: handler });
    }

    // Job card click handlers
    document.querySelectorAll('.job-card[data-job-id]').forEach(card => {
      const handler = e => {
        // Don't navigate if clicking action buttons
        if (e.target.closest('.job-actions')) return;
        const jobId = card.dataset.jobId;
        if (state.navigate) state.navigate(`jobs/${jobId}`);
      };
      card.addEventListener('click', handler);
      this._listeners.push({ el: card, type: 'click', fn: handler });
    });

    // Job action buttons (event delegation)
    const recentJobs = document.getElementById('recentJobs');
    if (recentJobs) {
      const actionHandler = e => {
        const btn = e.target.closest('[data-navigate]');
        if (!btn) return;
        e.stopPropagation();
        const target = btn.dataset.navigate;
        const jobId = btn.dataset.jobId;
        if (target === 'job' && jobId) state.navigate?.(`jobs/${jobId}`);
        if (target === 'navigate') {
          const lat = btn.dataset.lat;
          const lng = btn.dataset.lng;
          const addr = btn.dataset.address;
          if (lat && lng) {
            window.open(
              `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`,
              '_blank'
            );
          } else if (addr) {
            window.open(
              `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(addr)}&travelmode=driving`,
              '_blank'
            );
          }
        }
      };
      recentJobs.addEventListener('click', actionHandler);
      this._listeners.push({ el: recentJobs, type: 'click', fn: actionHandler });
    }
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._countUpDone = false;
  },

  // ─── Job Card (compact for dashboard) ───
  _jobCard(j, visits, repairs, photos, signatures) {
    const icon = SPECIES_ICONS[j.species] || '🐾';
    const sc = STATUS_STYLES[j.status] || 'active';
    const v = visits.filter(x => x.job_id === j.id).length;
    const r = repairs.filter(x => x.job_id === j.id).length;
    const p = photos.filter(x => x.job_id === j.id).length;
    const s = scoreJob(j, visits, repairs, photos, signatures);
    const est = estimateJob(j);

    return /* html */ `
      <div class="card stack job-card" data-job-id="${j.id}">
        <div class="job-header">
          <span class="species-icon" aria-hidden="true">${icon}</span>
          <h3>${E(j.title || j.species + ' job')}</h3>
          <span class="status-pill ${sc}">${E(j.status)}</span>
        </div>
        <div class="tiny">${E(j.customer)} &middot; <a href="${tel(j.phone)}">${E(j.phone)}</a></div>
        <div class="tiny">${E(j.address)}${j.town ? ', ' + E(j.town) : ''}</div>
        <div style="margin-top:6px;">
          <span class="pill">${E(j.species)}</span>
          <span class="pill">${v} visits</span>
          <span class="pill">${r} repairs</span>
          <span class="pill">${p} photos</span>
          ${j.latitude ? '<span class="pill info">📍 GPS</span>' : ''}
        </div>
        <div class="prog" role="progressbar" aria-label="Job completion score" aria-valuenow="${s}" aria-valuemin="0" aria-valuemax="100">
          <div class="bar" style="width:${s}%"></div>
        </div>
        <div class="tiny">Score ${s}% &middot; Est ${money(est)}</div>
        <div class="job-actions">
          <button class="primary" data-navigate="job" data-job-id="${j.id}">Open</button>
          <button class="secondary" data-navigate="navigate" data-lat="${j.latitude || ''}" data-lng="${j.longitude || ''}" data-address="${E(j.address)}">Navigate</button>
        </div>
      </div>
    `;
  },

  _renderWeather(weather) {
    return /* html */ `
      <div class="card reveal delay-2">
        <div class="section-title" style="margin-top:0;">Weather</div>
        <div class="weather-card">
          <img src="${E(weather.icon)}" alt="${E(weather.description)}" style="width:48px;height:48px;">
          <div>
            <div style="font-weight:700;font-size:18px;">${weather.temp}°F</div>
            <div class="tiny">${E(weather.condition)} &middot; ${E(weather.description)}</div>
          </div>
        </div>
      </div>
    `;
  },

  _renderAlerts(state, activeJobs) {
    const alerts = [];

    // Low completion score alert
    const lowScoreJobs = activeJobs.filter(j => {
      const v = (state.visits || []).some(x => x.job_id === j.id);
      const p = (state.photos || []).some(x => x.job_id === j.id);
      return !v || !p;
    });
    if (lowScoreJobs.length > 0) {
      alerts.push(/* html */ `
        <div class="alert warn reveal" role="alert">
          <span>⚠️ ${lowScoreJobs.length} job${lowScoreJobs.length > 1 ? 's' : ''} missing visits or photos</span>
          <button class="alert-dismiss" aria-label="Dismiss alert">&times;</button>
        </div>
      `);
    }

    // Offline alert
    if (!state.isOnline) {
      alerts.push(/* html */ `
        <div class="alert info reveal" role="alert">
          <span>📴 You are offline. Changes will sync when you reconnect.</span>
          <button class="alert-dismiss" aria-label="Dismiss alert">&times;</button>
        </div>
      `);
    }

    // Sync error
    if (state.syncStatus === 'error') {
      alerts.push(/* html */ `
        <div class="alert reveal" role="alert">
          <span>❌ Sync failed. Check your connection and try again.</span>
          <button class="alert-dismiss" aria-label="Dismiss alert">&times;</button>
        </div>
      `);
    }

    if (!alerts.length) return '';

    return /* html */ `
      <div class="alerts-container" role="region" aria-label="Alerts">
        ${alerts.join('')}
      </div>
    `;
  },

  _getGreeting() {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  },

  _animateCountUp() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const counters = document.querySelectorAll('.stat[data-target]');
    counters.forEach(el => {
      const target = parseInt(el.dataset.target, 10) || 0;
      if (target === 0) return;
      const duration = 800;
      const start = performance.now();
      const step = now => {
        const progress = Math.min((now - start) / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.floor(target * eased);
        el.textContent = current.toLocaleString();
        if (progress < 1) requestAnimationFrame(step);
        else el.textContent = target.toLocaleString();
      };
      requestAnimationFrame(step);
    });
  }
};
