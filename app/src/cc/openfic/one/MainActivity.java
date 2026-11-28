package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 应用入口：装配界面与各流程对象，串起启动流程。 */
public class MainActivity extends Activity {
    private String appVersionCache;

    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;

    FrameLayout root;
    LinearLayout consoleLayer;
    TextView consoleTitle;
    TextView headerLang;
    TextView headerTheme;
    ScrollView consoleScroll;
    TextView consoleView;
    FrameLayout overlay;
    WebView webView;

    File filesDir;
    File rootfs;
    File binDir;
    File shareDir;
    File logFile;

    Runnable pendingStorage;
    File pendingApk;
    File pendingImport;
    JSONObject lastAppUpdate;
    volatile boolean appDownloading;
    volatile boolean appDownloadCancel;
    TextView downloadText;
    Process server;
    PowerManager.WakeLock wakeLock;
    int spaDepth;
    ValueCallback<Uri[]> pendingFileCallback;
    Thread langWatch;

    final ConsoleUi console = new ConsoleUi(this);
    final RuntimeManager runtime = new RuntimeManager(this);
    final UpdateCheck updates = new UpdateCheck(this);
    final ApkInstaller apk = new ApkInstaller(this);
    final StorageFlow storage = new StorageFlow(this);

    String appVersion() {
        if (appVersionCache == null) {
            String v = "";
            try {
                v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Throwable ignored) {
            }
            appVersionCache = (v == null || v.isEmpty()) ? "0.0.0" : v;
        }
        return appVersionCache;
    }

    String clientJs() {
        String kernel = runtime.installedVersion();
        String label = "OpenFic One v" + appVersion()
                + (kernel == null || kernel.isEmpty() ? "" : " · OpenFic v" + kernel);
        return "(function(){"
            + "var LABEL=" + Util.jsString(label) + ";"
            + "var CSS_TEXT=" + Util.jsString(BRAND_FIX_CSS) + ";"
            + "var applyLabel=function(){var els=document.querySelectorAll('[data-slot=app-version]');"
            + "for(var i=0;i<els.length;i++){if(els[i].textContent!==LABEL)els[i].textContent=LABEL;}};"
            + "var maskOk=(function(){try{return !!(CSS&&CSS.supports&&CSS.supports('mask','url(#a)'))}catch(e){return false}})();"
            + "var skip=/sidebar-brand-logo|sidebar-brand-wordmark|auth-brand/;"
            + "var fixOne=function(el){var cs=getComputedStyle(el);"
            + "var mi=cs.webkitMaskImage||cs.maskImage;"
            + "if(!mi||mi==='none')return;"
            + "el.style.setProperty('-webkit-mask','none','important');"
            + "el.style.setProperty('mask','none','important');"
            + "el.style.setProperty('background-color','transparent','important');"
            + "el.style.setProperty('background-image',mi,'important');"
            + "var rep=cs.webkitMaskRepeat||cs.maskRepeat;"
            + "var siz=cs.webkitMaskSize||cs.maskSize;"
            + "var pos=cs.webkitMaskPosition||cs.maskPosition;"
            + "el.style.setProperty('background-repeat',(rep&&rep!=='initial')?rep:'no-repeat','important');"
            + "el.style.setProperty('background-size',(siz&&siz!=='initial')?siz:'contain','important');"
            + "el.style.setProperty('background-position',(pos&&pos!=='initial')?pos:'center','important');"
            + "el.setAttribute('data-ofone-mask','1');};"
            + "var fixMask=function(){if(maskOk)return;"
            + "var els=document.querySelectorAll('[style*=mask],[class*=logo],[class*=Logo],[class*=brand],[class*=Brand],[class*=icon],[class*=Icon]');"
            + "for(var i=0;i<els.length;i++){var el=els[i];"
            + "if(el.getAttribute('data-ofone-mask'))continue;"
            + "if(skip.test(el.className||''))continue;"
            + "fixOne(el);}};"
            + "var push=history.pushState,rep=history.replaceState,depth=0;"
            + "var send=function(){try{if(window.OFOneNative&&OFOneNative.setSpaDepth)OFOneNative.setSpaDepth(depth);}catch(e){}};"
            + "history.pushState=function(){depth++;var r=push.apply(this,arguments);send();return r;};"
            + "history.replaceState=function(){var r=rep.apply(this,arguments);send();return r;};"
            + "window.addEventListener('popstate',function(){if(depth>0)depth--;send();});"
            + "var run=function(){applyLabel();fixMask();};"
            + "var boot=function(){if(!document.getElementById('ofone-brand-fix')){"
            + "var s=document.createElement('style');s.id='ofone-brand-fix';s.textContent=CSS_TEXT;"
            + "(document.head||document.documentElement).appendChild(s);}"
            + "run();if(window.MutationObserver){new MutationObserver(run).observe(document.documentElement,{childList:true,subtree:true,characterData:true});}};"
            + "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',boot);}else{boot();}"
            + "})();";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ofone", MODE_PRIVATE);
        filesDir = getFilesDir();
        rootfs = new File(filesDir, "rootfs");
        binDir = new File(filesDir, "bin");
        shareDir = new File(filesDir, "share");
        logFile = new File(filesDir, "install.log");
        Lang.init(this);
        Theme.init(this);

        console.buildUi();
        if (needsInstall()) consoleLayer.setVisibility(View.GONE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenFicOne:server");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                bootstrap();
            }
        }, "ofone-bootstrap");
        t.start();
    }

    /** 根文件系统需要重解压，或 Python 环境缺失时视为需要初始化。 */
    boolean needsInstall() {
        File stamp = new File(rootfs, ".ok");
        String cur = "";
        try {
            if (stamp.exists()) cur = new String(Util.readAll(stamp), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
        }
        if (!ROOTFS_STAMP.equals(cur)) return true;
        return !new File(rootfs, "opt/venv/.ok").exists();
    }

    void bootstrap() {
        try {
            console.log("OpenFic One " + appVersion());
            String mirror = needsInstall() ? updates.askMirror(false) : null;
            runtime.ensureBinaries();
            runtime.ensureRootfs();
            String installed = runtime.installedVersion();
            if (installed == null) {
                console.logT("未检测到 OpenFic 运行环境，需要首次初始化");
                if (mirror == null) mirror = updates.askMirror(false);
                runtime.installOpenFic(mirror);
            } else {
                console.logT("已检测到 OpenFic %s", installed);
                updates.maybeUpdate(installed);
                updates.maybeUpdateApp();
            }
            runtime.ensureInjection();
            runtime.startServer();
            runtime.waitForServer();
            if (Lang.pending(this)) {
                Lang.sync(this);
            }
            Lang.align(this);
            if (Theme.pending(this)) {
                Theme.sync(this);
            }
            Theme.align(this);
            console.showWebView();
            startLangWatch();
        } catch (Throwable t) {
            Log.e(TAG, "bootstrap failed", t);
            consoleLayer.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.GONE);
            console.logT("启动失败: %s", t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 12; i++) console.log("  at " + st[i]);
        }
    }

    /** 常驻跟随 OpenFic 的界面语言与主题，服务端变化后刷新控制台。 */
    void startLangWatch() {
        if (langWatch != null) return;
        langWatch = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(LANG_WATCH_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    boolean lang = Lang.align(MainActivity.this);
                    boolean theme = Theme.align(MainActivity.this);
                    if (lang) console.applyLang();
                    if (theme) console.applyTheme();
                }
            }
        }, "ofone-lang-watch");
        langWatch.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean lang = Lang.align(MainActivity.this);
                boolean theme = Theme.align(MainActivity.this);
                if (lang) console.applyLang();
                if (theme) console.applyTheme();
            }
        }, "ofone-lang-resume").start();
    }

    /** 系统明暗切换时刷新控制台配色（跟随系统主题生效）。 */
    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        console.applyTheme();
    }


    static final String NOTIFY_CHANNEL = "openfic_one";

    String notificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return "granted";
        boolean ok = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return ok ? "granted" : "default";
    }

    void requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT < 33) {
                bridgeResult("onNotificationPermission", "granted", "", null);
                return;
            }
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                bridgeResult("onNotificationPermission", "granted", "", null);
                return;
            }
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        } catch (Throwable t) {
            bridgeResult("onNotificationPermission", "denied", "", null);
        }
    }

    private void ensureNotifyChannel(android.app.NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        android.app.NotificationChannel ch = new android.app.NotificationChannel(
                NOTIFY_CHANNEL, Txt.t(this, "OpenFic 通知"), android.app.NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);
    }

    void postNotification(String title, String body) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            ensureNotifyChannel(nm);
            android.app.Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new android.app.Notification.Builder(this, NOTIFY_CHANNEL);
            } else {
                b = new android.app.Notification.Builder(this);
            }
            b.setSmallIcon(getApplicationInfo().icon)
                    .setContentTitle(title == null ? "" : title)
                    .setContentText(body == null ? "" : body)
                    .setAutoCancel(true);
            if (body != null) {
                b.setStyle(new android.app.Notification.BigTextStyle().bigText(body));
            }
            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
        } catch (Throwable t) {
            Log.e(TAG, "postNotification failed", t);
        }
    }

    void bridgeResult(String fn, String status, String message, JSONObject extra) {
        final JSONObject o = new JSONObject();
        try {
            o.put("status", status);
            o.put("message", message == null ? "" : message);
            if (extra != null) {
                java.util.Iterator<String> it = extra.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    o.put(k, extra.get(k));
                }
            }
        } catch (Throwable ignored) {
        }
        final String js = "window.OFOneBridge&&window.OFOneBridge." + fn + "(" + o.toString() + ")";
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) webView.evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_NOTIFY) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            bridgeResult("onNotificationPermission", ok ? "granted" : "denied", "", null);
            return;
        }
        if (requestCode != REQ_STORAGE) return;
        Runnable task = pendingStorage;
        pendingStorage = null;
        if (task == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            task.run();
        } else {
            bridgeResult("onDataResult", "error", Txt.t(this, "未授予存储权限，无法写入下载目录"), null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_INSTALL_PERM) {
            File pending = pendingApk;
            pendingApk = null;
            if (pending != null && Build.VERSION.SDK_INT >= 26
                    && getPackageManager().canRequestPackageInstalls()) {
                apk.installApk(pending);
            } else {
                bridgeResult("onAppResult", "error", Txt.t(this, "未授予安装未知应用权限"), null);
            }
            return;
        }
        if (requestCode == REQ_FILE_CHOOSER) {
            ValueCallback<Uri[]> cb = pendingFileCallback;
            pendingFileCallback = null;
            if (cb == null) return;
            Uri[] picked = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    picked = new Uri[clip.getItemCount()];
                    for (int i = 0; i < picked.length; i++) picked[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    picked = new Uri[]{data.getData()};
                }
            }
            cb.onReceiveValue(picked);
            return;
        }
        if (requestCode != REQ_IMPORT_ZIP) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            bridgeResult("onImportPicked", "cancelled", Txt.t(this, "已取消选择"), null);
            return;
        }
        // 仅暂存文件并回传文件名，用户确认后再真正导入。
        storage.stageImport(data.getData());
    }

    /** 统一处理返回：硬件返回键与手势返回都走这里，按键本身始终消费。 */
    private boolean handleBack() {
        if (appDownloading) {
            appDownloadCancel = true;
            apk.updateDownloadText(Txt.t(this, "正在取消..."));
            return true;
        }
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            // 交给注入脚本按「最内层可返回」顺序处理：One 设置子页 > OpenFic 设置详情 > 弹窗 > 历史记录。
            webView.evaluateJavascript(
                "(window.__ofoneBack&&window.__ofoneBack())?true:false",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        if ("true".equals(v)) return;
                        // 前端 SPA 自身的路由历史（clientJs 维护的 spaDepth）。
                        if (spaDepth > 0) {
                            spaDepth--;
                            webView.evaluateJavascript("history.back()", null);
                            return;
                        }
                        if (webView.canGoBack()) {
                            webView.goBack();
                            return;
                        }
                        // 已在首页或安装页：回到后台而不是直接退出，保留本地服务与安装进度。
                        moveTaskToBack(true);
                    }
                });
            return true;
        }
        moveTaskToBack(true);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return handleBack();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // 手势返回（尤其 Android 10+ 全屏手势）走 onBackPressed 而非 onKeyDown，需同样适配。
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (langWatch != null) langWatch.interrupt();
        runtime.stopServer();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}