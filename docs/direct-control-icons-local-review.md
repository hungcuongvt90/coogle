# 控制图标直接矢量构建与钱包双击待查

日期：2026-10-14，基于 0396095。用户同意保留新图标外观并缩减安装包，同时反馈开启后双击仍无法呼出钱包。

## 图标

将两枚 XML VectorDrawable 转换为 ControlIcons 中缓存的 ImageVector，移除 painterResource 加载路径和不再使用的 XML 资源。路径坐标、圆弧方向、24dp 尺寸、2 单位描边、圆端圆角和主题颜色均保持不变；标题栏及弹窗继续共用 ControlIcon。Lucide 来源声明及许可仍随 APK 保留，没有新增图标依赖。

Release 构建和 Lint 通过，0 错误、27 个既有警告。DEX 从 1,745,388 字节降为 1,714,992 字节；最终 APK 从 2,853,490 字节降为 2,834,750 字节，实际减少 18,740 字节。AndroidVectorParser/painterResource 解析路径从最终映射中移除；通用 ImageVector 缓存仍由 Compose 保留。

测试包：工作区 outputs/Coogle-direct-icons-test.apk，1.3.3 / 133，SHA-256：e01b35b1984e6e29782b7ff8a9334b2be084e565acec230ce857dc1932014df4。内嵌附属模块与上一测试包逐字节一致。钱包默认付款、Root 命令修复与认证隔离保持原样。构建日志 build/control-icons-direct.log。本次 ADB 无设备，尚未安装此包或取得新主页截图。

## 钱包双击：尚未完成

此前用户开启开关后 ADB 已读回手势为 1/1，但用户随后反馈实际双击不能呼出钱包。配置读回只能证明设置成功，不能证明系统已识别双击并执行启动。

重新读取已提取的当前 PLK110 services.jar，使用 JADX fallback 查看此前未成功反编译的 GestureLauncherService.interceptPowerKeyDown。代码保留原生相邻按键时间、钱包启用状态及原生 handleWalletGesture；PhoneWindowManager 调用前仍需通过欧加 InputPolicy.isCameraGestureEnabled。其实现取决于 mShutdownChordEnabled 和 SOS 次数，mShutdownChordEnabled 又来自 oplus.software.power_button_redefine。另有系统策略/KeyEvent 标志可能使原生手势处理被跳过。这些是需要采集实际事件和运行状态的候选阻断点，未验证前不当作已确认根因，也未修改这些门控。

已请求用户连接国行手机，并确认开启钱包后是否重启过。设备就绪后应核对当前系统进程加载的 Hook、原生模式/设置、欧加功能与 SOS 配置、双击时 GestureLauncherService 的 consecutive taps/launchWallet 日志，以及 SystemUI 是否收到钱包回调。先检查系统和事件证据，再选择修复位置；不伪造认证结果，不代用户重启或在其他应用发送手势。

首页、附属模块、已发布 Release 均未修改，未推送 GitHub。
