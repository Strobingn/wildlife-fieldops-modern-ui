/**
 * @module api
 * @description Barrel export for the Wildlife Whisperer FieldOps API layer.
 * All modules are fully implemented with offline-first support, retry logic,
 * optimistic updates, and graceful degradation.
 */

// ─── Supabase Client ─────────────────────────────────────────────────────────
export {
  supabase,
  checkHealth,
  startHealthChecks,
  getConnectionState,
  getLastHealthCheck,
  onConnectionChange
} from './supabaseClient.js';

// ─── Sync Queue ──────────────────────────────────────────────────────────────
export { syncQueue, queueInsert, queueUpdate, queueDelete, queueUpsert, processSyncQueue, setupSync } from './sync.js';

// ─── Jobs ────────────────────────────────────────────────────────────────────
export {
  getJobs,
  getJobById,
  createJob,
  updateJob,
  deleteJob,
  searchJobs,
  getJobStats,
  getJobsBySpecies,
  getJobsByStatus,
  getJobsByTown,
  getRevenueByTech,
  addService,
  removeService
} from './jobs.js';

// ─── Customers ───────────────────────────────────────────────────────────────
export {
  getCustomers,
  getCustomerById,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  searchCustomers,
  getCustomerHistory,
  mergeCustomers,
  findDuplicateCustomers
} from './customers.js';

// ─── Photos ──────────────────────────────────────────────────────────────────
export {
  uploadPhoto,
  getPhotosByJob,
  deletePhoto,
  compressImage,
  getPhotoDisplayUrl,
  uploadPhotosBatch
} from './photos.js';

// ─── Weather ─────────────────────────────────────────────────────────────────
export { getWeather, getForecast, clearWeatherCache, getCacheAge } from './weather.js';

// ─── Maps ────────────────────────────────────────────────────────────────────
export {
  initMap,
  getMap,
  destroyMap,
  addMarker,
  clearMarkers,
  fitBounds,
  fitToMarkers,
  getDirectionsUrl,
  navigateTo,
  geocodeAddress,
  reverseGeocode,
  initAutocomplete,
  loadGoogleMaps
} from './maps.js';

// ─── Calendar ────────────────────────────────────────────────────────────────
export {
  initGoogleCalendar,
  createCalendarEvent,
  requestAuth,
  revokeAuth,
  listCalendarEvents,
  deleteCalendarEvent,
  isCalendarConfigured,
  isCalendarReady,
  hasAuth
} from './calendar.js';

// ─── PDF Generation ──────────────────────────────────────────────────────────
export {
  generateJobPDF,
  generateEstimatePDF,
  generateInvoicePDF,
  downloadJobPDF,
  downloadEstimatePDF,
  downloadInvoicePDF
} from './pdf.js';
