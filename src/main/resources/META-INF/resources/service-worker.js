const STATIC_CACHE = "rosies-books-static-v2";
const OFFLINE_FALLBACK = "/offline.html";
const PRECACHE_URLS = [
  OFFLINE_FALLBACK,
  "/assets/manifest.webmanifest",
  "/assets/app.css",
  "/assets/pwa-registration.js",
  "/assets/state-change.js",
  "/assets/shelf-notice.js",
  "/assets/isbn-barcode-scanner.js",
  "/assets/quagga-1.12.1.min.js",
  "/assets/fonts/newsreader-latin-v1.woff2",
  "/assets/fonts/ibm-plex-sans-latin-v1.woff2",
  "/assets/icons/rosies-books-rounded-16.png",
  "/assets/icons/rosies-books-rounded-32.png",
  "/assets/icons/rosies-books-192.png",
  "/assets/icons/rosies-books-512.png",
  "/assets/icons/rosies-books-square-180.png"
];
const STATIC_ASSET_URLS = new Set(PRECACHE_URLS);

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(STATIC_CACHE).then((cache) => cache.addAll(PRECACHE_URLS)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("rosies-books-static-") && key !== STATIC_CACHE)
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  if (url.pathname.startsWith("/oidc/") || url.pathname.startsWith("/covers/")) {
    return;
  }

  if (STATIC_ASSET_URLS.has(url.pathname)) {
    event.respondWith(caches.match(request).then((cached) => cached || fetch(request)));
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(fetch(request).catch(() => caches.match(OFFLINE_FALLBACK)));
  }
});
