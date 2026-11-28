/* OpenFic One 系统通知兼容层：在 WebView 内提供 window.Notification，实际通知由原生 Android 发出。 */
(function () {
  'use strict';

  var NATIVE = window.OFOneNative || null;

  function nativePerm() {
    try {
      if (NATIVE && NATIVE.getNotificationPermission) {
        var p = NATIVE.getNotificationPermission();
        if (p === 'granted' || p === 'denied' || p === 'default') return p;
      }
    } catch (e) {}
    return 'granted';
  }

  function ofoneNotify(title, opts) {
    try {
      if (NATIVE && NATIVE.notify) {
        NATIVE.notify(String(title == null ? '' : title), String((opts && opts.body) || ''));
      }
    } catch (e) {}
  }

  function NotificationShim(title, opts) {
    this.title = title == null ? '' : String(title);
    this.body = (opts && opts.body) ? String(opts.body) : '';
    ofoneNotify(this.title, opts);
  }
  NotificationShim.__ofone = true;
  NotificationShim.permission = nativePerm();
  NotificationShim.maxActions = 0;
  NotificationShim.requestPermission = function (cb) {
    return new Promise(function (resolve) {
      function done(p) {
        NotificationShim.permission = p;
        if (typeof cb === 'function') { try { cb(p); } catch (e) {} }
        resolve(p);
      }
      if (!(NATIVE && NATIVE.requestNotificationPermission)) { done('granted'); return; }
      window.__ofoneNotifResolve = done;
      try { NATIVE.requestNotificationPermission(); } catch (e) { done(nativePerm()); }
    });
  };

  try { window.Notification = NotificationShim; } catch (e) {}
})();
