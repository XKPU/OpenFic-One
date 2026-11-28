package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.content.res.Configuration;

import org.json.JSONObject;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 界面主题：默认跟随系统，写入 OpenFic 后改为跟随服务端设置。 */
final class Theme {
    static final String KEY = "theme";
    static final String PENDING = "theme_pending";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    private Theme() {
    }

    /** 系统当前明暗。 */
    static String system(MainActivity act) {
        int mode = act.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
    }

    static boolean valid(String v) {
        return SYSTEM.equals(v) || LIGHT.equals(v) || DARK.equals(v);
    }

    /** 本地保存的主题设置，取值 system / light / dark。 */
    static String current(MainActivity act) {
        String v = act.prefs.getString(KEY, null);
        return valid(v) ? v : SYSTEM;
    }

    /** 实际生效的明暗。 */
    static String effective(MainActivity act) {
        String v = current(act);
        return SYSTEM.equals(v) ? system(act) : v;
    }

    static boolean isDark(MainActivity act) {
        return DARK.equals(effective(act));
    }

    /** 主题设置的显示名。 */
    static String name(MainActivity act, String code) {
        if (LIGHT.equals(code)) return Txt.t(act, "浅色");
        if (DARK.equals(code)) return Txt.t(act, "深色");
        return Txt.t(act, "跟随系统");
    }

    static String name(MainActivity act) {
        return name(act, current(act));
    }

    /** 首次启动按跟随系统初始化，随后由服务端设置覆盖。 */
    static void init(MainActivity act) {
        if (act.prefs.contains(KEY)) return;
        act.prefs.edit().putString(KEY, SYSTEM).putBoolean(PENDING, false).apply();
    }

    /** 读取 OpenFic 当前主题并跟随，返回本地是否发生变化。 */
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
            String theme = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8)).optString("theme", "");
            if (!valid(theme) || theme.equals(act.prefs.getString(KEY, null))) return false;
            act.prefs.edit().putString(KEY, theme).apply();
            act.console.logT("界面主题已跟随 OpenFic: %s", name(act, theme));
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static void set(MainActivity act, String theme) {
        if (!valid(theme)) return;
        act.prefs.edit().putString(KEY, theme).putBoolean(PENDING, true).apply();
    }

    /** 依次切换 跟随系统 → 浅色 → 深色。 */
    static String next(MainActivity act) {
        String cur = current(act);
        String next = SYSTEM.equals(cur) ? LIGHT : (LIGHT.equals(cur) ? DARK : SYSTEM);
        set(act, next);
        return next;
    }

    static boolean pending(MainActivity act) {
        return act.prefs.getBoolean(PENDING, false);
    }

    /** 写入 OpenFic 的主题设置：服务在线时走接口，未启动时直接改数据库。 */
    static void sync(MainActivity act) {
        if (!pending(act)) return;
        boolean ok = act.runtime.probe() ? put(act, current(act)) : applyOffline(act);
        if (ok) act.prefs.edit().putBoolean(PENDING, false).apply();
    }

    /** 服务未运行时直接写 OpenFic 数据库，返回是否写入成功。 */
    static boolean applyOffline(MainActivity act) {
        if (!new File(act.rootfs, "root/.openfic/openfic.db").exists()) {
            act.console.logT("OpenFic 尚未初始化，界面主题将在服务启动后同步");
            return false;
        }
        String theme = current(act);
        try {
            ProcResult r = act.runtime.runProotQuiet("export HOME=/root TMPDIR=/tmp; "
                    + "python3 /mnt/share/ofone_theme.py " + theme);
            if (r.code == 0 && Util.marker(r.output, "OFONE_THEME_OK=") != null) {
                return true;
            }
            String missing = Util.marker(r.output, "OFONE_THEME_MISSING=");
            if (missing != null) {
                act.console.logT("OpenFic 尚未初始化，界面主题将在服务启动后同步");
                return false;
            }
            String err = Util.marker(r.output, "OFONE_THEME_ERROR=");
            act.console.logT("界面主题同步失败: %s", err == null ? "未知错误" : err);
        } catch (Throwable t) {
            act.console.logT("界面主题同步失败: %s", t.toString());
        }
        return false;
    }

    /** 通过接口写入 OpenFic 主题设置。 */
    static boolean put(MainActivity act, String theme) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/api/v1/settings").openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = ("{\"theme\":\"" + theme + "\"}").getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream out = c.getOutputStream();
            out.write(body);
            out.close();
            int code = c.getResponseCode();
            return code > 0 && code < 400;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
