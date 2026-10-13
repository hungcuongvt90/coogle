# 钱包权限修复与界面调整：本地审查

日期：2026-10-14。基于钱包预览 Hook 回退 c3dc011，继续修复钱包开关无法开启，并完成用户随后要求的界面调整。未授权推送或发布。

## 钱包错误及修复

ADB 截图确认原始错误：普通应用读取 `nfc_payment_default_component` 被 Android 的隐藏设置读取限制拒绝，抛出 SecurityException；该异常阻止 WalletSettings 保存开关。手机实际默认付款组件与 Wallet role holder 都已是 Google，故错误并非未设置默认付款。

WalletPayment 优先使用原有读取；仅在 SecurityException 时通过现有按需 Root 通道读取当前用户的设置。正常可读路径不调用 Root。Shell 的空值/null 转成未设置；不合法组件、Root 拒绝与其他读取错误不会伪装成成功。保留默认付款读回验证、失败回滚和仅在开关开启时选择默认付款的行为。没有重新加入钱包应用 Hook，也不更改付款验证流程。

WalletPaymentReadTest 的受限读取用例先在没有回退的实现上重现 SecurityException，修复后通过。覆盖可读/未设置、Root 读取、Root 拒绝、其他异常、异常输出和用户编号。既有钱包选择/读回/回滚测试继续通过。

## 界面

- 卡片取消“上次确认”和“正在检测”字样，后台检查保留原有状态；检查失败仍显示“检测失败”。缓存加载本身保留，删除仅用于旧字样的 companionCached 标记。
- 重启弹窗移除设备重启按钮、确认分支和对应命令路径；按钮改为“重启目标作用域”。系统框架仍需设备重启的说明保留。
- 左侧模块拼图与右侧重启箭头统一为 Material Outlined、24dp、主题 secondary 色；启用/禁用不透明度分别为 100%/38%。弹窗复用同一图标组件。
- Hey Google 标题去掉“开启”；已安装说明改为“补全系统级 Voice Match 功能”。导航说明句号改为逗号。
- 标题栏保留标准上下两行布局，折叠行只显示标题，第二行小字只在展开行显示。标题使用 KernelSU LargeFlexibleTopAppBar 对应的 displaySmall/titleLarge 字体标记（默认 36sp/22sp）；图标固定在折叠行中。

参考：[KernelSU HomeMaterial](https://github.com/tiann/KernelSU/blob/main/manager/app/src/main/java/me/weishu/kernelsu/ui/screen/home/HomeMaterial.kt) 及 Google Maven Material3 1.5.0-alpha28 sources.jar 中 AppBar.kt/字体标记。

## 验证及限制

全部既有 APK 回归、新增钱包读取用例、状态用例及最终 Release 构建通过。Lint 为 0 错误、27 个既有警告。APK 不含被移除的卡片字样、重启设备按钮文案或钱包预览 Hook。附属模块源代码、内嵌 ZIP、README 和版本号未变。

测试 APK 已覆盖安装到国行 PLK110。折叠截图和界面树确认标题中心、左/右图标中心均为 y=253px，折叠后无第二行小字。截图显示钱包开关已开启。进一步 OFF/ON 测试在读取前台时发现手机已经切到其他应用，安全检查在发送手势前终止；未完成连续切换与付款验证，不将已显示开启等同于付款终端测试。没有重启系统或执行“重启目标作用域”。

测试包：outputs/Coogle-wallet-ui-test.apk，版本仍为 1.3.3 / 133，2,816,729 字节。SHA-256：`8ffa536a689583943d65a1ece5db76dffafa815533234ad4801adb969e90b55d`。构建/测试日志与截图位于 build/wallet-{read,ui}*，不打入 APK。
