package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 网络读取与安装包下载。 */
final class Net {
    private Net() {
    }

    static JSONObject fetchJsonObject(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("Accept", "application/json");
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString());
        } catch (Throwable e) {
            Log.w(TAG, "fetch json failed: " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 拉取 PyPI 上 openfic 的版本与简介（README 摘要，用于更新说明）。 */
    static JSONObject fetchOpenficInfo() {
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
            JSONObject info = o.optJSONObject("info");
            if (info == null) return null;
            String v = info.optString("version", "");
            if (v.isEmpty()) return null;
            String desc = info.optString("description", "");
            if (desc.length() > 1200) desc = desc.substring(0, 1200).trim() + "…";
            JSONObject out = new JSONObject();
            out.put("version", v);
            out.put("notes", desc);
            return out;
        } catch (Throwable e) {
            Log.w(TAG, "fetch openfic info failed: " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String fetchLatestOpenfic() {
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
}