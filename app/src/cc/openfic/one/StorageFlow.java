package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 数据导入导出与下载目录写入。 */
class StorageFlow {
    private final MainActivity act;

    StorageFlow(MainActivity act) {
        this.act = act;
    }

    void runStorageTask(final Runnable task) {
        if (Build.VERSION.SDK_INT >= 29
                || act.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            task.run();
            return;
        }
        act.pendingStorage = task;
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        });
    }

    String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    /** 归一化导出格式为扩展名：zip / tar.gz / tgz。 */
    static String exportExt(String fmt) {
        if ("tar.gz".equals(fmt)) return ".tar.gz";
        if ("tgz".equals(fmt)) return ".tgz";
        return ".zip";
    }

    static String exportMime(String ext) {
        if (ext.endsWith(".zip")) return "application/zip";
        return "application/gzip";
    }

    void doExport(String fmt) {
        final String ext = exportExt(fmt);
        final String mime = exportMime(ext);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File out = new File(act.shareDir, "_ofone-export" + ext);
                try {
                    if (out.exists() && !out.delete()) throw new IOException(Txt.t(act, "无法清理旧导出文件"));
                    act.console.logT("开始导出数据...");
                    ProcResult r = act.runtime.runProotCmd("export HOME=/root TMPDIR=/tmp; "
                            + "python3 /mnt/share/ofone_data.py export /mnt/share/_ofone-export" + ext);
                    if (r.code != 0 || !out.exists()) {
                        String err = Util.marker(r.output, "OFONE_EXPORT_ERROR=");
                        act.bridgeResult("onDataResult", "error", err != null ? err : Txt.f(act, "导出失败 (exit %d)", r.code), null);
                        act.console.logT("导出失败");
                        return;
                    }
                    String items = Util.marker(r.output, "OFONE_EXPORT_ITEMS=");
                    String name = "openfic-data-" + stamp() + ext;
                    String saved = writeToDownloads(out, name, mime, DOWNLOAD_SUBDIR);
                    out.delete();
                    JSONObject extra = new JSONObject();
                    extra.put("action", "export");
                    extra.put("path", saved);
                    extra.put("items", items == null ? "" : items.trim());
                    act.bridgeResult("onDataResult", "ok", Txt.f(act, "已导出到 %s", saved), extra);
                    act.console.logT("导出完成: %s", saved);
                    openFolder(new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_SUBDIR));
                } catch (Throwable t) {
                    Log.e(TAG, "export failed", t);
                    act.bridgeResult("onDataResult", "error", Txt.f(act, "导出失败: %s", t.getMessage()), null);
                    act.console.logT("导出失败: %s", t);
                }
            }
        }, "ofone-export").start();
    }

    void pickImportFile() {
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/zip", "application/x-zip-compressed",
                            "application/gzip", "application/x-gzip",
                            "application/x-tar", "application/x-compressed-tar",
                            "application/octet-stream"});
                    act.startActivityForResult(i, REQ_IMPORT_ZIP);
                } catch (Throwable t) {
                    act.bridgeResult("onImportPicked", "error", Txt.f(act, "无法打开文件选择器: %s", t.getMessage()), null);
                }
            }
        });
    }

    /** 从 uri 查显示文件名。 */
    String displayName(Uri uri) {
        String name = null;
        try {
            Cursor c = act.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) name = c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return name == null ? "" : name;
    }

    /** 计算临时文件扩展名（.zip / .tar.gz / .tgz）。 */
    static String importExt(String name) {
        String low = name == null ? "" : name.toLowerCase();
        if (low.endsWith(".tar.gz")) return ".tar.gz";
        if (low.endsWith(".tgz")) return ".tgz";
        return ".zip";
    }

    /** 将用户选择的文件复制为 shareDir 下的临时归档，并回调 onImportPicked（此时尚未开始导入）。 */
    void stageImport(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String name = displayName(uri);
                    String ext = importExt(name);
                    File temp = new File(act.shareDir, "_ofone-import" + ext);
                    InputStream in = act.getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException(Txt.t(act, "无法读取所选文件"));
                    OutputStream out = new FileOutputStream(temp);
                    try {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        out.close();
                        in.close();
                    }
                    act.pendingImport = temp;
                    JSONObject extra = new JSONObject();
                    extra.put("name", name.isEmpty() ? temp.getName() : name);
                    act.bridgeResult("onImportPicked", "ok", name.isEmpty() ? temp.getName() : name, extra);
                } catch (Throwable t) {
                    Log.e(TAG, "stage import failed", t);
                    act.bridgeResult("onImportPicked", "error", Txt.f(act, "读取文件失败: %s", t.getMessage()), null);
                }
            }
        }, "ofone-stage-import").start();
    }

    String writeToDownloads(File src, String name, String mime, String subdir) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + subdir);
            Uri target = act.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (target == null) throw new IOException(Txt.t(act, "无法在下载目录创建文件"));
            InputStream in = new FileInputStream(src);
            OutputStream out = act.getContentResolver().openOutputStream(target);
            if (out == null) {
                in.close();
                throw new IOException(Txt.t(act, "无法写入下载目录"));
            }
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
                in.close();
            }
            return Txt.f(act, "下载/%s/%s", subdir, name);
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), subdir);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException(Txt.f(act, "无法创建 %s", dir));
        File dst = new File(dir, name);
        Util.copyTree(src, dst);
        return dst.getAbsolutePath();
    }

    void doOpenLogFolder() {
        try {
            String where = writeToDownloads(act.logFile, "install.log", "text/plain", DOWNLOAD_SUBDIR + "/logs");
            openFolder(new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_SUBDIR + "/logs"));
            act.bridgeResult("onDataResult", "ok", Txt.f(act, "日志已下载到 %s", where), null);
        } catch (Throwable t) {
            Log.e(TAG, "open log folder failed", t);
            act.bridgeResult("onDataResult", "error", Txt.f(act, "打开日志目录失败: %s", t.getMessage()), null);
        }
    }

    /** 执行导入：暂停服务→解压覆盖→重启服务。archive 为已暂存的临时归档。 */
    void doImport(final File archive) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    act.console.showConsole(Txt.t(act, "正在导入数据"));
                    act.console.logT("开始导入数据...");
                    act.runtime.stopServer();
                    Thread.sleep(800);
                    ProcResult r = act.runtime.runProotCmd("export HOME=/root TMPDIR=/tmp; "
                            + "python3 /mnt/share/ofone_data.py import /mnt/share/" + archive.getName());
                    archive.delete();
                    if (r.code != 0) {
                        String err = Util.marker(r.output, "OFONE_IMPORT_ERROR=");
                        act.bridgeResult("onDataResult", "error", err != null ? err : Txt.f(act, "导入失败 (exit %d)", r.code), null);
                        act.console.logT("导入失败，恢复原服务");
                        act.runtime.startServer();
                        act.runtime.waitForServer();
                        act.console.showWebViewReload();
                        return;
                    }
                    String files = Util.marker(r.output, "OFONE_IMPORT_FILES=");
                    JSONObject extra = new JSONObject();
                    extra.put("action", "import");
                    extra.put("files", files == null ? "" : files.trim());
                    act.bridgeResult("onDataResult", "ok", Txt.t(act, "导入完成，正在重启服务"), extra);
                    act.console.logT("导入完成，重启服务");
                    act.runtime.startServer();
                    act.runtime.waitForServer();
                    act.console.showWebViewReload();
                } catch (Throwable t) {
                    Log.e(TAG, "import failed", t);
                    act.bridgeResult("onDataResult", "error", Txt.f(act, "导入失败: %s", t.getMessage()), null);
                    act.console.logT("导入失败: %s", t);
                    try {
                        if (act.server == null) {
                            act.runtime.startServer();
                            act.runtime.waitForServer();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "ofone-import").start();
    }

    void openFolder(File dir) {
        final String path = dir.getAbsolutePath();
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                int i = path.indexOf("/Download");
                String docId = i > 0 ? "primary:" + path.substring(i + 1) : "primary:Download/" + DOWNLOAD_SUBDIR + "/logs";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId),
                        "vnd.android.document/directory");
                try {
                    act.startActivity(intent);
                    return;
                } catch (Throwable ignored) {
                }
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallback.setDataAndType(
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"),
                        "vnd.android.document/directory");
                try {
                    act.startActivity(fallback);
                } catch (Throwable t) {
                    act.console.toast(Txt.f(act, "已复制到 下载/%s/logs", DOWNLOAD_SUBDIR));
                }
            }
        });
    }
}