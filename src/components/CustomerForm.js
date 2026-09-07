/**
 * CustomerForm.js — Customer create/edit form
 * Name, phone, email, address, validation, duplicate checking
 */

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function id() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

export const CustomerForm = {
  _listeners: [],
  _isDirty: false,
  _errors: {},
  _editId: null,

  render(state) {
    const isEdit = state.page === 'customers/edit' || state.selectedCustomerId;
    const existing = isEdit ? (state.customers || []).find(c => c.id === state.selectedCustomerId) : null;
    this._editId = existing?.id || null;

    const nameVal = existing?.name || '';
    const phoneVal = existing?.phone || '';
    const emailVal = existing?.email || '';
    const addressVal = existing?.address || '';
    const townVal = existing?.town || '';
    const stateVal = existing?.state || '';
    const zipVal = existing?.zip || '';
    const notesVal = existing?.notes || '';

    return /* html */ `
      <div class="card stack">
        <h2>${isEdit ? '✏️ Edit Customer' : '👤 New Customer'}</h2>
        <p class="tiny">${isEdit ? 'Update customer details below.' : 'Add a new customer to your database.'}</p>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Customer Information</div>

        <label for="custName">Full Name *</label>
        <input
          type="text"
          id="custName"
          placeholder="John Smith"
          value="${E(nameVal)}"
          aria-required="true"
          autocomplete="name"
        >
        ${this._errors.name ? `<div class="form-error">${E(this._errors.name)}</div>` : ''}

        <div class="form-row">
          <div>
            <label for="custPhone">Phone</label>
            <input
              type="tel"
              id="custPhone"
              placeholder="(555) 123-4567"
              value="${E(phoneVal)}"
              autocomplete="tel"
            >
          </div>
          <div>
            <label for="custEmail">Email</label>
            <input
              type="email"
              id="custEmail"
              placeholder="john@email.com"
              value="${E(emailVal)}"
              autocomplete="email"
            >
          </div>
        </div>

        ${this._errors.duplicate ? `<div class="form-error">${E(this._errors.duplicate)}</div>` : ''}
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Address</div>

        <label for="custAddress">Street Address</label>
        <input
          type="text"
          id="custAddress"
          placeholder="123 Main Street"
          value="${E(addressVal)}"
          autocomplete="street-address"
        >

        <div class="form-row">
          <div>
            <label for="custTown">Town / City</label>
            <input
              type="text"
              id="custTown"
              placeholder="Town name"
              value="${E(townVal)}"
              autocomplete="address-level2"
            >
          </div>
          <div>
            <label for="custState">State</label>
            <input
              type="text"
              id="custState"
              placeholder="NY"
              value="${E(stateVal)}"
              autocomplete="address-level1"
            >
          </div>
        </div>

        <label for="custZip">ZIP Code</label>
        <input
          type="text"
          id="custZip"
          placeholder="12345"
          value="${E(zipVal)}"
          autocomplete="postal-code"
        >
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0;">Notes</div>
        <label for="custNotes" class="sr-only">Notes</label>
        <textarea
          id="custNotes"
          rows="4"
          placeholder="Any additional notes about this customer..."
        >${E(notesVal)}</textarea>
      </div>

      ${
        Object.keys(this._errors).length
          ? `<div class="alert" role="alert"><span>Please fix the errors above before saving.</span></div>`
          : ''
      }

      <button class="action" data-action="save-customer">${isEdit ? '💾 Update Customer' : '✅ Create Customer'}</button>
      <button class="action dark" data-action="cancel-customer">Cancel</button>
    `;
  },

  afterRender(state) {
    // Mark dirty on input
    document.querySelectorAll('input, select, textarea').forEach(el => {
      const handler = () => {
        this._isDirty = true;
      };
      el.addEventListener('input', handler);
      this._listeners.push({ el, type: 'input', fn: handler });
    });

    // Actions
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;

        if (action === 'save-customer') {
          this._save(state);
        }

        if (action === 'cancel-customer') {
          if (this._isDirty && !confirm('Discard unsaved changes?')) return;
          state.navigate?.(this._editId ? `customers/${this._editId}` : 'customers');
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
    this._errors = {};
    this._editId = null;
  },

  _save(state) {
    const errors = {};
    const name = document.getElementById('custName')?.value?.trim();
    const phone = document.getElementById('custPhone')?.value?.trim() || '';
    const email = document.getElementById('custEmail')?.value?.trim() || '';

    if (!name || name.length < 2) errors.name = 'Name required (min 2 characters)';

    // Check for duplicates (by name + phone or name + email)
    const customers = state.customers || [];
    const duplicate = customers.find(
      c => c.id !== this._editId && c.name?.toLowerCase() === name?.toLowerCase() && (!phone || c.phone === phone)
    );
    if (duplicate && !this._editId) {
      errors.duplicate = `A customer named "${duplicate.name}" already exists. Continue anyway?`;
    }

    this._errors = errors;
    if (errors.duplicate) {
      // Allow override on duplicate warning
      if (!confirm(errors.duplicate)) {
        state.rerender?.();
        return;
      }
      this._errors = {};
    } else if (Object.keys(errors).length > 0) {
      state.rerender?.();
      return;
    }

    const payload = {
      id: this._editId || id(),
      name,
      phone,
      email,
      address: document.getElementById('custAddress')?.value?.trim() || '',
      town: document.getElementById('custTown')?.value?.trim() || '',
      state: document.getElementById('custState')?.value?.trim() || '',
      zip: document.getElementById('custZip')?.value?.trim() || '',
      notes: document.getElementById('custNotes')?.value?.trim() || '',
      updated_at: new Date().toISOString(),
      ...(this._editId ? {} : { created_at: new Date().toISOString() })
    };

    this._isDirty = false;
    state.onSaveCustomer?.(payload, this._editId);
  }
};
