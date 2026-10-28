/**
 * Service Worker registration
 *
 * Registers the service worker on page load and listens for controllerchange
 * to auto-reload when an updated SW takes control.
 *
 * @author OAISS Team
 */

export function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator)) {
    return;
  }

  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/',
      });

      console.log('[SW] Registered with scope:', registration.scope);

      // Check for updates periodically (every 60 minutes)
      setInterval(() => {
        registration.update();
      }, 60 * 60 * 1000);
    } catch (error) {
      console.warn('[SW] Registration failed:', error);
    }
  });

  // Auto-reload when a new service worker takes control
  let refreshing = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!refreshing) {
      refreshing = true;
      window.location.reload();
    }
  });
}
