package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** 与界面无关的小工具：版本比较、体积格式化、文件读写与哈希。 */
final class Util {
    private Util() {
    }

    static String marker(String text, String key) {
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

    static int compareVersion(String a, String b) {
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

    static String fmtSize(long n) {
        if (n >= 1048576L) return String.format(Locale.US, "%.1f MB", n / 1048576.0);
        if (n >= 1024L) return String.format(Locale.US, "%.0f KB", n / 1024.0);
        return n + " B";
    }

    static String sha256Of(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    static String jsString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else b.append(c);
        }
        return b.append('"').toString();
    }

    static byte[] readAll(File f) throws IOException {
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

    static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    static void copyTree(File src, File dst) throws IOException {
        if (isSymlink(src)) return;
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

    static void deleteTree(File f) {
        // rootfs 内含大量指向绝对目标（/usr/bin/...）的软链，listFiles 会跟随软链进入宿主目录并误删。
        // 软链只删链接本身，不进入其目标。
        if (isSymlink(f)) {
            f.delete();
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    static boolean isSymlink(File f) {
        try {
            return android.system.OsConstants.S_ISLNK(
                    android.system.Os.lstat(f.getAbsolutePath()).st_mode);
        } catch (Throwable t) {
            return false;
        }
    }

    static int dp(android.content.Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
