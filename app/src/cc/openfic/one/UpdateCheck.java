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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.InputType;
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

/** 启动阶段与面板内的更新检查。 */
class UpdateCheck {
    private final MainActivity act;

    UpdateCheck(MainActivity act) {
        this.act = act;
    }

    void maybeUpdate(String installed) throws IOException, InterruptedException {
        if (act.prefs.getBoolean("never_check", false)) {
            act.console.logT("已设置为永不检查更新，跳过检查");
            return;
        }
        if (!act.prefs.getBoolean("auto_update", true)) {
            act.console.logT("自动检查更新已关闭，跳过检查");
            return;
        }
        long snooze = act.prefs.getLong("snooze_until", 0L);
        if (snooze > System.currentTimeMillis()) {
            act.console.logT("更新检查已暂停，跳过检查");
            return;
        }
        act.console.logT("检查 OpenFic 更新...");
        String latest = Net.fetchLatestOpenfic();
        if (latest == null) {
            act.console.logT("更新检查失败（网络不可用），继续启动");
            return;
        }
        act.console.logT("最新版本: %s", latest);
        if (Util.compareVersion(latest, installed) <= 0) {
            act.console.logT("已是最新版本");
            return;
        }
        String skipped = act.prefs.getString("skip_version", "");
        if (latest.equals(skipped)) {
            act.console.logT("已跳过版本 %s", latest);
            return;
        }
        int choice = new UpdatePanel(act).askUpdate(Txt.t(act, "发现 OpenFic 新版本"), Txt.t(act, "更新只重建 Python 环境，不会删除你的作品与设置。"),
                installed, latest, "snooze_until");
        if (choice == UPDATE_ACCEPT) {
            String mirror = askMirror(true);
            if (mirror == null) {
                act.console.logT("已取消更新");
            } else {
                act.runtime.installOpenFic(mirror);
                act.runtime.ensureInjection();
            }
        } else if (choice == UPDATE_SKIP_VERSION) {
            act.prefs.edit().putString("skip_version", latest).apply();
            act.console.logT("已跳过版本 %s", latest);
        } else if (choice == UPDATE_NEVER) {
            act.prefs.edit().putBoolean("never_check", true).apply();
            act.console.logT("已关闭更新检查");
        } else if (choice == UPDATE_SNOOZE) {
            long until = act.prefs.getLong("snooze_until", 0L);
            long days = Math.max(1L, (until - System.currentTimeMillis()) / 86400000L);
            act.console.logT("已暂停更新检查 %d 天", days);
        } else {
            act.console.logT("本次不更新");
        }
    }

    void maybeUpdateApp() throws InterruptedException {
        if (act.prefs.getBoolean("app_never_check", false)) {
            act.console.logT("已无限期暂停 OpenFic One 更新检查，跳过检查");
            return;
        }
        if (!act.prefs.getBoolean("app_auto_update", true)) {
            act.console.logT("OpenFic One 自动检查已关闭，跳过检查");
            return;
        }
        long snooze = act.prefs.getLong("app_snooze_until", 0L);
        if (snooze > System.currentTimeMillis()) {
            act.console.logT("OpenFic One 更新检查已暂停，跳过检查");
            return;
        }
        act.console.logT("检查 OpenFic One 更新...");
        JSONObject info = queryAppUpdate();
        if (info == null) {
            act.console.logT("OpenFic One 更新检查失败（网络不可用），继续启动");
            return;
        }
        String latest = info.optString("latest", "");
        act.console.logT("OpenFic One 最新版本: %s", latest);
        if (!info.optBoolean("updateAvailable", false)) {
            act.console.logT("OpenFic One 已是最新版本");
            return;
        }
        String skipped = act.prefs.getString("app_skip_version", "");
        if (latest.equals(skipped)) {
            act.console.logT("已跳过 OpenFic One 版本 %s", latest);
            return;
        }
        act.lastAppUpdate = info;
        int choice = new UpdatePanel(act).askUpdate(Txt.t(act, "发现 OpenFic One 新版本"), info.optString("notes", ""),
                info.optString("current", ""), latest, "app_snooze_until");
        if (choice == UPDATE_ACCEPT) {
            new Bridge(act).downloadAppUpdate(0);
        } else if (choice == UPDATE_SKIP_VERSION) {
            act.prefs.edit().putString("app_skip_version", latest).apply();
            act.console.logT("已跳过 OpenFic One 版本 %s", latest);
        } else if (choice == UPDATE_NEVER) {
            act.prefs.edit().putBoolean("app_never_check", true).apply();
            act.console.logT("已无限期暂停 OpenFic One 更新检查");
        } else if (choice == UPDATE_SNOOZE) {
            long until = act.prefs.getLong("app_snooze_until", 0L);
            long days = Math.max(1L, (until - System.currentTimeMillis()) / 86400000L);
            act.console.logT("已暂停 OpenFic One 更新检查 %d 天", days);
        } else {
            act.console.logT("本次不更新 OpenFic One");
        }
    }

    String askMirror(final boolean forUpdate) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> chosen = new AtomicReference<String>(null);
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.removeAllViews();
                act.overlay.addView(buildMirrorPanel(latch, chosen, forUpdate), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                act.overlay.setVisibility(View.VISIBLE);
            }
        });
        latch.await();
        String picked = chosen.get();
        if (picked == null) return forUpdate ? null : savedMirror();
        return picked;
    }

    String savedMirror() {
        String saved = act.prefs.getString("mirror", "");
        if (saved != null && saved.length() > 0) return saved;
        // 中文界面默认国内镜像，英文界面默认官方 PyPI。
        return "en".equals(Lang.current(act)) ? OFFICIAL_PYPI : DEFAULT_MIRROR;
    }

    void useMirror(String name, String url) {
        act.prefs.edit().putString("mirror", url).putString("mirror_name", name).apply();
        try {
            Util.writeText(new File(act.filesDir, "mirror.txt"), url);
        } catch (IOException ignored) {
        }
        act.console.logT("已选择镜像: %s (%s)", name, url);
    }

    /** 中文界面优先国内镜像；英文界面把官方 PyPI 提到首位。 */
    int[] mirrorOrder(MainActivity act) {
        int n = MIRRORS.length;
        int[] order = new int[n];
        if ("en".equals(Lang.current(act))) {
            order[0] = n - 1;
            for (int i = 1; i < n; i++) order[i] = i - 1;
        } else {
            for (int i = 0; i < n; i++) order[i] = i;
        }
        return order;
    }

    /** 语言切换区：标题一行，两个选项另起一行，当前项高亮。 */
    View buildLangRow(final CountDownLatch latch, final AtomicReference<String> chosen,
                      final boolean forUpdate) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, act.console.dp(16), 0, act.console.dp(4));

        TextView label = new TextView(act);
        label.setText(Txt.t(act, "界面语言 / Language"));
        label.setTextColor(Palette.of(act).muted);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(label);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, act.console.dp(8), 0, 0);

        String cur = Lang.current(act);
        if (cur.startsWith("zh")) cur = "zh-CN";
        row.addView(langChip("简体中文", "zh-CN", cur, latch, chosen, forUpdate));
        row.addView(langChip("English", "en", cur, latch, chosen, forUpdate));
        box.addView(row);
        return box;
    }

    TextView langChip(final String name, final String code, String cur,
                      final CountDownLatch latch, final AtomicReference<String> chosen,
                      final boolean forUpdate) {
        final boolean on = code.equals(cur);
        TextView chip = new TextView(act);
        chip.setText(name);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        Palette pal = Palette.of(act);
        chip.setTextColor(on ? pal.accent : pal.muted);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(act.console.dp(14), act.console.dp(7), act.console.dp(14), act.console.dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(act.console.dp(6));
        bg.setColor(on ? pal.accentSoft : 0x00000000);
        bg.setStroke(act.console.dp(1), on ? pal.accent : pal.border);
        chip.setBackgroundDrawable(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = "zh-CN".equals(code) ? 0 : act.console.dp(8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (on) return;
                Lang.set(act, code);
                act.console.applyLang();
                rebuildMirrorPanel(latch, chosen, forUpdate);
                act.console.logT("界面语言已设为: %s", name);
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
        return chip;
    }

    /** 切换语言后就地重建面板，让标题、说明与按钮立即变成新语言。 */
    void rebuildMirrorPanel(final CountDownLatch latch, final AtomicReference<String> chosen,
                            final boolean forUpdate) {
        act.overlay.removeAllViews();
        act.overlay.addView(buildMirrorPanel(latch, chosen, forUpdate), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        act.overlay.setVisibility(View.VISIBLE);
    }

    View buildMirrorPanel(final CountDownLatch latch, final AtomicReference<String> chosen,
                                  final boolean forUpdate) {
        final Palette pal = Palette.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(pal.bg);
        box.setPadding(act.console.dp(24), act.console.dp(36), act.console.dp(24), act.console.dp(28));

        TextView title = new TextView(act);
        title.setText(Txt.t(act, "选择 PyPI 镜像"));
        title.setTextColor(pal.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        box.addView(buildLangRow(latch, chosen, forUpdate));

        TextView note = new TextView(act);
        note.setText(Txt.t(act, "建议在系统设置中允许「OpenFic One」后台运行并忽略电池优化，避免切出应用后进程被回收、再次进入时重新执行初始化检查。"));
        note.setTextColor(pal.muted);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setPadding(0, act.console.dp(10), 0, act.console.dp(6));
        box.addView(note);

        TextView link = new TextView(act);
        link.setText(Txt.t(act, "前往系统设置 ›"));
        link.setTextColor(pal.accent);
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        link.setPadding(0, 0, 0, act.console.dp(12));
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.console.openBatterySettings();
            }
        });
        box.addView(link);

        TextView hint = new TextView(act);
        hint.setText(Txt.t(act, "首次初始化在下载完 Python 包后还需要解压安装，视设备性能可能需要 5 至 30 分钟不等，请保持前台并耐心等待。"));
        hint.setTextColor(pal.muted);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setPadding(0, 0, 0, act.console.dp(14));
        box.addView(hint);

        int[] order = mirrorOrder(act);
        for (int k = 0; k < order.length; k++) {
            final String name = MIRRORS[order[k]][0];
            final String url = MIRRORS[order[k]][1];
            Button b = new Button(act);
            b.setText(Txt.t(act, name));
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    useMirror(name, url);
                    chosen.set(url);
                    act.console.dismissOverlay();
                    latch.countDown();
                }
            });
            box.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        box.addView(buildCustomMirror(latch, chosen));

        if (forUpdate) {
            Button cancel = new Button(act);
            cancel.setText(Txt.t(act, "取消更新，直接进入应用"));
            cancel.setAllCaps(false);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen.set(null);
                    act.console.dismissOverlay();
                    latch.countDown();
                }
            });
            box.addView(cancel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        ScrollView sc = new ScrollView(act);
        sc.setBackgroundColor(pal.bg);
        sc.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    View buildCustomMirror(final CountDownLatch latch, final AtomicReference<String> chosen) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, act.console.dp(12), 0, 0);

        final TextView toggle = new TextView(act);
        toggle.setText(Txt.t(act, "自定义镜像源 ›"));
        toggle.setTextColor(Palette.of(act).accent);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toggle.setPadding(0, 0, 0, act.console.dp(6));
        box.addView(toggle);

        final LinearLayout editor = new LinearLayout(act);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setVisibility(View.GONE);

        final EditText input = new EditText(act);
        input.setSingleLine(true);
        input.setHint("https://example.com/pypi/simple/");
        input.setHintTextColor(Palette.of(act).faint);
        input.setTextColor(Palette.of(act).text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String saved = act.prefs.getString("mirror", "");
        if (saved != null && saved.length() > 0) input.setText(saved);
        editor.addView(input);

        Button apply = new Button(act);
        apply.setText(Txt.t(act, "使用该镜像"));
        apply.setAllCaps(false);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = input.getText().toString().trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(act, Txt.t(act, "请填写以 http:// 或 https:// 开头的完整地址"), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (url.indexOf(' ') >= 0) {
                    Toast.makeText(act, Txt.t(act, "地址中不能包含空格"), Toast.LENGTH_SHORT).show();
                    return;
                }
                useMirror(Txt.t(act, "自定义"), url);
                chosen.set(url);
                act.console.dismissOverlay();
                latch.countDown();
            }
        });
        editor.addView(apply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(editor);

        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = editor.getVisibility() != View.VISIBLE;
                editor.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(Txt.t(act, show ? "收起自定义镜像源 ›" : "自定义镜像源 ›"));
                if (show) input.requestFocus();
            }
        });
        return box;
    }

    /** 中文界面按「镜像→官方」顺序，英文界面把官方源提到首位。 */
    String[] orderedAppUpdateApis() {
        String[] base = DEFAULT_APP_UPDATE_APIS;
        if (!"en".equals(Lang.current(act))) return base;
        String[] out = new String[base.length];
        out[0] = base[base.length - 1];
        for (int i = 1; i < base.length; i++) out[i] = base[i - 1];
        return out;
    }

    JSONObject queryAppUpdate() {
        String custom = act.prefs.getString("app_update_api", "");
        String[] apis;
        if (custom == null || custom.trim().isEmpty()) {
            apis = orderedAppUpdateApis();
        } else {
            apis = new String[]{custom.trim()};
        }
        JSONObject o = null;
        String used = "";
        for (String api : apis) {
            act.console.logT("检查 OpenFic One 更新: %s", api);
            o = Net.fetchJsonObject(api);
            if (o != null) { used = api; break; }
            act.console.logT("更新源不可用，尝试下一个");
        }
        if (o == null) return null;
        String latest = o.optString("version", "");
        if (latest.startsWith("v")) latest = latest.substring(1);
        if (latest.isEmpty()) return null;
        try {
            boolean en = "en".equals(Lang.current(act));
            JSONArray sources = null;
            JSONObject apk = o.optJSONObject("apk");
            if (apk != null) {
                sources = apk.optJSONArray(en ? "en" : "zh");
                if (sources == null || sources.length() == 0) {
                    sources = apk.optJSONArray(en ? "zh" : "en");
                }
            }
            if (sources == null) sources = o.optJSONArray("apk");
            String notes = o.optString(en ? "notes_en" : "notes", "");
            if (notes.isEmpty()) notes = o.optString(en ? "notes" : "notes_en", "");
            JSONObject r = new JSONObject();
            r.put("latest", latest);
            r.put("current", act.appVersion());
            r.put("notes", notes);
            r.put("updateAvailable", Util.compareVersion(latest, act.appVersion()) > 0);
            r.put("sources", sources == null ? new JSONArray() : sources);
            r.put("source", used);
            r.put("size", o.optLong("size", 0));
            r.put("sha256", o.optString("sha256", ""));
            return r;
        } catch (Throwable t) {
            return null;
        }
    }
}
