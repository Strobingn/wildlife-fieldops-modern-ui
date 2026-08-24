/**
 * EstimateCalc.js — Standalone estimate calculator page
 * Species/severity/service selectors, line items, templates, email/save
 */

import {
  SPECIES,
  SEVERITIES,
  SERVICES,
  ESTIMATE_TEMPLATES,
  BASE_PRICES,
  SEVERITY_MULTIPLIERS,
  DEFAULT_TAX_RATE,
  SPECIES_ICONS
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

function id() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

export const EstimateCalc = {
  _listeners: [],
  _lineItems: [],
  _species: SPECIES[0],
  _severity: 'Medium',
  _issue: '',
  _customerName: '',
  _customerEmail: '',

  render(state) {
    const taxRate = state.settings?.taxRate || DEFAULT_TAX_RATE;

    // Auto-calculate base from species + severity
    const base = BASE_PRICES[this._species] || 500;
    const mult = SEVERITY_MULTIPLIERS[this._severity] || 1.35;
    const baseEstimate = Math.round(base * mult);

    // Calculate totals from line items
    const lineTotal = this._lineItems.reduce((a, item) => a + item.total, 0);
    const subtotal = lineTotal || baseEstimate;
    const tax = subtotal * taxRate;
    const grandTotal = subtotal + tax;

    return /* html */ `
      <div class="card stack">
        <h2>💵 Estimate Calculator</h2>
        <p class="tiny">Build a professional estimate for your customer.</p>
      </div>

      <!-- Customer -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Customer</div>
        <label for="estCustName">Customer Name</label>
        <input type="text" id="estCustName" placeholder="Customer name" value="${E(this._customerName)}">
        <label for="estCustEmail">Email</label>
        <input type="email" id="estCustEmail" placeholder="customer@email.com" value="${E(this._customerEmail)}">
      </div>

      <!-- Template -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Quick Template</div>
        <label for="estTemplate">Scenario Template</label>
        <select id="estTemplate">
          <option value="">-- Custom estimate --</option>
          ${Object.entries(ESTIMATE_TEMPLATES)
            .map(
              ([key, t]) => `<option value="${key}">${E(t.label)} (${SPECIES_ICONS[t.species]} ${t.species})</option>`
            )
            .join('')}
        </select>
      </div>

      <!-- Species & Severity -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Job Parameters</div>
        <div class="form-row">
          <div>
            <label for="estSpecies">Species</label>
            <select id="estSpecies">
              ${SPECIES.map(s => `<option value="${E(s)}" ${this._species === s ? 'selected' : ''}>${SPECIES_ICONS[s] || '🐾'} ${E(s)}</option>`).join('')}
            </select>
          </div>
          <div>
            <label for="estSeverity">Severity</label>
            <select id="estSeverity">
              ${SEVERITIES.map(s => `<option value="${E(s)}" ${this._severity === s ? 'selected' : ''}>${E(s)} (${SEVERITY_MULTIPLIERS[s] || 1}x)</option>`).join('')}
            </select>
          </div>
        </div>
        <label for="estIssue">Issue Description</label>
        <textarea id="estIssue" rows="3" placeholder="Describe the wildlife issue...">${E(this._issue)}</textarea>
        <div class="tiny" style="margin-top:8px;">
          Base: ${money(base)} × Severity: ${mult}x = ${money(baseEstimate)}
        </div>
      </div>

      <!-- Line Items -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Line Items</div>
        <div style="display:flex;gap:8px;margin-bottom:10px;">
          <select id="estService" style="margin-top:0;flex:2;">
            <option value="">-- Select service --</option>
            ${SERVICES.map(s => `<option value="${E(s.name)}" data-price="${s.price}">${E(s.name)} — $${s.price}</option>`).join('')}
          </select>
          <input type="number" id="estQty" placeholder="Qty" min="1" value="1" style="margin-top:0;width:60px;flex-shrink:0;">
          <button class="action dark" data-action="add-service" style="margin-top:0;width:auto;padding:10px;">+</button>
        </div>

        ${
          this._lineItems.length
            ? `<div style="margin-bottom:12px;">
              ${this._lineItems
                .map(
                  (item, idx) => `
                <div class="service-item">
                  <div class="service-info">
                    <b>${E(item.service)}</b>
                    <div class="tiny">${item.qty} × $${item.price.toFixed(2)}</div>
                  </div>
                  <div class="service-price">${money(item.total)}</div>
                  <button class="service-remove" data-action="remove-service" data-idx="${idx}" aria-label="Remove ${E(item.service)}">&times;</button>
                </div>
              `
                )
                .join('')}
             </div>`
            : `<div class="tiny" style="margin-bottom:12px;">No line items yet. Add services above or use a template.</div>`
        }
      </div>

      <!-- Totals -->
      <div class="card" style="background:var(--card2);">
        <div style="display:flex;justify-content:space-between;margin-bottom:6px;font-size:14px;">
          <span>Subtotal</span>
          <b>${money(subtotal)}</b>
        </div>
        <div style="display:flex;justify-content:space-between;margin-bottom:6px;font-size:14px;">
          <span>Tax (${(taxRate * 100).toFixed(0)}%)</span>
          <b>${money(tax)}</b>
        </div>
        <div style="display:flex;justify-content:space-between;font-size:18px;font-weight:700;color:var(--green);border-top:1px solid var(--border);padding-top:10px;margin-top:8px;">
          <span>Grand Total</span>
          <span>${money(grandTotal)}</span>
        </div>
      </div>

      <!-- Actions -->
      <div class="row">
        <button class="action blue" data-action="email-estimate" style="margin-top:0;">📧 Email Estimate</button>
        <button class="action" data-action="save-estimate" style="margin-top:0;">💾 Save as Job</button>
      </div>

      <!-- Preview -->
      <div class="card" style="margin-top:12px;">
        <div class="section-title" style="margin-top:0;">Preview</div>
        <pre style="font-size:12px;line-height:1.6;white-space:pre-wrap;color:var(--muted);overflow-x:auto;">${E(this._generatePreview(subtotal, tax, grandTotal, taxRate))}</pre>
      </div>
    `;
  },

  afterRender(state) {
    // Template select
    const tmplEl = document.getElementById('estTemplate');
    if (tmplEl) {
      const handler = () => {
        const key = tmplEl.value;
        if (!key) return;
        const t = ESTIMATE_TEMPLATES[key];
        if (!t) return;
        this._species = t.species;
        this._issue = t.issue;
        this._lineItems = [
          {
            id: id(),
            service: t.service,
            qty: t.qty,
            price: t.price,
            total: t.qty * t.price
          }
        ];
        state.rerender?.();
      };
      tmplEl.addEventListener('change', handler);
      this._listeners.push({ el: tmplEl, type: 'change', fn: handler });
    }

    // Species and severity changes
    const specEl = document.getElementById('estSpecies');
    const sevEl = document.getElementById('estSeverity');
    if (specEl) {
      const handler = () => {
        this._species = specEl.value;
        state.rerender?.();
      };
      specEl.addEventListener('change', handler);
      this._listeners.push({ el: specEl, type: 'change', fn: handler });
    }
    if (sevEl) {
      const handler = () => {
        this._severity = sevEl.value;
        state.rerender?.();
      };
      sevEl.addEventListener('change', handler);
      this._listeners.push({ el: sevEl, type: 'change', fn: handler });
    }

    // Track customer name/email
    const nameEl = document.getElementById('estCustName');
    const emailEl = document.getElementById('estCustEmail');
    const issueEl = document.getElementById('estIssue');
    if (nameEl) {
      const h = () => {
        this._customerName = nameEl.value;
      };
      nameEl.addEventListener('input', h);
      this._listeners.push({ el: nameEl, type: 'input', fn: h });
    }
    if (emailEl) {
      const h = () => {
        this._customerEmail = emailEl.value;
      };
      emailEl.addEventListener('input', h);
      this._listeners.push({ el: emailEl, type: 'input', fn: h });
    }
    if (issueEl) {
      const h = () => {
        this._issue = issueEl.value;
      };
      issueEl.addEventListener('input', h);
      this._listeners.push({ el: issueEl, type: 'input', fn: h });
    }

    // Action buttons
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;

        if (action === 'add-service') {
          const svcEl = document.getElementById('estService');
          const qtyEl = document.getElementById('estQty');
          const svcName = svcEl?.value;
          const qty = parseInt(qtyEl?.value || 1, 10);
          if (!svcName) {
            state.showToast?.('Select a service', 'warn');
            return;
          }
          const svc = SERVICES.find(s => s.name === svcName);
          const price = svc?.price || 0;
          this._lineItems.push({ id: id(), service: svcName, qty, price, total: qty * price });
          svcEl.value = '';
          qtyEl.value = '1';
          state.rerender?.();
        }

        if (action === 'remove-service') {
          const idx = parseInt(btn.dataset.idx, 10);
          this._lineItems.splice(idx, 1);
          state.rerender?.();
        }

        if (action === 'email-estimate') {
          const preview = this._generatePreview(
            this._calcSubtotal(),
            this._calcSubtotal() * (state.settings?.taxRate || DEFAULT_TAX_RATE),
            this._calcGrandTotal(state),
            state.settings?.taxRate || DEFAULT_TAX_RATE
          );
          const subject = encodeURIComponent('Wildlife Whisperer LLC — Estimate');
          const body = encodeURIComponent(preview);
          const email = this._customerEmail ? `mailto:${this._customerEmail}?` : 'mailto:?';
          window.location.href = email + `subject=${subject}&body=${body}`;
        }

        if (action === 'save-estimate') {
          const subtotal = this._calcSubtotal();
          const taxRate = state.settings?.taxRate || DEFAULT_TAX_RATE;
          const grandTotal = this._calcGrandTotal(state);
          const payload = {
            id: id(),
            customer: this._customerName || 'TBD',
            species: this._species,
            status: 'Active',
            priority: 'Normal',
            title: this._species + ' — ' + (this._issue ? this._issue.slice(0, 50) : 'Estimate'),
            scope: this._issue || '',
            estimate: grandTotal,
            subtotal,
            tax_rate: taxRate,
            tax_amount: subtotal * taxRate,
            grand_total: grandTotal,
            deposit_paid: 0,
            balance_due: grandTotal,
            services: this._lineItems,
            created_at: new Date().toISOString(),
            updated_at: new Date().toISOString()
          };
          state.onSaveEstimateAsJob?.(payload);
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._lineItems = [];
    this._species = SPECIES[0];
    this._severity = 'Medium';
    this._issue = '';
    this._customerName = '';
    this._customerEmail = '';
  },

  _calcSubtotal() {
    if (this._lineItems.length) return this._lineItems.reduce((a, item) => a + item.total, 0);
    const base = BASE_PRICES[this._species] || 500;
    const mult = SEVERITY_MULTIPLIERS[this._severity] || 1.35;
    return Math.round(base * mult);
  },

  _calcGrandTotal(state) {
    const subtotal = this._calcSubtotal();
    const taxRate = state.settings?.taxRate || DEFAULT_TAX_RATE;
    return subtotal + subtotal * taxRate;
  },

  _generatePreview(subtotal, tax, grandTotal, taxRate) {
    const items = this._lineItems.length
      ? this._lineItems
          .map(item => `  - ${item.service}: ${item.qty} x $${item.price.toFixed(2)} = $${item.total.toFixed(2)}`)
          .join('\n')
      : `  - ${this._species} removal (${this._severity} severity): $${this._calcSubtotal().toFixed(2)}`;

    return `WILDLIFE WHISPERER LLC — ESTIMATE
================================
Customer: ${this._customerName || '[Name TBD]'}
Date: ${new Date().toLocaleDateString()}
Species: ${this._species}
Severity: ${this._severity}
Issue: ${this._issue || '[See scope]'}

LINE ITEMS:
${items}

Subtotal: $${subtotal.toFixed(2)}
Tax (${(taxRate * 100).toFixed(0)}%): $${tax.toFixed(2)}

GRAND TOTAL: $${grandTotal.toFixed(2)}

This estimate is valid for 30 days. Final pricing may
vary based on actual conditions discovered during service.
Warranty applies only to listed sealed/repaired areas.`;
  }
};
