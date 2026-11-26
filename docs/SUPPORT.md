# 支持

项目的一般说明见 [README.md](../README.md)，参与开发见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 目录

- [报告缺陷与建议](#报告缺陷与建议)
- [常见问题](#常见问题)
- [安全](#安全)
- [参与贡献](#参与贡献)
- [许可](#许可)

## 报告缺陷与建议

缺陷报告、功能建议与使用疑问，请提交到 GitHub Issues：

[https://github.com/XKPU/OpenFic-One/issues](https://github.com/XKPU/OpenFic-One/issues)

### 一份有用的缺陷报告通常包含

1. **你做了什么** —— 导致问题的操作步骤。
2. **预期结果** 与 **实际结果**。
3. **环境** —— OF-One 版本（控制台首行形如 `OF-One 1.0.0`）与 OpenFic 版本（「One 设置」面板或页脚）、Android 版本、机型。
4. **启动日志**（可选） —— 控制台页面的滚动输出，截图即可。

> **请勿附带含有 API Key 或个人数据的日志或配置文件。**

## 常见问题

**首次启动停在「正在解压并安装」，很久没动静**

首次启动需要联网从 PyPI 下载 `openfic` 及其依赖，视网络情况约 2-5 分钟。若超过 10 分钟仍无进展，多半是网络不通，请检查能否访问 PyPI 镜像后重启应用；已下载的部分会复用缓存。

**应用需要 root 吗**

不需要。应用通过 proot 在自身私有目录内运行一个精简 Ubuntu 根文件系统。

**怎么换 PyPI 镜像**

镜像只在首次初始化和选择更新时询问，选择结果会被记住。想重新选择，在「One 设置」里点「立即更新 OpenFic」，选择镜像时就会重新出现。

**不想每次启动都被问更新**

更新面板里有三种免打扰方式：「跳过此版本」只忽略当前这个版本；「在 N 日内不再询问」在指定天数内不再检查；「永不更新」彻底关闭更新检查。之后可在「One 设置」里重新开启，或手动点「检查」。

**切出应用后回来，又开始重新初始化检查**

多半是系统回收了后台进程。请按首次启动时小字提示，在系统设置里允许 OF-One 后台运行并忽略电池优化；控制台页面顶部有直达该设置的链接。

**「One 设置」在哪里**

打开 OpenFic 的设置弹层，分类列表（桌面侧栏或移动端的分类页）最后一项就是「One 设置」，样式与原生项一致。若当前版本前端结构变动导致插入失败，会退化为右下角的圆形「One」按钮。

**更新会不会丢作品**

不会。更新只重建 `/opt/venv`；重建根文件系统前会把 `/root/.openfic` 备份到 `files/share/_backup_openfic`，解压后自动恢复。

## 安全

发现安全问题请**不要**开公开 issue。可通过以下渠道私下报告：

- GitHub 安全公告：[https://github.com/XKPU/OpenFic-One/security/advisories/new](https://github.com/XKPU/OpenFic-One/security/advisories/new)
- 邮箱：`csaxongmail@gmail.com` 或 `u-eptm@u-eptm.top`

更详细的内容见 [SECURITY.md](SECURITY.md)。

## 参与贡献

欢迎提交 Pull Request 或参与 Issue 讨论，开发环境、构建步骤与提交规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。

**未完成或未经测试的提交**同样欢迎，请在提交时说明其状态。

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