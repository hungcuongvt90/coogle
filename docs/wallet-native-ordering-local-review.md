# 原生钱包锁屏消息顺序修复

日期：2026-10-14；基于本地 e0e1fe3。用户要求国行尽可能对齐国际版的呼出过程和效果。本次完成锁屏衔接修复；实机对照仍是验收条件，不以单元测试宣称完全一致。

## 直接依据

已读取两台手机当前的 services.jar、oplus-services.jar 和 SystemUI。电源识别、StatusBar 钱包通知、QuickAccessWalletController 回调及锁屏 reset 的关键判断一致。两台的 launchWalletOptionOnPowerDoubleTap 与 launchWalletViaSysuiCallbacks 均为 true；通过欧加框架自身查询，power_button_redefine 在国际版为 true、国行为 false。普通 pm has-feature 对这项内部能力在两台均返回 false，不能据此判定欧加框架的运行值。

国行连续日志捕获 29 次双击。典型失败为 15:26:36.006 钱包请求解除锁屏、36.029 原生 reset(true) 发送 notifyDismissCancelled、36.365 钱包通过 finishAndRemoveTask 自行关闭。源码中原生 reset 会取消已显示认证界面的请求，Wallet 的取消回调会收起页面。因此仅补电源资格没有解决请求先于锁屏覆盖状态更新的竞态。

钱包主 APK 在两台字节相同，国际版包为系统应用、国行为普通应用，GMS 版本不同；尚未证明这些差异直接造成重复认证，不据此系统化钱包、降级 GMS 或修改认证结果。

## 最小修改

继续使用已有原生电源识别与钱包回调。在 SystemUI 的实际 KeyguardViewMediator Handler 收到原生 DismissMessage 时，仅限以下条件才短暂保留消息：模块钱包开启，真实 callback 非空，锁屏正在显示且未被覆盖，当前用户顶部任务是 Google Wallet 的 QUICKDRAW。

使用 Message.obtain 保存原消息。原生 StatusBarKeyguardViewManager.reset 完成且覆盖状态已生效后，将副本交回同一个原生 Handler。原始 callback、参数、数据和目标均保留。没有生成成功/取消结果，没有跳过系统或 Google 的认证判断，没有 Hook 钱包应用。

队列限 8 个请求；没有状态事件时 500ms 兜底交回原流程，正常路径不固定等待。单次身份标记避免副本再次排队；旧计时器不能释放新批次。开关关闭或用户切换时，已接受的消息仍交给原生处理器决定。调度失败、查询异常、结构不匹配保留原流程；发送失败记录日志并清理副本，不伪造认证结果。普通 Keyguard 消息只做类型检查，不读取偏好或查询任务。

钱包作用域增加系统界面，保留 system；不增加 Google Wallet/GMS 应用作用域。新增组采用原子安装，任一步失败则回滚。本次未改首页/UI 文案、附属模块 payload 或版本号。

## 回归与审查

- 独立测试先用立即转发的旧行为 fixture 复现 RED，再在实际队列上 GREEN；立即转发变异被拒绝。
- 实际 Kotlin Hook 测试先证明原处理器早于 reset 会被取消，再验证完整消息在 reset 后仅处理一次。覆盖真实成功和取消、其他包/动作/用户、关闭功能、已覆盖/未锁定、无 callback、消息数据、500ms 兜底、排程/发送失败、查询和反射异常、用户切换与 reset 失败。
- 完整应用回归、Release 构建与 Lint 通过。Lint 为 0 错误、27 个既有警告；保留 getRunningTasks 的编译弃用提示，此处运行于具有 REAL_GET_TASKS 权限的 SystemUI，用于识别当前实际任务。
- 构建中发现测试 Bundle stub 丢失原有整数接口，已恢复；未删除或弱化原回归。
- 独立测试作者审查和主代理审查未发现正常主线程路径中请求重复、认证结果伪造或无界队列。实机系统时序、认证后页面是否保持、近期认证是否免重复，仍需用户对照。

## 测试包

outputs/Coogle-wallet-native-ordering-test.apk：1.3.3 / 133，2,834,750 字节；SHA-256 b9a064054a7562b9a4e0eaf11527f213e5573ee62994dfd93fc64afb73e68fb2。

签名与此前测试包一致。内嵌 assets/coogle-companion.zip 与上个测试包逐字节相同，SHA-256 548e5c2b69f7a164c08c425d8d3dbf7f512ca96a0726a316239f5bbbab419c5a。

已安装国行测试包。用户勾选系统界面作用域并手动重启后，确认认证后钱包保持显示，但仍报告每次需要指纹，近期认证行为尚未与国际版对齐。

重启后的 LSPosed 日志确认 SystemUI 新 Hook 已安装。16:10:37.898 / 37.913、16:10:44.292 / 44.302 等多组记录证明原请求在原生 reset 后交回，正常路径只等待约 7–16ms。这与用户页面保持的结果一致，不能据此宣称支付验证或整个链路已完全等价。

继续只读分析同一 Wallet APK：PaymentFocusedViewModel 的认证状态由 Google TapAndPay 服务事件提供；需要认证时，锁定设备分支调用 requestDismissKeyguard，未锁定分支调用生物识别并报告解锁。界面是否显示“身份认证”文字不能单独证明支付资格。国行 mIsSecureNfcEnabled=false，与此前国际版相同，排除这项开关的直接差异。当前仍需采集一次真实成功后 5 秒内再次双击的完整日志，判断重复认证来源。

未推送 GitHub、修改首页、更新已发布 Release 或代用户重启设备。

## 后续认证定位与当前约束

用户确认两台同一账号、默认付款卡相同，但国际版 2 张付款卡、国行 8 张。均为多卡，不能归因于单卡自动选择。设备读取确认国际版 SDK 36 / Android 16、国行 SDK 37 / Android 17；Wallet 主 APK 仍为同一 SHA-256，GMS 版本不同。

已核对两版 GMS 的近期认证实现：读取 BiometricManager.getLastAuthenticationTime(32783)，按原有有效期判断；不支持时使用原有记录及 Android Keystore 校验。两台 Wallet 角色均为 com.google.android.gms，默认 NFC 服务均为 TpHceService。系统 KeyguardManager/TrustManager 锁定查询未发现 Wallet 包专属豁免。

临时只读诊断于 16:51:05.298 记录：Google 返回 recent=true，lastAuthAgeMs=3606，locked=true，cards=8，selected=7，suppliedCard=false，valuables=0，requestAuth=false；同一原生回调结束后 requestAuth=true。16:51:12.946 / 12.947 再次复现。新版 Wallet 原有 xht 回调在缺少当前预览 CardInfo/ValuableInfo 且设备锁定时，即使近期认证有效，也设置界面解锁请求。该行为不同于“系统没有记录指纹”，也不能归因于 8 张卡列表尚未加载。

国际版本地持续采集确认 10:02:05.523 指纹成功、10:02:06.282 原生成功回调，10:02:09.864 再次启动 QUICKDRAW 后没有新的解锁请求；结果仍 showing=true、occluded=true、deviceLocked=1。日志中的 acek 关闭路径属于原有 acbs/acfc 界面；国行记录的 acax/xir/xht 属于另一界面实现。两套代码同在当前 APK 中。此前 TapAndPay 的 IDLE/AUTH_SUCCESS 警告来自 PollingFrameHandler，无付款设备场景下不能据此认定认证缓存失效。

用户随后明确要求保留国行新版界面，不继续纠结界面差别。已撤回选择原有界面的试验代码、DexKit 查询、新增 Wallet 作用域和相关测试修改。该试验未安装到手机、未提交。临时只读诊断源码已移除，国行已重新安装 Coogle-wallet-native-ordering-test.apk，并关闭诊断钱包进程；当前仍为 system + SystemUI 作用域，不 Hook Wallet 认证代码。

回退后 testHookLifecycle、assembleRelease、lintRelease 全部通过，记录在 build/wallet-new-ui-preserved-regression.log。保留原生启动与锁屏消息顺序修复；重复认证尚未修复，不宣称与国际版所有行为完全相同。继续工作的边界是保留新版界面和真实锁定/认证结果，不以伪造解锁状态或认证成功取得表面一致。
