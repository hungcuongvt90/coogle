# 模块控制界面与附属状态：本地修改记录

日期：2026-10-14。基于本地作用域申请和相机智能镜头共用开关的 44227e4。用户选择保留按需 Root，并要求加入钱包开启时的一次性默认付款设置，以及独立安装和重启入口。未授权发布本轮修改。

## 最终行为

- 左上角安装图标打开附属模块安装/更新弹窗。安装保留原有 Hey Google 选择，不自动开启它。Hey Google 开关和后台检测都不会触发安装弹窗。
- 右上角重启图标打开作用域重启弹窗。普通应用通过原生关闭操作重载，下次打开时加载新 Hook；SystemUI 单独重载。系统框架及 Hey Google 组件配置使用独立的设备重启确认，应用作用域操作不结束 system_server。
- 激活卡片原版本号位置显示附属模块状态。正常状态为未安装、已安装并启用、已安装未启用、已安装请重启设备。尚未检测、检测失败和需要升级作为单独的未确认/异常提示，不冒充已安装或未安装。
- Hey Google 依赖附属模块可用状态：未安装、未确认、未启用或版本不兼容时置灰。未安装/未确认时小字为“需点击左上角安装附属模块”；已安装时为“补全系统级 Voice Match 唤醒功能”。
- Google 钱包只在用户关闭后再开启时设置一次默认付款；启动、恢复前台和附属模块检测不设置付款默认值。已是有效 Google 付款服务时跳过 Root 设置。设置或偏好保存失败时恢复之前的选择；关闭钱包开关不修改系统付款选择。

## 状态与性能

冷启动使用中性连接状态，已有开关使用上次确认的缓存；没有缓存时使用无开启/关闭含义的占位。绑定服务并读取完成后一次更新，不缓存“已激活”作为事实。页面读取配置变更通知，合并刷新并在销毁时注销，覆盖后台保存结束和界面重建的情况。

附属模块检查保留上次确认结果，检测失败只标注失败及重试入口。首次从未确认模块时不自动请求 Root；可以通过模块状态手动检测。已知模块在返回前台时进行一次轻量查询，不执行 dumpsys，也不通过检查重写 Hey Google 选择。一般设置与 Root 操作使用独立的串行队列，避免 Root 授权等待拖慢其他开关。

安装/挂载状态不再比较 features.conf 与 applied-features.conf 的 Hey Google 差异。附属模块在本次启动成功准备后写入 applied-boot-id，APK 比对当前启动标识，并检查 KSU 启用/移除及准备失败标记。安装/更新后使旧启动记录失效。安装过程保留 KSU 禁用状态，并将实际结果返回界面，不能一律标记成待重启。Google/Voice Match/DSP/实时翻译的运行细节继续由 KSU 简介报告。

增加 payloadRevision=3，controlVersion 仍为 1。旧 1.3.2 及已发布 1.3.3 的 payloadRevision=2 会被识别为需要更新，避免缺失启动记录时错误显示已启用。

## 权限与实现边界

LSP Hook 本身仍不需要 Coogle APK 单独获得 Root。附属模块管理和精确状态检测、实际修改默认付款应用、重启作用域/设备使用按需 Root。拒绝授权不阻断其他 LSP 设置，也不把状态判为未安装。

默认付款优先使用系统 Wallet role；没有该 role 时仅选择声明付款类别的有效 Google HCE 服务，不硬编码混淆后的服务类名。不绕过 role 资格检查，也不改变钱包的支付身份验证代码。

主要接口依据：[AOSP RoleShellCommand](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/service/java/com/android/role/RoleShellCommand.java)、[WalletRoleBehavior](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/role-controller/java/com/android/role/controller/behavior/v35/WalletRoleBehavior.java)、[Android HCE 文档](https://developer.android.com/develop/connectivity/nfc/hce)。

## 验证与范围

APK 的全部既有回归及新增状态、作用域重启、钱包设置/回滚和实际 Shell 命令测试通过。附属模块 35 项测试通过，包含普通 KernelSU/NoMount 和启动标识记录。原文与译文渲染、相机原生资格、付款身份验证流程没有重写。

钱包读回验证删除、启动状态比较反转的两个隔离变异都被测试拒绝，生产代码未留下变异。Windows Shell 测试使用脚本文件，保留引号与特殊字符的原意；不会执行设备重启、付款设置或真实应用关闭。

构建成功，签名沿用现有证书；保留现有目标 Android 版本和未经压制的检查。版本号暂仍为 1.3.3/133，本轮为独立本地测试包，包含新附属 ZIP。ADB 离线/未连接，因此未完成这轮真实授权弹窗、启动画面、默认付款修改和作用域重启的实机验证。GitHub 首页、既有 Release 和远端分支均不修改。
