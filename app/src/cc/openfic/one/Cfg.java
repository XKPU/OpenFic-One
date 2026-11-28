package cc.openfic.one;

/** 全局常量：端口、内置更新源、PyPI 镜像、请求码与界面文案片段。 */
final class Cfg {
    private Cfg() {
    }

    static final String TAG = "OpenFicOne";
    static final String ROOTFS_STAMP = "ofone-rootfs-1";
    static final int PORT = 8000;
    static final int LANG_WATCH_MS = 5000;
    static final String DEFAULT_MIRROR = "https://pypi.tuna.tsinghua.edu.cn/simple";
    static final String OFFICIAL_PYPI = "https://pypi.org/simple/";
    static final String PYPI_JSON = "https://pypi.org/pypi/openfic/json";
    static final String[] DEFAULT_APP_UPDATE_APIS = {
            "https://gh-proxy.org/https://raw.githubusercontent.com/XKPU/OpenFic-One/main/update.json",
            "https://cdn.jsdmirror.com/gh/XKPU/OpenFic-One@main/update.json",
            "https://cdn.jsdelivr.net/gh/XKPU/OpenFic-One@main/update.json",
            "https://raw.githubusercontent.com/XKPU/OpenFic-One/main/update.json"
    };
    static final String[][] MIRRORS = {
            {"清华大学", "https://pypi.tuna.tsinghua.edu.cn/simple"},
            {"中科大", "https://pypi.mirrors.ustc.edu.cn/simple/"},
            {"阿里云", "https://mirrors.aliyun.com/pypi/simple/"},
            {"官方 PyPI", "https://pypi.org/simple/"},
    };

    static final int UPDATE_ACCEPT = 0;
    static final int UPDATE_SKIP_VERSION = 1;
    static final int UPDATE_SNOOZE = 2;
    static final int UPDATE_NEVER = 3;
    static final int UPDATE_LATER = 4;

    static final int REQ_IMPORT_ZIP = 9001;
    static final int REQ_OPEN_FOLDER = 9002;
    static final int REQ_STORAGE = 9003;
    static final int REQ_INSTALL_PERM = 9004;
    static final int REQ_FILE_CHOOSER = 9005;
    static final int REQ_NOTIFY = 9006;
    static final String DOWNLOAD_SUBDIR = "OpenFic";

    static final String BRAND_FIX_CSS =
    ".sidebar-brand-logo{-webkit-mask:none!important;mask:none!important;"
    + "background-color:transparent!important;background-image:url(/openfic.svg)!important;"
    + "background-repeat:no-repeat!important;background-position:50% 50%!important;"
    + "background-size:contain!important}"
    + ".sidebar-brand-wordmark{-webkit-mask:none!important;mask:none!important;"
    + "background-color:transparent!important;background-image:url(/brand.svg)!important;"
    + "background-repeat:no-repeat!important;background-position:-28.12px 0!important;"
    + "background-size:108.4px 24px!important}"
    + ".auth-brand{-webkit-mask:none!important;mask:none!important;"
    + "background-color:transparent!important;background-image:url(/brand.svg)!important;"
    + "background-repeat:no-repeat!important;background-position:50% 50%!important;"
    + "background-size:contain!important}";
}
