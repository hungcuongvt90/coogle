# 钱包原生验证回退：本地审查

日期：2026-10-14。基于本地设置界面更新 649287a。用户要求保留双击电源呼出钱包，回退到不 Hook Google Wallet 应用、允许原本指纹验证的实现。Issue #7 的 ColorOS 实际付款影响尚未证实；本次按用户要求移除预览解锁请求拦截，并不将界面显示等同于已绕过付款验证。

## 修改

- 移除 CoogleModule 对 Google Wallet 包的 Hook 分发，删除 WalletPreviewHooks、WalletPreviewBinding 及专用 DexKit 查询。模块不再拦截钱包的锁屏解锁请求或返回 Kotlin Unit。
- 钱包功能仅要求 system 和 com.android.systemui 作用域，声明列表取消钱包包名。旧用户仍手动勾选钱包作用域时，包初始化也不触碰钱包内部代码。
- 保留 OplusWalletController → StatusBarManagerInternal.onWalletLaunchGestureDetected 的原生系统侧呼出路径。钱包/系统自行处理原本的验证请求；不额外插入认证窗口。
- 默认付款设置、设置界面、相机和实时翻译不变；附属模块与 APK 内嵌 ZIP 不变。版本仍为本地测试用 1.3.3 / 133，未发布或推送。

## 验证

- 新 WalletPackageIsolationTest 执行实际 CoogleModule 包初始化，在 ColorOS 17 条件下覆盖钱包开关开/关；旧代码因尝试取得钱包 ClassLoader 而失败，移除分发后通过。
- 作用域回归检查钱包只申请系统框架/SystemUI。DexKit 回归保留 Lens 缓存、原生相机旧缓存迁移和 APK 更新失效覆盖，并确认已退休的钱包定位入口无论有无旧缓存都拒绝扫描。
- 移除退休功能前置检查的隔离变异被回归测试拒绝，生产文件已恢复。
- APK 既有完整回归均通过，包括默认付款设置/读回/回滚、作用域申请、附属模块升级、圈定服务、相机与 Lens、热词 UID、壁纸入口和 Hook 生命周期。
- Release 构建成功；Lint 为 0 错误、27 个既有警告。APK 内作用域不含钱包包名，DEX 不含钱包预览 Hook、QUICKDRAW 定位字符串或 requestDismissKeyguard 拦截定位字符串。内嵌附属模块逐字节等于上一轮 controls-test ZIP；签名证书不变。
- 本地测试 APK：outputs/Coogle-wallet-native-test.apk，2,816,729 字节，SHA-256 `40b8311581ed07b94a8aac8262c4bc521854377c277d0b501a46baa20fd3c837`。日志保存在 build/wallet-native-{red,green,mutation,full,lint}.log。

## 实机验收

安装后重启设备，确保旧钱包进程中的 Hook 不再保留。LSPosed 保留 system/SystemUI 作用域；钱包应用作用域可以取消。息屏锁定后双击电源应仍能呼出钱包，真实指纹/密码提示的时机以钱包和系统原本行为为准；取消认证不应被模块当作认证成功。本次 ADB 未连接，未验证实际指纹提示或付款终端行为；显示卡片/靠近读卡器本身不证明可完成付款。
