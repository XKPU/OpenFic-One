package cc.openfic.one;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 界面文案：中文原文作键，英文界面返回译文，缺译文时回落中文。 */
final class Txt {
    private static final Map<String, String> EN = new HashMap<String, String>();

    private Txt() {
    }

    static String t(MainActivity act, String zh) {
        if (!"en".equals(Lang.current(act))) return zh;
        String v = EN.get(zh);
        return v == null ? zh : v;
    }

    static String f(MainActivity act, String zh, Object... args) {
        return String.format(Locale.ROOT, t(act, zh), args);
    }

    static boolean known(String zh) {
        return EN.containsKey(zh);
    }

    private static void p(String zh, String en) {
        EN.put(zh, en);
    }

    static {
        p("清华大学", "Tsinghua University");
        p("中科大", "USTC");
        p("阿里云", "Aliyun");
        p("官方 PyPI", "Official PyPI");
        p("自定义", "Custom");

        p("界面语言: %s", "UI language: %s");
        p("界面语言已跟随 OpenFic: %s", "UI language now follows OpenFic: %s");
        p("界面语言已同步: %s", "UI language synced: %s");
        p("界面语言已写入 OpenFic: %s", "UI language written to OpenFic: %s");
        p("界面语言写入 OpenFic 失败: %s", "Failed to write UI language to OpenFic: %s");
        p("OpenFic 尚未初始化，界面语言将在服务启动后同步",
                "OpenFic is not initialized yet; the UI language will sync once the service starts");
        p("exit %d", "exit %d");
        p("界面语言同步失败 (HTTP %s)", "UI language sync failed (HTTP %s)");
        p("界面语言同步失败: %s", "UI language sync failed: %s");

        p("界面主题", "UI theme");
        p("界面主题: %s", "UI theme: %s");
        p("界面主题已跟随 OpenFic: %s", "UI theme now follows OpenFic: %s");
        p("界面主题已设为: %s", "UI theme set to: %s");
        p("界面主题同步失败: %s", "UI theme sync failed: %s");
        p("OpenFic 尚未初始化，界面主题将在服务启动后同步",
                "OpenFic is not initialized yet; the UI theme will sync once the service starts");
        p("跟随系统", "Follow system");
        p("浅色", "Light");
        p("深色", "Dark");

        p("OpenFic One 控制台", "OpenFic One Console");
        p("选择文件", "Choose file");
        p("无法打开系统设置，请手动进入 设置 → 应用 → OpenFic One",
                "Cannot open system settings. Go to Settings → Apps → OpenFic One manually");

        p("解压运行时组件...", "Extracting runtime components...");
        p("备份用户数据...", "Backing up user data...");
        p("解压 Linux 根文件系统 (约 45MB，首次较慢，请勿退出)...",
                "Extracting Linux root filesystem (~45 MB, slow on first run, do not exit)...");
        p("恢复用户数据...", "Restoring user data...");
        p("根文件系统就绪，解压耗时 %d 秒", "Root filesystem ready, extraction took %d s");
        p("注入 One 设置入口...", "Injecting One Settings entry...");
        p("注入未完成（不影响使用）", "Injection incomplete (the app still works)");
        p("注入失败: %s", "Injection failed: %s");
        p("开始安装 OpenFic（首次约 5-30 分钟，请保持前台）",
                "Installing OpenFic (first run takes 5-30 minutes, keep the app in the foreground)");
        p("镜像源: %s", "Mirror: %s");
        p("[1/5] 重建 Python 环境（保留用户数据）", "[1/5] Rebuilding Python environment (user data kept)");
        p("[2/5] 下载 OpenFic 及依赖（约 60MB）", "[2/5] Downloading OpenFic and dependencies (~60 MB)");
        p("[3/5] 安装依赖（约 2-5 分钟）", "[3/5] Installing dependencies (2-5 minutes)");
        p("[1/5] 创建 Python 环境", "[1/5] Creating Python environment");
        p("正在通过 pip 升级 OpenFic（保留用户数据）", "Upgrading OpenFic via pip (user data kept)");
        p("重新注入 One 设置入口", "Re-injecting One Settings entry");
        p("记录版本", "Recording version");
        p("[4/5] 注入 One 设置入口", "[4/5] Injecting One Settings entry");
        p("注入失败（不影响使用）", "Injection failed (the app still works)");
        p("[5/5] 记录版本", "[5/5] Recording version");
        p("依赖安装失败 (exit %d)，详见 %s", "Dependency installation failed (exit %d), see %s");
        p("OpenFic 安装完成", "OpenFic installed");
        p("OpenFic 安装完成: %s", "OpenFic installed: %s");
        p("检测到已在运行的服务，直接复用", "Detected a running service, reusing it");
        p("服务进程已启动", "Service process started");
        p("等待服务就绪...", "Waiting for the service to be ready...");
        p("服务已就绪 http://127.0.0.1:%d", "Service ready at http://127.0.0.1:%d");
        p("服务进程退出 code=%d", "Service exited with code=%d");
        p("服务启动超时", "Service start timed out");

        p("开启", "on");
        p("停止", "off");
        p("关闭", "off");
        p("自动检查 OpenFic 更新: %s", "Auto-check OpenFic updates: %s");
        p("自动检查 OpenFic One 更新: %s", "Auto-check OpenFic One updates: %s");
        p("无限期暂停 OpenFic 更新检查: %s", "Suspend OpenFic update checks indefinitely: %s");
        p("无限期暂停 OpenFic One 更新检查: %s", "Suspend OpenFic One update checks indefinitely: %s");
        p("OpenFic 更新检查暂停至 %s", "OpenFic update checks suspended until %s");
        p("OpenFic One 更新检查暂停至 %s", "OpenFic One update checks suspended until %s");
        p("OpenFic One 更新源: %s", "OpenFic One update source: %s");
        p("内置源（按顺序尝试 %d 个）", "built-in sources (try %d in order)");
        p("无法连接 PyPI，请检查网络", "Cannot reach PyPI, please check your network");
        p("正在更新 OpenFic", "Updating OpenFic");
        p("已取消更新", "Update cancelled");
        p("更新失败: %s", "Update failed: %s");
        p("服务启动失败，请重启应用", "Service failed to start, please restart the app");
        p("无法获取 OpenFic One 版本信息", "Cannot get OpenFic One version info");
        p("请先检查更新", "Please check for updates first");
        p("更新源未提供安装包地址", "The update source did not provide a download URL");
        p("安装包地址为空", "The download URL is empty");
        p("已有下载任务正在进行", "A download is already in progress");
        p("下载 OpenFic One %s ...", "Downloading OpenFic One %s ...");
        p("已取消下载 OpenFic One %s", "Cancelled downloading OpenFic One %s");
        p("已取消下载", "Download cancelled");
        p("正在校验安装包...", "Verifying the package...");
        p("安装包校验失败，请重试", "Package verification failed, please retry");
        p("下载完成: %d 字节", "Download complete: %d bytes");
        p("下载失败: %s", "Download failed: %s");

        p("已设置为永不检查更新，跳过检查", "Update checks are disabled permanently, skipping");
        p("自动检查更新已关闭，跳过检查", "Auto update check is off, skipping");
        p("更新检查已暂停，跳过检查", "Update check is suspended, skipping");
        p("检查 OpenFic 更新...", "Checking for OpenFic updates...");
        p("更新检查失败（网络不可用），继续启动",
                "Update check failed (network unavailable), continuing startup");
        p("最新版本: %s", "Latest version: %s");
        p("已是最新版本", "Already up to date");
        p("已跳过版本 %s", "Skipped version %s");
        p("发现 OpenFic 新版本", "New OpenFic version available");
        p("更新通过 pip 原地升级，保留现有环境，不会删除你的作品与设置。",
                "Updating upgrades in place via pip, keeping the existing environment; your works and settings are kept.");
        p("已关闭更新检查", "Update checks disabled");
        p("已暂停更新检查 %d 天", "Update checks paused for %d days");
        p("本次不更新", "Not updating now");
        p("已无限期暂停 OpenFic One 更新检查，跳过检查",
                "OpenFic One update checks suspended indefinitely, skipping");
        p("OpenFic One 自动检查已关闭，跳过检查", "OpenFic One auto update check is off, skipping");
        p("OpenFic One 更新检查已暂停，跳过检查", "OpenFic One update check is suspended, skipping");
        p("检查 OpenFic One 更新...", "Checking for OpenFic One updates...");
        p("OpenFic One 更新检查失败（网络不可用），继续启动",
                "OpenFic One update check failed (network unavailable), continuing startup");
        p("OpenFic One 最新版本: %s", "Latest OpenFic One version: %s");
        p("OpenFic One 已是最新版本", "OpenFic One is up to date");
        p("已跳过 OpenFic One 版本 %s", "Skipped OpenFic One version %s");
        p("发现 OpenFic One 新版本", "New OpenFic One version available");
        p("已无限期暂停 OpenFic One 更新检查", "OpenFic One update checks suspended indefinitely");
        p("已暂停 OpenFic One 更新检查 %d 天", "OpenFic One update checks paused for %d days");
        p("本次不更新 OpenFic One", "Not updating OpenFic One now");
        p("已选择镜像: %s (%s)", "Mirror selected: %s (%s)");
        p("界面语言 / Language", "Language / 界面语言");
        p("界面语言", "Language");
        p("选择 PyPI 镜像", "Choose a PyPI mirror");
        p("建议在系统设置中允许「OpenFic One」后台运行并忽略电池优化，避免切出应用后进程被回收、再次进入时重新执行初始化检查。",
                "Allow OpenFic One to run in the background and ignore battery optimization, so the process is not killed when you switch away and setup does not restart.");
        p("前往系统设置 ›", "Open system settings ›");
        p("界面语言已设为: %s", "UI language set to: %s");
        p("首次初始化在下载完 Python 包后还需要解压安装，视设备性能可能需要 5 至 30 分钟不等，请保持前台并耐心等待。",
                "After the Python packages are downloaded, first-time setup still has to unpack and install them. Depending on device performance this can take 5 to 30 minutes. Keep the app in the foreground and wait patiently.");
        p("取消更新，直接进入应用", "Cancel update and open the app");
        p("自定义镜像源 ›", "Custom mirror ›");
        p("收起自定义镜像源 ›", "Hide custom mirror ›");
        p("使用该镜像", "Use this mirror");
        p("请填写以 http:// 或 https:// 开头的完整地址",
                "Enter a full URL starting with http:// or https://");
        p("地址中不能包含空格", "The URL cannot contain spaces");
        p("检查 OpenFic One 更新: %s", "Checking OpenFic One updates: %s");
        p("更新源不可用，尝试下一个", "Update source unavailable, trying the next one");

        p("无法清理旧导出文件", "Cannot remove the old export file");
        p("开始导出数据...", "Exporting data...");
        p("导出失败 (exit %d)", "Export failed (exit %d)");
        p("导出失败", "Export failed");
        p("已导出到 %s", "Exported to %s");
        p("导出完成: %s", "Export complete: %s");
        p("导出失败: %s", "Export failed: %s");
        p("无法打开文件选择器: %s", "Cannot open the file picker: %s");
        p("无法在下载目录创建文件", "Cannot create a file in Downloads");
        p("无法写入下载目录", "Cannot write to Downloads");
        p("下载/%s/%s", "Downloads/%s/%s");
        p("无法创建 %s", "Cannot create %s");
        p("日志已下载到 %s", "Log downloaded to %s");
        p("打开日志目录失败: %s", "Cannot open the log folder: %s");
        p("已复制到 下载/%s/logs", "Copied to Downloads/%s/logs");

        p("未检测到 OpenFic 运行环境，需要首次初始化",
                "No OpenFic runtime found, first-time setup required");
        p("已检测到 OpenFic %s", "Found OpenFic %s");
        p("启动失败: %s", "Startup failed: %s");
        p("未授予存储权限，无法写入下载目录",
                "Storage permission denied, cannot write to Downloads");
        p("未授予安装未知应用权限", "Permission to install unknown apps denied");
        p("已取消导入", "Import cancelled");
        p("无法读取所选文件", "Cannot read the selected file");
        p("正在导入数据", "Importing data");
        p("开始导入数据...", "Importing data...");
        p("导入失败 (exit %d)", "Import failed (exit %d)");
        p("导入失败，恢复原服务", "Import failed, restoring the previous service");
        p("导入完成，正在重启服务", "Import complete, restarting the service");
        p("导入完成，重启服务", "Import complete, restarting the service");
        p("导入失败: %s", "Import failed: %s");
        p("正在取消...", "Cancelling...");

        p("正在下载 OpenFic One %s", "Downloading OpenFic One %s");
        p("准备中...", "Preparing...");
        p("取消下载", "Cancel download");
        p("服务器返回 %d", "Server returned %d");
        p("已下载 %s / %s", "Downloaded %s / %s");
        p("已下载 %s", "Downloaded %s");
        p("安装包不存在", "The package does not exist");
        p("无法打开安装权限设置: %s", "Cannot open install permission settings: %s");
        p("无法启动安装器: %s", "Cannot launch the installer: %s");

        p("当前版本 %s，最新版本 %s。", "Current version %s, latest version %s.");
        p("立即更新", "Update now");
        p("跳过此版本（%s）", "Skip this version (%s)");
        p("在", "Pause for");
        p("日内不再询问", "day(s)");
        p("确定", "OK");
        p("已设置 %d 天内不再检查更新", "No update checks for the next %d days");
        p("永不更新（关闭更新检查）", "Never update (disable update checks)");

        p("OpenFic 通知", "OpenFic notifications");
        p("已取消选择", "Selection cancelled");
        p("尚未选择备份文件", "No backup file selected");
        p("读取文件失败: %s", "Failed to read file: %s");
        p("无法清理旧导出文件", "Cannot clear the previous export file");
        p("无法在下载目录创建文件", "Cannot create file in Downloads");
    }
}