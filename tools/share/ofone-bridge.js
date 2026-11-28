/* OpenFic One 注入脚本：在 OpenFic 设置页插入「One 设置」入口与面板 */
(function () {
  'use strict';
  if (window.__ofoneInjected) return;
  window.__ofoneInjected = true;

  var NATIVE = window.OFOneNative || null;
  var EN = false;

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
    '.ofone-rec{display:inline-block;margin-left:6px;padding:1px 7px;border-radius:999px;border:1px solid #2ea043;color:#3fb950;font-size:11px;line-height:1.6;vertical-align:middle}',
    '.ofone-drop{cursor:pointer}'
  ].join('');

  function style() {
    if (document.getElementById('ofone-style')) return;
    var s = document.createElement('style');
    s.id = 'ofone-style';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  function info() {
    if (!NATIVE) return { appVersion: '1.0.0', openficVersion: '', autoUpdate: true, neverCheck: false, snoozeUntil: 0, language: 'zh' };
    try { return JSON.parse(NATIVE.getInfo()); } catch (e) { return {}; }
  }

  function refreshLang() {
    var i = info();
    EN = !!(i && i.language === 'en');
    return EN;
  }

  function t(zh, en) { return EN ? en : zh; }

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

  function fmtSize(n) {
    if (!n || n < 0) return '';
    if (n >= 1048576) return (n / 1048576).toFixed(1) + ' MB';
    if (n >= 1024) return Math.round(n / 1024) + ' KB';
    return n + ' B';
  }

  function rowNodes(keyNode, valueNode) {
    var r = el('div', 'ofone-row');
    r.appendChild(keyNode);
    r.appendChild(valueNode);
    return r;
  }

  function neverBtn(on, apply) {
    var onText = t('已开启', 'On');
    var offText = t('已关闭', 'Off');
    var b = el('button', 'ofone-btn' + (on ? ' primary' : ''), on ? onText : offText);
    b.onclick = function () {
      var next = b.textContent !== onText;
      apply(next);
      b.textContent = next ? onText : offText;
      b.className = 'ofone-btn' + (next ? ' primary' : '');
    };
    return b;
  }

  function pauseRow(label, until, apply) {
    var box = el('div', '');
    box.style.display = 'flex';
    box.style.gap = '6px';
    box.style.alignItems = 'center';
    var state = el('span', 'ofone-hint', snoozeText(until));
    var input = el('input', 'ofone-input');
    input.placeholder = t('天数', 'Days');
    input.style.width = '52px';
    var go = el('button', 'ofone-btn', t('暂停', 'Pause'));
    go.onclick = function () {
      var d = parseInt(input.value, 10);
      if (!d || d < 1) { input.focus(); return; }
      var next = Date.now() + d * 86400000;
      apply(next);
      state.textContent = snoozeText(next);
      input.value = '';
    };
    var clear = el('button', 'ofone-btn', t('恢复', 'Resume'));
    clear.onclick = function () {
      apply(0);
      state.textContent = snoozeText(0);
    };
    box.appendChild(input);
    box.appendChild(go);
    box.appendChild(clear);
    box.appendChild(state);
    return rowNodes(el('span', 'ofone-k', label), box);
  }

  function snoozeText(until) {
    if (!until || until < Date.now()) return t('未设置', 'Not set');
    var d = Math.ceil((until - Date.now()) / 86400000);
    if (EN) return d + (d === 1 ? ' day left' : ' days left');
    return '还剩 ' + d + ' 天';
  }

  function openPanel() {
    style();
    refreshLang();
    var old = document.getElementById('ofone-mask');
    if (old) old.remove();
    var i = info();

    var mask = el('div', 'ofone-mask');
    mask.id = 'ofone-mask';
    var card = el('div', 'ofone-card');

    card.appendChild(el('h2', 'ofone-title', 'OpenFic One'));
    card.appendChild(el('p', 'ofone-sub', t('本页由 OpenFic One 启动器注入，用于管理启动器自身与 OpenFic 的版本。',
      'Injected by the OpenFic One launcher to manage launcher and OpenFic versions.')));

    card.appendChild(row(t('启动器版本', 'Launcher version'), i.appVersion || '1.0.0'));
    card.appendChild(row(t('OpenFic 版本', 'OpenFic version'), i.openficVersion || t('未安装', 'Not installed')));

    var onText = t('已开启', 'On');
    var offText = t('已停止', 'Off');
    var enabled = !!(i.autoUpdate && !i.neverCheck);
    var auto = el('button', 'ofone-btn' + (enabled ? ' primary' : ''), enabled ? onText : offText);
    auto.onclick = function () {
      var cur = info();
      var next = !(cur.autoUpdate && !cur.neverCheck);
      if (NATIVE) NATIVE.setAutoUpdate(next);
      auto.textContent = next ? onText : offText;
      auto.className = 'ofone-btn' + (next ? ' primary' : '');
    };
    card.appendChild(row(t('启动时检查 OpenFic 更新', 'Check OpenFic updates on launch'), auto));

    card.appendChild(row(t('无限期暂停 OpenFic 更新', 'Pause OpenFic updates indefinitely'), neverBtn(!!i.neverCheck, function (v) {
      if (NATIVE) NATIVE.setNeverCheck(v);
    })));
    card.appendChild(pauseRow(t('暂停 OpenFic 更新', 'Pause OpenFic updates'), i.snoozeUntil, function (tt) {
      if (NATIVE) NATIVE.setSnoozeUntil(tt);
    }));

    var checkO = el('button', 'ofone-btn', t('检查', 'Check'));
    checkO.onclick = function () {
      checkO.textContent = t('检查中...', 'Checking...');
      checkO.disabled = true;
      window.__ofoneCheckBtn = checkO;
      if (NATIVE) NATIVE.checkOpenFicUpdate();
      else { checkO.textContent = t('仅 App 内可用', 'App only'); }
    };
    card.appendChild(row(t('启动时检查 OpenFic One 更新', 'Check OpenFic One updates on launch'), (function () {
      var on = !!(i.appAutoUpdate && !i.appNeverCheck);
      var b = el('button', 'ofone-btn' + (on ? ' primary' : ''), on ? onText : offText);
      b.onclick = function () {
        var next = b.textContent !== onText;
        if (NATIVE) NATIVE.setAppAutoUpdate(next);
        b.textContent = next ? onText : offText;
        b.className = 'ofone-btn' + (next ? ' primary' : '');
      };
      return b;
    })()));
    card.appendChild(row(t('无限期暂停 OpenFic One 更新', 'Pause OpenFic One updates indefinitely'), neverBtn(!!i.appNeverCheck, function (v) {
      if (NATIVE) NATIVE.setAppNeverCheck(v);
    })));
    card.appendChild(pauseRow(t('暂停 OpenFic One 更新', 'Pause OpenFic One updates'), i.appSnoozeUntil, function (tt) {
      if (NATIVE) NATIVE.setAppSnoozeUntil(tt);
    }));

    card.appendChild(row(t('手动检查 OpenFic 更新', 'Check OpenFic updates now'), checkO));

    var checkA = el('button', 'ofone-btn', t('检查', 'Check'));
    checkA.onclick = function () {
      checkA.textContent = t('检查中...', 'Checking...');
      checkA.disabled = true;
      window.__ofoneCheckAppBtn = checkA;
      if (NATIVE) NATIVE.checkAppUpdate();
      else { checkA.textContent = t('仅 App 内可用', 'App only'); }
    };
    card.appendChild(row(t('手动检查 OpenFic One 更新', 'Check OpenFic One updates now'), checkA));

    var appBox = el('div', 'ofone-appbox');
    appBox.style.display = 'none';
    window.__ofoneAppBox = appBox;
    card.appendChild(appBox);

    var apiRow = el('div', 'ofone-row');
    apiRow.appendChild(el('span', 'ofone-k', t('OpenFic One 更新源', 'OpenFic One update source')));
    var apiBox = el('div', '');
    apiBox.style.display = 'flex';
    apiBox.style.gap = '8px';
    var apiInput = el('input', 'ofone-input');
    apiInput.placeholder = t('留空用内置源', 'Blank = built-in');
    apiInput.style.width = '132px';
    try { apiInput.value = NATIVE ? (NATIVE.getAppUpdateApi() || '') : ''; } catch (e) { apiInput.value = ''; }
    var apiSave = el('button', 'ofone-btn', t('保存', 'Save'));
    apiSave.onclick = function () {
      if (!NATIVE) return;
      NATIVE.setAppUpdateApi(apiInput.value);
      apiSave.textContent = t('已保存', 'Saved');
      setTimeout(function () { apiSave.textContent = t('保存', 'Save'); }, 1200);
    };
    apiBox.appendChild(apiInput);
    apiBox.appendChild(apiSave);
    apiRow.appendChild(apiBox);
    card.appendChild(apiRow);

    var expBtn = el('button', 'ofone-btn', t('导出', 'Export'));
    expBtn.onclick = function () {
      if (!NATIVE) { expBtn.textContent = t('仅 App 内可用', 'App only'); return; }
      expBtn.textContent = t('导出中...', 'Exporting...');
      expBtn.disabled = true;
      window.__ofoneDataBtn = expBtn;
      window.__ofoneDataLabel = t('导出', 'Export');
      NATIVE.exportData();
    };
    card.appendChild(row(t('导出数据', 'Export data'), expBtn));

    var impBtn = el('button', 'ofone-btn', t('导入', 'Import'));
    impBtn.onclick = function () {
      if (!NATIVE) { impBtn.textContent = t('仅 App 内可用', 'App only'); return; }
      if (!confirm(t('导入会覆盖当前全部作品与设置，且会重启服务。确定继续？',
        'Import will overwrite all works and settings and restart the service. Continue?'))) return;
      impBtn.textContent = t('导入中...', 'Importing...');
      impBtn.disabled = true;
      window.__ofoneDataBtn = impBtn;
      window.__ofoneDataLabel = t('导入', 'Import');
      NATIVE.importData();
    };
    card.appendChild(row(t('导入数据', 'Import data'), impBtn));

    var logBtn = el('button', 'ofone-btn', t('下载日志', 'Download logs'));
    logBtn.onclick = function () {
      if (!NATIVE) { logBtn.textContent = t('仅 App 内可用', 'App only'); return; }
      NATIVE.openLogFolder();
    };
    card.appendChild(row(t('下载日志', 'Download logs'), logBtn));

    var upd = el('button', 'ofone-btn primary', t('立即更新 OpenFic', 'Update OpenFic now'));
    upd.onclick = function () {
      if (!NATIVE) return;
      mask.remove();
      NATIVE.startOpenficUpdate();
    };
    card.appendChild(row(t('更新 OpenFic', 'Update OpenFic'), upd));

    card.appendChild(el('p', 'ofone-hint', t(
      '导出会把作品、设置与数据库打包成 zip 存到 下载/OpenFic/；导入会覆盖现有数据并重启服务。更新只重建 Python 环境，不会删除作品与设置。',
      'Export packs works, settings and the database into a zip under Download/OpenFic/; import overwrites existing data and restarts the service. Updating only rebuilds the Python environment and keeps your works and settings.')));

    var foot = el('div', 'ofone-foot');
    var close = el('button', 'ofone-btn', t('关闭', 'Close'));
    close.onclick = function () { mask.remove(); };
    foot.appendChild(close);
    card.appendChild(foot);

    mask.appendChild(card);
    mask.addEventListener('click', function (e) { if (e.target === mask) mask.remove(); });
    document.body.appendChild(mask);
  }

  window.OFOneBridge = {
    onOpenficResult: function (r) {
      refreshLang();
      var b = window.__ofoneCheckBtn;
      if (b) { b.disabled = false; b.textContent = t('检查', 'Check'); }
      if (!r) return;
      if (r.status === 'error') { alert(r.message || t('检查失败', 'Check failed')); return; }
      if (r.updateAvailable) {
        if (confirm(t('发现 OpenFic ' + r.latest + '（当前 ' + r.current + '）。\n是否立即更新？',
          'OpenFic ' + r.latest + ' available (current ' + r.current + ').\nUpdate now?'))) {
          if (NATIVE) NATIVE.startOpenficUpdate();
        }
      } else {
        alert(t('已是最新版本 ' + (r.current || r.latest), 'Already up to date: ' + (r.current || r.latest)));
      }
    },
    onDataResult: function (r) {
      refreshLang();
      var b = window.__ofoneDataBtn;
      var label = window.__ofoneDataLabel || '';
      if (b) { b.disabled = false; b.textContent = label; }
      if (!r) return;
      if (r.status === 'ok') alert(r.message || t('完成', 'Done'));
      else if (r.status === 'cancelled') return;
      else alert(r.message || t('操作失败', 'Operation failed'));
    },
    onAppResult: function (r) {
      refreshLang();
      var b = window.__ofoneCheckAppBtn;
      if (b) { b.disabled = false; b.textContent = t('检查', 'Check'); }
      var btns = window.__ofoneAppBtns || [];
      for (var k = 0; k < btns.length; k++) {
        btns[k].disabled = false;
        if (btns[k].getAttribute('data-label')) btns[k].textContent = btns[k].getAttribute('data-label');
      }
      window.__ofoneAppBtns = [];
      if (!r) return;
      if (r.status === 'unconfigured') { alert(r.message || t('尚未配置 OpenFic One 更新源', 'No OpenFic One update source configured')); return; }
      if (r.status === 'error') { alert(r.message || t('操作失败', 'Operation failed')); return; }
      if (r.status === 'installing') {
        alert(t('安装包已下载，请在系统安装界面完成安装。', 'Package downloaded. Finish installing in the system installer.'));
        return;
      }
      if (r.status === 'cancelled') {
        alert(r.message || t('已取消下载', 'Download cancelled'));
        return;
      }
      var box = window.__ofoneAppBox;
      if (!box) return;
      box.innerHTML = '';
      if (!r.updateAvailable) {
        box.style.display = 'none';
        alert(t('OpenFic One 已是最新版本 ' + (r.current || r.latest), 'OpenFic One is up to date: ' + (r.current || r.latest)));
        return;
      }
      box.style.display = 'block';
      box.appendChild(el('p', 'ofone-sub', t('发现 OpenFic One ' + r.latest + '（当前 ' + r.current + '）',
        'OpenFic One ' + r.latest + ' available (current ' + r.current + ')')));
      if (r.notes) box.appendChild(el('p', 'ofone-hint', r.notes));
      if (r.source) box.appendChild(el('p', 'ofone-hint', t('版本信息来源：', 'Version info from: ') + r.source));
      if (r.size) box.appendChild(el('p', 'ofone-hint', t('安装包大小：', 'Package size: ') + fmtSize(r.size) + t('（各下载方式内容相同）', ' (same for all download options)')));
      var list = r.sources || [];
      if (!list.length) {
        box.appendChild(el('p', 'ofone-hint', t('更新源未提供安装包地址。', 'The update source provided no package URL.')));
        return;
      }
      var made = [];
      for (var i = 0; i < list.length; i++) {
        (function (idx, src) {
          var label = (src && src.label) ? src.label : t('源 ' + (idx + 1), 'Source ' + (idx + 1));
          var text = t('下载安装', 'Download & install');
          var btn = el('button', 'ofone-btn' + (idx === 0 ? ' primary' : ''), text);
          btn.setAttribute('data-label', text);
          btn.onclick = function () {
            if (!NATIVE) return;
            btn.disabled = true;
            btn.textContent = t('下载中...', 'Downloading...');
            window.__ofoneAppBtns = [btn];
            NATIVE.downloadAppUpdate(idx);
          };
          made.push(btn);
          var key = el('span', 'ofone-k', label);
          if (idx === 0) key.appendChild(el('span', 'ofone-rec', t('推荐', 'Recommended')));
          box.appendChild(rowNodes(key, btn));
        })(i, list[i]);
      }
      window.__ofoneAppBtns = made;
    },
    open: openPanel
  };

  var ICON = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">'
    + '<circle cx="8" cy="8" r="6.1" stroke="currentColor" stroke-width="1.5"/>'
    + '<path d="M8 5v6M5 8h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
  var CHEVRON = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">'
    + '<path d="M6 3.5 10.5 8 6 12.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  function mountItem(list, itemCls, iconCls, labelCls, mobile) {
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
    label.style.gap = '2px';
    label.appendChild(el('span', '', t('One 设置', 'One Settings')));
    if (mobile) {
      var wrap = el('div', 'settings-dialog-mobile-category-item-content');
      wrap.style.display = 'flex';
      wrap.style.alignItems = 'center';
      wrap.style.gap = '3px';
      wrap.appendChild(icon);
      wrap.appendChild(label);
      btn.appendChild(wrap);
      var arrow = el('span', 'settings-dialog-mobile-category-item-arrow');
      arrow.setAttribute('aria-hidden', 'true');
      arrow.innerHTML = CHEVRON;
      btn.appendChild(arrow);
    } else {
      btn.appendChild(icon);
      btn.appendChild(label);
    }
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
    if (desk) mountItem(desk, 'settings-sidebar-item', 'settings-sidebar-item-icon', 'settings-sidebar-item-label', false);
    if (mob) mountItem(mob, 'settings-dialog-mobile-category-item', 'settings-dialog-mobile-category-item-icon', 'settings-dialog-mobile-category-item-label', true);
    return true;
  }

  var DROP_SEL = '.import-skill-dropzone, .import-dialog-dropzone, .note-import-dropzone';

  function revealInput(d) {
    var box = d.parentNode;
    var input = box ? box.querySelector('input[type="file"]') : null;
    if (!input) return null;
    input.style.display = 'block';
    input.style.position = 'absolute';
    input.style.width = '1px';
    input.style.height = '1px';
    input.style.opacity = '0';
    input.style.overflow = 'hidden';
    input.style.pointerEvents = 'none';
    return input;
  }

  function patchImport() {
    var nodes = document.querySelectorAll(DROP_SEL);
    var pick = t('点击选择…', 'Click to choose…');
    for (var i = 0; i < nodes.length; i++) {
      var d = nodes[i];
      var p = d.querySelector('p');
      if (p && p.textContent !== pick) p.textContent = pick;
      var input = revealInput(d);
      if (d.getAttribute('data-ofone-drop')) continue;
      d.setAttribute('data-ofone-drop', '1');
      d.classList.add('ofone-drop');
      (function (node, target) {
        node.addEventListener('click', function (e) {
          if (!target) return;
          e.preventDefault();
          e.stopPropagation();
          target.click();
        }, true);
      })(d, input);
    }
  }

  function tick() {
    installSettingsItem();
    patchImport();
  }

  function boot() {
    style();
    refreshLang();
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
