package cc.oenfic.one;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final String TAG = "OpenFicOne";
    private static final String APP_VERSION = "1.0.0";
    private static final String ROOTFS_STAMP = "ofone-rootfs-1";
    private static final int PORT = 8000;
    private static final String DEFAULT_MIRROR = "https://mirrors.aliyun.com/pypi/simple/";
    private static final String PYPI_JSON = "https://pypi.org/pypi/openfic/json";
    private static final String DEFAULT_APP_UPDATE_API = "";
    private static final String[][] MIRRORS = {
            {"阿里云（推荐）", "https://mirrors.aliyun.com/pypi/simple/"},
            {"清华大学", "https://pypi.tuna.tsinghua.edu.cn/simple"},
            {"中科大", "https://pypi.mirrors.ustc.edu.cn/simple/"},
            {"腾讯云", "https://mirrors.cloud.tencent.com/pypi/simple"},
            {"华为云", "https://repo.huaweicloud.com/repository/pypi/simple"},
            {"官方 PyPI", "https://pypi.org/simple/"},
    };

    private static final int UPDATE_ACCEPT = 0;
    private static final int UPDATE_SKIP_VERSION = 1;
    private static final int UPDATE_SNOOZE = 2;
    private static final int UPDATE_NEVER = 3;

    private static final String BRAND_FIX_CSS =
            ".sidebar-brand-logo{-webkit-mask:none!important;mask:none!important;"
            + "background-color:transparent!important;background-image:url(/openfic.svg)!important;"
            + "background-repeat:no-repeat!important;background-position:50% 50%!important;"
            + "background-size:contain!important}"
            + ".sidebar-brand-wordmark{-webkit-mask:none!important;mask:none!important;"
            + "background-color:transparent!important;background-image:url(/brand.svg)!important;"
            + "background-repeat:no-repeat!important;background-position:-28.12px 0!important;"
            + "background-size:108.4px 24px!important}"
            + ".auth-brand{-webkit-mask:none!important;mask:none!important;"
            + "background-color:transparent!important;background-image:url(/brand.svg)!important;"
            + "background-repeat:no-repeat!important;background-position:50% 50%!important;"
            + "background-size:contain!important}";

    private static final String CLIENT_JS =
            "(function(){"
            + "var LABEL='OF-One v" + APP_VERSION + "';"
            + "var CSS_TEXT=" + jsString(BRAND_FIX_CSS) + ";"
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
            + "var run=function(){applyLabel();fixMask();};"
            + "var boot=function(){if(!document.getElementById('ofone-brand-fix')){"
            + "var s=document.createElement('style');s.id='ofone-brand-fix';s.textContent=CSS_TEXT;"
            + "(document.head||document.documentElement).appendChild(s);}"
            + "run();if(window.MutationObserver){new MutationObserver(run).observe(document.documentElement,{childList:true,subtree:true,characterData:true});}};"
            + "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',boot);}else{boot();}"
            + "})();";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private FrameLayout root;
    private LinearLayout consoleLayer;
    private ScrollView consoleScroll;
    private TextView consoleView;
    private FrameLayout overlay;
    private WebView webView;

    private File filesDir;
    private File rootfs;
    private File binDir;
    private File shareDir;
    private File logFile;

    private Process server;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ofone", MODE_PRIVATE);
        filesDir = getFilesDir();
        rootfs = new File(filesDir, "rootfs");
        binDir = new File(filesDir, "bin");
        shareDir = new File(filesDir, "share");
        logFile = new File(filesDir, "install.log");

        buildUi();

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

    private void buildUi() {
        root = new FrameLayout(this);

        consoleLayer = new LinearLayout(this);
        consoleLayer.setOrientation(LinearLayout.VERTICAL);
        consoleLayer.setBackgroundColor(0xFF101014);

        TextView title = new TextView(this);
        title.setText("OF-One 控制台");
        title.setTextColor(0xFFE8E8EC);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), dp(14), dp(16), dp(6));
        consoleLayer.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        consoleScroll = new ScrollView(this);
        consoleView = new TextView(this);
        consoleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        consoleView.setTypeface(Typeface.MONOSPACE);
        consoleView.setTextColor(0xFFCFCFD6);
        consoleView.setPadding(dp(14), dp(4), dp(14), dp(28));
        consoleScroll.addView(consoleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        consoleLayer.addView(consoleScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(consoleLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.addJavascriptInterface(new Bridge(), "OFOneNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(CLIENT_JS, null);
            }
        });

        setContentView(root);
    }

    private void bootstrap() {
        try {
            log("OF-One " + APP_VERSION);
            ensureBinaries();
            ensureRootfs();
            String installed = installedVersion();
            if (installed == null) {
                log("未检测到 OpenFic 运行环境，需要首次初始化");
                String mirror = askMirror(false);
                installOpenFic(mirror);
            } else {
                log("已检测到 OpenFic " + installed);
                maybeUpdate(installed);
            }
            ensureInjection();
            startServer();
            waitForServer();
            showWebView();
        } catch (Throwable t) {
            Log.e(TAG, "bootstrap failed", t);
            log("启动失败: " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 12; i++) log("  at " + st[i]);
        }
    }

    private void maybeUpdate(String installed) throws IOException, InterruptedException {
        if (prefs.getBoolean("never_check", false)) {
            log("已设置为永不检查更新，跳过检查");
            return;
        }
        if (!prefs.getBoolean("auto_update", true)) {
            log("自动检查更新已关闭，跳过检查");
            return;
        }
        long snooze = prefs.getLong("snooze_until", 0L);
        if (snooze > System.currentTimeMillis()) {
            log("更新检查已暂停，跳过检查");
            return;
        }
        log("检查 OpenFic 更新...");
        String latest = fetchLatestOpenfic();
        if (latest == null) {
            log("更新检查失败（网络不可用），继续启动");
            return;
        }
        log("最新版本: " + latest);
        if (compareVersion(latest, installed) <= 0) {
            log("已是最新版本");
            return;
        }
        String skipped = prefs.getString("skip_version", "");
        if (latest.equals(skipped)) {
            log("已跳过版本 " + latest);
            return;
        }
        int choice = askUpdate(installed, latest);
        if (choice == UPDATE_ACCEPT) {
            String mirror = askMirror(true);
            if (mirror == null) {
                log("已取消更新");
            } else {
                installOpenFic(mirror);
                ensureInjection();
            }
        } else if (choice == UPDATE_SKIP_VERSION) {
            prefs.edit().putString("skip_version", latest).apply();
            log("已跳过版本 " + latest);
        } else if (choice == UPDATE_NEVER) {
            prefs.edit().putBoolean("never_check", true).apply();
            log("已关闭更新检查");
        } else if (choice == UPDATE_SNOOZE) {
            long until = prefs.getLong("snooze_until", 0L);
            long days = Math.max(1L, (until - System.currentTimeMillis()) / 86400000L);
            log("已暂停更新检查 " + days + " 天");
        } else {
            log("本次不更新");
        }
    }

    private String askMirror(final boolean forUpdate) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> chosen = new AtomicReference<String>(null);
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.addView(buildMirrorPanel(latch, chosen, forUpdate), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                overlay.setVisibility(View.VISIBLE);
            }
        });
        latch.await();
        String picked = chosen.get();
        if (picked == null) return forUpdate ? null : DEFAULT_MIRROR;
        return picked;
    }

    private View buildMirrorPanel(final CountDownLatch latch, final AtomicReference<String> chosen,
                                  final boolean forUpdate) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF101014);
        box.setPadding(dp(24), dp(36), dp(24), dp(28));

        TextView title = new TextView(this);
        title.setText("选择 PyPI 镜像");
        title.setTextColor(0xFFF2F2F5);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView note = new TextView(this);
        note.setText("建议在系统设置中允许「OF-One」后台运行并忽略电池优化，避免切出应用后进程被回收、再次进入时重新执行初始化检查。");
        note.setTextColor(0xFF9A9AA5);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setPadding(0, dp(10), 0, dp(6));
        box.addView(note);

        TextView link = new TextView(this);
        link.setText("前往系统设置 ›");
        link.setTextColor(0xFF6FA8FF);
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        link.setPadding(0, 0, 0, dp(18));
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });
        box.addView(link);

        for (int i = 0; i < MIRRORS.length; i++) {
            final String name = MIRRORS[i][0];
            final String url = MIRRORS[i][1];
            Button b = new Button(this);
            b.setText(name);
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putString("mirror", url).apply();
                    try {
                        writeText(new File(filesDir, "mirror.txt"), url);
                    } catch (IOException ignored) {
                    }
                    chosen.set(url);
                    dismissOverlay();
                    log("已选择镜像: " + name);
                    latch.countDown();
                }
            });
            box.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (forUpdate) {
            Button cancel = new Button(this);
            cancel.setText("取消更新，直接进入应用");
            cancel.setAllCaps(false);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen.set(null);
                    dismissOverlay();
                    latch.countDown();
                }
            });
            box.addView(cancel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFF101014);
        sc.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private int askUpdate(final String installed, final String latest) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Integer> result = new AtomicReference<Integer>(UPDATE_SKIP_VERSION);
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.addView(buildUpdatePanel(latch, result, installed, latest), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                overlay.setVisibility(View.VISIBLE);
            }
        });
        latch.await();
        return result.get();
    }

    private View buildUpdatePanel(final CountDownLatch latch, final AtomicReference<Integer> result,
                                  String installed, String latest) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF101014);
        box.setPadding(dp(24), dp(36), dp(24), dp(28));

        TextView title = new TextView(this);
        title.setText("发现 OpenFic 新版本");
        title.setTextColor(0xFFF2F2F5);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView body = new TextView(this);
        body.setText("当前版本 " + installed + "，最新版本 " + latest + "。\n更新只重建 Python 环境，不会删除你的作品与设置。");
        body.setTextColor(0xFF9A9AA5);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setPadding(0, dp(10), 0, dp(18));
        box.addView(body);

        Button update = new Button(this);
        update.setText("立即更新");
        update.setAllCaps(false);
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_ACCEPT);
                dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(update, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button skip = new Button(this);
        skip.setText("跳过此版本（" + latest + "）");
        skip.setAllCaps(false);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_SKIP_VERSION);
                dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(skip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout snoozeRow = new LinearLayout(this);
        snoozeRow.setOrientation(LinearLayout.HORIZONTAL);
        snoozeRow.setGravity(Gravity.CENTER_VERTICAL);
        snoozeRow.setPadding(0, dp(10), 0, dp(10));

        TextView snoozeHint = new TextView(this);
        snoozeHint.setText("在");
        snoozeHint.setTextColor(0xFF9A9AA5);
        snoozeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        snoozeRow.addView(snoozeHint);

        final EditText days = new EditText(this);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        days.setText("7");
        days.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        days.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(6), 0, dp(6), 0);
        snoozeRow.addView(days, dlp);

        TextView tail = new TextView(this);
        tail.setText("日内不再询问");
        tail.setTextColor(0xFF9A9AA5);
        tail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        snoozeRow.addView(tail);

        Button snooze = new Button(this);
        snooze.setText("确定");
        snooze.setAllCaps(false);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int d = 7;
                try {
                    d = Integer.parseInt(days.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                if (d < 1) d = 1;
                prefs.edit().putLong("snooze_until", System.currentTimeMillis() + d * 86400000L).apply();
                result.set(UPDATE_SNOOZE);
                dismissOverlay();
                log("已设置 " + d + " 天内不再检查更新");
                latch.countDown();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(8), 0, 0, 0);
        snoozeRow.addView(snooze, slp);
        box.addView(snoozeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button never = new Button(this);
        never.setText("永不更新（关闭更新检查）");
        never.setAllCaps(false);
        never.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_NEVER);
                dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(never, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFF101014);
        sc.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private void openBatterySettings() {
        Intent[] candidates = new Intent[]{
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent it : candidates) {
            try {
                startActivity(it);
                return;
            } catch (Throwable ignored) {
            }
        }
        log("无法打开系统设置，请手动进入 设置 → 应用 → OF-One");
    }

    private void dismissOverlay() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.setVisibility(View.GONE);
                consoleLayer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showConsole(final String title) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                consoleLayer.setVisibility(View.VISIBLE);
                if (title != null) consoleView.append("\n== " + title + " ==\n");
                consoleScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void showWebViewReload() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.setVisibility(View.GONE);
                consoleLayer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl("http://127.0.0.1:" + PORT + "/");
            }
        });
    }

    private void showWebView() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                overlay.removeAllViews();
                overlay.setVisibility(View.GONE);
                consoleLayer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl("http://127.0.0.1:" + PORT + "/");
            }
        });
    }

    private void ensureBinaries() throws IOException {
        File stamp = new File(binDir, ".ok");
        if (!stamp.exists()) {
            log("解压运行时组件...");
            binDir.mkdirs();
            copyAsset("bin/proot", new File(binDir, "proot"), 0755);
            copyAsset("bin/libtalloc.so.2", new File(binDir, "libtalloc.so.2"), 0755);
            copyAsset("bin/libandroid-shmem.so", new File(binDir, "libandroid-shmem.so"), 0755);
            File libexec = new File(binDir, "libexec");
            libexec.mkdirs();
            copyAsset("bin/libexec/loader", new File(libexec, "loader"), 0755);
            copyAsset("bin/libexec/loader32", new File(libexec, "loader32"), 0755);
            new File(binDir, "tmp").mkdirs();
            stamp.createNewFile();
        }
        shareDir.mkdirs();
        copyAsset("share/ofone_patch.py", new File(shareDir, "ofone_patch.py"), 0644);
        copyAsset("share/ofone-bridge.js", new File(shareDir, "ofone-bridge.js"), 0644);
    }

    private void ensureRootfs() throws IOException {
        File stamp = new File(rootfs, ".ok");
        if (stamp.exists()) {
            String cur = new String(readAll(stamp), StandardCharsets.UTF_8).trim();
            if (cur.isEmpty()) {
                writeText(stamp, ROOTFS_STAMP);
                return;
            }
            if (ROOTFS_STAMP.equals(cur)) return;
        }
        File dataDir = new File(rootfs, "root/.openfic");
        File backup = new File(shareDir, "_backup_openfic");
        boolean hasData = dataDir.exists();
        if (hasData) {
            log("备份用户数据...");
            if (backup.exists()) deleteTree(backup);
            copyTree(dataDir, backup);
        }
        log("解压 Linux 根文件系统 (约 45MB，首次较慢)...");
        if (rootfs.exists()) deleteTree(rootfs);
        rootfs.mkdirs();
        final long[] acc = new long[]{0};
        TarExtractor.extractAsset(this, "rootfs.tar", rootfs, new TarExtractor.Progress() {
            @Override
            public void onBytes(long bytes) {
                acc[0] += bytes;
                final long mb = acc[0] / (1024 * 1024);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mb % 8 == 0) {
                            consoleView.append(".");
                            consoleScroll.post(new Runnable() {
                                @Override
                                public void run() {
                                    consoleScroll.fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    }
                });
            }
        });
        if (hasData) {
            log("恢复用户数据...");
            File dest = new File(rootfs, "root/.openfic");
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            copyTree(backup, dest);
            deleteTree(backup);
        }
        writeText(stamp, ROOTFS_STAMP);
        log("根文件系统就绪");
    }

    private void ensureInjection() {
        File marker = new File(shareDir, "inject.ok");
        String cur = installedVersion();
        long bridgeSize = new File(shareDir, "ofone-bridge.js").length();
        String token = (cur == null ? APP_VERSION : cur) + "-" + bridgeSize;
        if (marker.exists()) {
            try {
                if (token.equals(new String(readAll(marker), StandardCharsets.UTF_8).trim())) return;
            } catch (IOException ignored) {
            }
        }
        try {
            log("注入 One 设置入口...");
            ProcResult r = runProotCmd("python3 /mnt/share/ofone_patch.py /mnt/share/ofone-bridge.js");
            if (r.code == 0) {
                writeText(marker, token);
            } else {
                log("注入未完成（不影响使用）");
            }
        } catch (IOException e) {
            log("注入失败: " + e.getMessage());
        }
    }

    private String installedVersion() {
        File stamp = new File(rootfs, "opt/venv/.ok");
        if (stamp.exists()) {
            try {
                String s = new String(readAll(stamp), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return s;
            } catch (IOException ignored) {
            }
        }
        File lib = new File(rootfs, "opt/venv/lib");
        File[] pys = lib.listFiles();
        if (pys == null) return null;
        for (File py : pys) {
            File sp = new File(py, "site-packages");
            File[] pkgs = sp.listFiles();
            if (pkgs == null) continue;
            for (File pkg : pkgs) {
                String n = pkg.getName();
                if (n.startsWith("openfic-") && n.endsWith(".dist-info")) {
                    return n.substring("openfic-".length(), n.length() - ".dist-info".length());
                }
            }
        }
        return null;
    }

    private void installOpenFic(String mirrorUrl) throws IOException {
        log("开始安装 OpenFic（首次约 5-15 分钟）");
        log("镜像源: " + mirrorUrl);
        String script = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "
                + "TMPDIR=/tmp HOME=/root PIP_CACHE_DIR=/root/.cache/pip PIP_DISABLE_PIP_VERSION_CHECK=1; "
                + "set -e; "
                + "echo '[1/5] 重建 Python 环境（保留用户数据）'; "
                + "rm -rf /opt/venv /opt/wheels; "
                + "python3 -m venv --without-pip /opt/venv; "
                + "VP=/opt/venv/bin/python3; "
                + "$VP -m ensurepip --upgrade >/dev/null 2>&1 || true; "
                + "if ! $VP -c 'import pip' 2>/dev/null; then "
                + "if ls /usr/share/python-wheels/pip-*.whl >/dev/null 2>&1; then "
                + "W=$(ls /usr/share/python-wheels/pip-*.whl | head -1); "
                + "$VP -m zipfile -e $W /opt/venv/lib/python3.12/site-packages || true; "
                + "fi; fi; "
                + "echo '[2/5] 下载 OpenFic 及依赖（约 60MB）'; "
                + "mkdir -p /opt/wheels; "
                + "$VP -m pip download --cache-dir /root/.cache/pip --no-input -d /opt/wheels -i " + mirrorUrl + " openfic; "
                + "echo '[3/5] 安装依赖（约 2-5 分钟）'; "
                + "$VP -m pip install --no-input --no-index --find-links /opt/wheels openfic; "
                + "rm -rf /opt/wheels; "
                + "echo '[4/5] 注入 One 设置入口'; "
                + "python3 /mnt/share/ofone_patch.py /mnt/share/ofone-bridge.js || echo '注入失败（不影响使用）'; "
                + "echo '[5/5] 记录版本'; "
                + "VER=$($VP -m pip show openfic | awk '/^Version:/{print $2}'); "
                + "echo OFONE_VERSION=$VER; "
                + "echo -n $VER > /opt/venv/.ok; "
                + "echo INSTALL_DONE";
        ProcResult r = runProotCmd(script);
        if (r.code != 0) {
            throw new IOException("依赖安装失败 (exit " + r.code + ")，详见 " + logFile.getAbsolutePath());
        }
        String ver = marker(r.output, "OFONE_VERSION=");
        log("OpenFic 安装完成" + (ver != null ? ": " + ver : ""));
        File stamp = new File(rootfs, "opt/venv/.ok");
        if (ver != null) writeText(stamp, ver);
    }

    private static String marker(String text, String key) {
        if (text == null) return null;
        int i = text.lastIndexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') break;
            end++;
        }
        String v = text.substring(start, end).trim();
        return v.isEmpty() ? null : v;
    }

    private static int compareVersion(String a, String b) {
        String[] pa = a.split("[^0-9]");
        String[] pb = b.split("[^0-9]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length && !pa[i].isEmpty() ? Integer.parseInt(pa[i]) : 0;
            int y = i < pb.length && !pb[i].isEmpty() ? Integer.parseInt(pb[i]) : 0;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }

    private String fetchJsonVersion(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject o = new JSONObject(sb.toString());
            String v = o.optString("version", o.optString("tag_name", ""));
            if (v.startsWith("v")) v = v.substring(1);
            return v.isEmpty() ? null : v;
        } catch (Throwable e) {
            Log.w(TAG, "fetch app version failed: " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String fetchLatestOpenfic() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(PYPI_JSON).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject o = new JSONObject(sb.toString());
            String v = o.getJSONObject("info").optString("version", "");
            return v.isEmpty() ? null : v;
        } catch (Throwable e) {
            Log.w(TAG, "fetch latest failed: " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static final class ProcResult {
        int code;
        String output;
    }

    private ProcResult runProotCmd(String script) throws IOException {
        Process p = prootBuilder(script).start();
        final ProcResult res = new ProcResult();
        final StringBuilder sb = new StringBuilder();
        final OutputStream fo = new FileOutputStream(logFile, true);
        final InputStream in = p.getInputStream();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader r = null;
                try {
                    r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        fo.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                        fo.flush();
                        sb.append(line).append('\n');
                        final String l = line;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                consoleView.append(l + "\n");
                                consoleScroll.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                                    }
                                });
                            }
                        });
                    }
                } catch (IOException ignored) {
                } finally {
                    try {
                        if (r != null) r.close();
                        fo.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "pump-install");
        t.setDaemon(true);
        t.start();
        try {
            res.code = p.waitFor();
            t.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            res.code = -1;
        }
        res.output = sb.toString();
        return res;
    }

    private void startServer() throws IOException {
        if (server == null && probe()) {
            log("检测到已在运行的服务，直接复用");
            return;
        }
        String script = "export PATH=/opt/venv/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TMPDIR=/tmp HOME=/root; "
                + "exec openfic serve --host 127.0.0.1 --port " + PORT;
        server = prootBuilder(script).start();
        pumpToLog(server.getInputStream(), "server");
        log("服务进程已启动");
    }

    private void waitForServer() throws InterruptedException {
        log("等待服务就绪...");
        for (int i = 0; i < 180; i++) {
            if (probe()) {
                log("服务已就绪 http://127.0.0.1:" + PORT);
                return;
            }
            if (server != null && !server.isAlive()) {
                throw new IllegalStateException("服务进程退出 code=" + server.exitValue());
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("服务启动超时");
    }

    private boolean probe() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/").openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(2500);
            int code = c.getResponseCode();
            return code > 0 && code < 500;
        } catch (IOException e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private ProcessBuilder prootBuilder(String script) {
        ProcessBuilder pb = new ProcessBuilder(
                new File(binDir, "proot").getAbsolutePath(),
                "-r", rootfs.getAbsolutePath(),
                "-0",
                "-w", "/",
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", shareDir.getAbsolutePath() + ":/mnt/share",
                "/usr/bin/env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/bash", "-c", script);
        pb.directory(binDir);
        pb.redirectErrorStream(true);
        pb.environment().put("PROOT_TMP_DIR", new File(binDir, "tmp").getAbsolutePath());
        pb.environment().put("PROOT_LOADER", new File(binDir, "libexec/loader").getAbsolutePath());
        pb.environment().put("PROOT_NO_SECCOMP", "1");
        pb.environment().put("LD_LIBRARY_PATH", binDir.getAbsolutePath());
        pb.environment().put("HOME", "/root");
        pb.environment().put("TMPDIR", "/tmp");
        return pb;
    }

    private void pumpToLog(final InputStream in, final String tag) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        Log.i(TAG, tag + ": " + line);
                        final String l = line;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                consoleView.append(l + "\n");
                                consoleScroll.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                                    }
                                });
                            }
                        });
                    }
                } catch (IOException ignored) {
                }
            }
        }, "pump-" + tag);
        t.setDaemon(true);
        t.start();
    }

    private final class Bridge {
        @JavascriptInterface
        public String getInfo() {
            try {
                JSONObject o = new JSONObject();
                o.put("appVersion", APP_VERSION);
                String v = installedVersion();
                o.put("openficVersion", v == null ? "" : v);
                o.put("autoUpdate", prefs.getBoolean("auto_update", true));
                o.put("neverCheck", prefs.getBoolean("never_check", false));
                o.put("snoozeUntil", prefs.getLong("snooze_until", 0L));
                return o.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void setAutoUpdate(final boolean on) {
            prefs.edit().putBoolean("auto_update", on).apply();
            if (on) prefs.edit().putBoolean("never_check", false).apply();
            log("自动检查 OpenFic 更新: " + (on ? "开启" : "停止"));
        }

        @JavascriptInterface
        public String getAppUpdateApi() {
            return prefs.getString("app_update_api", DEFAULT_APP_UPDATE_API);
        }

        @JavascriptInterface
        public void setAppUpdateApi(final String url) {
            prefs.edit().putString("app_update_api", url == null ? "" : url.trim()).apply();
            log("OF-One 更新源: " + (url == null || url.trim().isEmpty() ? "未配置" : url.trim()));
        }

        @JavascriptInterface
        public void checkOpenFicUpdate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String latest = fetchLatestOpenfic();
                    final String cur = installedVersion();
                    String payload;
                    try {
                        JSONObject o = new JSONObject();
                        if (latest == null) {
                            o.put("status", "error");
                            o.put("message", "无法连接 PyPI，请检查网络");
                        } else {
                            o.put("status", "ok");
                            o.put("latest", latest);
                            o.put("current", cur == null ? "" : cur);
                            o.put("updateAvailable", cur != null && compareVersion(latest, cur) > 0);
                        }
                        payload = o.toString();
                    } catch (Throwable t) {
                        return;
                    }
                    final String js = "window.OFOneBridge&&window.OFOneBridge.onOpenficResult(" + payload + ")";
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(js, null);
                        }
                    });
                }
            }, "ofone-check-openfic").start();
        }

        @JavascriptInterface
        public void startOpenficUpdate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        showConsole("正在更新 OpenFic");
                        String mirror = askMirror(true);
                        if (mirror == null) {
                            log("已取消更新");
                            showWebViewReload();
                            return;
                        }
                        if (server != null) {
                            server.destroy();
                            server = null;
                            Thread.sleep(800);
                        }
                        installOpenFic(mirror);
                        ensureInjection();
                        startServer();
                        waitForServer();
                        showWebViewReload();
                    } catch (Throwable t) {
                        Log.e(TAG, "manual update failed", t);
                        log("更新失败: " + t);
                        try {
                            startServer();
                            waitForServer();
                            showWebViewReload();
                        } catch (Throwable ignored) {
                            log("服务启动失败，请重启应用");
                        }
                    }
                }
            }, "ofone-manual-update").start();
        }

        @JavascriptInterface
        public void checkAppUpdate() {
            String payload;
            try {
                JSONObject o = new JSONObject();
                String api = prefs.getString("app_update_api", DEFAULT_APP_UPDATE_API);
                if (api == null || api.isEmpty()) {
                    o.put("status", "unconfigured");
                    o.put("message", "尚未配置 OF-One 更新源");
                } else {
                    String latest = fetchJsonVersion(api);
                    if (latest == null) {
                        o.put("status", "error");
                        o.put("message", "无法获取 OF-One 版本信息");
                    } else {
                        o.put("status", "ok");
                        o.put("latest", latest);
                        o.put("current", APP_VERSION);
                        o.put("updateAvailable", compareVersion(latest, APP_VERSION) > 0);
                    }
                }
                payload = o.toString();
            } catch (Throwable t) {
                return;
            }
            final String js = "window.OFOneBridge&&window.OFOneBridge.onAppResult(" + payload + ")";
            ui.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }

    private void log(final String line) {
        Log.i(TAG, line);
        ui.post(new Runnable() {
            @Override
            public void run() {
                consoleView.append(line + "\n");
                consoleScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private static String jsString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void copyAsset(String name, File dest, int mode) throws IOException {
        InputStream in = getAssets().open(name);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        try {
            android.system.Os.chmod(dest.getAbsolutePath(), mode);
        } catch (Exception ignored) {
        }
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) break;
                off += n;
            }
            return b;
        } finally {
            in.close();
        }
    }

    private static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("mkdir " + dst);
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) copyTree(k, new File(dst, k.getName()));
            }
            return;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null
                && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
