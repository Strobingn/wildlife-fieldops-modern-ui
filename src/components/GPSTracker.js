/**
 * GPSTracker.js — GPS and map page
 * GPS capture, interactive map with job markers, navigate, town heat map, job list
 */

import { SPECIES_ICONS } from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

export const GPSTracker = {
  _listeners: [],
  _currentGps: null,
  _mapView: 'markers', // "markers" | "heatmap"
  _mapInitialized: false,
  _map: null,
  _markers: [],

  render(state) {
    const jobs = (state.jobs || []).filter(j => j.latitude && j.longitude);
    const isOnline = state.isOnline ?? navigator.onLine;

    // Town heat map data
    const townCounts = {};
    (state.jobs || []).forEach(j => {
      const town = j.town || 'Unsorted';
      if (!townCounts[town]) townCounts[town] = { count: 0, species: {} };
      townCounts[town].count++;
      townCounts[town].species[j.species || 'Other'] = (townCounts[town].species[j.species || 'Other'] || 0) + 1;
    });
    const sortedTowns = Object.entries(townCounts).sort((a, b) => b[1].count - a[1].count);

    return /* html */ `
      <div class="card stack">
        <h2>📍 GPS Tracker</h2>
        <p class="tiny">${jobs.length} job${jobs.length !== 1 ? 's' : ''} with GPS coordinates</p>
      </div>

      <!-- GPS Capture -->
      <div class="card">
        <div class="section-title" style="margin-top:0;">Your Location</div>
        <div style="display:flex;gap:10px;align-items:center;">
          <button class="action" data-action="capture-gps" style="margin-top:0;width:auto;padding:12px 20px;">
            📍 Capture GPS
          </button>
          <button class="action dark" data-action="view-my-location" style="margin-top:0;width:auto;padding:12px 20px;">
            🎯 My Location
          </button>
        </div>
        ${
          this._currentGps
            ? `<div class="tiny" style="margin-top:8px;">
              📍 ${this._currentGps.lat.toFixed(6)}, ${this._currentGps.lng.toFixed(6)}
              ${this._currentGps.accuracy ? `(±${this._currentGps.accuracy}m accuracy)` : ''}
             </div>`
            : `<div class="tiny" style="margin-top:8px;">No GPS captured yet. Tap "Capture GPS" to get your current location.</div>`
        }
      </div>

      <!-- View Toggle -->
      <div style="display:flex;gap:8px;margin-bottom:12px;">
        <button class="${this._mapView === 'markers' ? 'action' : 'action dark'}" data-action="view-markers" style="margin-top:0;flex:1;">📍 Job Markers</button>
        <button class="${this._mapView === 'heatmap' ? 'action' : 'action dark'}" data-action="view-heatmap" style="margin-top:0;flex:1;">🗺️ Town Heat</button>
      </div>

      <!-- Map Container -->
      <div id="gpsMap" class="map-container" role="img" aria-label="Map showing job locations" style="height:400px;">
        ${
          !isOnline
            ? `<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--muted);font-size:14px;">🌐 Connect to internet to view map</div>`
            : `<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--muted);font-size:14px;">🗺️ Map loading...</div>`
        }
      </div>

      ${
        this._mapView === 'heatmap'
          ? `
        <!-- Town Heat Map -->
        <div class="section-title">Town Heat Map</div>
        ${
          sortedTowns.length
            ? sortedTowns
                .map(
                  ([town, data]) => `
            <div class="card">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <b>${E(town)}</b>
                <span class="pill ${data.count > 5 ? 'bad' : data.count > 2 ? 'warn' : ''}">${data.count} job${data.count !== 1 ? 's' : ''}</span>
              </div>
              <div style="margin-top:6px;">
                ${Object.entries(data.species)
                  .sort((a, b) => b[1] - a[1])
                  .map(([sp, n]) => `<span class="pill">${SPECIES_ICONS[sp] || '🐾'} ${E(sp)} ${n}</span>`)
                  .join('')}
              </div>
              <div class="prog" style="margin-top:8px;" role="progressbar" aria-label="${E(town)} job density" aria-valuenow="${Math.min(data.count * 10, 100)}" aria-valuemin="0" aria-valuemax="100">
                <div class="bar" style="width:${Math.min(data.count * 10, 100)}%;opacity:${Math.min(0.3 + data.count * 0.1, 1)};"></div>
              </div>
            </div>
          `
                )
                .join('')
            : `<div class="card tiny">No town data available.</div>`
        }`
          : `
        <!-- Job List with GPS -->
        <div class="section-title">Jobs with GPS (${jobs.length})</div>
        ${
          jobs.length
            ? jobs
                .slice()
                .sort((a, b) => new Date(b.created_at || b.created || 0) - new Date(a.created_at || a.created || 0))
                .map(
                  j => `
            <div class="card" data-job-id="${j.id}" style="cursor:pointer;" tabindex="0" role="button" aria-label="Open job: ${E(j.title || j.species + ' job')}">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <div style="display:flex;align-items:center;gap:8px;min-width:0;">
                  <span style="font-size:20px;">${SPECIES_ICONS[j.species] || '🐾'}</span>
                  <div style="min-width:0;">
                    <div style="font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${E(j.title || j.species + ' job')}</div>
                    <div class="tiny" style="margin-top:0;">${E(j.customer)} &middot; ${E(j.town || 'No town')}</div>
                  </div>
                </div>
                <button class="action dark" data-action="navigate-to" data-lat="${j.latitude}" data-lng="${j.longitude}" data-address="${E(j.address)}" style="margin-top:0;width:auto;padding:8px 12px;font-size:12px;flex-shrink:0;" onclick="event.stopPropagation();">
                  🧭 Navigate
                </button>
              </div>
              <div class="tiny">📍 ${j.latitude}, ${j.longitude}</div>
            </div>
          `
                )
                .join('')
            : `<div class="empty-state">
              <div class="empty-icon" aria-hidden="true">📍</div>
              <h4>No GPS jobs yet</h4>
              <p>Capture GPS when creating or editing jobs to see them on the map.</p>
             </div>`
        }`
      }
    `;
  },

  afterRender(state) {
    // GPS capture
    document.querySelectorAll("[data-action='capture-gps']").forEach(btn => {
      const handler = () => {
        if (!navigator.geolocation) {
          state.showToast?.('GPS not supported on this device', 'error');
          return;
        }
        state.showLoading?.('Getting GPS fix...');
        navigator.geolocation.getCurrentPosition(
          pos => {
            this._currentGps = {
              lat: +pos.coords.latitude.toFixed(6),
              lng: +pos.coords.longitude.toFixed(6),
              accuracy: Math.round(pos.coords.accuracy)
            };
            state.hideLoading?.();
            state.showToast?.(`GPS: ${this._currentGps.lat}, ${this._currentGps.lng} (±${this._currentGps.accuracy}m)`);
            state.rerender?.();
            // Center map on new position
            if (this._map) {
              this._map.setCenter({ lat: this._currentGps.lat, lng: this._currentGps.lng });
            }
          },
          err => {
            state.hideLoading?.();
            state.showToast?.('GPS error: ' + err.message, 'error');
          },
          { enableHighAccuracy: true, timeout: 15000 }
        );
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // View my location
    document.querySelectorAll("[data-action='view-my-location']").forEach(btn => {
      const handler = () => {
        if (!this._currentGps) {
          state.showToast?.('Capture GPS first', 'warn');
          return;
        }
        if (this._map) {
          this._map.setCenter({ lat: this._currentGps.lat, lng: this._currentGps.lng });
          this._map.setZoom(16);
        }
        // Also open maps app
        window.open(`https://www.google.com/maps?q=${this._currentGps.lat},${this._currentGps.lng}`, '_blank');
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // View toggles
    document.querySelectorAll("[data-action='view-markers']").forEach(btn => {
      const handler = () => {
        this._mapView = 'markers';
        state.rerender?.();
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });
    document.querySelectorAll("[data-action='view-heatmap']").forEach(btn => {
      const handler = () => {
        this._mapView = 'heatmap';
        state.rerender?.();
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Navigate to job
    document.querySelectorAll("[data-action='navigate-to']").forEach(btn => {
      const handler = () => {
        const lat = btn.dataset.lat;
        const lng = btn.dataset.lng;
        const addr = btn.dataset.address;
        if (lat && lng && lat !== 'null' && lat !== 'undefined') {
          window.open(`https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`, '_blank');
        } else if (addr) {
          window.open(
            `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(addr)}&travelmode=driving`,
            '_blank'
          );
        }
      };
      btn.addEventListener('click', handler);
      this._listeners.push({ el: btn, type: 'click', fn: handler });
    });

    // Job card clicks
    document.querySelectorAll('[data-job-id][role="button"]').forEach(card => {
      const handler = () => state.navigate?.(`jobs/${card.dataset.jobId}`);
      card.addEventListener('click', handler);
      this._listeners.push({ el: card, type: 'click', fn: handler });
    });

    // Initialize map if online and in markers view
    if ((state.isOnline ?? navigator.onLine) && this._mapView === 'markers' && window.google?.maps) {
      setTimeout(() => this._initMap(state), 100);
    }
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._markers.forEach(m => m.setMap?.(null));
    this._markers = [];
    this._map = null;
    this._mapInitialized = false;
  },

  _initMap(state) {
    const container = document.getElementById('gpsMap');
    if (!container || !window.google?.maps) return;

    const jobs = (state.jobs || []).filter(j => j.latitude && j.longitude);
    if (!jobs.length) {
      container.innerHTML =
        '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--muted);">No GPS jobs to display</div>';
      return;
    }

    // Calculate center from all jobs
    const lats = jobs.map(j => parseFloat(j.latitude));
    const lngs = jobs.map(j => parseFloat(j.longitude));
    const center = {
      lat: lats.reduce((a, b) => a + b, 0) / lats.length,
      lng: lngs.reduce((a, b) => a + b, 0) / lngs.length
    };

    this._map = new google.maps.Map(container, {
      center,
      zoom: 11,
      mapTypeControl: false,
      streetViewControl: false,
      fullscreenControl: false
    });

    // Add markers
    this._markers.forEach(m => m.setMap(null));
    this._markers = [];
    const bounds = new google.maps.LatLngBounds();

    jobs.forEach(j => {
      const pos = { lat: parseFloat(j.latitude), lng: parseFloat(j.longitude) };
      const marker = new google.maps.Marker({
        position: pos,
        map: this._map,
        title: `${j.species} — ${j.customer}`,
        animation: google.maps.Animation.DROP
      });

      const icon = SPECIES_ICONS[j.species] || '🐾';
      const infoContent = `
        <div style="padding:8px;min-width:180px;">
          <div style="font-weight:700;margin-bottom:4px;">${icon} ${j.species}</div>
          <div style="font-size:13px;">${E(j.customer)}</div>
          <div style="font-size:12px;color:#666;">${E(j.address)}</div>
          <div style="margin-top:6px;">
            <a href="#/jobs/${j.id}" style="color:#3b82f6;font-size:12px;">Open Job</a> |
            <a href="https://www.google.com/maps/dir/?api=1&destination=${j.latitude},${j.longitude}&travelmode=driving" target="_blank" style="color:#3b82f6;font-size:12px;">Navigate</a>
          </div>
        </div>
      `;

      const infoWindow = new google.maps.InfoWindow({ content: infoContent });
      marker.addListener('click', () => {
        infoWindow.open(this._map, marker);
      });

      this._markers.push(marker);
      bounds.extend(pos);
    });

    if (jobs.length > 1) {
      this._map.fitBounds(bounds);
    } else {
      this._map.setZoom(14);
    }

    // Add current location marker if available
    if (this._currentGps) {
      new google.maps.Marker({
        position: { lat: this._currentGps.lat, lng: this._currentGps.lng },
        map: this._map,
        title: 'Your location',
        icon: {
          path: google.maps.SymbolPath.CIRCLE,
          scale: 8,
          fillColor: '#3b82f6',
          fillOpacity: 0.8,
          strokeColor: '#fff',
          strokeWeight: 2
        }
      });
    }

    this._mapInitialized = true;
  }
};
