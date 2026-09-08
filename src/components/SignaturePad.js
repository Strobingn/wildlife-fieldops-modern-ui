/**
 * SignaturePad.js — Signature capture component (standalone page + reusable)
 * Canvas with touch and mouse support, pen styling, clear, save, preview
 */

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

export const SignaturePad = {
  _listeners: [],
  _drawing: false,
  _ctx: null,
  _canvas: null,
  _hasDrawing: false,
  _sigImage: null,

  render(state) {
    const jobId = state.selectedJobId;
    const jobs = state.jobs || [];
    const signatures = (state.photos || []).filter(
      p => p.tag === 'Customer signature' && (jobId ? p.job_id === jobId : true)
    );

    return /* html */ `
      <div class="card stack">
        <h2>✍️ Signature Capture</h2>
        <p class="tiny">Have your customer sign on the screen using finger or stylus.</p>
      </div>

      <!-- Job Selector (if no job selected) -->
      ${
        !jobId
          ? `<div class="card">
            <label for="sigJobSelect">Select Job</label>
            <select id="sigJobSelect">
              <option value="">-- Choose a job --</option>
              ${jobs
                .filter(j => j.status !== 'Closed' && j.status !== 'Cancelled')
                .map(j => `<option value="${j.id}">${E(j.title || j.species + ' job')} — ${E(j.customer)}</option>`)
                .join('')}
            </select>
           </div>`
          : `<div class="card">
            <div class="section-title" style="margin-top:0;">Job</div>
            <div style="font-size:14px;">${E(jobs.find(j => j.id === jobId)?.title || 'Selected job')}</div>
           </div>`
      }

      <!-- Signer Name -->
      <div class="card">
        <label for="sigName">Signer Name</label>
        <input type="text" id="sigName" placeholder="Customer full name" value="${E(this._signerName || '')}">
      </div>

      <!-- Canvas -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Sign Here</div>
        <div class="sig-canvas-wrap" id="sigCanvasWrap">
          <canvas id="sigCanvas" aria-label="Signature pad. Draw your signature with finger, mouse, or stylus."></canvas>
        </div>
        <div style="display:flex;gap:8px;margin-top:10px;">
          <button class="action dark" data-action="clear-sig" style="margin-top:0;flex:1;">🧹 Clear</button>
          <button class="action" data-action="save-sig" style="margin-top:0;flex:1;">💾 Save Signature</button>
        </div>
        <div class="tiny" id="sigStatus">${this._hasDrawing ? '✏️ Signature captured' : '✋ Waiting for signature...'}</div>
      </div>

      <!-- Saved Signatures -->
      ${
        signatures.length
          ? `<div class="section-title">Saved Signatures (${signatures.length})</div>
           ${signatures
             .map(
               s => `
             <div class="card">
               <img src="${E(s.image_url || s.data || '')}" alt="Customer signature" class="sig-preview">
               <div class="tiny">${E(s.notes || s.name || 'Customer signature')} &middot; ${s.created_at ? new Date(s.created_at).toLocaleDateString() : ''}</div>
             </div>
           `
             )
             .join('')}`
          : ''
      }
    `;
  },

  afterRender(state) {
    this._initCanvas();

    // Track signer name
    const nameEl = document.getElementById('sigName');
    if (nameEl) {
      const handler = () => {
        this._signerName = nameEl.value;
      };
      nameEl.addEventListener('input', handler);
      this._listeners.push({ el: nameEl, type: 'input', fn: handler });
    }

    // Job selector
    const jobSelect = document.getElementById('sigJobSelect');
    if (jobSelect) {
      const handler = () => {
        if (jobSelect.value) {
          state.setSelectedJobId?.(jobSelect.value);
          state.rerender?.();
        }
      };
      jobSelect.addEventListener('change', handler);
      this._listeners.push({ el: jobSelect, type: 'change', fn: handler });
    }

    // Buttons
    document.querySelectorAll('[data-action]').forEach(btn => {
      const handler = () => {
        const action = btn.dataset.action;

        if (action === 'clear-sig') {
          this._clear();
        }

        if (action === 'save-sig') {
          if (!this._hasDrawing) {
            state.showToast?.('Please draw a signature first', 'warn');
            return;
          }
          const jobId = state.selectedJobId;
          if (!jobId) {
            state.showToast?.('Please select a job first', 'warn');
            return;
          }
          const name = document.getElementById('sigName')?.value?.trim() || 'Customer';
          const dataUrl = this._canvas.toDataURL('image/png');
          state.onSaveSignature?.(jobId, dataUrl, name);
          this._clear();
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._drawing = false;
    this._ctx = null;
    this._canvas = null;
    this._hasDrawing = false;
  },

  // ─── Canvas API ───
  _initCanvas() {
    this._canvas = document.getElementById('sigCanvas');
    if (!this._canvas) return;

    const wrap = document.getElementById('sigCanvasWrap');
    if (wrap) {
      this._canvas.width = wrap.clientWidth;
      this._canvas.height = 200;
    }

    this._ctx = this._canvas.getContext('2d');
    this._ctx.lineWidth = 2;
    this._ctx.lineCap = 'round';
    this._ctx.lineJoin = 'round';
    this._ctx.strokeStyle = '#000000';

    const getPos = e => {
      const rect = this._canvas.getBoundingClientRect();
      const clientX = e.touches ? e.touches[0].clientX : e.clientX;
      const clientY = e.touches ? e.touches[0].clientY : e.clientY;
      return { x: clientX - rect.left, y: clientY - rect.top };
    };

    const start = e => {
      this._drawing = true;
      this._hasDrawing = true;
      const p = getPos(e);
      this._ctx.beginPath();
      this._ctx.moveTo(p.x, p.y);
    };

    const move = e => {
      if (!this._drawing) return;
      if (e.touches) e.preventDefault();
      const p = getPos(e);
      this._ctx.lineTo(p.x, p.y);
      this._ctx.stroke();
    };

    const end = () => {
      this._drawing = false;
      this._ctx.closePath();
      const status = document.getElementById('sigStatus');
      if (status) status.textContent = '✏️ Signature captured';
    };

    // Mouse events
    this._canvas.addEventListener('mousedown', start);
    this._canvas.addEventListener('mousemove', move);
    this._canvas.addEventListener('mouseup', end);
    this._canvas.addEventListener('mouseleave', end);

    // Touch events
    this._canvas.addEventListener('touchstart', start, { passive: true });
    this._canvas.addEventListener('touchmove', move, { passive: false });
    this._canvas.addEventListener('touchend', end);

    // Store for cleanup
    this._canvasHandlers = { start, move, end };
  },

  _clear() {
    if (!this._ctx || !this._canvas) return;
    this._ctx.clearRect(0, 0, this._canvas.width, this._canvas.height);
    this._hasDrawing = false;
    const status = document.getElementById('sigStatus');
    if (status) status.textContent = '✋ Waiting for signature...';
  },

  // External API for JobDetail to use
  clearCanvas() {
    this._clear();
  },

  getSignatureData() {
    if (!this._hasDrawing || !this._canvas) return null;
    return this._canvas.toDataURL('image/png');
  }
};
