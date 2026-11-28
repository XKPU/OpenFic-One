package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 界面语言：首次跟随系统，写入 OpenFic 后改为跟随服务端设置。 */
final class Lang {
    static final String KEY = "language";
    static final String PENDING = "language_pending";

    private Lang() {
    }

    static String system() {
        return "zh".equals(Locale.getDefault().getLanguage()) ? "zh-CN" : "en";
    }

    static String current(MainActivity act) {
        String v = act.prefs.getString(KEY, null);
        return v == null ? system() : v;
    }

    /** 语言代码对应的显示名。 */
    static String name(String code) {
        return code != null && code.startsWith("zh") ? "简体中文" : "English";
    }

    static String name(MainActivity act) {
        return name(current(act));
    }

    /** 首次启动按系统语言初始化；已装过 OpenFic 时沿用服务端设置。 */
    static void init(MainActivity act) {
        if (act.prefs.contains(KEY)) return;
        boolean fresh = !new File(act.rootfs, "root/.openfic/openfic.db").exists();
        act.prefs.edit().putString(KEY, system()).putBoolean(PENDING, fresh).apply();
    }

    /** 读取 OpenFic 当前语言并跟随，返回本地是否发生变化。 */
    static boolean align(MainActivity act) {
        if (pending(act)) return false;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/api/v1/auth/preferences").openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() != 200) return false;
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            String lang = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8)).optString("language", "");
            if (lang.isEmpty() || lang.equals(act.prefs.getString(KEY, null))) return false;
            act.prefs.edit().putString(KEY, lang).apply();
            act.console.logT("界面语言已跟随 OpenFic: %s", name(lang));
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static void set(MainActivity act, String lang) {
        act.prefs.edit().putString(KEY, lang).putBoolean(PENDING, true).apply();
    }

    static String toggle(MainActivity act) {
        String next = "en".equals(current(act)) ? "zh-CN" : "en";
        set(act, next);
        return next;
    }

    static boolean pending(MainActivity act) {
        return act.prefs.getBoolean(PENDING, false);
    }

    /** 写入 OpenFic 的语言设置：服务在线时走接口，未启动时直接改数据库。 */
    static void sync(MainActivity act) {
        if (!pending(act)) return;
        boolean ok = act.runtime.probe() ? put(act, current(act)) : applyOffline(act);
        if (ok) act.prefs.edit().putBoolean(PENDING, false).apply();
    }

    /** 服务未运行时直接写 OpenFic 数据库，返回是否写入成功。 */
    static boolean applyOffline(MainActivity act) {
        if (!new File(act.rootfs, "root/.openfic/openfic.db").exists()) {
            act.console.logT("OpenFic 尚未初始化，界面语言将在服务启动后同步");
            return false;
        }
        String lang = current(act);
        try {
            ProcResult r = act.runtime.runProotQuiet("export HOME=/root TMPDIR=/tmp; "
                    + "python3 /mnt/share/ofone_lang.py " + lang);
            if (r.code == 0 && Util.marker(r.output, "OFONE_LANG_OK=") != null) {
                act.console.logT("界面语言已写入 OpenFic: %s", name(lang));
                return true;
            }
            if (Util.marker(r.output, "OFONE_LANG_MISSING=") != null) {
                act.console.logT("OpenFic 尚未初始化，界面语言将在服务启动后同步");
                return false;
            }
            String err = Util.marker(r.output, "OFONE_LANG_ERROR=");
            act.console.logT("界面语言写入 OpenFic 失败: %s", err != null ? err : Txt.f(act, "exit %d", r.code));
        } catch (IOException e) {
            act.console.logT("界面语言写入 OpenFic 失败: %s", e.getMessage());
        }
        return false;
    }

    private static boolean put(MainActivity act, String lang) {
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("language", lang);
            byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/api/v1/settings").openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream out = c.getOutputStream();
            try {
                out.write(raw);
            } finally {
                out.close();
            }
            int code = c.getResponseCode();
            if (code > 0 && code < 400) {
                act.console.logT("界面语言已同步: %s", name(lang));
                return true;
            }
            act.console.logT("界面语言同步失败 (HTTP %s)", String.valueOf(code));
        } catch (Throwable t) {
            act.console.logT("界面语言同步失败: %s", t);
        } finally {
            if (c != null) c.disconnect();
        }
        return false;
    }
}
