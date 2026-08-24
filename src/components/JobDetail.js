/**
 * JobDetail.js — Job detail page
 * Header, customer info, financials, timer, payments, tabs (visits/repairs/photos/expenses/signature/docs)
 * Property history, Google Calendar button
 */

import {
  SPECIES_ICONS,
  STATUS_STYLES,
  VISIT_TYPES,
  REPAIR_STATUSES,
  SEVERITIES,
  PHOTO_TAGS,
  STATUS_COLORS
} from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function money(n) {
  return '$' + (n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
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

function formatDate(d) {
  if (!d) return 'N/A';
  try {
    return new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return String(d);
  }
}

function formatDuration(mins) {
  if (!mins) return '0:00';
  const h = Math.floor(mins / 60);
  const m = Math.floor(mins % 60);
  return `${h}:${m.toString().padStart(2, '0')}`;
}

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'visits', label: 'Visits' },
  { id: 'repairs', label: 'Repairs' },
  { id: 'photos', label: 'Photos' },
  { id: 'expenses', label: 'Expenses' },
  { id: 'signature', label: 'Signature' },
  { id: 'documents', label: 'Documents' }
];

export const JobDetail = {
  _listeners: [],
  _activeTab: 'overview',
  _timerInterval: null,

  render(state) {
    const jobId = state.selectedJobId;
    const jobs = state.jobs || [];
    const job = jobs.find(j => j.id === jobId);

    if (!job) {
      return /* html */ `
        <div class="empty-state">
          <div class="empty-icon" aria-hidden="true">📂</div>
          <h4>No job selected</h4>
          <p>Select a job from the list or create a new one.</p>
          <button class="action" data-action="go-jobs" style="margin-top:16px;">View Jobs</button>
        </div>
      `;
    }

    const visits = (state.visits || []).filter(v => v.job_id === job.id);
    const repairs = (state.repairs || []).filter(r => r.job_id === job.id);
    const photos = (state.photos || []).filter(p => p.job_id === job.id);
    const signatures = (state.signatures || []).filter(s => s.job_id === job.id);
    const expenses = (state.expenses || []).filter(e => e.job_id === job.id);
    const icon = SPECIES_ICONS[job.species] || '🐾';
    const sc = STATUS_STYLES[job.status] || 'active';
    const s = scoreJob(job, visits, repairs, photos, signatures);
    const est = estimateJob(job);
    const balance = (job.grand_total || est) - (job.deposit_paid || 0);

    // Timer state
    const timerRunning = !!job.timer_start;
    const timerTotal =
      (job.timer_total || 0) +
      (timerRunning ? Math.ceil((Date.now() - new Date(job.timer_start).getTime()) / 60000) : 0);

    return /* html */ `
      <!-- Job Header -->
      <div class="card stack">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:8px;">
          <div class="job-header" style="margin-bottom:0;flex:1;">
            <span class="species-icon lg" aria-hidden="true">${icon}</span>
            <div style="min-width:0;">
              <h2 style="margin-bottom:4px;font-size:20px;">${E(job.title || job.species + ' job')}</h2>
              <span class="status-pill ${sc}">${E(job.status)}</span>
              ${job.priority ? `<span class="pill ${job.priority === 'Critical' || job.priority === 'High' ? 'bad' : ''}">${E(job.priority)} priority</span>` : ''}
            </div>
          </div>
          <div style="display:flex;gap:6px;flex-shrink:0;">
            <button class="menuButton" data-action="edit-job" aria-label="Edit job" title="Edit">✏️</button>
            <button class="menuButton" data-action="delete-job" aria-label="Delete job" title="Delete">🗑️</button>
          </div>
        </div>
      </div>

      <!-- Customer Info Card -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Customer</div>
        <div style="font-weight:600;font-size:16px;margin-bottom:4px;">${E(job.customer)}</div>
        <div class="tiny" style="margin-top:0;">
          ${job.phone ? `<a href="${tel(job.phone)}">${E(job.phone)}</a>` : 'No phone'}
          ${job.email ? `&middot; <a href="mailto:${E(job.email)}">${E(job.email)}</a>` : ''}
        </div>
        <div class="tiny">${E(job.address)}${job.town ? ', ' + E(job.town) : ''}${job.state ? ', ' + E(job.state) : ''} ${job.zip || ''}</div>
        ${job.latitude ? `<div class="tiny">📍 ${job.latitude}, ${job.longitude}${job.accuracy ? ' (±' + job.accuracy + 'm)' : ''}</div>` : ''}
      </div>

      <!-- Financial Card -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Financial</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;font-size:14px;">
          <div>
            <div class="tiny">Estimate</div>
            <div style="font-weight:600;">${money(job.estimate || est)}</div>
          </div>
          <div>
            <div class="tiny">Subtotal</div>
            <div style="font-weight:600;">${money(job.subtotal)}</div>
          </div>
          <div>
            <div class="tiny">Tax (${((job.tax_rate || 0.08) * 100).toFixed(0)}%)</div>
            <div style="font-weight:600;">${money(job.tax_amount)}</div>
          </div>
          <div>
            <div class="tiny">Grand Total</div>
            <div style="font-weight:700;color:var(--green);">${money(job.grand_total || est)}</div>
          </div>
          <div>
            <div class="tiny">Deposit Paid</div>
            <div style="font-weight:600;">${money(job.deposit_paid)}</div>
          </div>
          <div>
            <div class="tiny">Balance Due</div>
            <div style="font-weight:700;color:${balance > 0 ? 'var(--red)' : 'var(--green)'};">${money(balance)}</div>
          </div>
        </div>

        <!-- Payment Input -->
        <div class="row" style="margin-top:14px;">
          <input type="number" id="paymentAmount" placeholder="Payment amount" min="0" step="0.01" aria-label="Payment amount" style="margin-top:0;">
          <button class="action green" data-action="add-payment" style="margin-top:0;white-space:nowrap;">💵 Add Payment</button>
        </div>

        ${
          (job.payments || []).length > 0
            ? `<div style="margin-top:12px;">
              <div class="tiny">Payment History</div>
              ${(job.payments || [])
                .map(
                  pm => `
                <div class="payment-item">
                  <div>
                    <div>${formatDate(pm.date)}</div>
                    <div class="tiny">${E(pm.method || 'Payment')}</div>
                  </div>
                  <div class="payment-amount">${money(pm.amount)}</div>
                </div>
              `
                )
                .join('')}
             </div>`
            : ''
        }
      </div>

      <!-- Timer Section -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Timer</div>
        <div class="timer-display ${timerRunning ? 'running' : ''}" id="timerDisplay" aria-live="polite">
          ${formatDuration(timerTotal)}
        </div>
        <div class="row">
          ${
            timerRunning
              ? `<button class="action red" data-action="stop-timer" style="margin-top:0;">⏹️ Stop Timer</button>`
              : `<button class="action green" data-action="start-timer" style="margin-top:0;">▶️ Start Timer</button>`
          }
          <button class="action dark" data-action="reset-timer" style="margin-top:0;">🔄 Reset</button>
        </div>
        ${job.timer_total ? `<div class="tiny">Total logged time: ${formatDuration(job.timer_total)}</div>` : ''}
      </div>

      <!-- Tabs -->
      <div class="tabs" role="tablist" aria-label="Job sections">
        ${TABS.map(
          t => `
          <button
            class="tab-btn ${this._activeTab === t.id ? 'active' : ''}"
            role="tab"
            data-tab="${t.id}"
            aria-selected="${this._activeTab === t.id ? 'true' : 'false'}"
            aria-controls="tabpanel-${t.id}"
            id="tab-${t.id}"
          >${t.label}</button>
        `
        ).join('')}
      </div>

      <!-- Tab Panels -->
      <div id="tabpanel-overview" class="tab-panel ${this._activeTab === 'overview' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-overview">
        ${this._renderOverview(job, visits, repairs, photos, signatures, state)}
      </div>
      <div id="tabpanel-visits" class="tab-panel ${this._activeTab === 'visits' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-visits">
        ${this._renderVisits(job, visits)}
      </div>
      <div id="tabpanel-repairs" class="tab-panel ${this._activeTab === 'repairs' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-repairs">
        ${this._renderRepairs(job, repairs)}
      </div>
      <div id="tabpanel-photos" class="tab-panel ${this._activeTab === 'photos' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-photos">
        ${this._renderPhotos(job, photos)}
      </div>
      <div id="tabpanel-expenses" class="tab-panel ${this._activeTab === 'expenses' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-expenses">
        ${this._renderExpenses(job, expenses)}
      </div>
      <div id="tabpanel-signature" class="tab-panel ${this._activeTab === 'signature' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-signature">
        ${this._renderSignature(job, signatures)}
      </div>
      <div id="tabpanel-documents" class="tab-panel ${this._activeTab === 'documents' ? 'active' : ''}" role="tabpanel" aria-labelledby="tab-documents">
        ${this._renderDocuments(job)}
      </div>

      <!-- Property History -->
      <div class="section-title">Property History</div>
      <div class="card">
        ${this._renderPropertyHistory(job, jobs)}
      </div>

      <!-- Calendar Button -->
      ${
        job.latitude && job.longitude
          ? `<button class="action dark" data-action="add-calendar" style="margin-bottom:12px;">📅 Add to Google Calendar</button>`
          : ''
      }
    `;
  },

  afterRender(state) {
    const jobId = state.selectedJobId;
    const job = (state.jobs || []).find(j => j.id === jobId);

    // Tab switching
    document.querySelectorAll('.tab-btn[data-tab]').forEach(btn => {
      const handler = () => {
        this._activeTab = btn.dataset.tab;
        state.rerender?.();
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Edit / Delete actions
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = e => {
        const action = btn.dataset.action;
        if (action === 'edit-job' && job) state.navigate?.(`jobs/${job.id}/edit`);
        if (action === 'delete-job' && job) {
          if (confirm(`Delete job "${job.title || job.species}" for ${job.customer}?`)) {
            state.onDeleteJob?.(job.id);
          }
        }
        if (action === 'go-jobs') state.navigate?.('jobs');
        if (action === 'add-payment' && job) {
          const amount = parseFloat(document.getElementById('paymentAmount')?.value || 0);
          if (amount > 0) state.onAddPayment?.(job.id, amount);
        }
        if (action === 'start-timer' && job) state.onStartTimer?.(job.id);
        if (action === 'stop-timer' && job) state.onStopTimer?.(job.id);
        if (action === 'reset-timer' && job) {
          if (confirm('Reset timer?')) state.onResetTimer?.(job.id);
        }
        if (action === 'add-visit' && job) {
          const type = document.getElementById('visitType')?.value;
          const note = document.getElementById('visitNote')?.value;
          const animals = parseInt(document.getElementById('visitAnimals')?.value || 0);
          if (type) state.onAddVisit?.(job.id, { type, note, animals });
        }
        if (action === 'add-repair' && job) {
          const location = document.getElementById('repairLoc')?.value;
          const status = document.getElementById('repairStatus')?.value;
          const severity = document.getElementById('repairSev')?.value;
          const materials = document.getElementById('repairMat')?.value;
          const note = document.getElementById('repairNote')?.value;
          if (location) state.onAddRepair?.(job.id, { location, status, severity, materials, note });
        }
        if (action === 'add-expense' && job) {
          const desc = document.getElementById('expenseDesc')?.value;
          const amount = parseFloat(document.getElementById('expenseAmount')?.value || 0);
          if (desc && amount > 0) state.onAddExpense?.(job.id, desc, amount);
        }
        if (action === 'clear-sig' && job) state.onClearSig?.();
        if (action === 'save-sig' && job) state.onSaveSig?.(job.id);
        if (action === 'add-calendar' && job) state.onAddCalendar?.(job);
        if (action === 'generate-pdf' && job) state.onGeneratePDF?.(job);
        if (action === 'generate-contract' && job) state.onGenerateContract?.(job);
        if (action === 'quick-photo' && job) state.onQuickPhoto?.(job.id);
        if (action === 'upload-photo' && job) {
          const input = document.getElementById('photoUpload');
          if (input?.files?.[0]) {
            const tag = document.getElementById('photoTag')?.value || 'Before';
            const notes = document.getElementById('photoNotes')?.value || '';
            state.onUploadPhoto?.(job.id, input.files[0], tag, notes);
          }
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Photo grid click to view
    const photoGrid = document.getElementById('photoGrid');
    if (photoGrid) {
      const handler = e => {
        const item = e.target.closest('.photo-grid-item[data-photo]');
        if (!item) return;
        const photoData = item.dataset.photo;
        state.onViewPhoto?.(photoData);
      };
      photoGrid.addEventListener('click', handler);
      this._listeners.push({ el: photoGrid, type: 'click', fn: handler });
    }

    // Signature canvas
    if (this._activeTab === 'signature') {
      state.onInitSigCanvas?.();
    }

    // Map for overview tab
    if (this._activeTab === 'overview' && job?.latitude && state.onInitDetailMap) {
      setTimeout(() => state.onInitDetailMap(job.latitude, job.longitude, job.customer), 100);
    }

    // Timer display update
    if (job?.timer_start) {
      this._timerInterval = setInterval(() => {
        const display = document.getElementById('timerDisplay');
        if (display) {
          const elapsed =
            (job.timer_total || 0) + Math.ceil((Date.now() - new Date(job.timer_start).getTime()) / 60000);
          display.textContent = formatDuration(elapsed);
        }
      }, 60000);
    }
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._activeTab = 'overview';
    if (this._timerInterval) {
      clearInterval(this._timerInterval);
      this._timerInterval = null;
    }
  },

  // ─── Tab renderers ───
  _renderOverview(job, visits, repairs, photos, signatures, state) {
    const s = scoreJob(job, visits, repairs, photos, signatures);
    const est = estimateJob(job);

    return /* html */ `
      <div class="card">
        <div class="section-title" style="margin-top:0;">Scope</div>
        <div style="font-size:14px;line-height:1.6;">${E(job.scope) || '<span class="tiny">No scope defined.</span>'}</div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Notes</div>
        <div style="font-size:14px;line-height:1.6;white-space:pre-wrap;">${E(job.notes) || '<span class="tiny">No notes.</span>'}</div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Warranty</div>
        <div style="font-size:14px;">${E(job.warranty) || 'Not set'}</div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Job Score</div>
        <div class="prog" role="progressbar" aria-label="Job completion score" aria-valuenow="${s}" aria-valuemin="0" aria-valuemax="100">
          <div class="bar" style="width:${s}%"></div>
        </div>
        <div class="tiny">${s}% complete &middot; Est ${money(est)}</div>
        <div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap;">
          ${visits.length ? `<span class="pill">✅ Visits</span>` : `<span class="pill muted">⬜ Visits</span>`}
          ${photos.length ? `<span class="pill">✅ Photos</span>` : `<span class="pill muted">⬜ Photos</span>`}
          ${repairs.length ? `<span class="pill">✅ Repairs</span>` : `<span class="pill muted">⬜ Repairs</span>`}
          ${signatures.length ? `<span class="pill">✅ Signature</span>` : `<span class="pill muted">⬜ Signature</span>`}
        </div>
      </div>

      ${
        job.latitude
          ? `<div id="detailMap" class="map-container" role="img" aria-label="Job location map"></div>`
          : `<div class="card tiny">No GPS coordinates for this job.</div>`
      }

      ${
        state.weather && job.latitude
          ? `<div class="card">
            <div class="section-title" style="margin-top:0;">Weather at Job Site</div>
            <div class="weather-card">
              <img src="${E(state.weather.icon)}" alt="${E(state.weather.description)}" style="width:48px;height:48px;">
              <div>
                <div style="font-weight:700;font-size:18px;">${state.weather.temp}°F</div>
                <div class="tiny">${E(state.weather.condition)} &middot; ${E(state.weather.description)}</div>
              </div>
            </div>
           </div>`
          : ''
      }
    `;
  },

  _renderVisits(job, visits) {
    return /* html */ `
      <!-- Add Visit Form -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Add Visit</div>
        <label for="visitType">Visit Type</label>
        <select id="visitType">
          ${VISIT_TYPES.map(t => `<option value="${E(t)}">${E(t)}</option>`).join('')}
        </select>
        <label for="visitNote">Notes</label>
        <textarea id="visitNote" rows="3" placeholder="Visit notes..."></textarea>
        <label for="visitAnimals">Animals Captured</label>
        <input type="number" id="visitAnimals" min="0" value="0">
        <button class="action" data-action="add-visit">Add Visit</button>
      </div>

      <!-- Visit History -->
      <div class="section-title">Visit History (${visits.length})</div>
      ${
        visits.length
          ? visits
              .slice()
              .reverse()
              .map(
                v => `
          <div class="card">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <b>${E(v.type)}</b>
              <span class="tiny">${formatDate(v.date || v.created_at)}</span>
            </div>
            <div class="tiny">Animals: ${v.animals || 0}</div>
            ${v.note ? `<div style="margin-top:6px;font-size:14px;">${E(v.note)}</div>` : ''}
          </div>
        `
              )
              .join('')
          : `<div class="card tiny">No visits recorded yet.</div>`
      }
    `;
  },

  _renderRepairs(job, repairs) {
    return /* html */ `
      <!-- Add Repair Form -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Add Repair</div>
        <label for="repairLoc">Location</label>
        <input type="text" id="repairLoc" placeholder="e.g., Soffit return, east side">
        <div class="form-row">
          <div>
            <label for="repairStatus">Status</label>
            <select id="repairStatus">
              ${REPAIR_STATUSES.map(s => `<option value="${E(s)}">${E(s)}</option>`).join('')}
            </select>
          </div>
          <div>
            <label for="repairSev">Severity</label>
            <select id="repairSev">
              ${SEVERITIES.map(s => `<option value="${E(s)}">${E(s)}</option>`).join('')}
            </select>
          </div>
        </div>
        <label for="repairMat">Materials</label>
        <input type="text" id="repairMat" placeholder="Materials used">
        <label for="repairNote">Notes</label>
        <textarea id="repairNote" rows="2" placeholder="Repair notes..."></textarea>
        <button class="action" data-action="add-repair">Add Repair</button>
      </div>

      <!-- Repair List -->
      <div class="section-title">Repairs (${repairs.length})</div>
      ${
        repairs.length
          ? repairs
              .slice()
              .reverse()
              .map(
                r => `
          <div class="card">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <b>${E(r.location)}</b>
              <div>
                <span class="pill ${r.status === 'Open' ? 'bad' : r.status === 'Sealed' ? 'info' : 'warn'}">${E(r.status)}</span>
                <span class="pill">${E(r.severity)}</span>
              </div>
            </div>
            ${r.materials ? `<div class="tiny">Materials: ${E(r.materials)}</div>` : ''}
            ${r.note ? `<div style="margin-top:6px;font-size:14px;">${E(r.note)}</div>` : ''}
          </div>
        `
              )
              .join('')
          : `<div class="card tiny">No repairs recorded yet.</div>`
      }
    `;
  },

  _renderPhotos(job, photos) {
    return /* html */ `
      <!-- Upload -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Upload Photo</div>
        <input type="file" id="photoUpload" accept="image/*" capture="environment" aria-label="Choose photo">
        <label for="photoTag">Tag</label>
        <select id="photoTag">
          ${PHOTO_TAGS.filter(t => t !== 'Customer signature')
            .map(t => `<option value="${E(t)}">${E(t)}</option>`)
            .join('')}
        </select>
        <label for="photoNotes">Notes</label>
        <textarea id="photoNotes" rows="2" placeholder="Photo notes..."></textarea>
        <button class="action" data-action="upload-photo">📤 Upload Photo</button>
        <button class="action dark" data-action="quick-photo" style="margin-top:8px;">📷 Quick Photo</button>
      </div>

      <!-- Photo Gallery -->
      <div class="section-title">Photos (${photos.length})</div>
      ${
        photos.length
          ? `<div class="photo-grid" id="photoGrid">
            ${photos
              .map(
                p => `
              <div class="photo-grid-item" data-photo="${E(p.image_url || p.data || '')}" role="button" tabindex="0" aria-label="View photo: ${E(p.tag || 'photo')}">
                <img src="${E(p.image_url || p.data || '')}" alt="${E(p.tag || 'Job photo')}" loading="lazy">
                <span class="photo-tag">${E(p.tag || '')}</span>
              </div>
            `
              )
              .join('')}
           </div>`
          : `<div class="card tiny">No photos yet.</div>`
      }
    `;
  },

  _renderExpenses(job, expenses) {
    const totalExpenses = expenses.reduce((a, e) => a + (e.amount || 0), 0);

    return /* html */ `
      <!-- Add Expense -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Add Expense</div>
        <label for="expenseDesc">Description</label>
        <input type="text" id="expenseDesc" placeholder="e.g., Traps, bait, materials">
        <label for="expenseAmount">Amount ($)</label>
        <input type="number" id="expenseAmount" min="0" step="0.01" placeholder="0.00">
        <button class="action" data-action="add-expense">Add Expense</button>
      </div>

      <!-- Expense List -->
      <div class="section-title">Expenses (${expenses.length}) &middot; Total: ${money(totalExpenses)}</div>
      ${
        expenses.length
          ? expenses
              .slice()
              .reverse()
              .map(
                e => `
          <div class="expense-item">
            <div class="expense-desc">
              <div>${E(e.description)}</div>
              <div class="tiny">${formatDate(e.date || e.created_at)}</div>
            </div>
            <div class="expense-amount">${money(e.amount)}</div>
          </div>
        `
              )
              .join('')
          : `<div class="card tiny">No expenses recorded yet.</div>`
      }
    `;
  },

  _renderSignature(job, signatures) {
    return /* html */ `
      <div class="card">
        <div class="section-title" style="margin-top:0;">Signature Capture</div>
        <div class="sig-canvas-wrap">
          <canvas id="sigCanvas" aria-label="Signature pad, draw with finger or mouse"></canvas>
        </div>
        <div class="row">
          <button class="action dark" data-action="clear-sig" style="margin-top:0;">🧹 Clear</button>
          <button class="action" data-action="save-sig" style="margin-top:0;">💾 Save Signature</button>
        </div>
      </div>

      ${
        signatures.length
          ? `<div class="section-title">Saved Signatures (${signatures.length})</div>
           ${signatures
             .map(
               s => `
             <div class="card">
               <img src="${E(s.data || s.image_url || '')}" alt="Customer signature" class="sig-preview">
               <div class="tiny">${E(s.name || 'Customer')} &middot; ${formatDate(s.date || s.created_at)}</div>
             </div>
           `
             )
             .join('')}`
          : ''
      }
    `;
  },

  _renderDocuments(job) {
    return /* html */ `
      <div class="card">
        <div class="section-title" style="margin-top:0;">Generate Documents</div>
        <div class="doc-buttons">
          <button data-action="generate-pdf">📄 Job PDF</button>
          <button data-action="generate-contract">📝 Contract</button>
        </div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Job Summary</div>
        <div style="font-size:14px;line-height:1.8;">
          <div><b>Customer:</b> ${E(job.customer)}</div>
          <div><b>Address:</b> ${E(job.address)}${job.town ? ', ' + E(job.town) : ''}</div>
          <div><b>Phone:</b> ${E(job.phone)}</div>
          <div><b>Species:</b> ${E(job.species)}</div>
          <div><b>Status:</b> ${E(job.status)}</div>
          <div><b>Scope:</b> ${E(job.scope)}</div>
          <div><b>Warranty:</b> ${E(job.warranty)}</div>
        </div>
      </div>
    `;
  },

  _renderPropertyHistory(job, allJobs) {
    const related = allJobs.filter(
      j => j.id !== job.id && (j.address?.toLowerCase() === job.address?.toLowerCase() || j.phone === job.phone)
    );

    if (!related.length) {
      return `<div class="tiny">No property history found.</div>`;
    }

    return related
      .sort((a, b) => new Date(b.created_at || b.created || 0) - new Date(a.created_at || a.created || 0))
      .map(
        j => `
      <div style="padding:8px 0;border-bottom:1px solid var(--border);font-size:14px;">
        <div style="display:flex;justify-content:space-between;">
          <b>${E(j.species)} — ${E(j.title || j.species + ' job')}</b>
          <span class="status-pill ${STATUS_STYLES[j.status] || 'active'}">${E(j.status)}</span>
        </div>
        <div class="tiny">${formatDate(j.created_at || j.created)} &middot; ${E(j.customer)}</div>
      </div>
    `
      )
      .join('');
  }
};
