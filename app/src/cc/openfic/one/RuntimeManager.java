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
import java.util.zip.CRC32;

/** 运行时安装、proot 命令与本地服务生命周期。 */
class RuntimeManager {
    private final MainActivity act;

    RuntimeManager(MainActivity act) {
        this.act = act;
    }

    void stopServer() {
        if (act.server != null) {
            act.server.destroy();
            act.server = null;
        }
        killOrphanServer();
    }

    /** 结束 proot 退出后残留的 openfic 服务进程（其 PPID 变为 1 仍占用端口）。 */
    private void killOrphanServer() {
        File[] procs = new File("/proc").listFiles();
        if (procs == null) return;
        for (File p : procs) {
            String name = p.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            // procfs 的 cmdline 报告长度为 0，必须流式读到 EOF（Util.readAll 依赖 length 会读空）
            String line;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = new FileInputStream(new File(p, "cmdline"))) {
                byte[] buf = new byte[512];
                int n;
                while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            } catch (IOException e) {
                continue;
            }
            line = sb.toString().replace('\0', ' ').trim();
            if (line.contains("openfic serve")) {
                try {
                    android.os.Process.killProcess(Integer.parseInt(name));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    void ensureBinaries() throws IOException {
        File stamp = new File(act.binDir, ".ok");
        if (!stamp.exists()) {
            act.console.logT("解压运行时组件...");
            act.binDir.mkdirs();
            copyAsset("bin/proot", new File(act.binDir, "proot"), 0755);
            copyAsset("bin/libtalloc.so.2", new File(act.binDir, "libtalloc.so.2"), 0755);
            copyAsset("bin/libandroid-shmem.so", new File(act.binDir, "libandroid-shmem.so"), 0755);
            File libexec = new File(act.binDir, "libexec");
            libexec.mkdirs();
            copyAsset("bin/libexec/loader", new File(libexec, "loader"), 0755);
            copyAsset("bin/libexec/loader32", new File(libexec, "loader32"), 0755);
            new File(act.binDir, "tmp").mkdirs();
            stamp.createNewFile();
        }
        act.shareDir.mkdirs();
        copyAsset("share/ofone_patch.py", new File(act.shareDir, "ofone_patch.py"), 0644);
        copyAsset("share/ofone-bridge.js", new File(act.shareDir, "ofone-bridge.js"), 0644);
        copyAsset("share/ofone-notify.js", new File(act.shareDir, "ofone-notify.js"), 0644);
        copyAsset("share/ofone_data.py", new File(act.shareDir, "ofone_data.py"), 0644);
        copyAsset("share/ofone_lang.py", new File(act.shareDir, "ofone_lang.py"), 0644);
        copyAsset("share/ofone_theme.py", new File(act.shareDir, "ofone_theme.py"), 0644);
        copyAsset("share/ofone_patch_hf.py", new File(act.shareDir, "ofone_patch_hf.py"), 0644);
    }

    void ensureRootfs() throws IOException {
        File stamp = new File(act.rootfs, ".ok");
        if (stamp.exists()) {
            String cur = new String(Util.readAll(stamp), StandardCharsets.UTF_8).trim();
            if (cur.isEmpty()) {
                Util.writeText(stamp, ROOTFS_STAMP);
                return;
            }
            if (ROOTFS_STAMP.equals(cur)) return;
        }
        File dataDir = new File(act.rootfs, "root/.openfic");
        File backup = new File(act.shareDir, "_backup_openfic");
        boolean hasData = dataDir.exists();
        if (hasData) {
            act.console.logT("备份用户数据...");
            if (backup.exists()) Util.deleteTree(backup);
            Util.copyTree(dataDir, backup);
        }
        act.console.logT("解压 Linux 根文件系统 (约 45MB，首次较慢，请勿退出)...");
        if (act.rootfs.exists()) Util.deleteTree(act.rootfs);
        act.rootfs.mkdirs();
        final long started = System.currentTimeMillis();
        final long[] acc = new long[]{0};
        final long[] dotted = new long[]{0};
        TarExtractor.extractAsset(act, "rootfs.tar", act.rootfs, new TarExtractor.Progress() {
            @Override
            public void onBytes(long bytes) {
                acc[0] += bytes;
                final long mb = acc[0] / (1024 * 1024);
                act.ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mb - dotted[0] >= 8) {
                            dotted[0] = mb;
                            act.console.raw(".");
                            act.consoleScroll.post(new Runnable() {
                                @Override
                                public void run() {
                                    act.consoleScroll.fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    }
                });
            }
        });
        if (hasData) {
            act.console.logT("恢复用户数据...");
            File dest = new File(act.rootfs, "root/.openfic");
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            Util.copyTree(backup, dest);
            Util.deleteTree(backup);
        }
        Util.writeText(stamp, ROOTFS_STAMP);
        act.console.logT("根文件系统就绪，解压耗时 %d 秒", (System.currentTimeMillis() - started) / 1000);
    }

    /** 读取文件内容并返回 CRC32 校验值，用于判断注入脚本是否变化。 */
    private long checksum(File f) {
        if (!f.exists()) return 0;
        CRC32 crc = new CRC32();
        java.io.InputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) crc.update(buf, 0, n);
        } catch (IOException ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {
            }
        }
        return crc.getValue();
    }

    void ensureInjection() {
        // 每次启动都把内核 fastembed 的 HF 预检地址改为随 HF_ENDPOINT 走镜像，幂等（不随 token 门跳过）。
        try {
            runProotQuiet("export HOME=/root TMPDIR=/tmp; python3 /mnt/share/ofone_patch_hf.py");
        } catch (IOException ignored) {
        }
        File marker = new File(act.shareDir, "inject.ok");
        String cur = installedVersion();
        long bridgeSum = checksum(new File(act.shareDir, "ofone-bridge.js"));
        long notifySum = checksum(new File(act.shareDir, "ofone-notify.js"));
        String token = (cur == null ? act.appVersion() : cur) + "-" + bridgeSum + "-" + notifySum;
        if (marker.exists()) {
            try {
                if (token.equals(new String(Util.readAll(marker), StandardCharsets.UTF_8).trim())) return;
            } catch (IOException ignored) {
            }
        }
        try {
            act.console.logT("注入 One 设置入口...");
            ProcResult r = runProotCmd("python3 /mnt/share/ofone_patch.py /mnt/share/ofone-bridge.js");
            if (r.code == 0) {
                Util.writeText(marker, token);
            } else {
                act.console.logT("注入未完成（不影响使用）");
            }
        } catch (IOException e) {
            act.console.logT("注入失败: %s", e.getMessage());
        }
    }

    String installedVersion() {
        // 以实际安装的 dist-info 为准，避免 .ok 与实际安装不一致时误报版本。
        String scanned = scanInstalledVersion();
        if (scanned != null) {
            File stamp = new File(act.rootfs, "opt/venv/.ok");
            try {
                if (!stamp.exists()
                        || !scanned.equals(new String(Util.readAll(stamp), StandardCharsets.UTF_8).trim())) {
                    Util.writeText(stamp, scanned);
                }
            } catch (IOException ignored) {
            }
            return scanned;
        }
        // 扫描不到（环境未建立或损坏）时，回落到记录文件。
        File stamp = new File(act.rootfs, "opt/venv/.ok");
        if (stamp.exists()) {
            try {
                String s = new String(Util.readAll(stamp), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return s;
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private String scanInstalledVersion() {
        File lib = new File(act.rootfs, "opt/venv/lib");
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

    void installOpenFic(String mirrorUrl) throws IOException {
        act.console.logT("开始安装 OpenFic（首次约 5-30 分钟，请保持前台）");
        act.console.logT("镜像源: %s", mirrorUrl);
        String mirrorArg = mirrorUrl.replace("'", "'\\''");
        // /opt/venv/bin/python3 是指向 /usr/bin/python3.x 的软链，宿主侧 File.exists() 会把绝对目标解析到宿主根，
        // 恒为 false 导致每次都判为首次安装。改用 venv 的常规标记文件判断是否已安装。
        boolean fresh = !new File(act.rootfs, "opt/venv/pyvenv.cfg").exists()
                && !new File(act.rootfs, "opt/venv/.ok").exists();
        String script;
        if (!fresh) {
            // 已安装过：直接用 pip 升级，保留现有环境与用户数据，几分钟内完成。
            script = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "
                    + "TMPDIR=/tmp HOME=/root PIP_CACHE_DIR=/root/.cache/pip PIP_DISABLE_PIP_VERSION_CHECK=1; "
                    + "set -e; "
                    + "VP=/opt/venv/bin/python3; "
                    + "echo '" + Txt.t(act, "正在通过 pip 升级 OpenFic（保留用户数据）") + "'; "
                    + "$VP -m pip install --no-input --upgrade -i '" + mirrorArg + "' openfic; "
                    + "echo '" + Txt.t(act, "重新注入 One 设置入口") + "'; "
                    + "python3 /mnt/share/ofone_patch.py /mnt/share/ofone-bridge.js || echo '" + Txt.t(act, "注入失败（不影响使用）") + "'; "
                    + "echo '" + Txt.t(act, "记录版本") + "'; "
                    + "VER=$($VP -m pip show openfic | awk '/^Version:/{print $2}'); "
                    + "echo OFONE_VERSION=$VER; "
                    + "echo -n $VER > /opt/venv/.ok; "
                    + "echo INSTALL_DONE";
        } else {
            // 首次安装：建立虚拟环境并安装 OpenFic。
            script = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "
                    + "TMPDIR=/tmp HOME=/root PIP_CACHE_DIR=/root/.cache/pip PIP_DISABLE_PIP_VERSION_CHECK=1; "
                    + "set -e; "
                    + "echo '" + Txt.t(act, "[1/5] 创建 Python 环境") + "'; "
                    + "rm -rf /opt/venv; "
                    + "python3 -m venv --without-pip /opt/venv; "
                    + "VP=/opt/venv/bin/python3; "
                    + "$VP -m ensurepip --upgrade >/dev/null 2>&1 || true; "
                    + "if ! $VP -c 'import pip' 2>/dev/null; then "
                    + "if ls /usr/share/python-wheels/pip-*.whl >/dev/null 2>&1; then "
                    + "W=$(ls /usr/share/python-wheels/pip-*.whl | head -1); "
                    + "$VP -m zipfile -e $W /opt/venv/lib/python3.12/site-packages || true; "
                    + "fi; fi; "
                    + "echo '" + Txt.t(act, "[2/5] 下载 OpenFic 及依赖（约 60MB）") + "'; "
                    + "mkdir -p /opt/wheels; "
                    + "$VP -m pip download --cache-dir /root/.cache/pip --no-input -d /opt/wheels -i '" + mirrorArg + "' openfic; "
                    + "echo '" + Txt.t(act, "[3/5] 安装依赖（约 2-5 分钟）") + "'; "
                    + "$VP -m pip install --no-input --no-index --find-links /opt/wheels openfic; "
                    + "rm -rf /opt/wheels; "
                    + "echo '" + Txt.t(act, "[4/5] 注入 One 设置入口") + "'; "
                    + "python3 /mnt/share/ofone_patch.py /mnt/share/ofone-bridge.js || echo '" + Txt.t(act, "注入失败（不影响使用）") + "'; "
                    + "echo '" + Txt.t(act, "[5/5] 记录版本") + "'; "
                    + "VER=$($VP -m pip show openfic | awk '/^Version:/{print $2}'); "
                    + "echo OFONE_VERSION=$VER; "
                    + "echo -n $VER > /opt/venv/.ok; "
                    + "echo INSTALL_DONE";
        }
        ProcResult r = runProotCmd(script);
        if (r.code != 0) {
            throw new IOException(Txt.f(act, "依赖安装失败 (exit %d)，详见 %s", r.code, act.logFile.getAbsolutePath()));
        }
        String ver = Util.marker(r.output, "OFONE_VERSION=");
        if (ver != null) act.console.logT("OpenFic 安装完成: %s", ver);
        else act.console.logT("OpenFic 安装完成");
        File stamp = new File(act.rootfs, "opt/venv/.ok");
        if (ver != null) Util.writeText(stamp, ver);
    }

    ProcResult runProotCmd(String script) throws IOException {
        return runProotCmd(script, true);
    }

    /** 静默执行 proot 命令：输出只写日志，不回显到控制台。 */
    ProcResult runProotQuiet(String script) throws IOException {
        return runProotCmd(script, false);
    }

    ProcResult runProotCmd(String script, final boolean echo) throws IOException {
        Process p = prootBuilder(script).start();
        final ProcResult res = new ProcResult();
        final StringBuilder sb = new StringBuilder();
        final OutputStream fo = new FileOutputStream(act.logFile, true);
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
                        if (!echo) continue;
                        final String l = line;
                        act.ui.post(new Runnable() {
                            @Override
                            public void run() {
                                act.console.raw(l + "\n");
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

    void startServer() throws IOException {
        if (act.server == null && probe()) {
            act.console.logT("检测到已在运行的服务，直接复用");
            return;
        }
        String script = "export PATH=/opt/venv/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TMPDIR=/tmp HOME=/root; "
                + "exec openfic serve --host 127.0.0.1 --port " + PORT;
        act.server = prootBuilder(script).start();
        pumpToLog(act.server.getInputStream(), "server");
        act.console.logT("服务进程已启动");
    }

    void waitForServer() throws InterruptedException {
        act.console.logT("等待服务就绪...");
        for (int i = 0; i < 180; i++) {
            if (probe()) {
                act.console.logT("服务已就绪 http://127.0.0.1:%d", PORT);
                return;
            }
            if (act.server != null && !act.server.isAlive()) {
                throw new IllegalStateException(Txt.f(act, "服务进程退出 code=%d", act.server.exitValue()));
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException(Txt.t(act, "服务启动超时"));
    }

    boolean probe() {
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

    ProcessBuilder prootBuilder(String script) {
        ProcessBuilder pb = new ProcessBuilder(
                new File(act.binDir, "proot").getAbsolutePath(),
                "--link2symlink",
                "-r", act.rootfs.getAbsolutePath(),
                "-0",
                "-w", "/",
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", act.shareDir.getAbsolutePath() + ":/mnt/share",
                "/usr/bin/env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/bash", "-c", script);
        pb.directory(act.binDir);
        pb.redirectErrorStream(true);
        pb.environment().put("PROOT_TMP_DIR", new File(act.binDir, "tmp").getAbsolutePath());
        pb.environment().put("PROOT_LOADER", new File(act.binDir, "libexec/loader").getAbsolutePath());
        pb.environment().put("PROOT_NO_SECCOMP", "1");
        pb.environment().put("LD_LIBRARY_PATH", act.binDir.getAbsolutePath());
        pb.environment().put("HOME", "/root");
        pb.environment().put("TMPDIR", "/tmp");
        // 内置模型下载源按界面语言选择：中文优先国内镜像，英文优先官方源。
        String hfLang = Lang.current(act);
        String hfEndpoint = (hfLang != null && hfLang.startsWith("zh"))
                ? "https://hf-mirror.com" : "https://huggingface.co";
        pb.environment().put("HF_ENDPOINT", hfEndpoint);
        pb.environment().put("HF_HUB_ENDPOINT", hfEndpoint);
        return pb;
    }

    /** 去掉 ANSI 颜色控制符，便于匹配日志级别。 */
    private static String plain(String line) {
        if (line.indexOf('\u001b') < 0) return line;
        return line.replaceAll("\u001b\\[[0-9;]*[A-Za-z]", "");
    }

    /** 是否为服务启动时的首条 INFO 行（loguru 会带时间戳与级别列）。 */
    private static boolean isInfo(String line) {
        return java.util.regex.Pattern.compile("(^|[\\s|])INFO([\\s|:]|$)")
                .matcher(line).find();
    }

    void pumpToLog(final InputStream in, final String tag) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    boolean skipFirstInfo = "server".equals(tag);
                    while ((line = r.readLine()) != null) {
                        Log.i(TAG, tag + ": " + line);
                        if (skipFirstInfo && isInfo(plain(line))) {
                            skipFirstInfo = false;
                            continue;
                        }
                        final String l = line;
                        act.ui.post(new Runnable() {
                            @Override
                            public void run() {
                                act.console.raw(l + "\n");
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

    void copyAsset(String name, File dest, int mode) throws IOException {
        InputStream in = act.getAssets().open(name);
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
}
