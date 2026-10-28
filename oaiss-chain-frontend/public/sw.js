/**
 * OAISS CHAIN — Service Worker
 *
 * Provides offline capability for the Vue 3 SPA:
 *   - Cache-First for static assets (JS, CSS, images, fonts)
 *   - Network-First for API calls (/api/)
 *   - App shell caching (index.html, critical CSS, main JS)
 *
 * @author OAISS Team
 */

// ---------------------------------------------------------------------------
// Cache versioning — updated on each build via the registration script.
// When the build hash changes, a new cache is created and old ones are purged.
// ---------------------------------------------------------------------------
const CACHE_VERSION = 'v1'; // Increment or replace with build hash at deploy time
const CACHE_NAME = `oaiss-chain-${CACHE_VERSION}`;

/** App shell files pre-cached on install */
const APP_SHELL = [
  '/',
  '/index.html',
];

/** Static asset patterns matched against request URL pathname */
const STATIC_ASSET_PATTERNS = [
  /\.js$/,
  /\.css$/,
  /\.woff2?$/,
  /\.ttf$/,
  /\.eot$/,
  /\.otf$/,
  /\.png$/,
  /\.jpg$/,
  /\.jpeg$/,
  /\.gif$/,
  /\.svg$/,
  /\.webp$/,
  /\.ico$/,
];

// ---------------------------------------------------------------------------
// install — pre-cache the app shell
// ---------------------------------------------------------------------------
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(APP_SHELL);
    })
  );
  // Activate immediately without waiting for existing clients to close
  self.skipWaiting();
});

// ---------------------------------------------------------------------------
// activate — clean up stale caches from previous versions
// ---------------------------------------------------------------------------
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => caches.delete(name))
      );
    })
  );
  // Take control of all open clients immediately
  self.clients.claim();
  // Trim cache to prevent unbounded growth (max 100 entries)
  event.waitUntil(trimCache(100));
});

// ---------------------------------------------------------------------------
// fetch — routing strategy
// ---------------------------------------------------------------------------
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Only handle GET requests
  if (request.method !== 'GET') {
    return;
  }

  // API calls → Network-First
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request));
    return;
  }

  // Static assets → Cache-First
  if (isStaticAsset(url.pathname)) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // API docs / Swagger → Stale-While-Revalidate
  if (url.pathname.includes('/swagger') || url.pathname.includes('/api-docs')) {
    event.respondWith(staleWhileRevalidate(request));
    return;
  }

  // Navigation requests (HTML) → Network-First with cache fallback
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request));
    return;
  }

  // Everything else → Network-First
  event.respondWith(networkFirst(request));
});

// ---------------------------------------------------------------------------
// Stale-While-Revalidate: return cache immediately, update in background
// ---------------------------------------------------------------------------
async function staleWhileRevalidate(request) {
  const cached = await caches.match(request);
  const fetchPromise = fetch(request).then(async (response) => {
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  }).catch(() => cached);

  return cached || fetchPromise;
}

/**
 * Trim cache to a maximum number of entries to prevent unbounded growth.
 */
async function trimCache(maxEntries) {
  const cache = await caches.open(CACHE_NAME);
  const keys = await cache.keys();
  if (keys.length > maxEntries) {
    // Delete oldest entries (FIFO)
    const toDelete = keys.slice(0, keys.length - maxEntries);
    await Promise.all(toDelete.map((key) => cache.delete(key)));
  }
}

// ---------------------------------------------------------------------------
// Strategies
// ---------------------------------------------------------------------------

/**
 * Cache-First: return from cache, fall back to network and cache the response.
 */
async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    // Network failed and no cache — return a generic offline response
    return new Response('Offline', { status: 503, statusText: 'Service Unavailable' });
  }
}

/**
 * Network-First: try the network, fall back to cache.
 */
async function networkFirst(request) {
  try {
    const response = await fetch(request);
    // Cache successful GET responses for offline fallback
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    return new Response('Offline', { status: 503, statusText: 'Service Unavailable' });
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Check if the pathname looks like a static asset by extension.
 */
function isStaticAsset(pathname) {
  return STATIC_ASSET_PATTERNS.some((pattern) => pattern.test(pathname));
}
