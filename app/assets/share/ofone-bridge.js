/* OpenFic One 注入脚本：在 OpenFic 设置页插入「One 设置」入口，点击后在设置对话框内进入子页面（与原生设置一致的导航方式） */
(function () {
  'use strict';
  if (window.__ofoneInjected) return;
  window.__ofoneInjected = true;

  var NATIVE = window.OFOneNative || null;
  var EN = false;
  var THEME = 'dark';
  var AI_TASK_KEY = 'ofone.ai.lastTask';

  var CSS = [
    /* 子页面容器：覆盖在设置详情区之上，一级页面仍留在 DOM 中，返回后滚动位置自然保留 */
    '.ofone-page{position:absolute;inset:0;z-index:20;display:flex;flex-direction:column;background:var(--color-background)}',
    '.ofone-pagebar{display:flex;align-items:center;gap:8px;min-height:56px;padding:0 12px;border-bottom:1px solid var(--gray-a4);flex:none}',
    '.ofone-topbar{flex:none;border-bottom:1px solid var(--gray-a4)}',
    '.ofone-back{display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;border:0;background:transparent;color:var(--gray-11);border-radius:8px;cursor:pointer;flex:none}',
    '.ofone-back:hover{background:var(--gray-a3)}',
    '.ofone-pagetitle{font-size:16px;font-weight:600;color:var(--gray-12)}',
    /* 折叠分组 */
    '.ofone-sec{border-bottom:1px solid var(--gray-a4)}',
    '.ofone-sec-head{display:flex;align-items:center;gap:12px;width:100%;background:transparent;border:0;padding:16px 0;cursor:pointer;color:var(--gray-12);text-align:left;font:inherit}',
    '.ofone-sec-title{flex:1;min-width:0}',
    '.ofone-sec-ver{color:var(--gray-11);font-variant-numeric:tabular-nums;flex:none}',
    '.ofone-sec-chev{display:inline-flex;color:var(--gray-9);transition:transform .18s;flex:none}',
    '.ofone-sec.open .ofone-sec-chev{transform:rotate(180deg)}',
    '.ofone-sec-body{display:none;padding:0 0 16px}',
    '.ofone-sec.open .ofone-sec-body{display:flex;flex-direction:column;gap:16px}',
    /* 整行可点击操作项 */
    '.ofone-act{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;background:transparent;border:0;border-top:1px solid var(--gray-a4);padding:14px 0;cursor:pointer;color:var(--gray-12);font:inherit;text-align:left}',
    '.ofone-act-left{display:flex;flex-direction:column;gap:3px;min-width:0}',
    '.ofone-act-chev{display:inline-flex;color:var(--gray-9);flex:none}',
    '.ofone-sel{display:inline-flex;max-width:100%}',
    '.ofone-sel .rt-SelectTrigger{min-width:180px}',
    '.ofone-sel-chev{display:inline-flex;color:var(--gray-9);flex:none;margin-left:auto}',
    /* 下拉菜单（fixed，避免被滚动容器裁剪） */
    '.ofone-menu-fixed{pointer-events:auto;position:fixed;z-index:2147483001;min-width:180px;background:var(--color-background);border-radius:10px;box-shadow:0 12px 32px rgba(0,0,0,.28),inset 0 0 0 1px var(--gray-a5);padding:4px}',
    '.ofone-menu-item{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 10px;border-radius:6px;font-size:14px;color:var(--gray-12);cursor:pointer;user-select:none}',
    '.ofone-menu-item:hover{background:var(--accent-a3)}',
    '.ofone-menu-item[data-on=true]{color:var(--accent-11)}',
    '.ofone-check{color:var(--accent-11);flex:none;display:inline-flex}',
    /* 底部上滑覆盖层 */
    '.ofone-sheet-mask{pointer-events:auto;position:fixed;inset:0;z-index:2147483000;background:rgba(0,0,0,.45);display:flex;align-items:flex-end;justify-content:center}',
    '.ofone-sheet{width:100%;max-width:640px;max-height:82vh;background:var(--color-background);border-radius:16px 16px 0 0;box-shadow:0 -12px 40px rgba(0,0,0,.4),inset 0 1px 0 var(--gray-a4);display:flex;flex-direction:column;transform:translateY(100%);transition:transform .24s cubic-bezier(.2,.8,.2,1)}',
    '.ofone-sheet-mask.open .ofone-sheet{transform:translateY(0)}',
    '.ofone-sheet-head{padding:16px 20px 8px;display:flex;align-items:center;gap:8px}',
    '.ofone-sheet-title{flex:1;font-size:16px;font-weight:600;color:var(--gray-12)}',
    '.ofone-sheet-body{padding:4px 20px 8px;overflow:auto;display:flex;flex-direction:column;gap:12px}',
    '.ofone-sheet-foot{padding:12px 20px 20px;display:flex;justify-content:flex-end;gap:8px;flex:none}',
    '.ofone-spinner{width:28px;height:28px;border-radius:50%;border:3px solid var(--gray-a5);border-top-color:var(--accent-9,#3e63dd);animation:ofone-spin .8s linear infinite;margin:14px auto}',
    '@keyframes ofone-spin{to{transform:rotate(360deg)}}',
    '.ofone-sheet-hint{color:var(--gray-11);font-size:13px;line-height:1.6;margin:0}',
    '.ofone-sheet-msg{color:var(--gray-12);font-size:14px;line-height:1.6;margin:0}',
    '.ofone-sheet-note{color:var(--gray-11);font-size:13px;line-height:1.6;margin:0;white-space:pre-wrap}',
    '.ofone-kv{display:flex;justify-content:space-between;gap:12px;font-size:14px;color:var(--gray-12)}',
    '.ofone-kv .k{color:var(--gray-11)}',
    '.ofone-file{display:flex;align-items:center;gap:8px;padding:10px 12px;border-radius:8px;background:var(--gray-a3);color:var(--gray-12);font-size:14px;word-break:break-all}',
    '.ofone-dl{display:flex;flex-direction:column;gap:8px}'
  ].join('');

  function style() {
    if (document.getElementById('ofone-style')) return;
    var s = document.createElement('style');
    s.id = 'ofone-style';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  function info() {
    if (!NATIVE) return { appVersion: '1.0.0', openficVersion: '', autoUpdate: true, neverCheck: false, snoozeUntil: 0, appAutoUpdate: true, appNeverCheck: false, appSnoozeUntil: 0, language: 'zh', theme: 'system' };
    try { return JSON.parse(NATIVE.getInfo()); } catch (e) { return {}; }
  }

  function refreshLang() {
    var i = info();
    EN = !!(i && i.language === 'en');
    var dark = (i && typeof i.themeDark === 'boolean') ? i.themeDark : !(i && i.theme === 'light');
    THEME = dark ? 'dark' : 'light';
    return EN;
  }

  function t(zh, en) { return EN ? en : zh; }
  function f(zh, en) {
    var args = Array.prototype.slice.call(arguments, 2);
    var fmt = EN ? en : zh;
    var k = 0;
    return fmt.replace(/%s/g, function () { return String(args[k++]); });
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  // 覆盖层挂载点：优先放进设置对话框内容子树。Radix 模态打开时 body 被设为 pointer-events:none，
  // 挂到 body 会被继承导致点击穿透，且会被 onInteractOutside 当作外部点击关闭整个对话框；
  // 放进对话框内部则二者都不会发生，position:fixed 仍相对视口（祖先无 transform）。
  function overlayHost() {
    return document.querySelector('.settings-dialog-main') ||
           document.querySelector('.settings-dialog-surface') ||
           document.body;
  }

  var CHEV = '<svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var CHECK = '<svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M3.5 8.5l3 3 6-6.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var BACK = '<svg width="20" height="20" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M10 3.5 5.5 8l4.5 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var RARROW = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M6 3.5 10.5 8 6 12.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var CHEV_LEFT18 = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg>';
  var X18 = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>';

  // 原生风格按钮
  function nbtn(label, primary, onClick) {
    var b = el('button', 'rt-BaseButton rt-r-size-2 rt-variant-' + (primary ? 'solid' : 'soft') + ' rt-Button', label);
    b.type = 'button';
    b.onclick = onClick || function () {};
    return b;
  }

  // 原生风格开关
  function nswitch(on, onChange) {
    var b = el('button', 'rt-SwitchRoot rt-r-size-2 rt-variant-surface');
    b.type = 'button';
    b.setAttribute('data-state', on ? 'checked' : 'unchecked');
    b.appendChild(el('span', 'rt-SwitchThumb'));
    b.onclick = function () {
      on = !on;
      b.setAttribute('data-state', on ? 'checked' : 'unchecked');
      onChange(on);
    };
    return b;
  }

  // 原生风格下拉选择
  function nselect(options, value, onChange) {
    var wrap = el('div', 'ofone-sel');
    var trig = el('button', 'rt-SelectTrigger rt-r-size-2 rt-variant-surface select-trigger--background');
    trig.type = 'button';
    var val = el('span', 'rt-Text rt-r-size-2');
    trig.appendChild(val);
    var chev = el('span', 'ofone-sel-chev');
    chev.innerHTML = CHEV;
    trig.appendChild(chev);
    var cur = value;
    function labelOf(c) { for (var i = 0; i < options.length; i++) if (options[i][0] === c) return options[i][1]; return options.length ? options[0][1] : ''; }
    function paint() { val.textContent = labelOf(cur); }
    paint();
    var menu = null;
    function close() {
      if (menu) { menu.remove(); menu = null; }
      document.removeEventListener('click', onDoc, true);
      window.removeEventListener('scroll', close, true);
    }
    function onDoc(e) { if (menu && !menu.contains(e.target) && !trig.contains(e.target)) close(); }
    trig.onclick = function (e) {
      e.stopPropagation();
      if (menu) { close(); return; }
      var r = trig.getBoundingClientRect();
      menu = el('div', 'ofone-menu-fixed');
      for (var i = 0; i < options.length; i++) {
        (function (code, text) {
          var it = el('div', 'ofone-menu-item');
          it.appendChild(el('span', '', text));
          if (code === cur) {
            it.setAttribute('data-on', 'true');
            var ck = el('span', 'ofone-check');
            ck.innerHTML = CHECK;
            it.appendChild(ck);
          }
          it.onclick = function () { cur = code; paint(); close(); onChange(code); };
          menu.appendChild(it);
        })(options[i][0], options[i][1]);
      }
      overlayHost().appendChild(menu);
      menu.style.pointerEvents = 'auto';
      menu.style.position = 'fixed';
      menu.style.left = r.left + 'px';
      menu.style.top = (r.bottom + 4) + 'px';
      menu.style.minWidth = r.width + 'px';
      var mr = menu.getBoundingClientRect();
      if (mr.right > window.innerWidth - 8) menu.style.left = Math.max(8, window.innerWidth - 8 - mr.width) + 'px';
      document.addEventListener('click', onDoc, true);
      window.addEventListener('scroll', close, true);
    };
    wrap.appendChild(trig);
    return wrap;
  }

  // 底部上滑覆盖层
  var sheetMask = null;
  function closeSheet() {
    if (!sheetMask) return;
    var m = sheetMask;
    sheetMask = null;
    m.classList.remove('open');
    setTimeout(function () { try { m.remove(); } catch (e) {} }, 240);
  }
  function openSheet(opt) {
    closeSheet();
    var mask = el('div', 'ofone-sheet-mask');
    var sheet = el('div', 'ofone-sheet');
    var head = el('div', 'ofone-sheet-head');
    head.appendChild(el('span', 'ofone-sheet-title', opt.title || ''));
    var body = el('div', 'ofone-sheet-body');
    var foot = el('div', 'ofone-sheet-foot');
    sheet.appendChild(head);
    sheet.appendChild(body);
    sheet.appendChild(foot);
    mask.appendChild(sheet);
    mask.style.pointerEvents = 'auto';
    overlayHost().appendChild(mask);
    sheetMask = mask;
    // 点击空白不关闭（用户要求）
    var ctrl = {
      body: body, foot: foot, sheet: sheet,
      setContent: function (fn) { body.innerHTML = ''; fn(body); },
      setFooter: function (list) {
        foot.innerHTML = '';
        for (var i = 0; i < (list || []).length; i++) {
          (function (def) {
            var b = nbtn(def.label, !!def.primary, def.onClick);
            if (def.disabled) b.disabled = true;
            if (def.ref) def.ref(b);
            foot.appendChild(b);
          })(list[i]);
        }
      },
      close: closeSheet
    };
    if (opt.build) opt.build(body);
    if (opt.footer) ctrl.setFooter(opt.footer);
    requestAnimationFrame(function () { mask.classList.add('open'); });
    return ctrl;
  }

  function spinner(message) {
    var box = el('div', '');
    box.appendChild(el('div', 'ofone-spinner'));
    if (message) {
      var p = el('p', 'ofone-sheet-hint', message);
      p.style.textAlign = 'center';
      box.appendChild(p);
    }
    return box;
  }

  function actionRow(titleText, descText, onClick) {
    var row = el('button', 'ofone-act');
    row.type = 'button';
    var left = el('div', 'ofone-act-left');
    left.appendChild(el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium', titleText));
    if (descText) left.appendChild(el('span', 'rt-Text rt-r-size-1', descText));
    row.appendChild(left);
    var chev = el('span', 'ofone-act-chev');
    chev.innerHTML = RARROW;
    row.appendChild(chev);
    row.onclick = onClick;
    return row;
  }

  function freqValue(auto, never, snooze) {
    if (never) return 'never';
    if (snooze && snooze > Date.now()) return 'snooze';
    if (auto) return 'always';
    return 'never';
  }

  function freqOptions() {
    return [
      ['always', t('每次启动时更新', 'On every launch')],
      ['never', t('暂停更新', 'Pause updates')],
      ['snooze', t('在指定天数内暂停', 'Pause for N days')]
    ];
  }

  function callNative(name) {
    if (!NATIVE) return;
    var fn = NATIVE[name];
    if (typeof fn !== 'function') return;
    var args = Array.prototype.slice.call(arguments, 1);
    fn.apply(NATIVE, args);
  }

  function applyFreq(kind, code) {
    var setAuto = kind === 'app' ? 'setAppAutoUpdate' : 'setAutoUpdate';
    var setNever = kind === 'app' ? 'setAppNeverCheck' : 'setNeverCheck';
    var setSnooze = kind === 'app' ? 'setAppSnoozeUntil' : 'setSnoozeUntil';
    if (code === 'always') {
      callNative(setNever, false);
      callNative(setSnooze, 0);
      callNative(setAuto, true);
    } else if (code === 'never') {
      callNative(setAuto, false);
      callNative(setSnooze, 0);
      callNative(setNever, true);
    } else if (code === 'snooze') {
      openDaysSheet(kind);
    }
  }

  function openDaysSheet(kind) {
    var ctrl = openSheet({
      title: t('暂停更新', 'Pause updates'),
      build: function (b) {
        b.appendChild(el('p', 'ofone-sheet-hint', t('请输入暂停天数，指定天数内不再检查更新。', 'Enter number of days to pause update checks.')));
        var root = el('div', 'rt-TextFieldRoot rt-r-size-2 rt-variant-surface');
        var input = el('input', 'rt-TextFieldInput');
        input.type = 'number';
        input.min = '1';
        input.placeholder = t('天数', 'Days');
        input.style.flex = '1';
        input.style.width = '100%';
        root.appendChild(input);
        b.appendChild(root);
        setTimeout(function () { try { input.focus(); } catch (e) {} }, 260);
      },
      footer: [
        { label: t('取消', 'Cancel'), onClick: closeSheet },
        {
          label: t('确定', 'OK'), primary: true, onClick: function () {
            var inp = ctrl.body.querySelector('input');
            var d = inp ? parseInt(inp.value, 10) : NaN;
            if (!d || d < 1) { if (inp) inp.focus(); return; }
            var until = Date.now() + d * 86400000;
            var setAuto = kind === 'app' ? 'setAppAutoUpdate' : 'setAutoUpdate';
            var setNever = kind === 'app' ? 'setAppNeverCheck' : 'setNeverCheck';
            var setSnooze = kind === 'app' ? 'setAppSnoozeUntil' : 'setSnoozeUntil';
            callNative(setNever, false);
            callNative(setAuto, true);
            callNative(setSnooze, until);
            closeSheet();
          }
        }
      ]
    });
  }

  // 立即检查：OpenFic（内核）
  function openCheckOpenficSheet() {
    var ctrl = openSheet({
      title: t('检查 OpenFic 更新', 'Check OpenFic updates'),
      build: function (b) { b.appendChild(spinner(t('正在检查更新…', 'Checking for updates…'))); },
      footer: [{ label: t('取消', 'Cancel'), onClick: closeSheet }]
    });
    window.__ofoneCheckCtrl = ctrl;
    window.__ofoneCheckKind = 'openfic';
    callNative('checkOpenFicUpdate');
  }

  // 立即检查：OpenFic One（启动器）
  function openCheckAppSheet() {
    var ctrl = openSheet({
      title: t('检查 OpenFic One 更新', 'Check OpenFic One updates'),
      build: function (b) { b.appendChild(spinner(t('正在检查更新…', 'Checking for updates…'))); },
      footer: [{ label: t('取消', 'Cancel'), onClick: closeSheet }]
    });
    window.__ofoneCheckCtrl = ctrl;
    window.__ofoneCheckKind = 'app';
    callNative('checkAppUpdate');
  }

  function openCheckSheet(kind) {
    if (kind === 'app') openCheckAppSheet(); else openCheckOpenficSheet();
  }

  function buildSection(titleText, verText, buildBody) {
    var sec = el('div', 'ofone-sec');
    var head = el('button', 'ofone-sec-head');
    head.type = 'button';
    head.appendChild(el('span', 'ofone-sec-title rt-Text rt-r-size-2 rt-r-weight-medium', titleText));
    head.appendChild(el('span', 'ofone-sec-ver rt-Text rt-r-size-2', verText || ''));
    var chev = el('span', 'ofone-sec-chev');
    chev.innerHTML = CHEV;
    head.appendChild(chev);
    var body = el('div', 'ofone-sec-body');
    buildBody(body);
    head.onclick = function () { sec.classList.toggle('open'); };
    sec.appendChild(head);
    sec.appendChild(body);
    return sec;
  }

  function versionBody(body, kind) {
    var i = info();
    var auto = kind === 'app' ? i.appAutoUpdate : i.autoUpdate;
    var never = kind === 'app' ? i.appNeverCheck : i.neverCheck;
    var snooze = kind === 'app' ? i.appSnoozeUntil : i.snoozeUntil;
    var fld = el('div', 'rt-Flex rt-r-fd-column rt-r-gap-2');
    fld.appendChild(el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium', t('更新检查频率', 'Update check frequency')));
    fld.appendChild(nselect(freqOptions(), freqValue(auto, never, snooze), function (code) { applyFreq(kind, code); }));
    body.appendChild(fld);
    body.appendChild(actionRow(t('立即检查更新', 'Check for updates now'), t('查看是否有可用更新', 'See if an update is available'), function () { openCheckSheet(kind); }));
  }

  function dataRows(container) {
    container.appendChild(actionRow(t('导入备份数据', 'Import backup'), t('从 .zip / .tar.gz / .tgz 恢复', 'Restore from .zip / .tar.gz / .tgz'), openImportSheet));
    container.appendChild(actionRow(t('导出备份数据', 'Export backup'), t('打包作品与设置到 下载/OpenFic/', 'Pack works and settings to Download/OpenFic/'), openExportSheet));
    container.appendChild(actionRow(t('下载日志', 'Download logs'), null, function () { callNative('openLogFolder'); }));
  }

  function generalBody(body) {
    var i = info();
    var curLang = EN ? 'en' : 'zh-CN';
    var lf = el('div', 'rt-Flex rt-r-fd-column rt-r-gap-2');
    lf.appendChild(el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium', t('界面语言', 'Language')));
    lf.appendChild(nselect([['zh-CN', '中文'], ['en', 'English']], curLang, function (code) { callNative('setLanguage', code); }));
    body.appendChild(lf);
    var curTheme = (i.theme === 'light' || i.theme === 'dark') ? i.theme : 'system';
    var tf = el('div', 'rt-Flex rt-r-fd-column rt-r-gap-2');
    tf.appendChild(el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium', t('界面主题', 'Theme')));
    tf.appendChild(nselect([['system', t('跟随系统', 'System')], ['light', t('浅色', 'Light')], ['dark', t('深色', 'Dark')]], curTheme, function (code) { callNative('setTheme', code); }));
    body.appendChild(tf);
  }

  function buildContent(scroll) {
    var i = info();
    // 启动器
    scroll.appendChild(buildSection(t('启动器版本', 'Launcher version'), i.appVersion || '1.0.0', function (b) { versionBody(b, 'app'); }));
    // OpenFic
    scroll.appendChild(buildSection(t('OpenFic 版本', 'OpenFic version'), i.openficVersion || t('未安装', 'Not installed'), function (b) { versionBody(b, 'openfic'); }));
    // 数据
    var dataSec = buildSection(t('备份与数据', 'Backup & data'), '', function (b) { dataRows(b); });
    dataSec.classList.add('open');
    scroll.appendChild(dataSec);
    // 通用（语言/主题）
    scroll.appendChild(buildSection(t('通用', 'General'), '', function (b) { generalBody(b); }));

    scroll.appendChild(el('p', 'ofone-sheet-hint', t(
      '更新通过 pip / 应用安装包原地升级，保留作品与设置。导出的备份包含作品、设置与数据库；导入会覆盖现有数据并重启服务。',
      'Updates upgrade in place (pip / app package) and keep your works and settings. Exported backups contain works, settings and the database; import overwrites existing data and restarts the service.')));
  }

  var pageHost = null;

  function buildPage() {
    var page = el('div', 'ofone-page');
    // 复用原生设置二级页顶栏结构（与 .settings-dialog-mobile-topbar 一致的外观）
    var bar = el('div', 'rt-Flex rt-r-ai-center settings-dialog-mobile-topbar ofone-topbar');
    var lead = el('div', 'rt-Flex rt-r-ai-center rt-r-gap-2 settings-dialog-mobile-topbar-leading');
    var back = el('button', 'rt-reset rt-BaseButton rt-r-size-2 rt-variant-ghost rt-IconButton');
    back.type = 'button';
    back.setAttribute('data-accent-color', 'gray');
    back.setAttribute('data-radius', 'full');
    back.setAttribute('aria-label', t('返回', 'Back'));
    back.innerHTML = CHEV_LEFT18;
    back.onclick = function () { closePage(); };
    lead.appendChild(back);
    lead.appendChild(el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium settings-dialog-mobile-topbar-title', t('One 设置', 'One Settings')));
    bar.appendChild(lead);
    var side = el('div', 'rt-Box settings-dialog-mobile-topbar-side settings-dialog-mobile-topbar-side--end');
    var closeBtn = el('button', 'rt-reset rt-BaseButton rt-r-size-2 rt-variant-ghost rt-IconButton');
    closeBtn.type = 'button';
    closeBtn.setAttribute('data-accent-color', 'gray');
    closeBtn.setAttribute('data-radius', 'full');
    closeBtn.setAttribute('aria-label', t('关闭', 'Close'));
    closeBtn.innerHTML = X18;
    closeBtn.onclick = function () { closePage(); };
    side.appendChild(closeBtn);
    bar.appendChild(side);
    page.appendChild(bar);

    var scroll = el('div', 'settings-dialog-content-scroll settings-dialog-content-scroll--form-page');
    buildContent(scroll);
    page.appendChild(scroll);
    return page;
  }

  function isVisible(n) {
    if (!n) return false;
    return !!(n.offsetWidth || n.offsetHeight || n.getClientRects().length);
  }

  function openPage() {
    style();
    refreshLang();
    var main = document.querySelector('.settings-dialog-main');
    if (!main) return;
    if (pageHost && main.contains(pageHost)) return;
    pageHost = buildPage();
    main.appendChild(pageHost);
  }

  function closePage() {
    if (!pageHost) return;
    try { pageHost.remove(); } catch (e) {}
    pageHost = null;
  }

  // 记住/恢复 AI 任务：返回收起面板时记录当前任务标题，重开面板后自动进入该任务详情。
  function findAiTaskCard(title) {
    var rs = document.querySelector('.right-sidebar');
    if (!rs || !isVisible(rs)) return null;
    var t = String(title);
    var c = t.indexOf(':');
    if (c >= 0) t = t.slice(c + 1);
    t = t.trim();
    if (!t) return null;
    var cand = [].slice.call(rs.querySelectorAll('button,[role=button],div'))
      .filter(function (e) { return (e.textContent || '').indexOf(t) >= 0 && e.textContent.length < 60; });
    cand.sort(function (a, b) { return a.textContent.length - b.textContent.length; });
    for (var i = 0; i < cand.length; i++) {
      var b = cand[i].closest('button,[role=button]') || cand[i];
      if (b && b.click) return b;
    }
    return null;
  }

  // 仅记录当前详情态任务标题（供返回收起后重开时恢复）
  function aiTick() {
    var tt = document.querySelector('.ai-sidebar-task-title');
    if (tt && isVisible(tt)) {
      var cur = (tt.textContent || '').trim();
      if (cur) window.__ofoneLastAiTask = cur;
    }
  }

  // 打开面板后尝试恢复上次任务（needRestore 时）；带重试以应对列表异步渲染
  function restoreIfNeeded() {
    if (!window.__ofoneAiNeedRestore || window.__ofoneAiClosing) return;
    var tt = document.querySelector('.ai-sidebar-task-title');
    if (tt && isVisible(tt)) { window.__ofoneAiNeedRestore = false; return; }
    var shell = document.querySelector('.ai-sidebar-shell');
    if (!shell || !isVisible(shell) || shell.getBoundingClientRect().left >= 80) return;
    var want = window.__ofoneLastAiTask;
    if (!want) return;
    var card = findAiTaskCard(want);
    if (!card) {
      if (!window.__ofoneAiRetry) {
        window.__ofoneAiRetry = true;
        setTimeout(function () { window.__ofoneAiRetry = false; restoreIfNeeded(); }, 150);
      }
      return;
    }
    window.__ofoneAiNeedRestore = false;
    card.click();
  }

  // 点击 Agent 入口（编辑器内、非面板内部）时，面板打开后尝试恢复上次任务
  function hookAgentEntry() {
    if (window.__ofoneAgentHooked) return;
    window.__ofoneAgentHooked = true;
    document.addEventListener('click', function (e) {
      var t = e.target;
      var b = t && t.closest ? t.closest('button[aria-label]') : null;
      if (!b) return;
      var al = b.getAttribute('aria-label') || '';
      if (!/Agent/i.test(al)) return;
      if (b.closest('.ai-sidebar-shell')) return;
      setTimeout(restoreIfNeeded, 300);
      setTimeout(restoreIfNeeded, 700);
      setTimeout(restoreIfNeeded, 1200);
    }, true);
  }

  // 供原生返回键/返回手势调用：按「最内层可返回」的顺序消费一次返回，返回 true 表示已处理。
  window.__ofoneBack = function () {
    if (sheetMask) { closeSheet(); return true; }
    if (pageHost) { closePage(); return true; }
    // AI 助手侧栏：面板打开时按返回应收起侧栏回编辑页（绝不退首页）
    var aiShell = document.querySelector('.ai-sidebar-shell');
    if (aiShell && isVisible(aiShell) && aiShell.getBoundingClientRect().left < 80) {
      var visClose = function () {
        var cs = document.querySelectorAll('[aria-label="关闭"],[aria-label="Close"]');
        for (var i = 0; i < cs.length; i++) {
          if (isVisible(cs[i]) && cs[i].getBoundingClientRect().left < 80) return cs[i];
        }
        return null;
      };
      var aiTitle = function () { var tt = document.querySelector('.ai-sidebar-task-title'); return tt ? (tt.textContent || '').trim() : ''; };
      var capture = function () { var s = aiTitle(); if (s) window.__ofoneLastAiTask = s; window.__ofoneAiNeedRestore = true; };
      window.__ofoneAiClosing = true;
      setTimeout(function () { window.__ofoneAiClosing = false; }, 500);
      var listClose = visClose();
      if (listClose) { capture(); listClose.click(); return true; }
      var row = document.querySelector('.ai-sidebar-task-header-row');
      var detailBack = null;
      if (row) {
        var bks = row.querySelectorAll('[aria-label="返回"],[aria-label="Back"]');
        for (var j = 0; j < bks.length; j++) { if (isVisible(bks[j])) { detailBack = bks[j]; break; } }
      }
      if (detailBack) {
        capture();
        detailBack.click();
        setTimeout(function () { var c = visClose(); if (c) c.click(); }, 90);
        return true;
      }
      return true;
    }
    var surface = document.querySelector('.settings-dialog-surface');
    if (surface && isVisible(surface)) {
      var backBtn = surface.querySelector('.settings-dialog-mobile-topbar-leading button')
        || surface.querySelector('.settings-dialog-main-close')
        || surface.querySelector('[aria-label="返回"],[aria-label="Back"]');
      if (backBtn && isVisible(backBtn)) { backBtn.click(); return true; }
      try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true })); } catch (e) {}
      return true;
    }
    var dialogs = document.querySelectorAll('[role="dialog"],.rt-DialogContent');
    for (var i = dialogs.length - 1; i >= 0; i--) {
      var dlg = dialogs[i];
      if (!isVisible(dlg)) continue;
      var closeBtn = dlg.querySelector('[aria-label="关闭"],[aria-label="Close"],.rt-DialogClose');
      if (closeBtn && isVisible(closeBtn)) { closeBtn.click(); return true; }
    }
    if (history.length > 1) { try { history.back(); return true; } catch (e) {} }
    return false;
  };

  var ICON = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">'
    + '<circle cx="8" cy="8" r="6.1" stroke="currentColor" stroke-width="1.5"/>'
    + '<path d="M8 5v6M5 8h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
  var CHEVRON = RARROW;

  function fmtSize(n) {
    if (!n || n < 0) return '';
    if (n >= 1048576) return (n / 1048576).toFixed(1) + ' MB';
    if (n >= 1024) return Math.round(n / 1024) + ' KB';
    return n + ' B';
  }

  // 导入覆盖层
  function openImportSheet() {
    var picked = null;
    var ctrl;
    var pickBtnRef = null, confirmBtnRef = null, nameLine = null;
    ctrl = openSheet({
      title: t('导入备份数据', 'Import backup'),
      build: function (b) {
        b.appendChild(el('p', 'ofone-sheet-hint', t(
          '选择备份压缩包（.zip / .tar.gz / .tgz）。导入会覆盖现有作品与设置并重启服务，请谨慎操作。',
          'Choose a backup archive (.zip / .tar.gz / .tgz). Import overwrites existing works and settings and restarts the service.')));
        var pick = nbtn(t('选择文件', 'Choose file'), false, function () { callNative('pickImportFile'); });
        pickBtnRef = pick;
        b.appendChild(pick);
        nameLine = el('div', 'ofone-file', t('未选择文件', 'No file selected'));
        b.appendChild(nameLine);
      },
      footer: [
        { label: t('取消', 'Cancel'), onClick: closeSheet },
        {
          label: t('导入', 'Import'), primary: true, disabled: true,
          ref: function (b) { confirmBtnRef = b; },
          onClick: function () {
            if (!picked) return;
            closeSheet();
            callNative('confirmImportFile');
          }
        }
      ]
    });
    window.__ofoneImportCtrl = {
      onPicked: function (r) {
        if (!r || r.status !== 'ok') return;
        picked = r.name || '';
        if (nameLine) nameLine.textContent = picked || t('已选择', 'Selected');
        if (confirmBtnRef) confirmBtnRef.disabled = false;
        if (pickBtnRef) pickBtnRef.textContent = t('重新选择', 'Choose again');
      }
    };
  }

  // 导出覆盖层
  function openExportSheet() {
    var fmt = 'zip';
    var ctrl = openSheet({
      title: t('导出备份数据', 'Export backup'),
      build: function (b) {
        b.appendChild(el('p', 'ofone-sheet-hint', t(
          '选择导出压缩格式，文件会保存到 下载/OpenFic/。',
          'Choose archive format. The file is saved to Download/OpenFic/.')));
        b.appendChild(nselect([['zip', 'ZIP (.zip)'], ['tar.gz', 'TAR.GZ (.tar.gz)'], ['tgz', 'TGZ (.tgz)']], fmt, function (v) { fmt = v; }));
      },
      footer: [
        { label: t('取消', 'Cancel'), onClick: closeSheet },
        { label: t('导出', 'Export'), primary: true, onClick: function () { closeSheet(); callNative('exportData', fmt); } }
      ]
    });
  }

  function showResultSheet(ok, message) {
    openSheet({
      title: ok ? t('完成', 'Done') : t('操作失败', 'Failed'),
      build: function (b) { b.appendChild(el('p', 'ofone-sheet-msg', message || (ok ? t('已完成', 'Done') : t('操作失败', 'Operation failed')))); },
      footer: [{ label: t('好的', 'Close'), primary: true, onClick: closeSheet }]
    });
  }

  window.OFOneBridge = {
    onOpenficResult: function (r) {
      refreshLang();
      var c = window.__ofoneCheckCtrl;
      window.__ofoneCheckCtrl = null;
      var kind = window.__ofoneCheckKind;
      if (!c) return;
      if (kind !== 'openfic') return;
      if (!r || r.status === 'error') {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', (r && r.message) || t('检查失败', 'Check failed'))); });
        c.setFooter([{ label: t('关闭', 'Close'), primary: true, onClick: closeSheet }]);
        return;
      }
      if (r.updateAvailable) {
        c.setContent(function (b) {
          b.appendChild(el('p', 'ofone-sheet-msg', t('发现 OpenFic 新版本', 'OpenFic update available')));
          var kv1 = el('div', 'ofone-kv'); kv1.appendChild(el('span', 'k', t('当前版本', 'Current'))); kv1.appendChild(el('span', '', r.current || '')); b.appendChild(kv1);
          var kv2 = el('div', 'ofone-kv'); kv2.appendChild(el('span', 'k', t('最新版本', 'Latest'))); kv2.appendChild(el('span', '', r.latest || '')); b.appendChild(kv2);
          if (r.notes) b.appendChild(el('p', 'ofone-sheet-note', r.notes));
        });
        c.setFooter([
          { label: t('返回', 'Back'), onClick: closeSheet },
          { label: t('前往更新', 'Go to update'), primary: true, onClick: function () { closeSheet(); callNative('startOpenficUpdate'); } }
        ]);
      } else {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', f('已是最新版本 %s', 'Already up to date: %s', r.current || r.latest || ''))); });
        c.setFooter([{ label: t('好的', 'OK'), primary: true, onClick: closeSheet }]);
      }
    },
    onAppResult: function (r) {
      refreshLang();
      var c = window.__ofoneCheckCtrl;
      if (!c) {
        // 无活动检查窗（例如下载过程的事件）：交给原生界面处理，避免重复弹窗
        return;
      }
      window.__ofoneCheckCtrl = null;
      if (!r) { c.close(); return; }
      if (r.status === 'unconfigured') {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', r.message || t('尚未配置 OpenFic One 更新源', 'No update source configured'))); });
        c.setFooter([{ label: t('关闭', 'Close'), primary: true, onClick: closeSheet }]);
        return;
      }
      if (r.status === 'error') {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', r.message || t('操作失败', 'Operation failed'))); });
        c.setFooter([{ label: t('关闭', 'Close'), primary: true, onClick: closeSheet }]);
        return;
      }
      if (r.status === 'installing') {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', t('安装包已下载，请在系统安装界面完成安装。', 'Package downloaded. Finish installing in the system installer.'))); });
        c.setFooter([{ label: t('关闭', 'Close'), primary: true, onClick: closeSheet }]);
        return;
      }
      if (r.status === 'cancelled') {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', r.message || t('已取消下载', 'Download cancelled'))); });
        c.setFooter([{ label: t('关闭', 'Close'), primary: true, onClick: closeSheet }]);
        return;
      }
      // ok：检查结果
      if (!r.updateAvailable) {
        c.setContent(function (b) { b.appendChild(el('p', 'ofone-sheet-msg', f('OpenFic One 已是最新版本 %s', 'OpenFic One is up to date: %s', r.current || r.latest || ''))); });
        c.setFooter([{ label: t('好的', 'OK'), primary: true, onClick: closeSheet }]);
        return;
      }
      var list = r.sources || [];
      c.setContent(function (b) {
        b.appendChild(el('p', 'ofone-sheet-msg', t('发现 OpenFic One 新版本', 'OpenFic One update available')));
        var kv1 = el('div', 'ofone-kv'); kv1.appendChild(el('span', 'k', t('当前版本', 'Current'))); kv1.appendChild(el('span', '', r.current || '')); b.appendChild(kv1);
        var kv2 = el('div', 'ofone-kv'); kv2.appendChild(el('span', 'k', t('最新版本', 'Latest'))); kv2.appendChild(el('span', '', r.latest || '')); b.appendChild(kv2);
        if (r.notes) b.appendChild(el('p', 'ofone-sheet-note', r.notes));
        if (r.size) b.appendChild(el('p', 'ofone-sheet-note', t('安装包大小：', 'Package size: ') + fmtSize(r.size)));
        var dl = el('div', 'ofone-dl');
        if (!list.length) {
          dl.appendChild(el('p', 'ofone-sheet-hint', t('更新源未提供安装包地址。', 'The update source provided no package URL.')));
        } else {
          for (var idx = 0; idx < list.length; idx++) {
            (function (ii, src) {
              var label = (src && src.label) ? src.label : t('下载方式 ' + (ii + 1), 'Download option ' + (ii + 1));
              var btn = nbtn(label, ii === 0, function () {
                closeSheet();
                callNative('downloadAppUpdate', ii);
              });
              dl.appendChild(btn);
            })(idx, list[idx]);
          }
        }
        b.appendChild(dl);
      });
      c.setFooter([{ label: t('返回', 'Back'), onClick: closeSheet }]);
    },
    onImportPicked: function (r) {
      refreshLang();
      var c = window.__ofoneImportCtrl;
      if (c && c.onPicked) c.onPicked(r);
    },
    onDataResult: function (r) {
      refreshLang();
      if (!r) return;
      if (r.status === 'cancelled') return;
      if (r.status === 'ok') showResultSheet(true, r.message || t('已完成', 'Done'));
      else showResultSheet(false, r.message || t('操作失败', 'Operation failed'));
    },
    onNotificationPermission: function (r) {
      var p = (r && r.status) ? r.status : 'denied';
      if (window.__ofoneNotifResolve) window.__ofoneNotifResolve(p);
    },
    open: openPage
  };

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
      wrap.style.flex = '1';
      wrap.style.minWidth = '0';
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
      openPage();
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

  function injectNative() {
    var scrolls = document.querySelectorAll('.settings-dialog-content-scroll');
    for (var i = 0; i < scrolls.length; i++) {
      var sc = scrolls[i];
      if (sc.querySelector('[data-ofone-boot]')) continue;
      var switches = sc.querySelectorAll('.rt-SwitchRoot');
      if (switches.length < 5) continue;
      if (!sc.querySelector('button.rt-BaseButton.rt-variant-soft')) continue;
      var sw = switches[0];
      var rowEl = sw.closest ? sw.closest('.rt-Flex') : null;
      var col = rowEl ? rowEl.parentElement : sc.firstElementChild;
      if (!col) continue;
      var row = el('div', 'rt-Flex rt-r-ai-center rt-r-jc-space-between rt-r-gap-4');
      row.setAttribute('data-ofone-boot', '1');
      var left = el('div', 'rt-Flex rt-r-fd-column rt-r-gap-1');
      var title = el('span', 'rt-Text rt-r-size-2 rt-r-weight-medium', t('应用启动管理', 'App launch management'));
      var hint = el('span', 'rt-Text rt-r-size-1', t('若您希望让 OpenFic 长期在后台运行，请在应用启动管理中将 OF-One 设置为手动管理，并允许后台活动。', 'To keep OpenFic running in the background, set OF-One to manual management in App launch management and allow background activity.'));
      hint.style.color = 'var(--gray-9)';
      left.appendChild(title);
      left.appendChild(hint);
      row.appendChild(left);
      row.appendChild(nbtn(t('前往', 'Open'), false, function () { callNative('openBatterySettings'); }));
      col.appendChild(row);
    }
  }

  function tick() {
    installSettingsItem();
    injectNative();
  }

  function boot() {
    style();
    refreshLang();
    tick();
    aiTick();
    hookAgentEntry();
    if (window.MutationObserver) {
      new MutationObserver(function () { tick(); aiTick(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    }
    window.addEventListener('popstate', function () {
      tick();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
