# 国行原生钱包手势门控修复

日期：2026-10-14，基于 4e0b00f。用户反馈原生设置成功后双击仍不能呼出钱包；同时保留直接矢量图标、开启时自动选择 Google 默认付款及此前 Root 命令修复。

## 根因依据

用户重新连接 PLK110 后，ADB 确认系统已在约 13:31 重启，钱包手势设置为 1/1，LSPosed 启动日志加载了上一版 native Wallet duplicate-route guard。此前双击不是旧系统进程未重启导致。

13:48:18 的物理电源 DOWN 间隔约 207 毫秒，但系统均按单次电源处理，没有 GestureLauncherService 的钱包双击日志。pm has-feature oplus.software.power_button_redefine 和 oplus.software.sos_special_function 均为 false；SOS 自定义次数未设置。

当前固件 PhoneWindowManager.handleCameraGesture 在交给 GestureLauncherService 前调用欧加 isCameraGestureEnabled。该原方法为 mShutdownChordEnabled && sSosPowerKeyDownCount != 3，mShutdownChordEnabled 在初始化时读取 power_button_redefine 能力；此前移植未补齐这个上游区域门控，仅配置原生目标并抑制旧入口，因此国行仍无法进入原生识别。已用当前设备 services.jar 的 fallback 反编译检查实际双击分支，不把 JADX 未反编译的方法占位异常当作运行代码。

## 修改范围

在原生 isCameraGestureEnabled 执行后的返回值上，仅当原结果 false、模块钱包已开启、mShutdownChordEnabled=false、SOS 次数不是 3，且原生 isWalletDoubleTapPowerSettingEnabled 对当前策略用户为 true 时放行。原结果 true、功能关闭、未选中原生钱包、三次 SOS、其他原生拒绝或上下文/查询异常均保留原结果。没有修改原始电源组合、SOS 字段或全局能力声明。

抑制 OplusWalletController 的旧钱包重复路径与新门控作为同一组安装；任一步失败时回滚该组，保留系统原行为。模块只 Hook 两项资格判断。按键事件、300ms 原生识别、user_setup_complete、SystemUI 手势 PendingIntent、锁屏/钱包/GMS 认证仍由原有代码处理；没有重新加入钱包应用 Hook 或模块直接启动钱包的分支。

门控作用于通用的原生钱包目标，并非只限定 OPPO 或只硬编码 Google。开启时自动设置 Google 为默认付款；之后用户若自行更换系统默认钱包，原生目标遵循系统选择。关闭功能后恢复原生设置备份，并让门控保持原结果。

## 验证

- 新测试先在缺少门控的旧实现上失败。执行实际 Kotlin Hook，覆盖原方法执行一次、当前用户、区域能力缺失、功能关闭、未选中钱包、已有放行、其他拒绝、三次 SOS、字段值不变、上下文缺失及查询异常。
- 临时移除 SOS 排除的变异被测试拒绝，生产代码已恢复。钱包包隔离、Hook 安装回滚及全部既有回归通过；Release 构建成功，Lint 为 0 错误、27 个既有警告。
- APK 包含区域门控与 SOS 判断，不含 requestDismissKeyguard 或模块 onWalletLaunchGestureDetected 转发；签名验证通过，内嵌附属模块逐字节不变。
- 已安装测试包；用户手动重启后，14:03:00 的 LSPosed 日志显示 Installed native Wallet region gate and duplicate-route guard。14:03:38 和 14:03:44 的 GestureLauncherService 日志分别识别 160ms、168ms 双击，launchCamera=false、launchWallet=true，并执行 launching wallet。后续系统日志确认 Google Wallet PresentationModeActivity 由系统/GMS PendingIntent 启动。实机双击呼出修复已验证。

测试 APK：工作区 outputs/Coogle-wallet-native-gate-test.apk，1.3.3 / 133，2,834,750 字节，SHA-256：c9f1cfd312d92689252b27e3e45e9fb0b71216bba373d17fd4f23cb60c75aed1。设备安装时间 14:02:02。主要日志为 build/wallet-native-gate-{red,green,sos-mutation,full,lint,device-keys,device-hooks}.log，均不打入 APK。

## 认证反馈及范围

用户反馈每次出现系统指纹界面，钱包内没有“验证是本人”提示，随后表示可忽略。系统记录 isKeyguard=true、isBiometricPrompt=false；模块代码没有主动请求或跳过认证。已停止继续调整认证频率，保留系统行为。当前固件与已提取国际版 SystemUI 的钱包回调均交给 getGestureTargetActivityPendingIntent；不宣称两种固件、Google Wallet/GMS 版本或认证缓存行为完全相同。

用户暂时没有付款终端，本次成功呼出和资格回归不等于真实交易或防盗刷实测。未改首页文案、附属模块或已发布 Release，未推送 GitHub，未代用户重启设备。
