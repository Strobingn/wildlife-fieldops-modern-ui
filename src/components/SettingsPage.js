/**
 * SettingsPage.js — Settings page
 * Theme toggle, tax rate, company info, sync URL, import/export, data recovery, wipe
 */

import { APP_VERSION, STORAGE_KEY } from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

export const SettingsPage = {
  _listeners: [],

  render(state) {
    const settings = state.settings || {};
    const theme = state.theme || 'dark';
    const syncUrl = settings.syncUrl || '';
    const taxRate = settings.taxRate !== undefined ? settings.taxRate : 0.08;
    const companyName = settings.companyName || 'Wildlife Whisperer LLC';
    const companyPhone = settings.companyPhone || '';
    const companyEmail = settings.companyEmail || '';
    const companyAddress = settings.companyAddress || '';

    return /* html */ `
      <div class="card stack">
        <h2>⚙️ Settings</h2>
        <p class="tiny">Configure your app preferences and business details.</p>
      </div>

      <!-- Appearance -->
      <div class="setting-group">
        <h3>Appearance</h3>
        <div class="card">
          <div class="setting">
            <label>Theme</label>
            <div style="display:flex;gap:8px;">
              <button class="${theme === 'dark' ? 'action' : 'action dark'}" data-action="set-theme" data-theme="dark" style="margin-top:0;flex:1;">🌙 Dark</button>
              <button class="${theme === 'light' ? 'action' : 'action dark'}" data-action="set-theme" data-theme="light" style="margin-top:0;flex:1;">☀️ Light</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Business Info -->
      <div class="setting-group">
        <h3>Business Information</h3>
        <div class="card">
          <div class="setting">
            <label for="setCompanyName">Company Name</label>
            <input type="text" id="setCompanyName" value="${E(companyName)}">
          </div>
          <div class="setting">
            <label for="setCompanyPhone">Phone</label>
            <input type="tel" id="setCompanyPhone" value="${E(companyPhone)}">
          </div>
          <div class="setting">
            <label for="setCompanyEmail">Email</label>
            <input type="email" id="setCompanyEmail" value="${E(companyEmail)}">
          </div>
          <div class="setting">
            <label for="setCompanyAddress">Address</label>
            <input type="text" id="setCompanyAddress" value="${E(companyAddress)}">
          </div>
          <button class="action" data-action="save-company" style="margin-top:8px;">💾 Save Business Info</button>
        </div>
      </div>

      <!-- Tax Rate -->
      <div class="setting-group">
        <h3>Tax Rate</h3>
        <div class="card">
          <div class="setting-row">
            <input type="number" id="taxRate" value="${(taxRate * 100).toFixed(0)}" min="0" max="20" step="0.5" style="margin-top:0;">
            <span style="padding:14px 0;font-size:15px;">%</span>
            <button class="action" data-action="save-tax" style="margin-top:0;width:auto;padding:12px 16px;">Save</button>
          </div>
        </div>
      </div>

      <!-- Sync Configuration -->
      <div class="setting-group">
        <h3>Cloud Sync</h3>
        <div class="card">
          <div class="setting">
            <label for="syncUrl">Sync Endpoint URL</label>
            <input type="url" id="syncUrl" placeholder="https://your-api.com/sync" value="${E(syncUrl)}">
            <div class="form-hint">Your custom sync server endpoint. Leave blank to use local storage only.</div>
          </div>
          <div class="row">
            <button class="action" data-action="save-sync-url" style="margin-top:0;">💾 Save URL</button>
            <button class="action blue" data-action="sync-now" style="margin-top:0;">🔄 Sync Now</button>
          </div>
        </div>
      </div>

      <!-- Data Management -->
      <div class="setting-group">
        <h3>Data Management</h3>
        <div class="card">
          <div class="setting">
            <label>Export Data</label>
            <div class="form-hint">Download all your data as a JSON backup file.</div>
            <button class="action dark" data-action="export-data" style="margin-top:8px;">📤 Export JSON</button>
          </div>
          <div class="setting">
            <label>Import Data</label>
            <div class="form-hint">Restore from a previous JSON export. This will merge with existing data.</div>
            <textarea id="importData" rows="4" placeholder="Paste JSON data here..."></textarea>
            <button class="action dark" data-action="import-data" style="margin-top:8px;">📥 Import JSON</button>
          </div>
          <div class="setting">
            <label>Data Recovery</label>
            <div class="form-hint">Recover from the last auto-saved snapshot.</div>
            <button class="action yellow" data-action="recover-data" style="margin-top:8px;">♻️ Recover from Backup</button>
          </div>
          <div class="menu-divider"></div>
          <div class="setting">
            <label>Danger Zone</label>
            <div class="form-hint" style="color:var(--red);">This will permanently delete ALL data. This cannot be undone.</div>
            <button class="action red" data-action="wipe-data" style="margin-top:8px;">⚠️ Wipe All Data</button>
          </div>
        </div>
      </div>

      <!-- Keyboard Shortcuts -->
      <div class="setting-group">
        <h3>Keyboard Shortcuts</h3>
        <div class="card">
          <div class="kb-shortcuts">
            <div class="kb-row"><span>Navigation menu</span><span><span class="kb-key">M</span></span></div>
            <div class="kb-row"><span>New job</span><span><span class="kb-key">N</span></span></div>
            <div class="kb-row"><span>Search</span><span><span class="kb-key">/</span> or <span class="kb-key">Ctrl</span>+<span class="kb-key">K</span></span></div>
            <div class="kb-row"><span>Go to dashboard</span><span><span class="kb-key">G</span> then <span class="kb-key">D</span></span></div>
            <div class="kb-row"><span>Go to jobs</span><span><span class="kb-key">G</span> then <span class="kb-key">J</span></span></div>
            <div class="kb-row"><span>Go to GPS</span><span><span class="kb-key">G</span> then <span class="kb-key">M</span></span></div>
            <div class="kb-row"><span>Close modal / menu</span><span><span class="kb-key">Esc</span></span></div>
          </div>
        </div>
      </div>

      <!-- About -->
      <div class="setting-group">
        <h3>About</h3>
        <div class="card">
          <div style="text-align:center;padding:12px 0;">
            <div style="font-size:48px;margin-bottom:8px;">🦝</div>
            <div style="font-weight:700;font-size:16px;">Wildlife Whisperer FieldOps</div>
            <div class="tiny">Version ${APP_VERSION}</div>
            <div class="tiny" style="margin-top:8px;">Built for wildlife removal professionals.</div>
            <div class="tiny">Offline-first. Mobile-ready. Field-tested.</div>
          </div>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;

        if (action === 'set-theme') {
          const newTheme = btn.dataset.theme;
          document.documentElement.setAttribute('data-theme', newTheme);
          localStorage.setItem('ww_theme', newTheme);
          state.onSetTheme?.(newTheme);
        }

        if (action === 'save-company') {
          state.onSaveSettings?.({
            companyName: document.getElementById('setCompanyName')?.value?.trim(),
            companyPhone: document.getElementById('setCompanyPhone')?.value?.trim(),
            companyEmail: document.getElementById('setCompanyEmail')?.value?.trim(),
            companyAddress: document.getElementById('setCompanyAddress')?.value?.trim()
          });
        }

        if (action === 'save-tax') {
          const rate = parseFloat(document.getElementById('taxRate')?.value || 8);
          state.onSaveSettings?.({ taxRate: rate / 100 });
        }

        if (action === 'save-sync-url') {
          const url = document.getElementById('syncUrl')?.value?.trim();
          state.onSaveSettings?.({ syncUrl: url });
          localStorage.setItem(STORAGE_KEY + '_syncUrl', url);
        }

        if (action === 'sync-now') {
          state.onSyncNow?.();
        }

        if (action === 'export-data') {
          state.onExportData?.();
        }

        if (action === 'import-data') {
          const json = document.getElementById('importData')?.value?.trim();
          if (!json) {
            state.showToast?.('Paste JSON data first', 'warn');
            return;
          }
          state.onImportData?.(json);
        }

        if (action === 'recover-data') {
          state.onRecoverData?.();
        }

        if (action === 'wipe-data') {
          if (
            confirm(
              '⚠️ WARNING: This will permanently delete ALL data including jobs, customers, photos, and visits.\n\nAre you absolutely sure?'
            )
          ) {
            if (confirm("Final confirmation: Type 'DELETE' to proceed.\n\nThis action CANNOT be undone.")) {
              state.onWipeData?.();
            }
          }
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
  }
};
