# 钱包 Root 操作错误修复与默认付款设置恢复

日期：2026-10-14。基于 62de738，按用户最新要求恢复开启钱包时自动设置默认付款应用，并修复已授权后仍报 Root 操作失败的问题。此记录的默认付款行为取代此前取消付款设置的本地记录。

## 根因和实现

手机当前 aflags list 输出为 422,301 字节。原生手势检查将完整输出保存为 Shell 变量，再作为单个参数传给 Android 的外部 printf；已在实际 Root 环境重现 Argument list too long。模式资源实际为 2，两项钱包 flag 均已启用，但超长参数使检查返回失败，随后被 RootShell 的通用错误提示显示为 Root 操作失败。

现在先用管道筛出两项完整名称匹配的 flag，只将筛选后的小段输出保存到变量，避免单参数上限。管道启用 pipefail，防止 aflags 命令失败但部分输出被误当成有效结果。原生模式读取失败、模式不支持、标志读取失败、标志未启用分别通过明确标记交给原生配置解析器；这些情况不再被当作 Root 拒绝，也不会保存备份或更改手势。

WalletPayment、WalletPowerPolicy、WalletSettings 的默认付款事务及对应测试已恢复：只在开启钱包开关时检查默认付款设置；已有有效 Google 默认项时不重复设置，其他默认项通过原有 role/NFC 选择及读回验证改为 Google。操作失败仍回滚付款选择、原生手势和开关。保留受限读取通过按需 Root 回退的修复。RootShell 本身未改变，原生手势、默认付款设置仍需要用户授权。

标题栏与弹窗的新图标保持原样，附属模块、首页文案、版本号均未改动；没有钱包认证 Hook。

## 验证

- 隔离测试模拟超过 128 KiB 的单参数限制及长 flag 列表，在旧检查上失败，修复后通过。
- 覆盖资源模式不支持、读取命令失败、flag 关闭、aflags 输出有效行后返回失败，以及明确错误不更改备份。临时移除 pipefail 的变异被测试拒绝，文件已恢复，最终原生命令测试再次通过。
- 通过编译后的生产 supportCommand/readCommand 生成只读脚本，在手机 Root 环境执行成功，返回原设置 0/null，不改动系统设置。
- 默认付款读取/选择/回滚、原生手势事务和全部既有回归通过；Release 构建成功，Lint 为 0 错误、27 个既有警告。
- 用户反馈新包钱包开关“已开启”后，ADB 读回 double_tap_power_button_gesture_enabled=1、double_tap_power_button_gesture=1；默认付款组件为 com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService，WALLET role holder 为 com.google.android.gms。此前只读采集默认付款已经是 Google，所以此轮实机覆盖的是有效默认项无需重复设置的路径；切换其他默认项的分支由隔离事务测试验证。
- 核对设置后手机已回到桌面，随后 ADB 断开；没有发送手势，未取得新图标的主页截图，也未代用户重启或验证付款终端。

测试 APK：工作区 outputs/Coogle-wallet-root-fixed-test.apk，1.3.3 / 133，2,853,490 字节，SHA-256：ab95ff5bce789986efd4cca936b2ee02d174a133d098f1244b65729824508000。已覆盖安装，设备记录的安装时间为 2026-10-14 13:28:34。签名验证通过，内嵌附属模块与 controls-test ZIP 逐字节一致。日志：build/wallet-root-{large-flags-red,large-flags-green,fixed-full,fixed-lint,pipefail-mutation,final-commands}.log。

## 安装包大小核对

正式 1.3.3 为 2,800,137 字节，上一界面测试包为 2,816,729 字节，图标和 Root 修复测试包均为 2,853,490 字节。相对正式版增加 53,353 字节（1.91%）。相对上一界面包，classes.dex 增加 36,564 字节；新增的 painterResource/AndroidVectorParser 路径属于新 XML 图标的加载代码。图标许可压缩后 1,499 字节，资源表及两枚图标增加少量内容。两项原生库 libandroidx.graphics.path.so、libdexkit.so 的压缩/原始大小均未改变。ZIP 中没有测试、日志或 Markdown 文件。

未推送 GitHub，未修改已发布 Release。实际付款安全性仍需独立的锁屏及付款终端验收；成功设置 1/1 仅证明原生手势配置已生效。
