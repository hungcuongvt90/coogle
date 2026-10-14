# 原生双击电源钱包入口：本地审查

日期：2026-10-14。接续 29065f4 的钱包权限修复及界面收尾；按用户要求恢复更上游的原生入口。没有推送 GitHub、修改 README、更新已发布 Release 或重启手机。

## 设备与链路依据

国行 PLK110 的当前 services.jar/oplus-services.jar 已通过 ADB 提取。PhoneWindowManager 将真实电源 KeyEvent 交给 GestureLauncherService；后者已有原生钱包选择与 handleWalletGesture，经过 user_setup_complete 判断后通知 SystemUI。原生手势时间判断、紧急呼叫条件及欧加 isCameraGestureEnabled 门控均保持原样。

实机只读结果：config_doubleTapPowerGestureMode 为 2，launch_wallet_option_on_power_double_tap 和 launch_wallet_via_sysui_callbacks 均为 enabled/read-only；原生手势 enabled 为 0、action 未设置。无需更改系统 flag 或绕过认证即可使用已有入口。反编译资源编号被 JADX 错误命名为 fingerprintMaxTemplatesPerUser 等，实现使用平台真实资源名称，不使用这些错误符号。

已提取的国际版一加 13 SystemUI 回调为 onWalletLaunchGestureDetected → onPowerButtonLaunchGestureTriggered(LAUNCH_WALLET_ON_GESTURE, -1)；后续由原生 QuickAccessWalletClient 的手势 PendingIntent、ActivityStarter 及锁屏逻辑处理。框架链路参考：[Android 16 GestureLauncherService](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/services/core/java/com/android/server/GestureLauncherService.java)。设备实际门控仍以当前固件为准，不宣称不同固件所有代码逐字一致。

## 最终实现

- 开启钱包时，通过既有按需 Root 通道检查原生配置模式与两个 flag，设置当前 Android 用户的 double_tap_power_button_gesture=1、double_tap_power_button_gesture_enabled=1，并读回验证。先选择钱包再启用，避免短暂选中相机。缺少原生支持时拒绝操作并回滚默认付款选择。
- 记录原有两项设置；关闭时只在钱包选择仍由模块管理的情况下恢复原值。保留用户后来在系统设置中选择的其他目标，原本缺失的键用 delete 恢复。备份保存在 APK 私有数据，关闭恢复成功后才清除。
- 系统设置、默认付款选择及 LSPosed 开关保存构成可回滚操作。部分写入、读回不一致、配置保存失败均尝试回滚；原生恢复也失败时保留恢复备份。
- 删除 OplusWalletController.startWallet → SystemUI 的模块转发。唯一钱包系统 Hook 是 isQuickLaunchSupport 的后置检查：原结果为 true、钱包开关已开启且当前用户原生钱包已选中时，阻止欧加旧钱包路径重复触发。原方法仍执行一次；关闭功能、原本不支持或原生目标不是钱包时保持原结果。桥接层的既有保护保留异常前的原结果。
- 钱包功能仅申请 system 作用域。SystemUI、Google Wallet、GMS 的认证、PendingIntent、付款流程都由原有代码运行；模块不 Hook 钱包包，不拦截 requestDismissKeyguard，不自行返回认证成功。其他功能所需 SystemUI 作用域仍保留。

这是持久化的系统手势选择。关闭 Coogle 钱包开关会恢复备份；仅停用 LSPosed 模块不会撤销系统设置。卸载或清除 APK 数据会丢失私有备份，所以需要恢复原选择时应先关闭钱包开关。没有在应用启动时静默改动系统设置或请求 Root。

## 验证

新增三个执行生产逻辑的测试：原生选择与备份/恢复、实际生成 Shell 命令的隔离执行、实际 Kotlin Hook 的原生选择/当前用户/原方法结果保护。覆盖当前用户 10、相机原选择、缺失键、外部设置变化、重复开启、部分写入、读回失败、保存失败、恢复失败备份保留、缺少支持、非法设置内容和开关关闭不干预。

新增回滚用例先在丢失恢复备份的实现上失败，修复后通过。移除读回比较的隔离变异被测试拒绝，生产文件随后恢复。所有既有回归及 Release 构建通过；Lint 为 0 错误、27 个既有警告。主要日志：build/wallet-upstream-{recovery-red,recovery-green,readback-mutation,full,lint}.log。

最终 DEX 含原生选择及设置路径，不含 WalletPreviewHooks、WalletPreviewBinding、模块 onWalletLaunchGestureDetected 转发或 requestDismissKeyguard。签名证书与上一测试包一致。附属模块源代码未变，内嵌 ZIP 逐字节等于 Coogle-Companion-controls-test.zip，SHA-256：548e5c2b69f7a164c08c425d8d3dbf7f512ca96a0726a316239f5bbbab419c5a。

测试 APK：工作区 outputs/Coogle-wallet-upstream-test.apk，1.3.3 / 133，2,816,729 字节，SHA-256：7ea59553c2a5940253051437800063b16156ddc26e23a4c49c91db050f4d1caf。已通过 ADB 覆盖安装，安装时间 2026-10-14 09:52:58。安装后只读核对原生设置仍为 0/null；没有发送手机手势、自动重启或代替用户切换钱包设置。

## 待完成的实机验收

已开启钱包的旧配置不会仅因覆盖安装自动请求 Root 或改写系统手势；用户需在 Coogle 将钱包关闭再开启一次，并手动重启手机，让 system_server 清除旧版转发 Hook。保持 system 作用域。

之后核对原生设置为 1/1，框架日志加载的是 native Wallet duplicate-route guard，测试息屏锁定、刚完成认证和认证失效等场景的双击行为。取消认证后，不应被模块当作认证成功；恢复关闭后应回到原选择。不要用连续快速电源按键触发紧急呼叫来验证普通钱包双击。

截至本次安装后记录，未收到用户重启完成反馈，因此原生双击、锁屏状态及恢复关闭的实机验证仍待完成。用户暂时没有收款终端；显示钱包或“靠近读卡器”不能证明真实交易通过或被绕过。当前证据支持模块保留原生认证路径，不等于已经完成支付安全性的实机证明。
