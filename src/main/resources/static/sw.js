/* 周报提交系统 - Service Worker
 * 用途：部署到 HTTP(S) 服务器后，首次访问把静态资源（HTML / Tailwind / Font Awesome / 字体）
 * 缓存进浏览器 Cache API，后续访问走缓存，秒开且可离线。
 * 注意：file:// 下不会注册，不影响本地预览。
 */
const CACHE_NAME = 'weekly-report-v1';

// 需要预缓存的静态资源（同 origin）
// 注意：用 './' 缓存目录索引（即站点首页），不写死 index.html / index.optimized.html，
// 这样无论服务器上首页叫什么名字，SW 都能正确预缓存与回退。
const PRECACHE_URLS = [
  './',
  './vendor/tailwind.css',
  './vendor/font-awesome/css/font-awesome.min.css',
  './vendor/font-awesome/fonts/fontawesome-webfont.eot',
  './vendor/font-awesome/fonts/fontawesome-webfont.woff2',
  './vendor/font-awesome/fonts/fontawesome-webfont.woff',
  './vendor/font-awesome/fonts/fontawesome-webfont.ttf'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_URLS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  // 只处理同 origin 的 GET（API 走跨域 POST，不拦截）
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
    return;
  }

  // 导航请求：网络优先，失败回退缓存（目录索引，与首页文件名无关）
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() => caches.match('./'))
    );
    return;
  }

  // 静态资源：缓存优先，同时后台更新
  event.respondWith(
    caches.match(request).then((cached) => {
      const network = fetch(request)
        .then((response) => {
          if (response && response.status === 200) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          }
          return response;
        })
        .catch(() => cached);
      return cached || network;
    })
  );
});
