/**
 * @module api/maps
 * @description Google Maps integration with lazy loading, dark theme styles,
 * marker management, bounds fitting, navigation URLs, and geocoding.
 */

import { config } from '../config.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const GOOGLE_MAPS_API_KEY = config.GOOGLE_MAPS_API_KEY || '';
const SCRIPT_ID = 'google-maps-script';
const DEFAULT_CENTER = { lat: 40.7128, lng: -74.006 }; // NYC default
const DEFAULT_ZOOM = 12;

// ─── Module State ────────────────────────────────────────────────────────────

let scriptLoading = false;
let _scriptLoaded = false;
let loadCallbacks = [];
const activeMaps = new Map();
const activeMarkers = new Map();

// ─── Dark Theme Map Styles ───────────────────────────────────────────────────

const GRAYSCALE_MAP_STYLES = [
  { elementType: 'geometry', stylers: [{ color: '#242424' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#bdbdbd' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#242424' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ color: '#4a4a4a' }] },
  { featureType: 'poi', elementType: 'geometry', stylers: [{ color: '#303030' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ color: '#2a2a2a' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#505050' }] },
  { featureType: 'road.highway', elementType: 'geometry', stylers: [{ color: '#686868' }] },
  { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#3c3c3c' }] },
  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#181818' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#8f8f8f' }] },
];

const DARK_THEME_STYLES = GRAYSCALE_MAP_STYLES;
const LIGHT_THEME_STYLES = GRAYSCALE_MAP_STYLES;

// ─── API Key Validation ──────────────────────────────────────────────────────

function hasApiKey() {
  return config.hasGoogleMaps;
}

// ─── Script Loader ───────────────────────────────────────────────────────────

/**
 * Lazy-load the Google Maps script. Only loads once; subsequent calls resolve immediately.
 *
 * @param {boolean} [loadPlaces=false] - Also load Places library
 * @returns {Promise<boolean>}
 */
export async function loadGoogleMaps(loadPlaces = true) {
  if (!hasApiKey()) {
    console.log('[maps] No Google Maps API key configured');
    return false;
  }

  // Already loaded
  if (window.google?.maps) {
    _scriptLoaded = true;
    return true;
  }

  // Already loading, wait for it
  if (scriptLoading) {
    return new Promise((resolve) => loadCallbacks.push(resolve));
  }

  scriptLoading = true;

  return new Promise((resolve) => {
    const libraries = loadPlaces ? 'places' : '';
    const existing = document.getElementById(SCRIPT_ID);
    if (existing) {
      // Script tag exists but not loaded yet
      const checkLoaded = setInterval(() => {
        if (window.google?.maps) {
          clearInterval(checkLoaded);
          _scriptLoaded = true;
          resolve(true);
        }
      }, 100);
      return;
    }

    // Create callback
    const callbackName = `_onGoogleMapsLoaded_${Date.now()}`;
    window[callbackName] = () => {
      _scriptLoaded = true;
      delete window[callbackName];
      loadCallbacks.forEach((cb) => cb(true));
      loadCallbacks = [];
      resolve(true);
    };

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = `https://maps.googleapis.com/maps/api/js?key=${GOOGLE_MAPS_API_KEY}&callback=${callbackName}&libraries=${libraries}`;
    script.async = true;
    script.defer = true;

    script.onerror = () => {
      console.error('[maps] Failed to load Google Maps script');
      scriptLoading = false;
      resolve(false);
    };

    document.head.appendChild(script);
  });
}

// ─── Map Initialization ──────────────────────────────────────────────────────

/**
 * Initialize a Google Map in the specified container.
 *
 * @param {string} containerId - DOM element ID
 * @param {Object} [options={}] - Map options
 * @param {Object} [options.center={lat, lng}] - Initial center
 * @param {number} [options.zoom=12] - Initial zoom
 * @param {boolean} [options.darkTheme=true] - Use dark theme
 * @param {boolean} [options.zoomControl=true]
 * @param {boolean} [options.streetViewControl=false]
 * @param {boolean} [options.mapTypeControl=false]
 * @param {boolean} [options.fullscreenControl=true]
 * @returns {Promise<Object|null>} Google Map instance or null
 */
export async function initMap(containerId, options = {}) {
  const loaded = await loadGoogleMaps(true);
  if (!loaded) {
    const el = document.getElementById(containerId);
    if (el) {
      el.innerHTML = `<div style="padding:16px;text-align:center;color:var(--muted);">Configure the GOOGLE_MAPS_API build secret to enable the map.</div>`;
    }
    return null;
  }

  const el = document.getElementById(containerId);
  if (!el) {
    console.error(`[maps] Container element "${containerId}" not found`);
    return null;
  }

  const {
    center = DEFAULT_CENTER,
    zoom = DEFAULT_ZOOM,
    darkTheme = true,
    zoomControl = true,
    streetViewControl = false,
    mapTypeControl = false,
    fullscreenControl = true,
  } = options;

  const map = new google.maps.Map(el, {
    center,
    zoom,
    zoomControl,
    streetViewControl,
    mapTypeControl,
    fullscreenControl,
    styles: darkTheme ? DARK_THEME_STYLES : LIGHT_THEME_STYLES,
    gestureHandling: 'cooperative',
  });

  activeMaps.set(containerId, map);
  return map;
}

/**
 * Get an already-initialized map by container ID.
 *
 * @param {string} containerId
 * @returns {Object|null}
 */
export function getMap(containerId) {
  return activeMaps.get(containerId) || null;
}

/**
 * Destroy a map instance and clean up markers.
 *
 * @param {string} containerId
 */
export function destroyMap(containerId) {
  const markers = activeMarkers.get(containerId);
  if (markers) {
    markers.forEach((m) => m.setMap(null));
    activeMarkers.delete(containerId);
  }
  activeMaps.delete(containerId);
}

// ─── Markers ─────────────────────────────────────────────────────────────────

/**
 * Add a marker to a map.
 *
 * @param {Object} map - Google Map instance
 * @param {{lat: number, lng: number}} position
 * @param {string} [title=''] - Hover tooltip
 * @param {Function} [onClick] - Click handler
 * @param {Object} [options={}] - Additional marker options
 * @returns {Object} The created marker
 */
export function addMarker(map, position, title = '', onClick, options = {}) {
  if (!map || !position?.lat || !position?.lng) {
    console.warn('[maps] Invalid map or position for marker');
    return null;
  }

  const marker = new google.maps.Marker({
    position: { lat: parseFloat(position.lat), lng: parseFloat(position.lng) },
    map,
    title,
    animation: options.animation || google.maps.Animation?.DROP,
    icon: options.icon || null,
    ...options,
  });

  if (typeof onClick === 'function') {
    marker.addListener('click', onClick);
  }

  // Track markers for this map
  const containerId = findContainerId(map);
  if (containerId) {
    const existing = activeMarkers.get(containerId) || [];
    existing.push(marker);
    activeMarkers.set(containerId, existing);
  }

  return marker;
}

/**
 * Clear all markers from a map.
 *
 * @param {Object} map - Google Map instance
 */
export function clearMarkers(map) {
  const containerId = findContainerId(map);
  const markers = activeMarkers.get(containerId);
  if (markers) {
    markers.forEach((m) => m.setMap(null));
    activeMarkers.set(containerId, []);
  }
}

// ─── Bounds ──────────────────────────────────────────────────────────────────

/**
 * Fit map bounds to encompass all markers.
 *
 * @param {Object} map - Google Map instance
 * @param {Array<{lat:number, lng:number}>} positions
 * @param {number} [padding=50] - Padding in pixels
 */
export function fitBounds(map, positions, padding = 50) {
  if (!map || !positions?.length) return;

  const bounds = new google.maps.LatLngBounds();
  let hasValid = false;

  positions.forEach((pos) => {
    const lat = parseFloat(pos.lat);
    const lng = parseFloat(pos.lng);
    if (!isNaN(lat) && !isNaN(lng)) {
      bounds.extend({ lat, lng });
      hasValid = true;
    }
  });

  if (hasValid) {
    map.fitBounds(bounds, padding);
  }
}

/**
 * Fit map to its current markers.
 *
 * @param {Object} map - Google Map instance
 * @param {number} [padding=50]
 */
export function fitToMarkers(map, padding = 50) {
  const containerId = findContainerId(map);
  const markers = activeMarkers.get(containerId) || [];
  const positions = markers.map((m) => m.getPosition().toJSON());
  fitBounds(map, positions, padding);
}

// ─── Navigation ──────────────────────────────────────────────────────────────

/**
 * Get a Google Maps navigation URL for a destination.
 * Opens in a new tab.
 *
 * @param {number} lat
 * @param {number} lng
 * @param {string} [address=''] - Fallback if no coordinates
 * @returns {string} Navigation URL
 */
export function getDirectionsUrl(lat, lng, address = '') {
  if (lat && lng && !isNaN(lat) && !isNaN(lng)) {
    return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`;
  }
  if (address) {
    return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}&travelmode=driving`;
  }
  return '';
}

/**
 * Open Google Maps navigation for a destination in a new tab.
 *
 * @param {number} lat
 * @param {number} lng
 * @param {string} [address='']
 */
export function navigateTo(lat, lng, address = '') {
  const url = getDirectionsUrl(lat, lng, address);
  if (url) {
    window.open(url, '_blank');
  } else {
    console.warn('[maps] No valid destination for navigation');
  }
}

// ─── Geocoding ───────────────────────────────────────────────────────────────

/**
 * Convert an address to geographic coordinates.
 *
 * @param {string} address
 * @returns {Promise<{lat: number, lng: number, formattedAddress: string}|null>}
 */
export async function geocodeAddress(address) {
  if (!address || address.trim().length < 3) {
    console.warn('[maps] Address too short for geocoding');
    return null;
  }

  const loaded = await loadGoogleMaps(false);
  if (!loaded) {
    console.log('[maps] Cannot geocode — Google Maps not loaded');
    return null;
  }

  return new Promise((resolve) => {
    const geocoder = new google.maps.Geocoder();
    geocoder.geocode({ address: address.trim() }, (results, status) => {
      if (status === 'OK' && results?.[0]?.geometry?.location) {
        const loc = results[0].geometry.location;
        resolve({
          lat: loc.lat(),
          lng: loc.lng(),
          formattedAddress: results[0].formatted_address,
        });
      } else {
        console.warn(`[maps] Geocoding failed: ${status}`);
        resolve(null);
      }
    });
  });
}

/**
 * Reverse geocode: coordinates to address.
 *
 * @param {number} lat
 * @param {number} lng
 * @returns {Promise<string|null>} Formatted address
 */
export async function reverseGeocode(lat, lng) {
  if (!lat || !lng || isNaN(lat) || isNaN(lng)) {
    console.warn('[maps] Invalid coordinates for reverse geocoding');
    return null;
  }

  const loaded = await loadGoogleMaps(false);
  if (!loaded) return null;

  return new Promise((resolve) => {
    const geocoder = new google.maps.Geocoder();
    geocoder.geocode({ location: { lat: parseFloat(lat), lng: parseFloat(lng) } }, (results, status) => {
      if (status === 'OK' && results?.[0]) {
        resolve(results[0].formatted_address);
      } else {
        console.warn(`[maps] Reverse geocoding failed: ${status}`);
        resolve(null);
      }
    });
  });
}

// ─── Autocomplete ────────────────────────────────────────────────────────────

/**
 * Attach Google Places autocomplete to an input element.
 *
 * @param {HTMLInputElement} inputElement
 * @param {Object} [options={}] - Autocomplete options
 * @returns {Object|null} Autocomplete instance
 */
export async function initAutocomplete(inputElement, options = {}) {
  if (!inputElement) {
    console.warn('[maps] No input element provided for autocomplete');
    return null;
  }

  const loaded = await loadGoogleMaps(true);
  if (!loaded) return null;

  const autocomplete = new google.maps.places.Autocomplete(inputElement, {
    types: ['address'],
    fields: ['formatted_address', 'geometry', 'address_components'],
    ...options,
  });

  return autocomplete;
}

// ─── Internal Helpers ────────────────────────────────────────────────────────

function findContainerId(map) {
  for (const [id, m] of activeMaps.entries()) {
    if (m === map) return id;
  }
  return null;
}
