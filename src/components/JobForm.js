/**
 * JobForm.js — Create/Edit job form
 * Customer select, all job fields, GPS capture, templates, estimate calculator, validation
 */

import {
  SPECIES,
  STATUSES,
  PRIORITIES,
  SPECIES_ICONS,
  ESTIMATE_TEMPLATES,
  BASE_PRICES,
  SEVERITY_MULTIPLIERS,
  SERVICES,
  DEFAULT_TAX_RATE
} from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function money(n) {
  return '$' + Math.round(n || 0).toLocaleString();
}

function id() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

export const JobForm = {
  _listeners: [],
  _isDirty: false,
  _gps: null,
  _lineItems: [],
  _editId: null,
  _errors: {},

  render(state) {
    const customers = state.customers || [];
    const isEdit = state.page === 'jobs/edit' || state.selectedJobId;
    const existingJob = isEdit ? (state.jobs || []).find(j => j.id === state.selectedJobId) : null;
    this._editId = existingJob?.id || null;

    // Pre-fill from existing job or defaults
    const j = existingJob || {};
    const customerVal = j.customer_id || j.customer || '';
    const selSpecies = j.species || SPECIES[0];
    const selStatus = j.status || 'Active';
    const selPriority = j.priority || 'Normal';
    const addressVal = j.address || '';
    const townVal = j.town || '';
    const stateVal = j.state || '';
    const zipVal = j.zip || '';
    const phoneVal = j.phone || '';
    const emailVal = j.email || '';
    const scopeVal = j.scope || '';
    const notesVal = j.notes || '';
    const titleVal = j.title || '';
    const estimateVal = j.estimate || '';

    // Line items from services if editing
    if (existingJob && this._lineItems.length === 0 && existingJob.services?.length) {
      this._lineItems = existingJob.services.map(s => ({
        id: s.id || id(),
        service: s.service,
        qty: s.qty || 1,
        price: s.unit_price || s.price || 0,
        total: (s.qty || 1) * (s.unit_price || s.price || 0)
      }));
    }

    // Calculate totals
    const subtotal =
      this._lineItems.reduce((a, item) => a + item.total, 0) || (estimateVal ? parseFloat(estimateVal) : 0);
    const taxRate = state.settings?.taxRate || DEFAULT_TAX_RATE;
    const tax = subtotal * taxRate;
    const grandTotal = subtotal + tax;

    return /* html */ `
      <div class="card stack">
        <h2>${isEdit ? '✏️ Edit Job' : '🆕 New Job'}</h2>
        <p class="tiny">${isEdit ? 'Update job details below.' : 'Fill in the details to create a new job.'}</p>
      </div>

      <!-- Customer Selection -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Customer</div>
        <label for="jobCustomer">Select Customer</label>
        <select id="jobCustomer">
          <option value="">-- New Customer --</option>
          ${customers.map(c => `<option value="${E(c.id)}" ${customerVal === c.id || customerVal === c.name ? 'selected' : ''}>${E(c.name)} — ${E(c.phone || 'no phone')}</option>`).join('')}
        </select>
        <div id="newCustomerFields" style="display:${!customerVal ? 'block' : 'none'};">
          <label for="jobCustName">Customer Name *</label>
          <input type="text" id="jobCustName" placeholder="Full name" value="${!customerVal ? '' : ''}">
          ${this._errors.customer ? `<div class="form-error">${E(this._errors.customer)}</div>` : ''}
          <label for="jobCustPhone">Phone</label>
          <input type="tel" id="jobCustPhone" placeholder="(555) 123-4567" value="${phoneVal}">
          <label for="jobCustEmail">Email</label>
          <input type="email" id="jobCustEmail" placeholder="customer@email.com" value="${emailVal}">
        </div>
      </div>

      <!-- Job Details -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Job Details</div>

        <label for="jobTitle">Job Title</label>
        <input type="text" id="jobTitle" placeholder="e.g., Raccoon in attic" value="${E(titleVal)}">

        <div class="form-row">
          <div>
            <label for="jobSpecies">Species *</label>
            <select id="jobSpecies">
              ${SPECIES.map(s => `<option value="${E(s)}" ${selSpecies === s ? 'selected' : ''}>${SPECIES_ICONS[s] || '🐾'} ${E(s)}</option>`).join('')}
            </select>
          </div>
          <div>
            <label for="jobStatus">Status</label>
            <select id="jobStatus">
              ${STATUSES.map(s => `<option value="${E(s)}" ${selStatus === s ? 'selected' : ''}>${E(s)}</option>`).join('')}
            </select>
          </div>
        </div>

        <label for="jobPriority">Priority</label>
        <select id="jobPriority">
          ${PRIORITIES.map(p => `<option value="${E(p)}" ${selPriority === p ? 'selected' : ''}>${E(p)}</option>`).join('')}
        </select>

        ${this._errors.species ? `<div class="form-error">${E(this._errors.species)}</div>` : ''}
      </div>

      <!-- Address -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Location</div>
        <label for="jobAddress">Address *</label>
        <input type="text" id="jobAddress" placeholder="123 Main St" value="${E(addressVal)}">
        ${this._errors.address ? `<div class="form-error">${E(this._errors.address)}</div>` : ''}

        <div class="form-row">
          <div>
            <label for="jobTown">Town</label>
            <input type="text" id="jobTown" placeholder="Town name" value="${E(townVal)}">
          </div>
          <div>
            <label for="jobState">State</label>
            <input type="text" id="jobState" placeholder="NY" value="${E(stateVal)}">
          </div>
        </div>

        <label for="jobZip">ZIP</label>
        <input type="text" id="jobZip" placeholder="12345" value="${E(zipVal)}">

        <!-- GPS Capture -->
        <div style="margin-top:12px;display:flex;gap:8px;align-items:center;">
          <button class="action dark" data-action="capture-gps" type="button" style="margin-top:0;width:auto;padding:10px 16px;">
            📍 ${this._gps ? 'Update GPS' : 'Capture GPS'}
          </button>
          ${
            this._gps
              ? `<span class="tiny" style="margin-top:0;">📍 ${this._gps.lat.toFixed(6)}, ${this._gps.lng.toFixed(6)} ±${this._gps.accuracy}m</span>`
              : j.latitude
                ? `<span class="tiny" style="margin-top:0;">📍 ${j.latitude}, ${j.longitude}</span>`
                : `<span class="tiny" style="margin-top:0;">No GPS captured</span>`
          }
        </div>
      </div>

      <!-- Scope & Notes -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Details</div>
        <label for="jobScope">Scope of Work</label>
        <textarea id="jobScope" rows="4" placeholder="Describe the work to be performed...">${E(scopeVal)}</textarea>
        <label for="jobNotes">Notes</label>
        <textarea id="jobNotes" rows="3" placeholder="Internal notes, observations...">${E(notesVal)}</textarea>
      </div>

      <!-- Estimate Calculator -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Estimate Calculator</div>

        <!-- Template Selector -->
        <label for="jobTemplate">Template</label>
        <select id="jobTemplate">
          <option value="">-- Custom --</option>
          ${Object.entries(ESTIMATE_TEMPLATES)
            .map(([key, t]) => `<option value="${key}">${E(t.label)}</option>`)
            .join('')}
        </select>

        <!-- Severity -->
        <label for="jobSeverity">Severity</label>
        <select id="jobSeverity">
          ${Object.keys(SEVERITY_MULTIPLIERS)
            .map(s => `<option value="${E(s)}">${E(s)} (${SEVERITY_MULTIPLIERS[s]}x)</option>`)
            .join('')}
        </select>

        <!-- Line Items -->
        <div style="margin-top:14px;">
          <div style="display:flex;gap:8px;margin-bottom:8px;">
            <select id="lineService" style="margin-top:0;flex:2;">
              <option value="">-- Add service --</option>
              ${SERVICES.map(s => `<option value="${E(s.name)}" data-price="${s.price}">${E(s.name)} — $${s.price}</option>`).join('')}
            </select>
            <input type="number" id="lineQty" placeholder="Qty" min="1" value="1" style="margin-top:0;width:70px;flex-shrink:0;">
            <button class="action dark" data-action="add-line-item" type="button" style="margin-top:0;width:auto;padding:10px 12px;">+</button>
          </div>

          ${
            this._lineItems.length
              ? `<div style="margin-bottom:10px;">
                ${this._lineItems
                  .map(
                    (item, idx) => `
                  <div class="service-item">
                    <div class="service-info">
                      <b>${E(item.service)}</b>
                      <div class="tiny">${item.qty} × $${item.price}</div>
                    </div>
                    <div class="service-price">${money(item.total)}</div>
                    <button class="service-remove" data-action="remove-line" data-idx="${idx}" aria-label="Remove ${E(item.service)}">&times;</button>
                  </div>
                `
                  )
                  .join('')}
               </div>`
              : ''
          }
        </div>

        <!-- Totals -->
        <div style="border-top:1px solid var(--border);padding-top:12px;margin-top:12px;font-size:14px;">
          <div style="display:flex;justify-content:space-between;margin-bottom:4px;">
            <span>Subtotal</span>
            <b>${money(subtotal)}</b>
          </div>
          <div style="display:flex;justify-content:space-between;margin-bottom:4px;">
            <span>Tax (${(taxRate * 100).toFixed(0)}%)</span>
            <b>${money(tax)}</b>
          </div>
          <div style="display:flex;justify-content:space-between;font-size:16px;font-weight:700;color:var(--green);border-top:1px solid var(--border);padding-top:8px;margin-top:8px;">
            <span>Grand Total</span>
            <span>${money(grandTotal)}</span>
          </div>
        </div>
      </div>

      <!-- Validation Errors Summary -->
      ${
        Object.keys(this._errors).length
          ? `<div class="alert" role="alert">
            <span>Please fix the errors above before saving.</span>
           </div>`
          : ''
      }

      <!-- Submit -->
      <button class="action" data-action="save-job">${isEdit ? '💾 Update Job' : '✅ Create Job'}</button>
      <button class="action dark" data-action="cancel-job">Cancel</button>
    `;
  },

  afterRender(state) {
    // Customer select toggle
    const custSelect = document.getElementById('jobCustomer');
    const newFields = document.getElementById('newCustomerFields');
    if (custSelect && newFields) {
      const handler = () => {
        newFields.style.display = custSelect.value ? 'none' : 'block';
      };
      custSelect.addEventListener('change', handler);
      this._listeners.push({ el: custSelect, type: 'change', fn: handler });
    }

    // Template selector
    const templateSelect = document.getElementById('jobTemplate');
    if (templateSelect) {
      const handler = () => {
        const key = templateSelect.value;
        if (!key) return;
        const t = ESTIMATE_TEMPLATES[key];
        if (!t) return;
        const speciesEl = document.getElementById('jobSpecies');
        const scopeEl = document.getElementById('jobScope');
        if (speciesEl) speciesEl.value = t.species;
        if (scopeEl && !scopeEl.value) scopeEl.value = t.issue;
        // Add template service as line item
        this._lineItems.push({
          id: id(),
          service: t.service,
          qty: t.qty,
          price: t.price,
          total: t.qty * t.price
        });
        this._isDirty = true;
        state.rerender?.();
      };
      templateSelect.addEventListener('change', handler);
      this._listeners.push({ el: templateSelect, type: 'change', fn: handler });
    }

    // Mark dirty on any input change
    document.querySelectorAll('input, select, textarea').forEach(el => {
      const handler = () => {
        this._isDirty = true;
      };
      el.addEventListener('input', handler);
      this._listeners.push({ el, type: 'input', fn: handler });
    });

    // Action buttons
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = e => {
        const action = btn.dataset.action;

        if (action === 'capture-gps') {
          if (!navigator.geolocation) {
            state.showToast?.('GPS not supported', 'warn');
            return;
          }
          navigator.geolocation.getCurrentPosition(
            pos => {
              this._gps = {
                lat: +pos.coords.latitude.toFixed(6),
                lng: +pos.coords.longitude.toFixed(6),
                accuracy: Math.round(pos.coords.accuracy)
              };
              state.showToast?.(`GPS captured: ${this._gps.lat}, ${this._gps.lng}`);
              state.rerender?.();
            },
            err => state.showToast?.('GPS error: ' + err.message, 'error'),
            { enableHighAccuracy: true, timeout: 12000 }
          );
        }

        if (action === 'add-line-item') {
          const svcEl = document.getElementById('lineService');
          const qtyEl = document.getElementById('lineQty');
          const svcName = svcEl?.value;
          const qty = parseInt(qtyEl?.value || 1, 10);
          if (!svcName) {
            state.showToast?.('Select a service', 'warn');
            return;
          }
          const svc = SERVICES.find(s => s.name === svcName);
          const price = svc?.price || 0;
          this._lineItems.push({ id: id(), service: svcName, qty, price, total: qty * price });
          this._isDirty = true;
          svcEl.value = '';
          qtyEl.value = '1';
          state.rerender?.();
        }

        if (action === 'remove-line') {
          const idx = parseInt(btn.dataset.idx, 10);
          this._lineItems.splice(idx, 1);
          this._isDirty = true;
          state.rerender?.();
        }

        if (action === 'save-job') {
          this._saveJob(state);
        }

        if (action === 'cancel-job') {
          if (this._isDirty && !confirm('Discard unsaved changes?')) return;
          state.navigate?.(this._editId ? `jobs/${this._editId}` : 'jobs');
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Before unload warning
    const beforeUnload = e => {
      if (this._isDirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', beforeUnload);
    this._listeners.push({ el: window, type: 'beforeunload', fn: beforeUnload });
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._isDirty = false;
    this._gps = null;
    this._lineItems = [];
    this._editId = null;
    this._errors = {};
  },

  _saveJob(state) {
    const errors = {};
    const custSelect = document.getElementById('jobCustomer');
    const custId = custSelect?.value;
    const custName = custId
      ? (state.customers || []).find(c => c.id === custId)?.name || ''
      : document.getElementById('jobCustName')?.value?.trim();

    if (!custName || custName.length < 2) errors.customer = 'Customer name required (min 2 chars)';

    const address = document.getElementById('jobAddress')?.value?.trim();
    if (!address || address.length < 5) errors.address = 'Valid address required (min 5 chars)';

    const species = document.getElementById('jobSpecies')?.value;
    if (!species) errors.species = 'Species required';

    this._errors = errors;
    if (Object.keys(errors).length > 0) {
      state.rerender?.();
      return;
    }

    const subtotal = this._lineItems.reduce((a, item) => a + item.total, 0);
    const taxRate = state.settings?.taxRate || DEFAULT_TAX_RATE;
    const tax = subtotal * taxRate;
    const grandTotal = subtotal + tax;

    const payload = {
      id: this._editId || id(),
      customer_id: custId || null,
      customer: custName,
      phone: custId
        ? (state.customers || []).find(c => c.id === custId)?.phone || ''
        : document.getElementById('jobCustPhone')?.value?.trim() || '',
      email: custId
        ? (state.customers || []).find(c => c.id === custId)?.email || ''
        : document.getElementById('jobCustEmail')?.value?.trim() || '',
      address,
      town: document.getElementById('jobTown')?.value?.trim() || '',
      state: document.getElementById('jobState')?.value?.trim() || '',
      zip: document.getElementById('jobZip')?.value?.trim() || '',
      species,
      status: document.getElementById('jobStatus')?.value || 'Active',
      priority: document.getElementById('jobPriority')?.value || 'Normal',
      title: document.getElementById('jobTitle')?.value?.trim() || species + ' job',
      scope: document.getElementById('jobScope')?.value?.trim() || '',
      notes: document.getElementById('jobNotes')?.value?.trim() || '',
      estimate: grandTotal || subtotal || 0,
      subtotal,
      tax_rate: taxRate,
      tax_amount: tax,
      grand_total: grandTotal,
      deposit_paid: 0,
      balance_due: grandTotal,
      latitude: this._gps?.lat || (state.jobs || []).find(j => j.id === this._editId)?.latitude || null,
      longitude: this._gps?.lng || (state.jobs || []).find(j => j.id === this._editId)?.longitude || null,
      accuracy: this._gps?.accuracy || null,
      services: this._lineItems,
      updated_at: new Date().toISOString(),
      ...(this._editId ? {} : { created_at: new Date().toISOString() })
    };

    this._isDirty = false;
    state.onSaveJob?.(payload, this._editId);
  }
};
