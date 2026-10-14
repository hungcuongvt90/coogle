# 取消付款应用自动设置及更换控制图标

日期：2026-10-14。基于本地原生钱包入口 5f8d125，按用户最新要求移除开启 Google 钱包时强制设置默认付款应用，并重新选择标题栏左右控制图标。本记录的行为说明取代此前本地审查记录中的默认付款选择步骤。

## 钱包行为

WalletSettings 仅执行原生电源手势设置和 LSPosed 开关保存，保留原生设置备份、关闭恢复以及配置保存失败的回滚。MainActivity 不再构造 WalletPayment；已删除该类、仅由其使用的 WalletPowerPolicy 以及旧付款读取/选择测试，清除对应构建登记。应用不再读取 nfc_payment_default_component，不写 NFC 默认付款设置，不修改 WALLET role holder，也不以 Google 是否为默认付款应用阻止保存开关。

系统默认付款应用由用户在系统设置中选择，本轮不会撤销用户已有的默认选择。原生双击手势仍通过既有按需 Root 通道配置，删除付款操作不表示删除该通道。系统、SystemUI、钱包及 GMS 的原生认证流程保持原样。锁屏快捷入口代码保留其原本的钱包能力查询，不会设置默认付款应用。

先在旧 WalletSettings 注入禁止付款读取/写入的测试驱动，启用因读取默认付款而失败；移除该接口和实现后，仅手势事务测试通过。覆盖启用/关闭、偏好保存返回失败或抛错、关闭失败回滚、手势设置失败不提交偏好、偏好与手势回滚失败保留异常。临时移除手势回滚的变异被测试拒绝，原文件已恢复。原生入口、Shell 命令、备份恢复及钱包包隔离测试继续通过。

## 图标

标题栏左侧改为 Lucide package 模块方块，右侧改为 rotate-cw 重启箭头；安装/安装完成及重启弹窗使用同一资源。只转换两枚 SVG 为 Android VectorDrawable，不添加图标库依赖。两者统一 24dp、24×24 视口、2 单位描边、圆端/圆角，使用同一 ControlIcon 的主题 secondary 色及 100%/38% 启用/禁用透明度。标题位置、字号、折叠与点击区域不变。

来源：[package SVG](https://github.com/lucide-icons/lucide/blob/main/icons/package.svg)、[rotate-cw SVG](https://github.com/lucide-icons/lucide/blob/main/icons/rotate-cw.svg)。NOTICE 已补充来源，ISC 原许可随 APK assets/lucide-icons-LICENSE.txt 分发。

## 构建、审查与测试包

完整既有回归、Release 构建通过；Lint 为 0 错误、27 个既有警告。最终 DEX 不含 WalletPayment、WalletPowerPolicy、nfc_payment_default_component 或钱包认证 Hook；原生设置及重复路径保护仍在。附属模块代码未变，内嵌 ZIP 与上一测试包逐字节相同。APK 签名证书保持原样。

测试包：工作区 outputs/Coogle-wallet-icons-test.apk，版本 1.3.3 / 133，2,853,490 字节，SHA-256：402eebcc1a85f11a61985bf37c0c655623c2f7ec0b6d80856be12e702b16da7d。已通过 ADB 覆盖安装到国行 PLK110，安装时间 2026-10-14 13:14:47。日志：build/wallet-no-default-{red,green,mutation}.log 与 build/wallet-icons-{full,lint}.log。

安装时手机焦点为 NotificationShade，Coogle 位于其下。未发送手势、切换功能或重启手机；已请用户解锁并回到 Coogle，以便补充图标实机显示核对。原生钱包双击及真实付款终端验证仍未完成，本轮自动化通过不能替代这些实机结果。

未改 README/首页文案，未推送 GitHub，未修改已发布 Release。
