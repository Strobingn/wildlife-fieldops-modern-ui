/**
 * @module api/calendar
 * @description Google Calendar API integration using Google Identity Services (GIS)
 * for OAuth. Supports creating events from jobs and checking API configuration.
 */

import { config } from '../config.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const CLIENT_ID = config.GOOGLE_CALENDAR_CLIENT_ID || '';
const API_KEY = config.GOOGLE_MAPS_API_KEY || '';
const DISCOVERY_DOC = 'https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest';
const SCOPE = 'https://www.googleapis.com/auth/calendar.events';

// ─── Module State ────────────────────────────────────────────────────────────

let gapiLoaded = false;
let gisLoaded = false;
let tokenClient = null;
let accessToken = null;
let authCallback = null;

// ─── API Key Check ───────────────────────────────────────────────────────────

function hasConfig() {
  return config.hasGoogleCalendar;
}

/** @returns {boolean} */
export function isCalendarConfigured() {
  return hasConfig();
}

/** @returns {boolean} */
export function isCalendarReady() {
  return gapiLoaded && gisLoaded && hasConfig();
}

// ─── Script Loading ──────────────────────────────────────────────────────────

/**
 * Initialize the Google Calendar API and Identity Services.
 * Loads required scripts if not already loaded.
 *
 * @returns {Promise<boolean>}
 */
export async function initGoogleCalendar() {
  if (!hasConfig()) {
    console.log('[calendar] Google Calendar not configured — skipping init');
    return false;
  }

  try {
    await Promise.all([loadGapi(), loadGis()]);
    return gapiLoaded && gisLoaded;
  } catch (err) {
    console.error('[calendar] Init failed:', err.message);
    return false;
  }
}

function loadGapi() {
  return new Promise((resolve, reject) => {
    if (gapiLoaded || window.gapi?.client?.calendar) {
      gapiLoaded = true;
      resolve();
      return;
    }

    const existing = document.getElementById('gapi-script');
    if (existing) {
      // Wait for existing script
      const check = setInterval(() => {
        if (window.gapi?.client?.calendar) {
          clearInterval(check);
          gapiLoaded = true;
          resolve();
        }
      }, 100);
      return;
    }

    const script = document.createElement('script');
    script.id = 'gapi-script';
    script.src = 'https://apis.google.com/js/api.js';
    script.async = true;
    script.defer = true;

    script.onload = () => {
      window.gapi.load('client', async () => {
        try {
          await window.gapi.client.init({
            apiKey: API_KEY,
            discoveryDocs: [DISCOVERY_DOC]
          });
          gapiLoaded = true;
          resolve();
        } catch (err) {
          reject(err);
        }
      });
    };

    script.onerror = () => reject(new Error('Failed to load GAPI script'));
    document.head.appendChild(script);
  });
}

function loadGis() {
  return new Promise((resolve, reject) => {
    if (gisLoaded || window.google?.accounts?.oauth2) {
      gisLoaded = true;
      resolve();
      return;
    }

    const existing = document.getElementById('gis-script');
    if (existing) {
      const check = setInterval(() => {
        if (window.google?.accounts?.oauth2) {
          clearInterval(check);
          gisLoaded = true;
          resolve();
        }
      }, 100);
      return;
    }

    const script = document.createElement('script');
    script.id = 'gis-script';
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;

    script.onload = () => {
      try {
        tokenClient = window.google.accounts.oauth2.initTokenClient({
          client_id: CLIENT_ID,
          scope: SCOPE,
          callback: tokenResponse => {
            if (tokenResponse?.access_token) {
              accessToken = tokenResponse.access_token;
            }
            if (authCallback) {
              authCallback(tokenResponse);
              authCallback = null;
            }
          }
        });
        gisLoaded = true;
        resolve();
      } catch (err) {
        reject(err);
      }
    };

    script.onerror = () => reject(new Error('Failed to load GIS script'));
    document.head.appendChild(script);
  });
}

// ─── Authentication ──────────────────────────────────────────────────────────

/**
 * Request OAuth authorization from the user.
 *
 * @returns {Promise<{success: boolean, error?: string, token?: string}>}
 */
export async function requestAuth() {
  if (!hasConfig()) {
    return { success: false, error: 'Google Calendar API not configured' };
  }

  const ready = await initGoogleCalendar();
  if (!ready) {
    return { success: false, error: 'Failed to initialize Google Calendar' };
  }

  return new Promise(resolve => {
    authCallback = tokenResponse => {
      if (tokenResponse?.error) {
        resolve({ success: false, error: tokenResponse.error });
      } else if (tokenResponse?.access_token) {
        accessToken = tokenResponse.access_token;
        resolve({ success: true, token: tokenResponse.access_token });
      } else {
        resolve({ success: false, error: 'No access token received' });
      }
    };

    // Re-init token client if needed
    if (!tokenClient) {
      tokenClient = window.google.accounts.oauth2.initTokenClient({
        client_id: CLIENT_ID,
        scope: SCOPE,
        callback: authCallback
      });
    }

    // Use prompt: '' to try silent auth first, then 'consent' if needed
    tokenClient.requestAccessToken({ prompt: 'consent' });
  });
}

/**
 * Check if we have a valid access token.
 *
 * @returns {boolean}
 */
export function hasAuth() {
  return !!accessToken;
}

/**
 * Revoke the current access token.
 */
export function revokeAuth() {
  if (accessToken && window.google?.accounts?.oauth2) {
    window.google.accounts.oauth2.revoke(accessToken, () => {
      console.log('[calendar] Access token revoked');
    });
  }
  accessToken = null;
}

// ─── Event Creation ──────────────────────────────────────────────────────────

/**
 * Create a Google Calendar event from a job.
 *
 * @param {Object} job - Job object
 * @param {Object} [options={}] - Event options
 * @param {Date} [options.startDate] - Override start date (defaults to now + 1 day 9am)
 * @param {number} [options.durationHours=1] - Event duration
 * @param {string} [options.calendarId='primary'] - Target calendar
 * @returns {Promise<{success: boolean, eventId?: string, htmlLink?: string, error?: string}>}
 */
export async function createCalendarEvent(job, options = {}) {
  if (!hasConfig()) {
    return { success: false, error: 'Google Calendar API not configured' };
  }

  if (!job || !job.customer) {
    return { success: false, error: 'Job data with customer name is required' };
  }

  const ready = await initGoogleCalendar();
  if (!ready) {
    return { success: false, error: 'Google Calendar not initialized' };
  }

  // Ensure we have auth
  if (!accessToken) {
    const authResult = await requestAuth();
    if (!authResult.success) {
      return { success: false, error: `Auth required: ${authResult.error}` };
    }
  }

  // Build event timing
  let startDate;
  if (options.startDate) {
    startDate = new Date(options.startDate);
  } else if (job.scheduled_start) {
    startDate = new Date(job.scheduled_start);
  } else {
    // Default: tomorrow at 9 AM
    startDate = new Date();
    startDate.setDate(startDate.getDate() + 1);
    startDate.setHours(9, 0, 0, 0);
  }

  const durationHours = options.durationHours || 1;
  const endDate = new Date(startDate.getTime() + durationHours * 60 * 60 * 1000);

  const event = {
    summary: `Wildlife Job: ${job.customer} - ${job.species || 'Unknown'}`,
    description: buildEventDescription(job),
    start: {
      dateTime: startDate.toISOString(),
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/New_York'
    },
    end: {
      dateTime: endDate.toISOString(),
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/New_York'
    },
    location: formatLocation(job),
    reminders: {
      useDefault: false,
      overrides: [
        { method: 'popup', minutes: 60 },
        { method: 'popup', minutes: 15 }
      ]
    }
  };

  try {
    // Set the access token for this request
    window.gapi.client.setToken({ access_token: accessToken });

    const response = await window.gapi.client.calendar.events.insert({
      calendarId: options.calendarId || 'primary',
      resource: event
    });

    if (response.status === 200 && response.result) {
      console.log('[calendar] Event created:', response.result.id);
      return {
        success: true,
        eventId: response.result.id,
        htmlLink: response.result.htmlLink,
        summary: response.result.summary
      };
    }

    return { success: false, error: `Unexpected response: ${response.status}` };
  } catch (err) {
    console.error('[calendar] createCalendarEvent error:', err.message || err);

    // Handle auth errors
    if (err.status === 401) {
      accessToken = null;
      return { success: false, error: 'Authentication expired. Please sign in again.' };
    }

    return { success: false, error: err.message || 'Failed to create calendar event' };
  }
}

/**
 * List upcoming calendar events.
 *
 * @param {number} [maxResults=10]
 * @returns {Promise<{success: boolean, events?: Array, error?: string}>}
 */
export async function listCalendarEvents(maxResults = 10) {
  if (!hasConfig()) {
    return { success: false, error: 'Google Calendar API not configured' };
  }

  const ready = await initGoogleCalendar();
  if (!ready) return { success: false, error: 'Not initialized' };

  if (!accessToken) {
    const authResult = await requestAuth();
    if (!authResult.success) return { success: false, error: authResult.error };
  }

  try {
    window.gapi.client.setToken({ access_token: accessToken });

    const now = new Date().toISOString();
    const response = await window.gapi.client.calendar.events.list({
      calendarId: 'primary',
      timeMin: now,
      showDeleted: false,
      singleEvents: true,
      maxResults,
      orderBy: 'startTime'
    });

    return {
      success: true,
      events: response.result.items || []
    };
  } catch (err) {
    console.error('[calendar] listCalendarEvents error:', err.message || err);
    if (err.status === 401) accessToken = null;
    return { success: false, error: err.message || 'Failed to list events' };
  }
}

/**
 * Delete a calendar event.
 *
 * @param {string} eventId
 * @param {string} [calendarId='primary']
 * @returns {Promise<{success: boolean, error?: string}>}
 */
export async function deleteCalendarEvent(eventId, calendarId = 'primary') {
  if (!eventId) return { success: false, error: 'Event ID required' };
  if (!accessToken) return { success: false, error: 'Not authenticated' };

  try {
    window.gapi.client.setToken({ access_token: accessToken });
    await window.gapi.client.calendar.events.delete({ calendarId, eventId });
    return { success: true };
  } catch (err) {
    console.error('[calendar] deleteCalendarEvent error:', err.message || err);
    if (err.status === 401) accessToken = null;
    return { success: false, error: err.message || 'Failed to delete event' };
  }
}

// ─── Private Helpers ─────────────────────────────────────────────────────────

function buildEventDescription(job) {
  const lines = [
    `Customer: ${job.customer}`,
    `Phone: ${job.phone || 'N/A'}`,
    `Address: ${job.address || 'N/A'}`,
    `Species: ${job.species || 'Unknown'}`
  ];

  if (job.scope) lines.push(`Scope: ${job.scope}`);
  if (job.notes) lines.push(`Notes: ${job.notes}`);
  if (job.assigned_tech) lines.push(`Technician: ${job.assigned_tech}`);

  lines.push('', '--- Created by Wildlife Whisperer FieldOps ---');

  return lines.join('\n');
}

function formatLocation(job) {
  const parts = [job.address, job.town, job.state].filter(Boolean);
  return parts.join(', ') || '';
}
