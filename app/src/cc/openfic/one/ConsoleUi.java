package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
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
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** 控制台界面、遮罩层与 WebView 切换。 */
class ConsoleUi {
    private final MainActivity act;

    ConsoleUi(MainActivity act) {
        this.act = act;
    }

    void buildUi() {
        act.root = new FrameLayout(act);
        Palette pal = Palette.of(act);

        act.consoleLayer = new LinearLayout(act);
        act.consoleLayer.setOrientation(LinearLayout.VERTICAL);
        act.consoleLayer.setBackgroundColor(pal.bg);

        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(6));

        // 主题与语言入口独占一行，避免标题被压缩换行。
        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        act.consoleTitle = new TextView(act);
        act.consoleTitle.setText(Txt.t(act, "OpenFic One 控制台"));
        act.consoleTitle.setTextColor(pal.text);
        act.consoleTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        act.consoleTitle.setTypeface(Typeface.DEFAULT_BOLD);
        act.consoleTitle.setSingleLine(true);
        act.consoleTitle.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(act.consoleTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        act.headerLang = new TextView(act);
        act.headerLang.setText(langLabel());
        act.headerLang.setTextColor(pal.accent);
        act.headerLang.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        act.headerLang.setPadding(0, dp(6), dp(16), 0);
        act.headerLang.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Lang.toggle(act);
                applyLang();
                logT("界面语言已设为: %s", Lang.name(act));
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Lang.sync(act);
                        act.ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (act.webView != null && act.webView.getVisibility() == View.VISIBLE) {
                                    act.webView.reload();
                                }
                            }
                        });
                    }
                }, "ofone-lang");
                t.start();
            }
        });
        act.headerLang.setSingleLine(true);
        act.headerLang.setEllipsize(TextUtils.TruncateAt.END);
        actions.addView(act.headerLang, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        act.headerTheme = new TextView(act);
        act.headerTheme.setText(themeLabel());
        act.headerTheme.setTextColor(pal.accent);
        act.headerTheme.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        act.headerTheme.setPadding(0, dp(6), 0, 0);
        act.headerTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String next = Theme.next(act);
                applyTheme();
                logT("界面主题已设为: %s", Theme.name(act, next));
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Theme.sync(act);
                        act.ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (act.webView != null && act.webView.getVisibility() == View.VISIBLE) {
                                    act.webView.reload();
                                }
                            }
                        });
                    }
                }, "ofone-theme");
                t.start();
            }
        });
        act.headerTheme.setSingleLine(true);
        act.headerTheme.setEllipsize(TextUtils.TruncateAt.END);
        actions.addView(act.headerTheme, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        act.consoleLayer.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        act.consoleScroll = new ScrollView(act);
        act.consoleView = new TextView(act);
        act.consoleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        act.consoleView.setTypeface(Typeface.MONOSPACE);
        act.consoleView.setTextColor(Palette.of(act).muted);
        act.consoleView.setPadding(dp(14), dp(4), dp(14), dp(28));
        act.consoleScroll.addView(act.consoleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        act.consoleLayer.addView(act.consoleScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        act.root.addView(act.consoleLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        act.webView = new WebView(act);
        act.webView.setVisibility(View.GONE);
        act.root.addView(act.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        act.overlay = new FrameLayout(act);
        act.overlay.setVisibility(View.GONE);
        act.root.addView(act.overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = act.webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        act.webView.addJavascriptInterface(new Bridge(act), "OFOneNative");
        act.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                act.pendingFileCallback = cb;
                Intent i;
                try {
                    i = params.createIntent();
                } catch (Throwable t) {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                }
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    act.startActivityForResult(Intent.createChooser(i, Txt.t(act, "选择文件")), REQ_FILE_CHOOSER);
                } catch (Throwable t) {
                    act.pendingFileCallback = null;
                    return false;
                }
                return true;
            }
        });
        act.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                view.evaluateJavascript(act.clientJs(), null);
            }
        });

        act.setContentView(act.root);
    }

    /** 标题栏语言入口：显示当前语言，点击切换。 */
    private String langLabel() {
        return Txt.t(act, "界面语言") + ": " + Lang.name(act) + " ›";
    }

    /** 标题栏主题入口：显示当前主题，点击切换。 */
    private String themeLabel() {
        return Txt.t(act, "界面主题") + ": " + Theme.name(act) + " ›";
    }

    /** 切换主题后刷新控制台配色。 */
    void applyTheme() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                Palette pal = Palette.of(act);
                if (act.consoleLayer != null) act.consoleLayer.setBackgroundColor(pal.bg);
                if (act.consoleTitle != null) act.consoleTitle.setTextColor(pal.text);
                if (act.headerLang != null) act.headerLang.setTextColor(pal.accent);
                if (act.headerTheme != null) {
                    act.headerTheme.setText(themeLabel());
                    act.headerTheme.setTextColor(pal.accent);
                }
                if (act.consoleView != null) act.consoleView.setTextColor(pal.muted);
            }
        });
    }

    /** 切换语言后刷新界面上的静态文案。 */
    void applyLang() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                if (act.consoleTitle != null) {
                    act.consoleTitle.setText(Txt.t(act, "OpenFic One 控制台"));
                }
                if (act.headerLang != null) {
                    act.headerLang.setText(langLabel());
                }
                if (act.headerTheme != null) {
                    act.headerTheme.setText(themeLabel());
                }
                renderConsole();
            }
        });
    }

    void openBatterySettings() {
        Intent[] candidates = new Intent[]{
                componentIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                new Intent("hihonor.intent.action.HSM_STARTUPAPP_MANAGER"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                new Intent("huawei.intent.action.HSM_STARTUPAPP_MANAGER"),
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + act.getPackageName())),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + act.getPackageName())),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent it : candidates) {
            try {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(it);
                return;
            } catch (Throwable ignored) {
            }
        }
        log(Txt.t(act, "无法打开系统设置，请手动进入 设置 → 应用 → OpenFic One"));
    }

    private static Intent componentIntent(String pkg, String cls) {
        Intent it = new Intent();
        it.setClassName(pkg, cls);
        return it;
    }

    void dismissOverlay() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.setOnKeyListener(null);
                act.overlay.removeAllViews();
                act.overlay.setVisibility(View.GONE);
                act.consoleLayer.setVisibility(View.VISIBLE);
            }
        });
    }

    void showConsole(final String title) {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.removeAllViews();
                act.overlay.setVisibility(View.GONE);
                act.webView.setVisibility(View.GONE);
                act.consoleLayer.setVisibility(View.VISIBLE);
                if (title != null) log("\n== " + title + " ==");
                act.consoleScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        act.consoleScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    void showWebViewReload() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.removeAllViews();
                act.overlay.setVisibility(View.GONE);
                act.consoleLayer.setVisibility(View.GONE);
                act.webView.setVisibility(View.VISIBLE);
                act.webView.loadUrl("http://127.0.0.1:" + PORT + "/");
            }
        });
    }

    void showWebView() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.removeAllViews();
                act.overlay.setVisibility(View.GONE);
                act.consoleLayer.setVisibility(View.GONE);
                act.webView.setVisibility(View.VISIBLE);
                act.webView.loadUrl("http://127.0.0.1:" + PORT + "/");
            }
        });
    }

    void toast(final String msg) {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 控制台一行：保留原文键与参数，切换语言时整体重绘。 */
    private static final class Line {
        final String zh;
        final Object[] args;
        final boolean nl;

        Line(String zh, Object[] args, boolean nl) {
            this.zh = zh;
            this.args = args;
            this.nl = nl;
        }

        String render(MainActivity act) {
            return zh == null ? (String) args[0] : Txt.f(act, zh, args);
        }
    }

    private final List<Line> lines = new ArrayList<Line>();

    void log(final String line) {
        push(new Line(null, new Object[]{line}, true));
    }

    /** 原样追加，不换行。 */
    void raw(final String text) {
        push(new Line(null, new Object[]{text}, false));
    }

    void logT(final String zh, final Object... args) {
        push(new Line(zh, args, true));
    }

    private void push(final Line ln) {
        Log.i(TAG, ln.render(act));
        synchronized (lines) {
            lines.add(ln);
        }
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.consoleView.append(ln.render(act) + (ln.nl ? "\n" : ""));
                act.consoleScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        act.consoleScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    /** 按当前语言重绘整段控制台输出。 */
    private void renderConsole() {
        StringBuilder sb = new StringBuilder();
        synchronized (lines) {
            for (Line ln : lines) {
                sb.append(ln.render(act));
                if (ln.nl) sb.append('\n');
            }
        }
        act.consoleView.setText(sb.toString());
        act.consoleScroll.post(new Runnable() {
            @Override
            public void run() {
                act.consoleScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}
