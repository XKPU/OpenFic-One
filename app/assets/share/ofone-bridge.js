/* OF-One 注入脚本：在 OpenFic 设置页插入「One 设置」入口与面板 */
(function () {
  'use strict';
  if (window.__ofoneInjected) return;
  window.__ofoneInjected = true;

  var NATIVE = window.OFOneNative || null;
  var LABEL = 'One 设置';

  var CSS = [
    '.ofone-mask{position:fixed;inset:0;background:rgba(0,0,0,.55);z-index:2147483000;display:flex;align-items:center;justify-content:center;padding:24px;font-family:inherit}',
    '.ofone-card{width:100%;max-width:420px;max-height:80vh;overflow:auto;border-radius:16px;background:var(--ofone-bg,#17171c);color:var(--ofone-fg,#ececf1);box-shadow:0 24px 64px rgba(0,0,0,.5);padding:20px 20px 16px}',
    '.ofone-title{font-size:17px;font-weight:600;margin:0 0 4px}',
    '.ofone-sub{font-size:12px;opacity:.6;margin:0 0 16px;line-height:1.6}',
    '.ofone-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 0;border-top:1px solid rgba(255,255,255,.08)}',
    '.ofone-k{font-size:13px;opacity:.85}',
    '.ofone-v{font-size:13px;font-weight:600;font-variant-numeric:tabular-nums}',
    '.ofone-btn{appearance:none;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,.06);color:inherit;border-radius:9px;padding:7px 13px;font-size:13px;cursor:pointer}',
    '.ofone-btn:active{opacity:.7}',
    '.ofone-btn.primary{background:#4c8dff;border-color:#4c8dff;color:#fff}',
    '.ofone-foot{display:flex;justify-content:flex-end;margin-top:16px}',
    '.ofone-hint{font-size:11.5px;opacity:.55;line-height:1.6;margin-top:10px}',
    '.ofone-input{background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.16);border-radius:8px;color:inherit;font-size:12px;padding:6px 8px;min-width:0}',
    '.ofone-fab{position:fixed;right:18px;bottom:78px;z-index:2147482000;width:44px;height:44px;border-radius:50%;background:#4c8dff;color:#fff;border:none;font-size:12px;font-weight:600;box-shadow:0 6px 18px rgba(0,0,0,.35)}'
  ].join('');

  function style() {
    if (document.getElementById('ofone-style')) return;
    var s = document.createElement('style');
    s.id = 'ofone-style';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  function info() {
    if (!NATIVE) return { appVersion: '1.0.0', openficVersion: '', autoUpdate: true, neverCheck: false, snoozeUntil: 0 };
    try { return JSON.parse(NATIVE.getInfo()); } catch (e) { return {}; }
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function row(key, valueNode) {
    var r = el('div', 'ofone-row');
    r.appendChild(el('span', 'ofone-k', key));
    if (typeof valueNode === 'string') r.appendChild(el('span', 'ofone-v', valueNode));
    else r.appendChild(valueNode);
    return r;
  }

  function snoozeText(until) {
    if (!until || until < Date.now()) return '未设置';
    var d = Math.ceil((until - Date.now()) / 86400000);
    return '还剩 ' + d + ' 天';
  }

  function openPanel() {
    style();
    var old = document.getElementById('ofone-mask');
    if (old) old.remove();
    var i = info();

    var mask = el('div', 'ofone-mask');
    mask.id = 'ofone-mask';
    var card = el('div', 'ofone-card');

    card.appendChild(el('h2', 'ofone-title', 'OF-One'));
    card.appendChild(el('p', 'ofone-sub', '本页由 OF-One 启动器注入，用于管理启动器自身与 OpenFic 的版本。'));

    card.appendChild(row('启动器版本', i.appVersion || '1.0.0'));
    card.appendChild(row('OpenFic 版本', i.openficVersion || '未安装'));

    var enabled = !!(i.autoUpdate && !i.neverCheck);
    var auto = el('button', 'ofone-btn' + (enabled ? ' primary' : ''), enabled ? '已开启' : '已停止');
    auto.onclick = function () {
      var cur = info();
      var next = !(cur.autoUpdate && !cur.neverCheck);
      if (NATIVE) NATIVE.setAutoUpdate(next);
      auto.textContent = next ? '已开启' : '已停止';
      auto.className = 'ofone-btn' + (next ? ' primary' : '');
    };
    card.appendChild(row('启动时检查 OpenFic 更新', auto));

    card.appendChild(row('暂停检查', snoozeText(i.snoozeUntil)));

    var checkO = el('button', 'ofone-btn', '检查');
    checkO.onclick = function () {
      checkO.textContent = '检查中...';
      checkO.disabled = true;
      window.__ofoneCheckBtn = checkO;
      if (NATIVE) NATIVE.checkOpenFicUpdate();
      else { checkO.textContent = '仅 App 内可用'; }
    };
    card.appendChild(row('手动检查 OpenFic 更新', checkO));

    var checkA = el('button', 'ofone-btn', '检查');
    checkA.onclick = function () {
      checkA.textContent = '检查中...';
      checkA.disabled = true;
      window.__ofoneCheckAppBtn = checkA;
      if (NATIVE) NATIVE.checkAppUpdate();
      else { checkA.textContent = '仅 App 内可用'; }
    };
    card.appendChild(row('手动检查 OF-One 更新', checkA));

    var apiRow = el('div', 'ofone-row');
    apiRow.appendChild(el('span', 'ofone-k', 'OF-One 更新源'));
    var apiBox = el('div', '');
    apiBox.style.display = 'flex';
    apiBox.style.gap = '8px';
    var apiInput = el('input', 'ofone-input');
    apiInput.placeholder = 'JSON 地址';
    apiInput.style.width = '132px';
    try { apiInput.value = NATIVE ? (NATIVE.getAppUpdateApi() || '') : ''; } catch (e) { apiInput.value = ''; }
    var apiSave = el('button', 'ofone-btn', '保存');
    apiSave.onclick = function () {
      if (!NATIVE) return;
      NATIVE.setAppUpdateApi(apiInput.value);
      apiSave.textContent = '已保存';
      setTimeout(function () { apiSave.textContent = '保存'; }, 1200);
    };
    apiBox.appendChild(apiInput);
    apiBox.appendChild(apiSave);
    apiRow.appendChild(apiBox);
    card.appendChild(apiRow);

    var upd = el('button', 'ofone-btn primary', '立即更新 OpenFic');
    upd.onclick = function () {
      if (!NATIVE) return;
      mask.remove();
      NATIVE.startOpenficUpdate();
    };
    card.appendChild(row('更新 OpenFic', upd));

    card.appendChild(el('p', 'ofone-hint', '更新只重建 Python 环境，不会删除作品与设置。更新过程会在控制台显示进度，完成后自动回到应用。'));

    var foot = el('div', 'ofone-foot');
    var close = el('button', 'ofone-btn', '关闭');
    close.onclick = function () { mask.remove(); };
    foot.appendChild(close);
    card.appendChild(foot);

    mask.appendChild(card);
    mask.addEventListener('click', function (e) { if (e.target === mask) mask.remove(); });
    document.body.appendChild(mask);
  }

  window.OFOneBridge = {
    onOpenficResult: function (r) {
      var b = window.__ofoneCheckBtn;
      if (b) { b.disabled = false; b.textContent = '检查'; }
      if (!r) return;
      if (r.status === 'error') { alert(r.message || '检查失败'); return; }
      if (r.updateAvailable) {
        if (confirm('发现 OpenFic ' + r.latest + '（当前 ' + r.current + '）。\n是否立即更新？')) {
          if (NATIVE) NATIVE.startOpenficUpdate();
        }
      } else {
        alert('已是最新版本 ' + (r.current || r.latest));
      }
    },
    onAppResult: function (r) {
      var b = window.__ofoneCheckAppBtn;
      if (b) { b.disabled = false; b.textContent = '检查'; }
      if (!r) return;
      if (r.status === 'unconfigured') { alert(r.message || '尚未配置 OF-One 更新源'); return; }
      if (r.status === 'error') { alert(r.message || '检查失败'); return; }
      if (r.updateAvailable) alert('发现 OF-One ' + r.latest + '，请前往发布页下载安装。');
      else alert('OF-One 已是最新版本');
    },
    open: openPanel
  };

  var ICON = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">'
    + '<circle cx="8" cy="8" r="6.1" stroke="currentColor" stroke-width="1.5"/>'
    + '<path d="M8 5v6M5 8h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';

  function mountItem(list, itemCls, iconCls, labelCls) {
    if (list.querySelector('[data-ofone]')) return;
    var btn = el('button', itemCls);
    btn.type = 'button';
    btn.setAttribute('data-ofone', '1');
    var icon = el('span', iconCls);
    icon.setAttribute('aria-hidden', 'true');
    icon.innerHTML = ICON;
    var label = el('div', labelCls);
    label.style.display = 'flex';
    label.style.alignItems = 'center';
    label.appendChild(el('span', '', LABEL));
    btn.appendChild(icon);
    btn.appendChild(label);
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      openPanel();
    });
    list.appendChild(btn);
  }

  function installSettingsItem() {
    var desk = document.querySelector('.settings-sidebar-list');
    var mob = document.querySelector('.settings-dialog-mobile-category-list');
    if (!desk && !mob) return false;
    if (desk) mountItem(desk, 'settings-sidebar-item', 'settings-sidebar-item-icon', 'settings-sidebar-item-label');
    if (mob) mountItem(mob, 'settings-dialog-mobile-category-item', 'settings-dialog-mobile-category-item-icon', 'settings-dialog-mobile-category-item-label');
    return true;
  }

  function installFab() {
    if (document.querySelector('.ofone-fab')) return;
    style();
    var b = el('button', 'ofone-fab', 'One');
    b.onclick = openPanel;
    document.body.appendChild(b);
  }

  function removeFab() {
    var f = document.querySelector('.ofone-fab');
    if (f && f.parentNode) f.parentNode.removeChild(f);
  }

  function tick() {
    if (installSettingsItem()) {
      removeFab();
      return;
    }
    if (document.querySelector('.settings-sidebar, [role="dialog"]')) installFab();
  }

  function boot() {
    style();
    tick();
    if (window.MutationObserver) {
      new MutationObserver(function () { tick(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    }
    window.addEventListener('popstate', tick);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
