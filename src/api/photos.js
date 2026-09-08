/**
 * @module api/photos
 * @description Photo upload, retrieval, and deletion with client-side
 * compression, offline queue support, and Supabase Storage integration.
 */

import { supabase, uploadToStorage, deleteFromStorage } from './supabaseClient.js';
import { syncQueue } from './sync.js';
import { config } from '../config.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const LOCAL_PHOTOS_KEY = 'ww_fieldops_photos_cache';
const DEFAULT_MAX_WIDTH = 1200;
const DEFAULT_QUALITY = 0.7;
const STORAGE_BUCKET = 'job-photos';

// ─── Local Cache Helpers ─────────────────────────────────────────────────────

function loadCache() {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_PHOTOS_KEY) || '[]');
  } catch {
    return [];
  }
}

function saveCache(photos) {
  localStorage.setItem(LOCAL_PHOTOS_KEY, JSON.stringify(photos));
}

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// ─── Image Compression ───────────────────────────────────────────────────────

/**
 * Compress an image file client-side using canvas.
 *
 * @param {File|Blob|string} file - Image file, blob, or data URL
 * @param {number} [maxWidth=1200] - Maximum width in pixels
 * @param {number} [quality=0.7] - JPEG quality 0-1
 * @returns {Promise<Blob>} Compressed image as JPEG Blob
 */
export async function compressImage(file, maxWidth = DEFAULT_MAX_WIDTH, quality = DEFAULT_QUALITY) {
  return new Promise((resolve, reject) => {
    const img = new Image();

    img.onload = () => {
      let { width, height } = img;

      // Calculate new dimensions
      if (width > maxWidth) {
        height = Math.round((height * maxWidth) / width);
        width = maxWidth;
      }

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');

      // Use better quality scaling
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, width, height);

      canvas.toBlob(
        blob => {
          if (!blob) {
            reject(new Error('Canvas toBlob returned null'));
            return;
          }
          console.log(`[photos] Compressed: ${(file.size / 1024).toFixed(1)}KB -> ${(blob.size / 1024).toFixed(1)}KB`);
          resolve(blob);
        },
        'image/jpeg',
        quality
      );
    };

    img.onerror = () => reject(new Error('Failed to load image for compression'));

    if (file instanceof File || file instanceof Blob) {
      const reader = new FileReader();
      reader.onload = e => {
        img.src = e.target.result;
      };
      reader.onerror = () => reject(new Error('Failed to read file'));
      reader.readAsDataURL(file);
    } else if (typeof file === 'string' && file.startsWith('data:')) {
      img.src = file;
    } else {
      reject(new Error('Invalid image input: must be File, Blob, or data URL'));
    }
  });
}

// ─── Core CRUD ───────────────────────────────────────────────────────────────

/**
 * Upload a photo for a job. Compresses client-side, uploads to Supabase Storage,
 * and stores metadata in the photos table.
 *
 * @param {string} jobId
 * @param {File|Blob|string} file - Image file, blob, or data URL
 * @param {string} [tag='General'] - Photo category tag
 * @param {string} [notes=''] - Optional notes
 * @returns {Promise<Object>}
 */
export async function uploadPhotoLegacy(jobId, file, tag = 'General', notes = '') {
  if (!jobId || typeof jobId !== 'string') throw new Error('Job ID is required');
  if (!file) throw new Error('File is required');

  const photoId = generateId();
  const now = new Date().toISOString();
  const ext = file instanceof File ? file.name.split('.').pop() || 'jpg' : 'jpg';
  const storagePath = `jobs/${jobId}/${photoId}.${ext}`;

  // 1. Compress image
  let compressedBlob;
  try {
    compressedBlob = await compressImage(file, DEFAULT_MAX_WIDTH, DEFAULT_QUALITY);
  } catch (err) {
    console.error('[photos] Compression failed:', err.message);
    // Try to use original if compression fails
    compressedBlob = file instanceof File ? file : null;
    if (!compressedBlob) throw new Error('Failed to compress image and no fallback available');
  }

  // 2. Convert blob to data URL for offline storage
  let localDataUrl = null;
  if (!navigator.onLine) {
    try {
      localDataUrl = await blobToDataUrl(compressedBlob);
    } catch (err) {
      console.warn('[photos] Could not create local data URL:', err.message);
    }
  }

  // 3. Build metadata record
  const photoMeta = {
    id: photoId,
    job_id: jobId,
    tag: tag || 'General',
    notes: notes?.trim() || null,
    storage_path: storagePath,
    image_url: null, // Will be set after upload
    created_at: now,
    updated_at: now
  };

  // 4. Optimistic: store locally
  const cached = loadCache();
  cached.unshift({ ...photoMeta, _localData: localDataUrl });
  saveCache(cached);

  // 5. Upload
  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({
        table: 'photos',
        action: 'insert',
        payload: photoMeta
      });
      return { ...photoMeta, _localData: localDataUrl, _syncStatus: 'pending' };
    }

    // Upload to Supabase Storage
    const uploadResult = await uploadToStorage(STORAGE_BUCKET, storagePath, compressedBlob, {
      contentType: 'image/jpeg'
    });

    if (uploadResult?.publicUrl) {
      photoMeta.image_url = uploadResult.publicUrl;
    }

    // Insert metadata row
    const { data, error } = await supabase.from('photos').insert(photoMeta).select().single();
    if (error) throw error;

    // Update cache with server data
    const updated = loadCache().map(p => (p.id === photoId ? { ...data, _localData: null } : p));
    saveCache(updated);

    return { ...data, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[photos] uploadPhoto(${jobId}) error:`, err.message);
    syncQueue.enqueue({
      table: 'photos',
      action: 'insert',
      payload: photoMeta
    });
    return { ...photoMeta, _localData: localDataUrl, _syncStatus: 'pending', _error: err.message };
  }
}

/**
 * Get all photos for a job.
 *
 * @param {string} jobId
 * @returns {Promise<Array>}
 */
export async function getPhotosByJobLegacy(jobId) {
  if (!jobId) throw new Error('Job ID is required');

  try {
    if (!navigator.onLine) {
      return loadCache().filter(p => p.job_id === jobId);
    }

    const { data, error } = await supabase
      .from('photos')
      .select('*')
      .eq('job_id', jobId)
      .order('created_at', { ascending: false });

    if (error) throw error;

    // Merge with local cache (include any pending uploads)
    const serverPhotos = data || [];
    const localPhotos = loadCache().filter(p => p.job_id === jobId && p._syncStatus === 'pending');
    const merged = [...localPhotos, ...serverPhotos];

    // Update cache
    const otherPhotos = loadCache().filter(p => p.job_id !== jobId);
    saveCache([...merged, ...otherPhotos]);

    return merged;
  } catch (err) {
    console.error(`[photos] getPhotosByJob(${jobId}) error:`, err.message);
    return loadCache().filter(p => p.job_id === jobId);
  }
}

/**
 * Delete a photo (storage file + metadata row).
 *
 * @param {string} photoId
 * @returns {Promise<Object>}
 */
export async function deletePhotoLegacy(photoId) {
  if (!photoId) throw new Error('Photo ID is required');

  // Find the photo to get storage path
  const cached = loadCache();
  const photo = cached.find(p => p.id === photoId);

  // Optimistic: remove from cache
  saveCache(cached.filter(p => p.id !== photoId));

  try {
    if (!navigator.onLine) {
      syncQueue.enqueue({ table: 'photos', action: 'delete', payload: { id: photoId } });
      if (photo?.storage_path) {
        // Will delete from storage when back online
        syncQueue.enqueue({ table: 'storage_delete', action: 'insert', payload: { path: photo.storage_path } });
      }
      return { id: photoId, deleted: true, _syncStatus: 'pending' };
    }

    // Delete metadata row
    const { error: dbError } = await supabase.from('photos').delete().eq('id', photoId);
    if (dbError) throw dbError;

    // Delete storage file if we have the path
    if (photo?.storage_path) {
      await deleteFromStorage(STORAGE_BUCKET, photo.storage_path).catch(err => {
        console.warn(`[photos] Storage delete warning for ${photo.storage_path}:`, err.message);
      });
    }

    return { id: photoId, deleted: true, _syncStatus: 'synced' };
  } catch (err) {
    console.error(`[photos] deletePhoto(${photoId}) error:`, err.message);
    syncQueue.enqueue({ table: 'photos', action: 'delete', payload: { id: photoId } });
    throw new Error(`Failed to delete photo ${photoId}: ${err.message}`);
  }
}

// ─── Utilities ───────────────────────────────────────────────────────────────

/**
 * Convert a Blob to a data URL for offline display.
 * @param {Blob} blob
 * @returns {Promise<string>}
 */
function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = e => resolve(e.target.result);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

/**
 * Get photo as a displayable URL (handles both online and offline).
 *
 * @param {Object} photo
 * @returns {string|null}
 */
export function getPhotoDisplayUrl(photo) {
  if (!photo) return null;
  // Prefer server URL
  if (photo.image_url) return photo.image_url;
  // Fallback to local data (offline uploads)
  if (photo._localData) return photo._localData;
  // Try to construct storage URL
  if (photo.storage_path) {
    return supabase.storage.from(STORAGE_BUCKET).getPublicUrl(photo.storage_path).data?.publicUrl || null;
  }
  return null;
}

/**
 * Batch upload multiple photos for a job.
 *
 * @param {string} jobId
 * @param {Array<{file: File, tag?: string, notes?: string}>} photos
 * @returns {Promise<Array>}
 */
export async function uploadPhotosBatch(jobId, photos) {
  if (!Array.isArray(photos)) throw new Error('photos must be an array');

  const results = [];
  for (const p of photos) {
    try {
      const result = await uploadPhotoLegacy(jobId, p.file, p.tag, p.notes);
      results.push(result);
    } catch (err) {
      results.push({ error: err.message, file: p.file?.name });
    }
  }
  return results;
}

// ─── Improved Upload with Better Reliability & Error Handling ───────────────

/**
 * Upload a photo with improved reliability and error handling.
 * Generates a structured path: job-photos/{jobId}/{timestamp}_{filename}
 *
 * @param {string} jobId - Associated job ID
 * @param {File|Blob} file - Photo file to upload
 * @param {Object} [metadata={}]
 * @param {string} [metadata.tag] - Photo tag (e.g. 'before', 'after')
 * @param {string} [metadata.notes] - Optional notes
 * @returns {Promise<{data: {path: string, publicUrl: string, photoId: string}|null, error: Error|null}>}
 */
export async function uploadPhoto(jobId, file, metadata = {}) {
  if (!jobId) return { data: null, error: new Error('jobId is required') };
  if (!file || !file.size) return { data: null, error: new Error('Valid file is required') };

  // Size check
  const maxSize = (config.MAX_FILE_SIZE_MB || 10) * 1024 * 1024;
  if (file.size > maxSize) {
    return { data: null, error: new Error(`File too large: max ${config.MAX_FILE_SIZE_MB || 10}MB`) };
  }

  const photoId = 'photo_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 7);
  const ext = file.name?.split('.').pop() || 'jpg';
  const filename = `${Date.now()}_${photoId}.${ext}`;
  const path = `${jobId}/${filename}`;
  const bucket = 'job-photos';

  // Upload to Supabase Storage
  if (config.hasSupabase) {
    try {
      const { data: uploadData, error: uploadError } = await supabase.storage.from(bucket).upload(path, file, {
        contentType: file.type || 'image/jpeg',
        upsert: false
      });

      if (uploadError) {
        console.error(`[photos] Upload failed:`, uploadError.message);
        return { data: null, error: uploadError };
      }

      const { data: urlData } = supabase.storage.from(bucket).getPublicUrl(path);
      const publicUrl = urlData?.publicUrl || '';

      // Save photo record to database
      const photoRecord = {
        id: photoId,
        job_id: jobId,
        storage_path: path,
        public_url: publicUrl,
        tag: metadata.tag || '',
        notes: metadata.notes || '',
        file_size: file.size,
        content_type: file.type || 'image/jpeg',
        created_at: new Date().toISOString()
      };

      try {
        await supabase.from('photos').insert(photoRecord);
      } catch (dbErr) {
        console.warn('[photos] DB insert failed (photo uploaded but not recorded):', dbErr.message);
      }

      return {
        data: { path, publicUrl, photoId },
        error: null
      };
    } catch (err) {
      console.error('[photos] Upload exception:', err.message);
      return { data: null, error: err };
    }
  }

  // Offline fallback: return local data URL
  return new Promise(resolve => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve({
        data: { path: `local://${path}`, publicUrl: reader.result, photoId },
        error: null
      });
    };
    reader.onerror = () => {
      resolve({ data: null, error: new Error('Failed to read file for local storage') });
    };
    reader.readAsDataURL(file);
  });
}

/**
 * Get all photos for a job.
 * @param {string} jobId
 * @returns {Promise<{data: Array, error: Error|null}>}
 */
export async function getPhotosByJob(jobId) {
  if (!jobId) return { data: [], error: null };

  if (config.hasSupabase) {
    try {
      const { data, error } = await supabase
        .from('photos')
        .select('*')
        .eq('job_id', jobId)
        .order('created_at', { ascending: false });

      if (error) {
        console.warn('[photos] DB fetch failed:', error.message);
        return { data: [], error };
      }
      return { data: data || [], error: null };
    } catch (e) {
      console.warn('[photos] Fetch exception:', e.message);
    }
  }

  // Local fallback
  try {
    const raw = localStorage.getItem('ww_rockstar_photos');
    const all = raw ? JSON.parse(raw) : [];
    return { data: all.filter(p => p.job_id === jobId), error: null };
  } catch {
    return { data: [], error: null };
  }
}

/**
 * Delete a photo from storage and database.
 * @param {string} photoId
 * @param {string} storagePath
 * @returns {Promise<{error: Error|null}>}
 */
export async function deletePhoto(photoId, storagePath) {
  if (config.hasSupabase && storagePath && !storagePath.startsWith('local://')) {
    try {
      const { error } = await supabase.storage.from('job-photos').remove([storagePath]);
      if (error) console.warn('[photos] Storage delete warning:', error.message);
    } catch (e) {
      console.warn('[photos] Storage delete exception:', e.message);
    }

    try {
      await supabase.from('photos').delete().eq('id', photoId);
    } catch (e) {
      console.warn('[photos] DB delete exception:', e.message);
    }
  }

  // Remove from local cache
  try {
    const raw = localStorage.getItem('ww_rockstar_photos');
    if (raw) {
      const all = JSON.parse(raw);
      const filtered = all.filter(p => p.id !== photoId);
      localStorage.setItem('ww_rockstar_photos', JSON.stringify(filtered));
    }
  } catch (e) {
    console.warn('[photos] Local delete failed:', e.message);
  }

  return { error: null };
}
