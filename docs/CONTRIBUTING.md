# 参与贡献

项目的一般说明见 [README.md](../README.md)，使用与缺陷报告见 [SUPPORT.md](SUPPORT.md)，安全政策见 [SECURITY.md](SECURITY.md)。

## 目录

- [我可以做什么](#我可以做什么)
- [开发环境](#开发环境)
- [构建](#构建)
- [提交规范](#提交规范)
- [提交前自查](#提交前自查)
- [许可](#许可)

## 我可以做什么

- **缺陷报告**：按 [SUPPORT.md](SUPPORT.md) 的模板提交 Issue，附上 Android 版本与日志片段。
- **功能建议**：说明使用场景与期望行为，不必附实现。
- **Pull Request**：欢迎任何大小的改动。**未完成或未经测试**的提交同样欢迎，请在描述里写明状态与未验证的部分。

本仓库不归档上游源码，`upstream/` 只有一份指向上游仓库的说明（见 [`upstream/README.md`](../upstream/README.md)）。需要读上游实现时按需 `git clone`，不要提交进本仓库。

不接受的改动：提交上游源码副本、构建产物或大体积缓存。

## 开发环境

| 依赖 | 版本 / 说明 |
| --- | --- |
| 系统 | Windows + MSYS Bash（Git for Windows 自带） |
| JDK | 17（`javac`/`java`，`apksigner` 需要） |
| Android SDK | `build-tools/36.0.0`、`platforms/android-37` |
| Python | 3.12 或 3.13，需 `zstandard`、`Pillow` |
| Node.js | 可选，仅用于 `node --check tools/share/ofone-bridge.js` 语法自检 |

`tools/build_apk.sh` 里**不含任何绝对路径**，工具位置按以下顺序解析：

1. 环境变量
2. 仓库根目录下的 `local.properties`（从 [`local.properties.example`](../local.properties.example) 复制）
3. PATH 上的命令

| 需要 | 环境变量 | `local.properties` 键 |
| --- | --- | --- |
| Android SDK | `ANDROID_SDK_ROOT` / `ANDROID_SDK` / `ANDROID_HOME` | `sdk.dir` |
| build-tools 版本 | `BUILD_TOOLS` | `build.tools` |
| 编译用 platform | `COMPILE_SDK` | `compile.sdk` |
| JDK | `JAVA_HOME` | `java.home` |
| Python | `PYTHON` | `python` |
| 签名密钥库 | `OFONE_KEYSTORE` | `keystore` |
| 密钥别名 | `OFONE_KEY_ALIAS` | `keystore.alias` |
| store 口令 | `OFONE_STOREPASS` | `keystore.storepass` |
| key 口令 | `OFONE_KEYPASS` | `keystore.keypass` |

未指定 `build-tools` / `platform` 时自动取已安装的最高版本。

本工程**不使用 Gradle**，`tools/build_apk.sh` 直接调用 `aapt` → `javac` → `d8` → `zipalign` → `apksigner`。

## 构建

```bash
# 1. 下载基础素材（Ubuntu base rootfs、proot 等）
python tools/fetch.py

# 2. 解析并下载 Python 运行时的 deb 依赖闭包
python tools/fetch_debs.py

# 3. 生成精简 rootfs
python tools/mkrootfs.py
python tools/mkruntime.py

# 4. 把 proot 与 rootfs 放进 assets
python tools/stage_assets.py

# 5. 编译并签名 APK
bash tools/build_apk.sh
```

首次执行第 1、2 步需要下载数百 MB 素材。

第 4 步除 proot 与 rootfs 外，还会把 `tools/share/` 下的注入脚本复制到 `app/assets/share/`，随 APK 一起分发。

若 `python` 不在 PATH 上，用 `PYTHON` 环境变量或 `local.properties` 的 `python=` 指定解释器。

`build_apk.sh` 开启了 `pipefail`，编译错误不会被后续管道吞掉。产物固定输出到 `out/OpenFicOne.apk`。

## 提交规范

- 首行一句话概括，必要时在正文分点说明动机与影响，例如：
  ```
  为无前缀 CSS `mask` 注入样式覆盖
  ```
- 一个提交只做一件事；格式化改动与逻辑改动分开。

## 提交前自查

- [ ] `bash tools/build_apk.sh` 能在干净环境下跑通
- [ ] `aapt dump badging out/OpenFicOne.apk` 的 `versionName` 与 `application-label` 符合预期
- [ ] 真机或模拟器上首次启动能走完安装流程并显示界面
- [ ] 覆盖安装后数据仍在
- [ ] 没有把调试密钥、日志或个人数据带进提交

## 许可

本项目基于 **GNU General Public License v3.0 Or Later (GPL-3.0-or-later)** 发布。

```
OpenFic One - Android 上的 OpenFic 启动器。
Copyright (C) 2026 K_PU

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```