/**
 * Wildlife Whisperer FieldOps — Configuration
 *
 * ALL API keys and secrets MUST come from environment variables.
 * There are NO hardcoded credentials in this file.
 *
 * Uses Vite's import.meta.env for build-time variable injection.
 * Falls back to empty strings (features gracefully degrade).
 *
 * @module config
 * @version 3.0.0
 */

// ═══════════════════════════════════════════════════
// Environment-based Configuration
// ═══════════════════════════════════════════════════

/** @type {Record<string, string>} */
const env = typeof import.meta !== 'undefined' && import.meta.env
  ? import.meta.env
  : {};

/** @type {string} Supabase project URL */
const SUPABASE_URL = env.VITE_SUPABASE_URL || '';

/** @type {string} Supabase anonymous/public API key */
const SUPABASE_ANON_KEY = env.VITE_SUPABASE_ANON_KEY || '';

/** @type {string} Google Maps JavaScript API key */
const GOOGLE_MAPS_API = env.VITE_GOOGLE_MAPS_API || env.VITE_GOOGLE_MAPS_API_KEY || '';

/** @type {string} Google Calendar OAuth client ID */
const GOOGLE_CALENDAR_CLIENT_ID = env.VITE_GOOGLE_CALENDAR_CLIENT_ID || '';

/** @type {string} OpenWeatherMap API key */
const OPENWEATHER_API_KEY = env.VITE_OPENWEATHER_API_KEY || '';

// ═══════════════════════════════════════════════════
// Key Validation
// ═══════════════════════════════════════════════════

/**
 * Validate that an API key is real (not a placeholder).
 * Rejects empty strings, short values, and common placeholder patterns.
 * @param {string} key
 * @param {number} [minLen=20]
 * @returns {boolean}
 */
function isValidKey(key, minLen = 20) {
  if (!key || typeof key !== 'string') return false;
  const k = key.trim();
  if (k.length < minLen) return false;
  const bad = ['your-', 'YOUR_', 'example', 'placeholder', 'xxx', 'testkey', 'demo_key', 'changeme', 'replace_me'];
  return !bad.some((b) => k.toLowerCase().includes(b.toLowerCase()));
}

// ═══════════════════════════════════════════════════
// Application Config Object
// ═══════════════════════════════════════════════════

/**
 * Application configuration — all settings in one place.
 * @readonly
 */
export const config = Object.freeze({
  // ── API Keys (from env only) ──
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  GOOGLE_MAPS_API,
  GOOGLE_MAPS_API_KEY: GOOGLE_MAPS_API,
  GOOGLE_CALENDAR_CLIENT_ID,
  OPENWEATHER_API_KEY,

  // ── Feature Flags ──
  /** Whether Supabase sync is configured */
  hasSupabase: isValidKey(SUPABASE_URL, 10) && isValidKey(SUPABASE_ANON_KEY, 10),
  /** Whether Google Maps is configured */
  hasGoogleMaps: isValidKey(GOOGLE_MAPS_API),
  /** Whether Google Calendar is configured */
  hasGoogleCalendar: isValidKey(GOOGLE_CALENDAR_CLIENT_ID, 10),
  /** Whether Weather API is configured */
  hasWeather: isValidKey(OPENWEATHER_API_KEY),

  // ── Version ──
  APP_VERSION: '3.0.0',
  BUILD_DATE: new Date().toISOString().slice(0, 10),

  // ── Timing ──
  /** Auto-sync interval: 5 minutes */
  SYNC_INTERVAL: 5 * 60 * 1000,
  /** Snapshot backup interval: 30 seconds */
  SNAPSHOT_INTERVAL: 30 * 1000,
  /** Toast display duration: 3 seconds */
  TOAST_DURATION: 3000,
  /** Debounce delay for search input: 250ms */
  SEARCH_DEBOUNCE: 250,
  /** GPS timeout: 12 seconds */
  GPS_TIMEOUT: 12000,
  /** Loading spinner timeout before warning: 15 seconds */
  LOADING_TIMEOUT: 15000,

  // ── Financial ──
  DEFAULT_TAX_RATE: 0.0875, // 8.875%
  DEFAULT_PROFIT_BUFFER: 1.35,

  // ── Image ──
  IMAGE_MAX_WIDTH: 1200,
  IMAGE_QUALITY: 0.7,
  THUMBNAIL_MAX_WIDTH: 400,

  // ── Pagination ──
  DEFAULT_PAGE_SIZE: 25,

  // ── Map ──
  DEFAULT_MAP_CENTER: Object.freeze({ lat: 40.7128, lng: -74.006 }), // NYC
  DEFAULT_MAP_ZOOM: 12,
  DETAIL_MAP_ZOOM: 16,

  // ── Limits ──
  MAX_PHOTOS_PER_JOB: 100,
  MAX_FILE_SIZE_MB: 10,
  MAX_SYNC_QUEUE: 500,
});

// ═══════════════════════════════════════════════════
// Feature Detection
// ═══════════════════════════════════════════════════

/**
 * Check if a feature is available given current configuration.
 * @param {'supabase'|'googleMaps'|'googleCalendar'|'weather'|'notifications'|'geolocation'|'speechRecognition'} feature
 * @returns {boolean}
 */
export function isFeatureAvailable(feature) {
  switch (feature) {
    case 'supabase': return config.hasSupabase;
    case 'googleMaps': return config.hasGoogleMaps;
    case 'googleCalendar': return config.hasGoogleCalendar;
    case 'weather': return config.hasWeather;
    case 'notifications': return 'Notification' in window;
    case 'geolocation': return 'geolocation' in navigator;
    case 'speechRecognition': return 'SpeechRecognition' in window || 'webkitSpeechRecognition' in window;
    default: return false;
  }
}

// ═══════════════════════════════════════════════════
// Build Info
// ═══════════════════════════════════════════════════

/**
 * Get a build summary string for the settings page.
 * @returns {string}
 */
export function getBuildInfo() {
  return [
    `Version: ${config.APP_VERSION}`,
    `Build Date: ${config.BUILD_DATE}`,
    `Supabase: ${config.hasSupabase ? '✅' : '❌'}`,
    `Google Maps: ${config.hasGoogleMaps ? '✅' : '❌'}`,
    `Google Calendar: ${config.hasGoogleCalendar ? '✅' : '❌'}`,
    `Weather: ${config.hasWeather ? '✅' : '❌'}`,
  ].join(' | ');
}