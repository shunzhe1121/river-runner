// Offline cache for River Runner (web).
//
// The first visit downloads about 5 MB; after that every file comes from this cache, so the
// app opens instantly and works without a network. Each deploy replaces BUILD below (CI
// writes the commit id), which makes the browser install the new worker. The new worker
// downloads the new files in the background while the old version keeps working, and takes
// over on the next start, so the app never runs a mix of two versions.

const BUILD = "3ce630d3d612";
const CACHE = "rr-" + BUILD;
const PREFIX = "rr-";

// Files fingerprinted by content (hex names such as 3ca4ee9449579f086d21.wasm) never change,
// so the new version can take them over from the old cache instead of downloading them again.
const FINGERPRINTED = /\/[0-9a-f]{16,}\.[a-z0-9]+$/;

function pageKey(request) {
    // the page is stored once, whatever query string it was opened with
    const url = new URL(request.url);
    return url.origin + url.pathname.replace(/index\.html$/, "");
}

self.addEventListener("install", (event) => {
    event.waitUntil((async () => {
        const cache = await caches.open(CACHE);
        // Carry over what the previous version had: fingerprinted files are copied,
        // the rest are downloaded fresh. Files new in this version are cached on first use.
        for (const name of await caches.keys()) {
            if (!name.startsWith(PREFIX) || name === CACHE) continue;
            const old = await caches.open(name);
            for (const req of await old.keys()) {
                try {
                    if (FINGERPRINTED.test(new URL(req.url).pathname)) {
                        const res = await old.match(req);
                        if (res) await cache.put(req, res);
                    } else {
                        const res = await fetch(req.url, { cache: "no-cache" });
                        if (res.ok) await cache.put(req, res);
                    }
                } catch (e) { /* offline or removed: fetched again when used */ }
            }
        }
        await self.skipWaiting();
    })());
});

self.addEventListener("activate", (event) => {
    event.waitUntil((async () => {
        for (const name of await caches.keys()) {
            if (name.startsWith(PREFIX) && name !== CACHE) await caches.delete(name);
        }
        await self.clients.claim();
    })());
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.method !== "GET" || new URL(req.url).origin !== self.location.origin) return;
    const isPage = req.mode === "navigate";
    // the worker script itself always comes from the network (that is how updates are found)
    if (new URL(req.url).pathname.endsWith("/sw.js")) return;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        const key = isPage ? pageKey(req) : req;
        const hit = await cache.match(key, { ignoreSearch: isPage });
        if (hit) return hit;
        const res = await fetch(req);
        if (res.ok) {
            try { await cache.put(key, res.clone()); } catch (e) { /* storage full: still serve it */ }
        }
        return res;
    })());
});

// The page sends the files it loaded before this worker was in control (the first visit),
// so they are cached too and the second start is already offline-ready.
self.addEventListener("message", (event) => {
    const data = event.data || {};
    if (data.type !== "cache" || !Array.isArray(data.urls)) return;
    event.waitUntil((async () => {
        const cache = await caches.open(CACHE);
        for (const u of data.urls) {
            try {
                const url = new URL(u);
                if (url.origin !== self.location.origin || url.pathname.endsWith("/sw.js")) continue;
                const isPage = url.pathname.endsWith("/") || url.pathname.endsWith("/index.html");
                const key = isPage ? url.origin + url.pathname.replace(/index\.html$/, "") : url.href;
                if (await cache.match(key)) continue;
                // usually answered from the browser's HTTP cache, without downloading again
                const res = await fetch(url.href);
                if (res.ok) await cache.put(key, res);
            } catch (e) { /* skip */ }
        }
    })());
});
