import { supabase } from '../auth/supabaseClient.js';

// Cloudflare Worker URL
const SYNC_URL = 'https://wildlife-fieldops-sync.YOUR_SUBDOMAIN.workers.dev';

// Queue for offline actions
let syncQueue = JSON.parse(localStorage.getItem('syncQueue') || '[]');

// Add an action to the sync queue
export function queueAction(type, payload) {
  const action = {
    id: generateId(),
    type,
    payload,
    at: new Date().toISOString(),
    device: 'fieldops-app'
  };
  syncQueue.push(action);
  localStorage.setItem('syncQueue', JSON.stringify(syncQueue));
  return action.id;
}

// Process the sync queue
export async function processSyncQueue() {
  if (syncQueue.length === 0) return;

  const queue = [...syncQueue]; // Copy to avoid race conditions
  syncQueue = [];
  localStorage.setItem('syncQueue', JSON.stringify(syncQueue));

  try {
    const response = await fetch(SYNC_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        device: 'fieldops-app',
        queue
      })
    });

    const result = await response.json();
    if (!result.ok) {
      console.error('Sync failed:', result.error);
      // Re-add failed actions to the queue
      syncQueue = queue.concat(syncQueue);
      localStorage.setItem('syncQueue', JSON.stringify(syncQueue));
    } else {
      console.log(`Synced ${result.syncedActions} actions`);
      if (result.failures && result.failures.length > 0) {
        console.warn('Some actions failed:', result.failures);
        // Re-add failed actions
        result.failures.forEach(failure => {
          syncQueue.push(failure.action);
        });
        localStorage.setItem('syncQueue', JSON.stringify(syncQueue));
      }
    }
  } catch (error) {
    console.error('Sync error:', error);
    // Re-add all actions to the queue
    syncQueue = queue.concat(syncQueue);
    localStorage.setItem('syncQueue', JSON.stringify(syncQueue));
  }
}

// Generate a unique ID
function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// Check online status and sync
export function setupSync() {
  // Sync immediately if online
  if (navigator.onLine) {
    processSyncQueue();
  }

  // Sync when coming online
  window.addEventListener('online', processSyncQueue);

  // Sync every 5 minutes as a fallback
  setInterval(processSyncQueue, 5 * 60 * 1000);
}
