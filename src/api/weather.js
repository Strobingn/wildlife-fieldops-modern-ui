/**
 * @module api/weather
 * @description OpenWeatherMap integration with 10-minute result caching,
 * graceful fallback when API key is missing, and offline awareness.
 */

import { config } from '../config.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const WEATHER_BASE_URL = 'https://api.openweathermap.org/data/2.5';
const CACHE_KEY = 'ww_fieldops_weather_cache';
const CACHE_DURATION_MS = 10 * 60 * 1000; // 10 minutes
const REQUEST_TIMEOUT_MS = 8000;

// ─── Cache Helpers ───────────────────────────────────────────────────────────

function getCache() {
  try {
    return JSON.parse(localStorage.getItem(CACHE_KEY) || '{}');
  } catch {
    return {};
  }
}

function setCache(entry) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(entry));
  } catch (e) {
    console.warn('[weather] Failed to cache weather data:', e.message);
  }
}

function getCacheKey(lat, lng, type) {
  return `${type}_${Math.round(lat * 100) / 100}_${Math.round(lng * 100) / 100}`;
}

function isCacheValid(cachedEntry) {
  if (!cachedEntry || !cachedEntry.timestamp) return false;
  return Date.now() - cachedEntry.timestamp < CACHE_DURATION_MS;
}

// ─── Fetch Helper with Timeout ───────────────────────────────────────────────

async function fetchWithTimeout(url, timeoutMs = REQUEST_TIMEOUT_MS) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, { signal: controller.signal });
    clearTimeout(timeoutId);
    return response;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error(`Weather request timed out after ${timeoutMs}ms`);
    }
    throw error;
  }
}

// ─── Icon Mapping ────────────────────────────────────────────────────────────

function getWeatherIconUrl(iconCode, size = '@2x') {
  return `https://openweathermap.org/img/wn/${iconCode}${size}.png`;
}

function mapWeatherCondition(code, description) {
  const conditions = {
    '01d': 'Clear',
    '01n': 'Clear',
    '02d': 'Partly Cloudy',
    '02n': 'Partly Cloudy',
    '03d': 'Cloudy',
    '03n': 'Cloudy',
    '04d': 'Overcast',
    '04n': 'Overcast',
    '09d': 'Showers',
    '09n': 'Showers',
    '10d': 'Rain',
    '10n': 'Rain',
    '11d': 'Thunderstorm',
    '11n': 'Thunderstorm',
    '13d': 'Snow',
    '13n': 'Snow',
    '50d': 'Mist',
    '50n': 'Mist'
  };
  return conditions[code] || description || 'Unknown';
}

// ─── API Key Check ───────────────────────────────────────────────────────────

function hasApiKey() {
  return config.hasOpenWeather;
}

// ─── Current Weather ─────────────────────────────────────────────────────────

/**
 * Get current weather for a location.
 * Results are cached for 10 minutes.
 *
 * @param {number} lat - Latitude
 * @param {number} lng - Longitude
 * @returns {Promise<Object|null>} Weather data or null if unavailable
 */
export async function getWeather(lat, lng) {
  // Validate coordinates
  if (typeof lat !== 'number' || typeof lng !== 'number') {
    console.warn('[weather] Invalid coordinates:', lat, lng);
    return null;
  }
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    console.warn('[weather] Coordinates out of range:', lat, lng);
    return null;
  }

  // Check API key
  if (!hasApiKey()) {
    console.log('[weather] No API key configured — returning null');
    return null;
  }

  // Check cache
  const cacheKey = getCacheKey(lat, lng, 'current');
  const cache = getCache();
  if (cache[cacheKey] && isCacheValid(cache[cacheKey])) {
    console.log('[weather] Returning cached weather data');
    return cache[cacheKey].data;
  }

  try {
    const url = `${WEATHER_BASE_URL}/weather?lat=${lat}&lon=${lng}&appid=${config.OPENWEATHER_API_KEY}&units=imperial`;
    const response = await fetchWithTimeout(url);

    if (!response.ok) {
      console.error(`[weather] HTTP ${response.status}: ${response.statusText}`);
      return null;
    }

    const raw = await response.json();

    const weather = {
      temp: Math.round(raw.main.temp),
      feelsLike: Math.round(raw.main.feels_like),
      humidity: raw.main.humidity,
      pressure: raw.main.pressure,
      windSpeed: Math.round(raw.wind.speed),
      windDeg: raw.wind.deg,
      condition: mapWeatherCondition(raw.weather[0]?.icon, raw.weather[0]?.main),
      description: raw.weather[0]?.description || '',
      icon: getWeatherIconUrl(raw.weather[0]?.icon),
      iconCode: raw.weather[0]?.icon,
      visibility: raw.visibility,
      sunrise: raw.sys?.sunrise ? new Date(raw.sys.sunrise * 1000).toISOString() : null,
      sunset: raw.sys?.sunset ? new Date(raw.sys.sunset * 1000).toISOString() : null,
      location: raw.name || '',
      lat,
      lng,
      fetchedAt: new Date().toISOString()
    };

    // Cache result
    cache[cacheKey] = { data: weather, timestamp: Date.now() };
    setCache(cache);

    return weather;
  } catch (err) {
    console.error('[weather] getWeather error:', err.message);
    // Return stale cache if available
    if (cache[cacheKey]) {
      console.log('[weather] Returning stale cache due to error');
      return cache[cacheKey].data;
    }
    return null;
  }
}

// ─── 5-Day Forecast ──────────────────────────────────────────────────────────

/**
 * Get 5-day/3-hour forecast for a location.
 * Results are cached for 10 minutes.
 *
 * @param {number} lat - Latitude
 * @param {number} lng - Longitude
 * @returns {Promise<Object|null>} Forecast data or null
 */
export async function getForecast(lat, lng) {
  if (typeof lat !== 'number' || typeof lng !== 'number') {
    console.warn('[weather] Invalid coordinates for forecast:', lat, lng);
    return null;
  }
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    console.warn('[weather] Coordinates out of range:', lat, lng);
    return null;
  }

  if (!hasApiKey()) {
    console.log('[weather] No API key configured — returning null');
    return null;
  }

  // Check cache
  const cacheKey = getCacheKey(lat, lng, 'forecast');
  const cache = getCache();
  if (cache[cacheKey] && isCacheValid(cache[cacheKey])) {
    return cache[cacheKey].data;
  }

  try {
    const url = `${WEATHER_BASE_URL}/forecast?lat=${lat}&lon=${lng}&appid=${config.OPENWEATHER_API_KEY}&units=imperial`;
    const response = await fetchWithTimeout(url);

    if (!response.ok) {
      console.error(`[weather] Forecast HTTP ${response.status}: ${response.statusText}`);
      return null;
    }

    const raw = await response.json();

    // Group by day
    const daily = {};
    raw.list.forEach(item => {
      const date = item.dt_txt.split(' ')[0];
      if (!daily[date]) {
        daily[date] = {
          date,
          temps: [],
          conditions: [],
          icons: [],
          descriptions: [],
          humidity: [],
          wind: []
        };
      }
      daily[date].temps.push(item.main.temp);
      daily[date].conditions.push(item.weather[0]?.main);
      daily[date].icons.push(item.weather[0]?.icon);
      daily[date].descriptions.push(item.weather[0]?.description);
      daily[date].humidity.push(item.main.humidity);
      daily[date].wind.push(item.wind.speed);
    });

    // Build daily summaries
    const forecast = Object.values(daily)
      .slice(0, 5)
      .map(day => {
        const conditionCounts = {};
        day.conditions.forEach(c => {
          conditionCounts[c] = (conditionCounts[c] || 0) + 1;
        });
        const dominantCondition = Object.entries(conditionCounts).sort((a, b) => b[1] - a[1])[0][0];
        const middayIcon = day.icons[Math.floor(day.icons.length / 2)] || day.icons[0];

        return {
          date: day.date,
          tempMin: Math.round(Math.min(...day.temps)),
          tempMax: Math.round(Math.max(...day.temps)),
          tempAvg: Math.round(day.temps.reduce((a, b) => a + b, 0) / day.temps.length),
          condition: dominantCondition,
          description: day.descriptions[Math.floor(day.descriptions.length / 2)] || '',
          icon: getWeatherIconUrl(middayIcon),
          iconCode: middayIcon,
          humidity: Math.round(day.humidity.reduce((a, b) => a + b, 0) / day.humidity.length),
          windSpeed: Math.round(Math.max(...day.wind))
        };
      });

    const result = {
      location: raw.city?.name || '',
      country: raw.city?.country || '',
      lat,
      lng,
      days: forecast,
      fetchedAt: new Date().toISOString()
    };

    cache[cacheKey] = { data: result, timestamp: Date.now() };
    setCache(cache);

    return result;
  } catch (err) {
    console.error('[weather] getForecast error:', err.message);
    if (cache[cacheKey]) return cache[cacheKey].data;
    return null;
  }
}

// ─── Cache Management ────────────────────────────────────────────────────────

/**
 * Clear all cached weather data.
 */
export function clearWeatherCache() {
  localStorage.removeItem(CACHE_KEY);
  console.log('[weather] Cache cleared');
}

/**
 * Get the age of cached weather data for a location.
 * @param {number} lat
 * @param {number} lng
 * @param {string} [type='current']
 * @returns {number|null} Age in milliseconds, or null if not cached
 */
export function getCacheAge(lat, lng, type = 'current') {
  const cacheKey = getCacheKey(lat, lng, type);
  const cache = getCache();
  if (!cache[cacheKey]) return null;
  return Date.now() - cache[cacheKey].timestamp;
}
