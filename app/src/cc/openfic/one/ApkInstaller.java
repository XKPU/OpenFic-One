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

/** 安装包下载、校验与安装。 */
class ApkInstaller {
    private final MainActivity act;

    ApkInstaller(MainActivity act) {
        this.act = act;
    }

    void showDownloadDialog(final String version) {
        act.appDownloading = true;
        act.appDownloadCancel = false;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Boolean> cancelled = new AtomicReference<Boolean>(false);
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                Palette pal = Palette.of(act);
                LinearLayout box = new LinearLayout(act);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackgroundColor(pal.bg);
                box.setPadding(act.console.dp(24), act.console.dp(36), act.console.dp(24), act.console.dp(28));

                TextView head = new TextView(act);
                head.setText(Txt.f(act, "正在下载 OpenFic One %s", version));
                head.setTextColor(pal.text);
                head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                head.setTypeface(Typeface.DEFAULT_BOLD);
                box.addView(head);

                act.downloadText = new TextView(act);
                act.downloadText.setText(Txt.t(act, "准备中..."));
                act.downloadText.setTextColor(pal.muted);
                act.downloadText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                act.downloadText.setPadding(0, act.console.dp(10), 0, act.console.dp(18));
                box.addView(act.downloadText);

                Button cancel = new Button(act);
                cancel.setText(Txt.t(act, "取消下载"));
                cancel.setAllCaps(false);
                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cancelled.set(true);
                        act.appDownloadCancel = true;
                        if (act.downloadText != null) act.downloadText.setText(Txt.t(act, "正在取消..."));
                    }
                });
                box.addView(cancel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                ScrollView sc = new ScrollView(act);
                sc.setBackgroundColor(pal.bg);
                sc.addView(box, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                act.overlay.removeAllViews();
                act.overlay.addView(sc, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                act.overlay.setVisibility(View.VISIBLE);
                act.overlay.setFocusable(true);
                act.overlay.setFocusableInTouchMode(true);
                act.overlay.requestFocus();
                act.overlay.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN
                                && keyCode == KeyEvent.KEYCODE_BACK && !cancelled.get()) {
                            cancelled.set(true);
                            act.appDownloadCancel = true;
                            if (act.downloadText != null) act.downloadText.setText(Txt.t(act, "正在取消..."));
                        }
                        return true;
                    }
                });
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }

    void closeDownloadDialog() {
        act.appDownloading = false;
        act.appDownloadCancel = false;
        act.downloadText = null;
        act.console.dismissOverlay();
    }

    void updateDownloadText(final String text) {
        final TextView tv = act.downloadText;
        if (tv == null) return;
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                if (tv == act.downloadText) tv.setText(text);
            }
        });
    }

    void downloadFile(String url, File dest) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException(Txt.f(act, "服务器返回 %d", code));
            long total = c.getContentLength();
            InputStream in = c.getInputStream();
            try {
                OutputStream out = new FileOutputStream(dest);
                try {
                    byte[] buf = new byte[65536];
                    long done = 0;
                    int n;
                    long lastPost = 0L;
                    while ((n = in.read(buf)) > 0) {
                        if (act.appDownloadCancel) throw new IOException(Txt.t(act, "已取消下载"));
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastPost > 200L) {
                            lastPost = now;
                            updateDownloadText(total > 0
                                    ? Txt.f(act, "已下载 %s / %s", Util.fmtSize(done), Util.fmtSize(total))
                                    : Txt.f(act, "已下载 %s", Util.fmtSize(done)));
                        }
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }

    void installApk(final File apk) {
        if (apk == null || !apk.isFile()) {
            act.bridgeResult("onAppResult", "error", Txt.t(act, "安装包不存在"), null);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            act.pendingApk = apk;
            act.ui.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + act.getPackageName()));
                        act.startActivityForResult(i, REQ_INSTALL_PERM);
                    } catch (Throwable t) {
                        act.bridgeResult("onAppResult", "error", Txt.f(act, "无法打开安装权限设置: %s", t.getMessage()), null);
                    }
                }
            });
            return;
        }
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(ApkProvider.uriFor(act, apk),
                            "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                    act.bridgeResult("onAppResult", "installing", "", null);
                } catch (Throwable t) {
                    act.bridgeResult("onAppResult", "error", Txt.f(act, "无法启动安装器: %s", t.getMessage()), null);
                }
            }
        });
    }
}
