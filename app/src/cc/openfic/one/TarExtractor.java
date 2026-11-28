package cc.openfic.one;

import android.content.Context;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal streaming tar extractor with symlink and mode support. */
public final class TarExtractor {
    private static final String TAG = "OpenFicOne";
    private static final int BLOCK = 512;
    /** 小于此体积的文件整块读入后交给线程池并行落盘。 */
    private static final int SMALL_FILE = 256 * 1024;
    private static final int BIG_BUF = 256 * 1024;

    public interface Progress {
        void onBytes(long bytes);
    }

    private TarExtractor() {}

    public static void extractAsset(Context ctx, String assetName, File destDir, Progress cb)
            throws IOException {
        AssetManager am = ctx.getAssets();
        InputStream in = am.open(assetName);
        try {
            java.io.PushbackInputStream pb = new java.io.PushbackInputStream(in, 2);
            byte[] magic = new byte[2];
            int n = pb.read(magic);
            if (n > 0) pb.unread(magic, 0, n);
            InputStream body = (n == 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b)
                    ? new java.util.zip.GZIPInputStream(new BufferedInputStream(pb, BIG_BUF), BIG_BUF) : pb;
            extract(body, destDir, cb);
            body.close();
        } finally {
            in.close();
        }
    }

    public static void extract(InputStream raw, File destDir, Progress cb) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("cannot create " + destDir);
        }
        int workers = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        final ExecutorService pool = Executors.newFixedThreadPool(workers);
        final Semaphore permits = new Semaphore(workers * 4);
        final AtomicReference<IOException> failure = new AtomicReference<IOException>();
        final List<Future<?>> pending = new ArrayList<Future<?>>();
        byte[] hdr = new byte[BLOCK];
        Map<String, String> pax = new HashMap<String, String>();
        String longName = null;
        String longLink = null;
        try {
            while (true) {
                int n = readFully(raw, hdr);
                if (n < BLOCK) break;
                if (isZero(hdr)) break;

                String name = cstr(hdr, 0, 100);
                String prefix = cstr(hdr, 345, 155);
                if (prefix.length() > 0) name = prefix + "/" + name;
                long size = parseNum(hdr, 124, 12);
                int mode = (int) parseNum(hdr, 100, 8);
                char type = (char) (hdr[156] & 0xff);
                String link = cstr(hdr, 157, 100);

                byte[] body = null;
                if (size > 0) {
                    if (type == 'x' || type == 'g' || type == 'L' || type == 'K') {
                        body = new byte[(int) size];
                        readFully(raw, body);
                        skipPadding(raw, size);
                    }
                }

                if (type == 'x') {
                    parsePax(new String(body, "UTF-8"), pax);
                    continue;
                }
                if (type == 'g') continue;
                if (type == 'L') { longName = new String(body, "UTF-8").replace("\0", ""); continue; }
                if (type == 'K') { longLink = new String(body, "UTF-8").replace("\0", ""); continue; }

                if (longName != null) { name = longName; longName = null; }
                if (longLink != null) { link = longLink; longLink = null; }
                if (pax.containsKey("path")) name = pax.get("path");
                if (pax.containsKey("linkpath")) link = pax.get("linkpath");
                if (pax.containsKey("size")) {
                    try { size = Long.parseLong(pax.get("size")); } catch (NumberFormatException ignored) {}
                }
                pax.clear();

                if (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty() || name.equals(".") || name.startsWith("dev/")
                        || name.startsWith("/") || hasParentSegment(name)) {
                    if (size > 0) { skip(raw, size); skipPadding(raw, size); }
                    continue;
                }
                File dest = new File(destDir, name);

                if (type == '5') {
                    if (!dest.exists() && !dest.mkdirs()) Log.w(TAG, "mkdir failed " + name);
                    chmod(dest, mode & 07777);
                    if (size > 0) { skip(raw, size); skipPadding(raw, size); }
                    continue;
                }
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                if (type == '2') {
                    deleteQuietly(dest);
                    try {
                        Os.symlink(link, dest.getAbsolutePath());
                    } catch (Exception e) {
                        Log.w(TAG, "symlink failed " + name + " -> " + link + ": " + e);
                    }
                    continue;
                }
                if (type == '1') {
                    drain(pending);
                    deleteQuietly(dest);
                    try {
                        copyFile(new File(destDir, link), dest);
                    } catch (IOException e) {
                        Log.w(TAG, "hardlink failed " + name);
                    }
                    if (size > 0) { skip(raw, size); skipPadding(raw, size); }
                    continue;
                }
                if (type != '0' && type != 0 && type != '7') {
                    if (size > 0) { skip(raw, size); skipPadding(raw, size); }
                    continue;
                }

                if (size <= SMALL_FILE) {
                    permits.acquireUninterruptibly();
                    byte[] data;
                    try {
                        data = new byte[(int) size];
                        readFully(raw, data);
                        skipPadding(raw, size);
                    } catch (IOException e) {
                        permits.release();
                        throw e;
                    }
                    final File f = dest;
                    final byte[] payload = data;
                    final int fmode = mode & 07777;
                    pending.add(pool.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                writeSmall(f, payload, fmode);
                            } catch (IOException e) {
                                failure.compareAndSet(null, e);
                            } finally {
                                permits.release();
                            }
                        }
                    }));
                    if (pending.size() >= 512) drain(pending);
                } else {
                    writeLarge(raw, dest, size, mode & 07777);
                    skipPadding(raw, size);
                }
                if (cb != null) cb.onBytes(size);
                IOException err = failure.get();
                if (err != null) throw err;
            }
            drain(pending);
        } finally {
            pool.shutdown();
        }
        IOException err = failure.get();
        if (err != null) throw err;
    }

    private static void writeSmall(File dest, byte[] data, int mode) throws IOException {
        if (dest.isDirectory()) deleteQuietly(dest);
        OutputStream out = new FileOutputStream(dest);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        chmod(dest, mode);
    }

    private static void writeLarge(InputStream raw, File dest, long size, int mode) throws IOException {
        if (dest.isDirectory()) deleteQuietly(dest);
        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[BIG_BUF];
            long left = size;
            while (left > 0) {
                int r = raw.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) break;
                out.write(buf, 0, r);
                left -= r;
            }
        } finally {
            out.close();
        }
        chmod(dest, mode);
    }

    private static void drain(List<Future<?>> pending) {
        for (Future<?> f : pending) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
        pending.clear();
    }

    private static boolean hasParentSegment(String name) {
        int i = 0;
        while (true) {
            int j = name.indexOf('/', i);
            String seg = j < 0 ? name.substring(i) : name.substring(i, j);
            if (seg.equals("..")) return true;
            if (j < 0) return false;
            i = j + 1;
        }
    }

    private static void chmod(File f, int mode) {
        try {
            Os.chmod(f.getAbsolutePath(), mode == 0 ? 0644 : mode);
        } catch (Exception ignored) {}
    }

    private static void parsePax(String text, Map<String, String> out) {
        int i = 0;
        while (i < text.length()) {
            int sp = text.indexOf(' ', i);
            if (sp < 0) break;
            int len;
            try { len = Integer.parseInt(text.substring(i, sp)); } catch (NumberFormatException e) { break; }
            if (len <= 0 || i + len > text.length()) break;
            String rec = text.substring(sp + 1, i + len);
            int eq = rec.indexOf('=');
            if (eq > 0) out.put(rec.substring(0, eq), rec.substring(eq + 1).replace("\n", ""));
            i += len;
        }
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off);
    }

    private static long parseNum(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) {
            long v = 0;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (b[i] & 0xff);
            return v;
        }
        String s = cstr(b, off, len).trim();
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s, 8); } catch (NumberFormatException e) { return 0; }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private static void skip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        long left = n;
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            left -= r;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (BLOCK - (size % BLOCK)) % BLOCK;
        if (pad > 0) skip(in, pad);
    }

    private static void copyFile(File src, File dst) throws IOException {
        InputStream in = new java.io.FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[BIG_BUF];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void deleteQuietly(File f) {
        if (f.exists() && !f.delete()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteQuietly(k);
            f.delete();
        }
    }
}
