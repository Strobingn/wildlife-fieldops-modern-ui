/**
 * PhotoGallery.js — Photo management page
 * Grid view, upload, tag selector, full-screen viewer, delete, compress
 */

import { PHOTO_TAGS } from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

export const PhotoGallery = {
  _listeners: [],
  _selectedTag: '',
  _selectedJobId: '',
  _viewerOpen: false,
  _viewerImage: null,

  render(state) {
    const allPhotos = state.photos || [];
    const jobs = state.jobs || [];

    // Filter photos
    let photos = allPhotos;
    if (this._selectedTag) photos = photos.filter(p => p.tag === this._selectedTag);
    if (this._selectedJobId) photos = photos.filter(p => p.job_id === this._selectedJobId);

    // Tag counts
    const tagCounts = {};
    allPhotos.forEach(p => {
      tagCounts[p.tag || 'Untagged'] = (tagCounts[p.tag || 'Untagged'] || 0) + 1;
    });

    return /* html */ `
      <!-- Header -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
        <h2 style="margin-bottom:0;">📸 Photos (${photos.length})</h2>
        <label for="galleryUpload" class="action" style="margin-top:0;width:auto;padding:10px 16px;font-size:13px;cursor:pointer;display:inline-block;text-align:center;">
          📤 Upload
        </label>
        <input type="file" id="galleryUpload" accept="image/*" multiple style="display:none;" aria-label="Upload photos">
      </div>

      <!-- Filters -->
      <div class="filter-bar">
        <select id="galleryTagFilter" aria-label="Filter by tag">
          <option value="">All Tags</option>
          ${Object.entries(tagCounts)
            .sort((a, b) => b[1] - a[1])
            .map(
              ([tag, count]) =>
                `<option value="${E(tag)}" ${this._selectedTag === tag ? 'selected' : ''}>${E(tag)} (${count})</option>`
            )
            .join('')}
        </select>
        <select id="galleryJobFilter" aria-label="Filter by job">
          <option value="">All Jobs</option>
          ${jobs.map(j => `<option value="${j.id}" ${this._selectedJobId === j.id ? 'selected' : ''}>${E(j.title || j.species + ' job')}</option>`).join('')}
        </select>
        ${
          this._selectedTag || this._selectedJobId
            ? `<button class="action dark" data-action="clear-filters" style="margin-top:0;width:auto;padding:8px 12px;font-size:12px;">Clear</button>`
            : ''
        }
      </div>

      <!-- Photo Grid -->
      <div id="photoGalleryGrid">
        ${
          photos.length
            ? `<div class="photo-grid">
              ${photos
                .map(
                  (p, idx) => `
                <div
                  class="photo-grid-item"
                  data-photo="${E(p.image_url || p.data || '')}"
                  data-index="${idx}"
                  data-id="${p.id}"
                  tabindex="0"
                  role="button"
                  aria-label="Photo: ${E(p.tag || 'untagged')}"
                >
                  <img
                    src="${E(p.image_url || p.data || '')}"
                    alt="${E(p.tag || 'Job photo')}"
                    loading="lazy"
                  >
                  <span class="photo-tag">${E(p.tag || '')}</span>
                </div>
              `
                )
                .join('')}
             </div>`
            : `<div class="empty-state">
              <div class="empty-icon" aria-hidden="true">📷</div>
              <h4>${this._selectedTag || this._selectedJobId ? 'No matching photos' : 'No photos yet'}</h4>
              <p>${this._selectedTag || this._selectedJobId ? 'Try different filters.' : 'Upload photos from your jobs.'}</p>
             </div>`
        }
      </div>

      <!-- Batch Upload Area -->
      <div class="card" style="margin-top:16px;">
        <div class="section-title" style="margin-top:0;">Batch Upload</div>
        <label for="galleryUpload2" style="display:block;padding:24px;border:2px dashed var(--border);border-radius:var(--radius);text-align:center;cursor:pointer;color:var(--muted);transition:var(--transition);" onmouseover="this.style.borderColor='var(--accent)'" onmouseout="this.style.borderColor='var(--border)'">
          <div style="font-size:32px;margin-bottom:8px;">📁</div>
          <div>Tap to select photos</div>
          <div class="tiny">JPG, PNG supported</div>
        </label>
        <input type="file" id="galleryUpload2" accept="image/*" multiple style="display:none;" aria-label="Batch upload photos">
        <label for="batchTag">Default Tag</label>
        <select id="batchTag" style="margin-top:4px;">
          ${PHOTO_TAGS.filter(t => t !== 'Customer signature')
            .map(t => `<option value="${E(t)}">${E(t)}</option>`)
            .join('')}
        </select>
      </div>
    `;
  },

  afterRender(state) {
    // Filter changes
    const tagFilter = document.getElementById('galleryTagFilter');
    const jobFilter = document.getElementById('galleryJobFilter');

    if (tagFilter) {
      const handler = () => {
        this._selectedTag = tagFilter.value;
        state.rerender?.();
      };
      tagFilter.addEventListener('change', handler);
      this._listeners.push({ el: tagFilter, type: 'change', fn: handler });
    }
    if (jobFilter) {
      const handler = () => {
        this._selectedJobId = jobFilter.value;
        state.rerender?.();
      };
      jobFilter.addEventListener('change', handler);
      this._listeners.push({ el: jobFilter, type: 'change', fn: handler });
    }

    // Photo click to view
    const grid = document.getElementById('photoGalleryGrid');
    if (grid) {
      const handler = e => {
        const item = e.target.closest('.photo-grid-item');
        if (!item) return;
        this._viewerImage = {
          id: item.dataset.id,
          url: item.dataset.photo,
          tag: item.querySelector('.photo-tag')?.textContent || ''
        };
        this._openViewer(state);
      };
      grid.addEventListener('click', handler);
      this._listeners.push({ el: grid, type: 'click', fn: handler });
    }

    // File uploads
    ['galleryUpload', 'galleryUpload2'].forEach(id => {
      const input = document.getElementById(id);
      if (input) {
        const handler = async () => {
          const files = Array.from(input.files || []);
          if (!files.length) return;
          const tag = document.getElementById('batchTag')?.value || 'Before';
          state.showLoading?.('Processing photos...');
          for (const file of files) {
            try {
              const compressed = await this._compressImage(file);
              state.onUploadPhoto?.(null, compressed, tag, '');
            } catch (err) {
              state.showToast?.('Failed to process ' + file.name, 'error');
            }
          }
          state.hideLoading?.();
          state.rerender?.();
        };
        input.addEventListener('change', handler);
        this._listeners.push({ el: input, type: 'change', fn: handler });
      }
    });

    // Clear filters
    document.querySelectorAll("[data-action='clear-filters']").forEach(btn => {
      const handler = () => {
        this._selectedTag = '';
        this._selectedJobId = '';
        state.rerender?.();
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._selectedTag = '';
    this._selectedJobId = '';
    this._viewerOpen = false;
    this._viewerImage = null;
  },

  _openViewer(state) {
    const viewer = document.getElementById('photoViewer');
    const img = document.getElementById('viewerImg');
    if (!viewer || !img || !this._viewerImage) return;
    img.src = this._viewerImage.url;
    img.alt = this._viewerImage.tag || 'Photo';
    viewer.classList.add('open');
    this._viewerOpen = true;

    // Close handler
    const closeBtn = viewer.querySelector('.viewer-close');
    const closeHandler = () => this._closeViewer();
    closeBtn.addEventListener('click', closeHandler);
    this._listeners.push({ el: closeBtn, type: 'click', fn: closeHandler });

    // Delete handler
    const deleteBtn = document.getElementById('viewerDelete');
    if (deleteBtn) {
      const deleteHandler = () => {
        if (confirm('Delete this photo?')) {
          state.onDeletePhoto?.(this._viewerImage.id);
          this._closeViewer();
          state.rerender?.();
        }
      };
      deleteBtn.addEventListener('click', deleteHandler);
      this._listeners.push({ el: deleteBtn, type: 'click', fn: deleteHandler });
    }

    // Keyboard
    const keyHandler = e => {
      if (e.key === 'Escape') this._closeViewer();
    };
    document.addEventListener('keydown', keyHandler);
    this._listeners.push({ el: document, type: 'keydown', fn: keyHandler });
  },

  _closeViewer() {
    const viewer = document.getElementById('photoViewer');
    if (viewer) viewer.classList.remove('open');
    this._viewerOpen = false;
  },

  async _compressImage(file, maxWidth = 1200, quality = 0.7) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement('canvas');
          let w = img.width,
            h = img.height;
          if (w > maxWidth) {
            h = Math.round((h * maxWidth) / w);
            w = maxWidth;
          }
          canvas.width = w;
          canvas.height = h;
          canvas.getContext('2d').drawImage(img, 0, 0, w, h);
          resolve(canvas.toDataURL('image/jpeg', quality));
        };
        img.onerror = reject;
        img.src = reader.result;
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }
};
