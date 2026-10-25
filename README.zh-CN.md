# Coogle

[English](README.md) | [简体中文](README.zh-CN.md)

[![Build release APK](https://github.com/zopulus/coogle/actions/workflows/build.yml/badge.svg)](https://github.com/zopulus/coogle/actions/workflows/build.yml)

让国行版 ColorOS 更加 Google。支持 ColorOS 16 与 17。

## 功能

- **Hey Google**：补全国行系统的 Google 语音唤醒支持。
- **Google 智能镜头**：补全 Google 智能镜头功能入口。
- **系统导航方式**：补全设置中的导航选项，支持圈定即搜和左/右下角向内滑动呼出数字助理。
- **Google 搜索框**：启用桌面底部 Google 搜索框。
- **Google 钱包**：支持双击电源键呼出 Google 钱包卡片，需将 Google 钱包设置为默认付款应用。
- **锁屏快捷入口**：补全 Google 数字助理、电子钱包、扫描二维码和家居控制。
- **控制中心**：补全原生钱包、二维码扫描入口。
- **移除 GMS 对国行的使用限制**。
- **设置底部显示 Google 服务**。

## 安装

要求：已 Root 的设备（KernelSU）、兼容 LSPosed 的框架（LSPosed、Vector 或其他 libxposed 实现），以及 ColorOS 16 或 17。

1. 安装正式版 APK。APK 已内置附属模块，无需单独下载。
2. 在框架管理器中启用 Coogle，并批准作用域请求。
3. 打开 Coogle 并在 KernelSU 中授予 Root 权限；在状态卡片中确认附属模块的安装。
4. 重启一次，使附属模块的载荷（Google 固化、语音注册组件、实时翻译配置）完成挂载，然后按需开启功能。

## Coogle 附属模块

整合 Google 系统应用固化、Hotword 注册组件、DSP 唤醒支持和圈定即搜实时翻译配置。

开启 Hey Google 时自动检查附属模块。

Google 固化与圈定即搜实时翻译配置默认开启，语音组件由 APK 开关同步控制；关闭 Hey Google 后，重启即停止挂载语音注册/DSP 配置，并跳过模型初始化。首次安装或从旧菜单版升级时，语音支持默认关闭，避免未与 APK 开关同步就执行初始化。

## 构建

参见 [BUILDING.md](BUILDING.md)。GitHub Actions 在每次推送时构建内嵌附属模块的正式版 APK，并在版本标签上发布，见 [Releases](https://github.com/zopulus/coogle/releases) 页面。

## 致谢

部分代码参考 [ONextBox](https://github.com/MiToverG422/ONextBox) 和 [LuckyTool](https://github.com/luckyzyx/LuckyTool)。

UI 参考 [KernelSU](https://github.com/tiann/KernelSU) 和 [LSPosed](https://github.com/LSPosed/LSPosed)。

感谢以上项目的开发者与贡献者。

作者：**zopulus**\
开源协议：**GPL-3.0**
