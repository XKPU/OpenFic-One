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
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
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

/** 注入网页的 OFOneNative 接口。 */
class Bridge {
    private final MainActivity act;

    Bridge(MainActivity act) {
        this.act = act;
    }

    @JavascriptInterface
    public void setSpaDepth(final int depth) {
        act.spaDepth = depth;
    }

    @JavascriptInterface
    public String getInfo() {
            try {
                JSONObject o = new JSONObject();
                o.put("appVersion", act.appVersion());
                String v = act.runtime.installedVersion();
                o.put("openficVersion", v == null ? "" : v);
                o.put("autoUpdate", act.prefs.getBoolean("auto_update", true));
                o.put("neverCheck", act.prefs.getBoolean("never_check", false));
                o.put("snoozeUntil", act.prefs.getLong("snooze_until", 0L));
                o.put("appAutoUpdate", act.prefs.getBoolean("app_auto_update", true));
                o.put("appNeverCheck", act.prefs.getBoolean("app_never_check", false));
                o.put("appSnoozeUntil", act.prefs.getLong("app_snooze_until", 0L));
                o.put("language", Lang.current(act));
                o.put("theme", Theme.current(act));
                o.put("themeDark", Theme.isDark(act));
                return o.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void setTheme(final String theme) {
            if (!Theme.valid(theme)) return;
            Theme.set(act, theme);
            act.console.applyTheme();
            act.console.logT("界面主题已设为: %s", Theme.name(act, theme));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Theme.sync(act);
                    // 写入 OpenFic 后重载页面，让 OpenFic 前端按新主题渲染。
                    act.ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (act.webView != null && act.webView.getVisibility() == View.VISIBLE) {
                                act.webView.reload();
                            }
                        }
                    });
                }
            }, "ofone-theme").start();
        }

        @JavascriptInterface
        public void setLanguage(final String lang) {
            if (lang == null || lang.isEmpty()) return;
            Lang.set(act, lang);
            act.console.applyLang();
            act.console.logT("界面语言已设为: %s", Lang.name(lang));
            new Thread(new Runnable() {
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
            }, "ofone-lang").start();
        }

        @JavascriptInterface
        public void setAutoUpdate(final boolean on) {
            act.prefs.edit().putBoolean("auto_update", on).apply();
            if (on) act.prefs.edit().putBoolean("never_check", false).apply();
            act.console.logT("自动检查 OpenFic 更新: %s", Txt.t(act, on ? "开启" : "停止"));
        }

        @JavascriptInterface
        public void setAppAutoUpdate(final boolean on) {
            act.prefs.edit().putBoolean("app_auto_update", on).apply();
            if (on) act.prefs.edit().putBoolean("app_never_check", false).apply();
            act.console.logT("自动检查 OpenFic One 更新: %s", Txt.t(act, on ? "开启" : "停止"));
        }

        @JavascriptInterface
        public void setNeverCheck(final boolean on) {
            act.prefs.edit().putBoolean("never_check", on).apply();
            act.console.logT("无限期暂停 OpenFic 更新检查: %s", Txt.t(act, on ? "开启" : "关闭"));
        }

        @JavascriptInterface
        public void setAppNeverCheck(final boolean on) {
            act.prefs.edit().putBoolean("app_never_check", on).apply();
            act.console.logT("无限期暂停 OpenFic One 更新检查: %s", Txt.t(act, on ? "开启" : "关闭"));
        }

        @JavascriptInterface
        public void setSnoozeUntil(final long until) {
            act.prefs.edit().putLong("snooze_until", until).apply();
            act.console.logT("OpenFic 更新检查暂停至 %s", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date(until)));
        }

        @JavascriptInterface
        public void setAppSnoozeUntil(final long until) {
            act.prefs.edit().putLong("app_snooze_until", until).apply();
            act.console.logT("OpenFic One 更新检查暂停至 %s", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date(until)));
        }

        @JavascriptInterface
        public String getAppUpdateApi() {
            String v = act.prefs.getString("app_update_api", "");
            return v == null ? "" : v.trim();
        }

        @JavascriptInterface
        public void setAppUpdateApi(final String url) {
            act.prefs.edit().putString("app_update_api", url == null ? "" : url.trim()).apply();
            act.console.logT("OpenFic One 更新源: %s", url == null || url.trim().isEmpty()
                    ? Txt.f(act, "内置源（按顺序尝试 %d 个）", DEFAULT_APP_UPDATE_APIS.length) : url.trim());
        }

        @JavascriptInterface
        public void checkOpenFicUpdate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject info = Net.fetchOpenficInfo();
                    final String latest = info == null ? null : info.optString("version", null);
                    final String notes = info == null ? "" : info.optString("notes", "");
                    final String cur = act.runtime.installedVersion();
                    String payload;
                    try {
                        JSONObject o = new JSONObject();
                        if (latest == null) {
                            o.put("status", "error");
                            o.put("message", Txt.t(act, "无法连接 PyPI，请检查网络"));
                        } else {
                            o.put("status", "ok");
                            o.put("latest", latest);
                            o.put("current", cur == null ? "" : cur);
                            o.put("notes", notes);
                            o.put("updateAvailable", cur != null && Util.compareVersion(latest, cur) > 0);
                        }
                        payload = o.toString();
                    } catch (Throwable t) {
                        return;
                    }
                    final String js = "window.OFOneBridge&&window.OFOneBridge.onOpenficResult(" + payload + ")";
                    act.ui.post(new Runnable() {
                        @Override
                        public void run() {
                            act.webView.evaluateJavascript(js, null);
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
                        act.console.showConsole(Txt.t(act, "正在更新 OpenFic"));
                        String mirror = act.updates.askMirror(true);
                        if (mirror == null) {
                            act.console.logT("已取消更新");
                            act.console.showWebViewReload();
                            return;
                        }
                        if (act.server != null) {
                            act.runtime.stopServer();
                            Thread.sleep(800);
                        }
                        act.runtime.installOpenFic(mirror);
                        act.runtime.ensureInjection();
                        act.runtime.startServer();
                        act.runtime.waitForServer();
                        act.console.showWebViewReload();
                    } catch (Throwable t) {
                        Log.e(TAG, "manual update failed", t);
                        act.console.logT("更新失败: %s", t);
                        try {
                            act.runtime.startServer();
                            act.runtime.waitForServer();
                            act.console.showWebViewReload();
                        } catch (Throwable ignored) {
                            act.console.logT("服务启动失败，请重启应用");
                        }
                    }
                }
            }, "ofone-manual-update").start();
        }

        @JavascriptInterface
        public void checkAppUpdate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject r = act.updates.queryAppUpdate();
                    if (r == null) {
                        act.bridgeResult("onAppResult", "error", Txt.t(act, "无法获取 OpenFic One 版本信息"), null);
                        return;
                    }
                    act.lastAppUpdate = r;
                    act.bridgeResult("onAppResult", "ok", "", r);
                }
            }, "ofone-check-app").start();
        }

        @JavascriptInterface
        public void downloadAppUpdate(final int index) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject info = act.lastAppUpdate;
                    if (info == null) {
                        act.bridgeResult("onAppResult", "error", Txt.t(act, "请先检查更新"), null);
                        return;
                    }
                    JSONArray list = info.optJSONArray("sources");
                    if (list == null || list.length() == 0) {
                        act.bridgeResult("onAppResult", "error", Txt.t(act, "更新源未提供安装包地址"), null);
                        return;
                    }
                    int i = Math.max(0, Math.min(index, list.length() - 1));
                    JSONObject src = list.optJSONObject(i);
                    String url = src == null ? "" : src.optString("url", "");
                    String sha = info.optString("sha256", "");
                    if (sha.isEmpty() && src != null) sha = src.optString("sha256", "");
                    if (url.isEmpty()) {
                        act.bridgeResult("onAppResult", "error", Txt.t(act, "安装包地址为空"), null);
                        return;
                    }
                    String version = info.optString("latest", act.appVersion());
                    File dir = new File(act.filesDir, "updates");
                    dir.mkdirs();
                    File apk = new File(dir, "OpenFicOne-" + version + ".apk");
                    if (act.appDownloading) {
                        act.bridgeResult("onAppResult", "error", Txt.t(act, "已有下载任务正在进行"), null);
                        return;
                    }
                    act.apk.showDownloadDialog(version);
                    try {
                        act.console.logT("下载 OpenFic One %s ...", version);
                        act.apk.downloadFile(url, apk);
                        if (act.appDownloadCancel) {
                            apk.delete();
                            act.console.logT("已取消下载 OpenFic One %s", version);
                            act.apk.closeDownloadDialog();
                            act.bridgeResult("onAppResult", "cancelled", Txt.t(act, "已取消下载"), null);
                            return;
                        }
                        act.apk.updateDownloadText(Txt.t(act, "正在校验安装包..."));
                        if (!sha.isEmpty()) {
                            String got = Util.sha256Of(apk);
                            if (!sha.equalsIgnoreCase(got)) {
                                apk.delete();
                                act.apk.closeDownloadDialog();
                                act.bridgeResult("onAppResult", "error", Txt.t(act, "安装包校验失败，请重试"), null);
                                return;
                            }
                        }
                        act.console.logT("下载完成: %d 字节", apk.length());
                        act.apk.closeDownloadDialog();
                        act.apk.installApk(apk);
                    } catch (Throwable t) {
                        Log.e(TAG, "download apk failed", t);
                        boolean cancel = act.appDownloadCancel;
                        apk.delete();
                        act.apk.closeDownloadDialog();
                        if (cancel) {
                            act.console.logT("已取消下载 OpenFic One %s", version);
                            act.bridgeResult("onAppResult", "cancelled", Txt.t(act, "已取消下载"), null);
                        } else {
                            act.bridgeResult("onAppResult", "error", Txt.f(act, "下载失败: %s", t.getMessage()), null);
                        }
                    }
                }
            }, "ofone-download-app").start();
        }

        @JavascriptInterface
        public void exportData(final String format) {
            act.storage.runStorageTask(new Runnable() {
                @Override
                public void run() {
                    act.storage.doExport(format);
                }
            });
        }

        @JavascriptInterface
        public void pickImportFile() {
            act.storage.runStorageTask(new Runnable() {
                @Override
                public void run() {
                    act.storage.pickImportFile();
                }
            });
        }

        @JavascriptInterface
        public void confirmImportFile() {
            final File f = act.pendingImport;
            act.pendingImport = null;
            if (f == null || !f.exists()) {
                act.bridgeResult("onDataResult", "error", Txt.t(act, "尚未选择备份文件"), null);
                return;
            }
            act.storage.runStorageTask(new Runnable() {
                @Override
                public void run() {
                    act.storage.doImport(f);
                }
            });
        }

        @JavascriptInterface
        public void openLogFolder() {
            act.storage.runStorageTask(new Runnable() {
                @Override
                public void run() {
                    act.storage.doOpenLogFolder();
                }
            });
        }

        @JavascriptInterface
        public String getNotificationPermission() {
            return act.notificationPermission();
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            act.ui.post(new Runnable() {
                @Override
                public void run() {
                    act.requestNotificationPermission();
                }
            });
        }

        @JavascriptInterface
        public void notify(final String title, final String body) {
            act.ui.post(new Runnable() {
                @Override
                public void run() {
                    act.postNotification(title, body);
                }
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            act.ui.post(new Runnable() {
                @Override
                public void run() {
                    act.console.openBatterySettings();
                }
            });
        }
}