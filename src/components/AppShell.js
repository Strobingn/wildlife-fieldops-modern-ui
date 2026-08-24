/**
 * AppShell.js — Main application shell
 * Top bar, sync status, side drawer, bottom nav, FAB, modal, toast, loading overlay
 */

import { DRAWER_PAGES, BOTTOM_NAV, APP_VERSION } from '../constants.js';

/** HTML escape helper */
function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

export const AppShell = {
  _menuOpen: false,
  _modalOpen: false,
  _listeners: [],
  _modalCallback: null,

  render(state) {
    const pageTitle = this._getPageTitle(state.page);
    const isOnline = state.isOnline ?? navigator.onLine;
    const syncStatus = state.syncStatus || 'idle';
    const pendingCount = state.jobs?.filter(j => j._pending)?.length || 0;

    return /* html */ `
      <!-- Loading Overlay -->
      <div id="loadingOverlay" class="loading-overlay" role="status" aria-live="polite" aria-label="Loading">
        <div class="spinner" aria-hidden="true"></div>
        <span id="loadingText">Loading...</span>
      </div>

      <!-- Toast Container -->
      <div id="toast" class="toast" role="status" aria-live="polite" aria-atomic="true"></div>

      <!-- Menu Backdrop -->
      <div id="menuBackdrop" class="menu-backdrop" aria-hidden="true"></div>

      <!-- Side Drawer Menu -->
      <nav id="drawer" class="drawer" aria-label="Main navigation" aria-hidden="true">
        <div class="menu-header">
          <span class="logo" aria-hidden="true">🦝</span>
          <div>
            <h3>Wildlife Whisperer</h3>
            <span class="version">v${APP_VERSION}</span>
          </div>
        </div>
        <div class="menu-label">Navigation</div>
        ${DRAWER_PAGES.map(
          p => /* html */ `
            <button
              class="nav-link"
              data-page="${p.id}"
              ${state.page === p.id ? 'aria-current="page"' : ''}
            >
              ${E(p.label)}
            </button>
          `
        ).join('')}
        <div class="menu-divider"></div>
        <div class="menu-label">Account</div>
        <button class="nav-link" data-action="sync" aria-label="Sync data with cloud">
          ☁️ Sync Now
        </button>
        <button class="nav-link" data-action="settings" aria-label="Open settings">
          ⚙️ Settings
        </button>
      </nav>

      <!-- Sync Status Bar -->
      <div class="sync-bar" role="status" aria-live="polite">
        <div class="sync-status ${isOnline ? 'online' : ''} ${syncStatus === 'syncing' ? 'syncing' : ''} ${syncStatus === 'error' ? 'error' : ''}" id="syncStatus">
          ${syncStatus === 'syncing' ? '🔄 Syncing...' : isOnline ? '🟢 Online' : '🔴 Offline'}
        </div>
        <div style="display:flex;gap:8px;align-items:center;">
          ${pendingCount > 0 ? `<span class="pill warn">${pendingCount} pending</span>` : ''}
          <span id="syncTime" class="tiny" style="margin-top:0;">${state.lastSync ? `Synced ${this._timeAgo(state.lastSync)}` : 'Not synced'}</span>
        </div>
      </div>

      <!-- Top Bar -->
      <header class="top" role="banner">
        <button
          id="menuBtn"
          class="menuButton"
          aria-label="Open menu"
          aria-expanded="false"
          aria-controls="drawer"
        >
          ☰
        </button>
        <h1 class="top-title" id="pageTitle">${E(pageTitle)}</h1>
        <div class="top-actions">
          <button
            id="themeToggle"
            class="theme-toggle"
            aria-label="Toggle ${state.theme === 'dark' ? 'light' : 'dark'} theme"
            title="Toggle theme"
          >
            ${state.theme === 'dark' ? '☀️' : '🌙'}
          </button>
          <button
            id="notifBtn"
            class="notification-btn"
            aria-label="Notifications"
            title="Notifications"
          >
            🔔
            ${state.notifications?.length ? `<span class="badge" aria-label="${state.notifications.length} notifications">${Math.min(state.notifications.length, 9)}</span>` : ''}
          </button>
        </div>
      </header>

      <!-- Main Content Area -->
      <main id="mainContent" class="wrap" role="main" aria-label="Main content"></main>

      <!-- Bottom Navigation -->
      <nav class="bottom-nav" role="navigation" aria-label="Primary navigation">
        ${BOTTOM_NAV.map(
          item => /* html */ `
            <button
              class="nav-tab ${state.page === item.id ? 'active' : ''}"
              data-page="${item.id}"
              aria-label="${E(item.label)}"
              ${state.page === item.id ? 'aria-current="page"' : ''}
            >
              ${item.icon}
              <span class="nav-label">${E(item.label)}</span>
            </button>
          `
        ).join('')}
      </nav>

      <!-- FAB -->
      <button
        id="fab"
        class="fab ${state.hideFab ? 'hidden-fab' : ''}"
        aria-label="Quick add"
        title="Quick add"
      >
        +
      </button>

      <!-- Modal Backdrop -->
      <div id="modalBackdrop" class="modal-backdrop" role="dialog" aria-modal="true" aria-hidden="true">
        <div class="modal" role="document">
          <div class="modal-header">
            <h3 id="modalTitle">Modal</h3>
            <button
              id="modalClose"
              class="modal-close"
              aria-label="Close modal"
            >
              &times;
            </button>
          </div>
          <div id="modalBody"></div>
        </div>
      </div>

      <!-- Photo Viewer -->
      <div id="photoViewer" class="photo-viewer" role="dialog" aria-modal="true" aria-label="Photo viewer">
        <button class="viewer-close" aria-label="Close viewer">&times;</button>
        <img id="viewerImg" src="" alt="Full size photo">
        <div class="viewer-actions">
          <button id="viewerDelete" class="red">🗑️ Delete</button>
          <button id="viewerShare">📤 Share</button>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    const drawer = document.getElementById('drawer');
    const backdrop = document.getElementById('menuBackdrop');
    const menuBtn = document.getElementById('menuBtn');
    const themeToggle = document.getElementById('themeToggle');
    const fab = document.getElementById('fab');
    const modalBackdrop = document.getElementById('modalBackdrop');
    const modalClose = document.getElementById('modalClose');
    const notifBtn = document.getElementById('notifBtn');

    // Menu open/close
    const toggleMenu = () => {
      this._menuOpen = !this._menuOpen;
      drawer.classList.toggle('open', this._menuOpen);
      backdrop.classList.toggle('open', this._menuOpen);
      drawer.setAttribute('aria-hidden', String(!this._menuOpen));
      menuBtn.setAttribute('aria-expanded', String(this._menuOpen));
      if (this._menuOpen) {
        const firstBtn = drawer.querySelector('button');
        if (firstBtn) firstBtn.focus();
      }
    };

    menuBtn.addEventListener('click', toggleMenu);
    backdrop.addEventListener('click', toggleMenu);
    this._listeners.push({ el: menuBtn, type: 'click', fn: toggleMenu });
    this._listeners.push({ el: backdrop, type: 'click', fn: toggleMenu });

    // Keyboard: Escape closes menu
    const keyHandler = e => {
      if (e.key === 'Escape') {
        if (this._menuOpen) {
          toggleMenu();
        }
        if (this._modalOpen) {
          this.closeModal();
        }
      }
    };
    document.addEventListener('keydown', keyHandler);
    this._listeners.push({ el: document, type: 'keydown', fn: keyHandler });

    // Theme toggle
    themeToggle.addEventListener('click', () => {
      const newTheme = state.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('ww_theme', newTheme);
      if (state.dispatch) state.dispatch({ type: 'SET_THEME', theme: newTheme });
    });
    this._listeners.push({ el: themeToggle, type: 'click' });

    // Navigation drawer links
    drawer.querySelectorAll('button[data-page]').forEach(btn => {
      const handler = () => {
        const page = btn.dataset.page;
        toggleMenu();
        if (state.navigate) state.navigate(page);
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Drawer action buttons
    drawer.querySelectorAll('button[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;
        toggleMenu();
        if (action === 'sync' && state.onSync) state.onSync();
        if (action === 'settings' && state.navigate) state.navigate('settings');
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Bottom nav
    document.querySelectorAll('.nav-tab[data-page]').forEach(btn => {
      const handler = () => {
        const page = btn.dataset.page;
        if (state.navigate) state.navigate(page);
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // FAB
    if (fab) {
      const fabHandler = () => {
        if (state.onFabClick) state.onFabClick();
      };
      fab.addEventListener('click', fabHandler);
      this._listeners.push({ el: fab, type: 'click', fn: fabHandler });
    }

    // Modal close
    modalClose.addEventListener('click', () => this.closeModal());
    this._listeners.push({ el: modalClose, type: 'click' });

    // Close modal on backdrop click
    modalBackdrop.addEventListener('click', e => {
      if (e.target === modalBackdrop) this.closeModal();
    });

    // Notification button
    if (notifBtn) {
      const notifHandler = () => {
        if (state.onNotifications) state.onNotifications();
      };
      notifBtn.addEventListener('click', notifHandler);
      this._listeners.push({ el: notifBtn, type: 'click', fn: notifHandler });
    }

    // Scroll reveal observer
    this._initScrollReveal();
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => {
      if (fn) el.removeEventListener(type, fn);
    });
    this._listeners = [];
    if (this._scrollObserver) {
      this._scrollObserver.disconnect();
      this._scrollObserver = null;
    }
  },

  // ─── Modal API ───
  openModal(title, bodyHtml, onClose) {
    const backdrop = document.getElementById('modalBackdrop');
    const titleEl = document.getElementById('modalTitle');
    const bodyEl = document.getElementById('modalBody');
    if (!backdrop || !titleEl || !bodyEl) return;
    titleEl.textContent = title;
    bodyEl.innerHTML = bodyHtml;
    this._modalOpen = true;
    this._modalCallback = onClose;
    backdrop.classList.add('open');
    backdrop.setAttribute('aria-hidden', 'false');
    // Focus trap: focus close button initially
    const closeBtn = document.getElementById('modalClose');
    if (closeBtn) closeBtn.focus();
  },

  closeModal() {
    const backdrop = document.getElementById('modalBackdrop');
    if (!backdrop) return;
    backdrop.classList.remove('open');
    backdrop.setAttribute('aria-hidden', 'true');
    this._modalOpen = false;
    if (this._modalCallback) {
      this._modalCallback();
      this._modalCallback = null;
    }
  },

  // ─── Toast API ───
  showToast(message, type = 'success', duration = 3000) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.className = `toast ${type}`;
    toast.textContent = message;
    requestAnimationFrame(() => toast.classList.add('show'));
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => {
      toast.classList.remove('show');
    }, duration);
  },

  // ─── Loading API ───
  showLoading(message = 'Loading...') {
    const overlay = document.getElementById('loadingOverlay');
    const text = document.getElementById('loadingText');
    if (overlay) overlay.classList.add('active');
    if (text) text.textContent = message;
  },

  hideLoading() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.remove('active');
  },

  // ─── Helpers ───
  _getPageTitle(page) {
    const titles = {
      dashboard: '🏠 Dashboard',
      jobs: '🦝 Jobs',
      gps: '📍 GPS Map',
      estimate: '💵 Estimator',
      ai: '🧠 AI Assistant',
      customers: '👥 Customers',
      photos: '📸 Photos',
      metrics: '📊 Metrics',
      settings: '⚙️ Settings'
    };
    return titles[page] || 'Wildlife Whisperer';
  },

  _timeAgo(date) {
    const now = Date.now();
    const d = new Date(date).getTime();
    const diff = Math.floor((now - d) / 1000);
    if (diff < 60) return 'just now';
    if (diff < 3600) return Math.floor(diff / 60) + 'm ago';
    if (diff < 86400) return Math.floor(diff / 3600) + 'h ago';
    return Math.floor(diff / 86400) + 'd ago';
  },

  _initScrollReveal() {
    const revealEls = document.querySelectorAll('.reveal');
    if (!revealEls.length) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      revealEls.forEach(el => el.classList.add('visible'));
      return;
    }
    this._scrollObserver = new IntersectionObserver(
      entries => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            this._scrollObserver.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.1, rootMargin: '0px 0px -20px 0px' }
    );
    revealEls.forEach(el => this._scrollObserver.observe(el));
  }
};
