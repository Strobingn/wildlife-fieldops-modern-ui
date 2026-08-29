/**
 * CustomerList.js — Customer management
 * Search bar, customer cards, add button, customer detail view
 */

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function tel(p) {
  return 'tel:' + String(p || '').replace(/[^\d+]/g, '');
}

const PAGE_SIZE = 15;

export const CustomerList = {
  _listeners: [],
  _page: 1,
  _searchQuery: '',
  _viewingCustomer: null,

  render(state) {
    const customers = state.customers || [];
    const jobs = state.jobs || [];

    // Customer detail view
    if (this._viewingCustomer) {
      return this._renderCustomerDetail(this._viewingCustomer, jobs, state);
    }

    // Filter customers by search
    const q = this._searchQuery.toLowerCase().trim();
    const filtered = customers
      .filter(c => {
        if (!q) return true;
        return (
          (c.name || '').toLowerCase().includes(q) ||
          (c.phone || '').toLowerCase().includes(q) ||
          (c.address || '').toLowerCase().includes(q) ||
          (c.town || '').toLowerCase().includes(q) ||
          (c.email || '').toLowerCase().includes(q)
        );
      })
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''));

    const endIdx = Math.min(this._page * PAGE_SIZE, filtered.length);
    const pageCustomers = filtered.slice(0, endIdx);

    // Count jobs per customer
    const jobCounts = {};
    jobs.forEach(j => {
      const key = j.customer_id || j.customer;
      if (key) jobCounts[key] = (jobCounts[key] || 0) + 1;
    });

    return /* html */ `
      <!-- Header -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
        <h2 style="margin-bottom:0;">👥 Customers</h2>
        <button class="action" data-action="add-customer" style="margin-top:0;width:auto;padding:10px 16px;font-size:13px;">+ Add Customer</button>
      </div>

      <!-- Search -->
      <div class="search-box" style="position:static;">
        <div class="search-input-wrap">
          <input
            type="search"
            id="custSearch"
            placeholder="Search customers by name, phone, address..."
            aria-label="Search customers"
            value="${E(this._searchQuery)}"
            autocomplete="off"
          />
          ${this._searchQuery ? `<button class="search-clear" id="custSearchClear" aria-label="Clear search">&times;</button>` : ''}
        </div>
      </div>

      <!-- Results count -->
      <div class="results-count" style="margin-bottom:10px;" aria-live="polite">${filtered.length} customer${filtered.length !== 1 ? 's' : ''}</div>

      <!-- Customer List -->
      <div id="customerList">
        ${
          pageCustomers.length
            ? pageCustomers.map(c => this._customerCard(c, jobCounts[c.id] || jobCounts[c.name] || 0)).join('')
            : `<div class="empty-state">
              <div class="empty-icon" aria-hidden="true">👤</div>
              <h4>${q ? 'No matching customers' : 'No customers yet'}</h4>
              <p>${q ? 'Try a different search term.' : 'Add your first customer to get started.'}</p>
             </div>`
        }
      </div>

      <!-- Load More -->
      ${
        endIdx < filtered.length
          ? `<div class="load-more">
            <button id="loadMoreCust" aria-label="Load more customers">Load more (${filtered.length - endIdx} remaining)</button>
           </div>`
          : ''
      }
    `;
  },

  afterRender(state) {
    // Search
    const searchInput = document.getElementById('custSearch');
    const searchClear = document.getElementById('custSearchClear');

    if (searchInput) {
      const debouncedSearch = this._debounce(q => {
        this._searchQuery = q;
        this._page = 1;
        state.rerender?.();
      }, 250);
      const handler = e => debouncedSearch(e.target.value);
      searchInput.addEventListener('input', handler);
      this._listeners.push({ el: searchInput, type: 'input', fn: handler });
    }

    if (searchClear) {
      const handler = () => {
        this._searchQuery = '';
        this._page = 1;
        state.rerender?.();
      };
      searchClear.addEventListener('click', handler);
      this._listeners.push({ el: searchClear, type: 'click', fn: handler });
    }

    // Load more
    const loadMore = document.getElementById('loadMoreCust');
    if (loadMore) {
      const handler = () => {
        this._page++;
        state.rerender?.();
      };
      loadMore.addEventListener('click', handler);
      this._listeners.push({ el: loadMore, type: 'click', fn: handler });
    }

    // Customer card clicks
    const list = document.getElementById('customerList');
    if (list) {
      const handler = e => {
        const card = e.target.closest('.customer-card[data-id]');
        if (!card) return;
        const id = card.dataset.id;
        const customer = (state.customers || []).find(c => c.id === id);
        if (customer) {
          this._viewingCustomer = customer;
          state.rerender?.();
        }
      };
      list.addEventListener('click', handler);
      this._listeners.push({ el: list, type: 'click', fn: handler });
    }

    // Action buttons
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;
        if (action === 'add-customer') state.navigate?.('customers/new');
        if (action === 'back-to-list') this._viewingCustomer = null;
        if (action === 'edit-customer' && this._viewingCustomer) {
          state.navigate?.(`customers/${this._viewingCustomer.id}/edit`);
        }
        if (action === 'new-job-for-customer' && this._viewingCustomer) {
          state.setSelectedCustomerId?.(this._viewingCustomer.id);
          state.navigate?.('jobs/new');
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Job card clicks within customer detail
    const custJobs = document.getElementById('customerJobs');
    if (custJobs) {
      const handler = e => {
        const card = e.target.closest('.job-card[data-job-id]');
        if (!card || e.target.closest('.job-actions')) return;
        state.navigate?.(`jobs/${card.dataset.jobId}`);
      };
      custJobs.addEventListener('click', handler);
      this._listeners.push({ el: custJobs, type: 'click', fn: handler });
    }
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._page = 1;
    this._searchQuery = '';
    this._viewingCustomer = null;
  },

  _customerCard(c, jobCount) {
    const initial = (c.name || '?').charAt(0).toUpperCase();
    return /* html */ `
      <div class="card customer-card" data-id="${c.id}" tabindex="0" role="button" aria-label="Customer: ${E(c.name)}, ${jobCount} jobs">
        <div style="display:flex;align-items:center;gap:12px;">
          <div class="customer-detail-avatar" style="width:44px;height:44px;font-size:18px;background:linear-gradient(135deg,var(--accent),var(--accent2));">${initial}</div>
          <div style="flex:1;min-width:0;">
            <div class="customer-name">${E(c.name)}</div>
            <div class="customer-meta">
              ${c.phone ? `<a href="${tel(c.phone)}">${E(c.phone)}</a>` : 'No phone'}
              ${c.town ? ` &middot; ${E(c.town)}` : ''}
            </div>
            ${c.address ? `<div class="tiny" style="margin-top:2px;">${E(c.address)}</div>` : ''}
          </div>
        </div>
        <span class="customer-jobs-count">${jobCount} job${jobCount !== 1 ? 's' : ''}</span>
      </div>
    `;
  },

  _renderCustomerDetail(customer, allJobs, state) {
    const customerJobs = allJobs.filter(j => j.customer_id === customer.id || j.customer === customer.name);
    const totalRevenue = customerJobs.reduce((a, j) => a + (j.grand_total || j.estimate || 0), 0);

    return /* html */ `
      <!-- Back button -->
      <button class="action dark" data-action="back-to-list" style="margin-bottom:12px;width:auto;padding:8px 14px;font-size:13px;">← Back to Customers</button>

      <!-- Customer Header -->
      <div class="card stack">
        <div class="customer-detail-header">
          <div class="customer-detail-avatar">${(customer.name || '?').charAt(0).toUpperCase()}</div>
          <div>
            <h2 style="margin-bottom:4px;">${E(customer.name)}</h2>
            <div class="tiny" style="margin-top:0;">
              ${customer.phone ? `<a href="${tel(customer.phone)}">${E(customer.phone)}</a>` : 'No phone'}
              ${customer.email ? ` &middot; <a href="mailto:${E(customer.email)}">${E(customer.email)}</a>` : ''}
            </div>
          </div>
        </div>
        ${customer.address ? `<div style="font-size:14px;margin-bottom:4px;">📍 ${E(customer.address)}${customer.town ? ', ' + E(customer.town) : ''}${customer.state ? ', ' + E(customer.state) : ''} ${customer.zip || ''}</div>` : ''}
        ${customer.notes ? `<div class="tiny" style="margin-top:8px;white-space:pre-wrap;">${E(customer.notes)}</div>` : ''}
      </div>

      <!-- Stats -->
      <div class="grid">
        <div class="card stat-card">
          <div class="stat-label">Total Jobs</div>
          <div class="stat">${customerJobs.length}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Active Jobs</div>
          <div class="stat">${customerJobs.filter(j => j.status !== 'Closed' && j.status !== 'Cancelled').length}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Total Revenue</div>
          <div class="stat">$${Math.round(totalRevenue).toLocaleString()}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Completion</div>
          <div class="stat">${customerJobs.length ? Math.round((customerJobs.filter(j => j.status === 'Closed').length / customerJobs.length) * 100) : 0}%</div>
        </div>
      </div>

      <!-- Actions -->
      <div class="row" style="margin-bottom:12px;">
        <button class="action" data-action="edit-customer" style="margin-top:0;">✏️ Edit</button>
        <button class="action green" data-action="new-job-for-customer" style="margin-top:0;">🆕 New Job</button>
      </div>

      <!-- Customer's Jobs -->
      <div class="section-title">Jobs (${customerJobs.length})</div>
      <div id="customerJobs">
        ${
          customerJobs.length
            ? customerJobs
                .sort((a, b) => new Date(b.created_at || b.created || 0) - new Date(a.created_at || a.created || 0))
                .map(j => {
                  const icon =
                    {
                      Raccoon: '🦝',
                      'Grey Squirrel': '🐿️',
                      'Red Squirrel': '🐿️',
                      'Flying Squirrel': '🦇',
                      Bat: '🦇',
                      Skunk: '🦨',
                      Groundhog: '🦫',
                      Bird: '🐦',
                      Snake: '🐍',
                      Opossum: '🦡',
                      Rodent: '🐁',
                      Mouse: '🐁',
                      Rat: '🐀',
                      'Carpenter Bee': '🐝'
                    }[j.species] || '🐾';
                  const sc =
                    {
                      Active: 'active',
                      Scheduled: 'scheduled',
                      Closed: 'closed',
                      Trapping: 'trapping',
                      Repair: 'repair',
                      'Waiting On Customer': 'scheduled',
                      Exclusion: 'active',
                      Warranty: 'active',
                      Cancelled: 'closed'
                    }[j.status] || 'active';
                  return `
                <div class="card stack job-card" data-job-id="${j.id}">
                  <div class="job-header">
                    <span class="species-icon" aria-hidden="true">${icon}</span>
                    <h3>${E(j.title || j.species + ' job')}</h3>
                    <span class="status-pill ${sc}">${E(j.status)}</span>
                  </div>
                  <div class="tiny">${E(j.address)}${j.town ? ', ' + E(j.town) : ''}</div>
                  <div class="tiny">${j.created_at || j.created ? new Date(j.created_at || j.created).toLocaleDateString() : ''}</div>
                </div>
              `;
                })
                .join('')
            : `<div class="card tiny">No jobs for this customer yet.</div>`
        }
      </div>
    `;
  },

  _debounce(fn, ms) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...args), ms);
    };
  }
};
