# 重启后失效：诊断候选

调查基线：`7ace2161d788ad1f869cee4f0f5041111d3451ff`。2026-11-23 核查 main、全部分支和 PR：无已有同类修复。

用户提供的 `ready, host=41.2.0, hooks=11` 只能证明注册 Hook 的流程完成。配置 IPC 失败不会中止 Hook 安装。不能据此认定临时 URI 授权丢失，也不能认定已读取启用配置或执行了过滤。

本候选改善诊断并把现有读取恢复策略提取为可测试组件：每进程独立缓存、500 ms 后按需重试，首次失败保持未启用，瞬时失败保留本进程上次有效配置。日志固定为 `configuration=enabled/disabled/unavailable/cached`，不输出 UID、cookie 或评论。设置页区分接入和配置状态，并标记为历史记录，避免旧的 ready 误导用户。若 Provider 本身不可达，状态上报也可能失败，必须以本次进程的 Xposed 日志为准，不能只看设置页。

不修改授权、Manifest、Binder 调用方验证、框架边界、过滤算法或版本号。首次读取失败时不会创建跨进程或跨重启的启用缓存。尚未确认原故障根因，因此不得将此候选表述为已修复真实抖音重启问题。

## 验证分层

- ConfigurationReaderTest：进程级缓存重建、读取失败、null 响应、重试节流、恢复和读取到关闭配置。
- ConfigProviderAndroidTest：Robolectric Android API 35 JVM 夹具，检查持久化设置的组件重建读取、未授权 Binder 调用被拒、诊断字段白名单。它不是操作系统进程重启，也不模拟 Android package visibility 或 URI 授权寿命。
- 真正的普通进程冷启动、强停和整机重启仍需独立 Android 系统夹具验证。三者不可相互替代。云端无 KVM；本轮不接触开发机或用户手机、真实账号与评论。
- 真机：分别记录正常退出重开、强停抖音重开、整机重启后直接开抖音、整机重启后先开模块。每个场景核对本次 `ready`、`configuration` 与 `page filtered`，仅提供状态日志及抖音/Android/框架版本，不提供 UID、cookie、评论。

## 发布交接

当前 `VERSION_NAME=0.1.0` / `VERSION_CODE=1`；本候选不提前改版本。待总控验收精确 SHA 后，在合并后的默认分支仅启动一次 Release，选择 patch 并保持 prerelease=true（真实验收尚未完成），预期生成 0.1.1 / 2。工作流仅在 `v*` tag push 或 workflow_dispatch 触发；普通分支 push、PR 不触发。不得重复 v0.1.0 的 run 45033063735。

工作流使用四项既有 ANDROID_KEYSTORE secrets 签名。applicationId 和签名配置不变，但本轮无法访问签名私钥，不能证明新 APK 签名与旧包一致。发布后必须用 apksigner 对照 v0.1.0 和候选签名证书 SHA-256，并验证覆盖安装。普通本地 debug APK 的证书不代表发布签名。

旧发布包已下载并验证，APK SHA-256 为 `e3f356f331c413644e98fa33a7cde6e59ad32dfb5e63924e88d7e92b516e8454`，与 GitHub asset digest 一致；apksigner 验证通过，签名证书 SHA-256 为 `7b852dc7c018d02cd70382e6fa8df0e743a00e787b4d8a22ad4097eeb866ebec`。这是后续发布包应对照的证书基线，不是本候选已签名的证明。

## 本轮云端结果

环境：Temurin JDK 17.0.21.1、Gradle 8.10.2、Android SDK 35 / build-tools 35.0.0。首次需补齐云端工具链；代理、CA 与测试缓存路径仅在仓库外配置，未提交环境专属设置。

执行 `gradle test assembleDebug assembleRelease lintRelease` 成功。Debug 和 Release 各 66 项测试，0 失败、0 错误、0 跳过；其中 3 项为 Robolectric Android API 35 JVM 夹具。发布版本脚本 7 项通过。Release Lint 0 errors / 0 warnings；`git diff --check` 通过；aapt 检查 Release APK 无 uses-permission。Release APK 未签名，Debug APK 使用本地测试证书，均非可发布升级包。

引入 Robolectric 仅用于测试，因此启用 AndroidX 测试依赖支持；产品仍使用原生 Android UI，没有新增产品运行时库或权限。

**验收结论：诊断误报已改善，读取异常后的恢复策略通过夹具测试；用户真实重启故障根因和真实 Android 授权寿命尚未验证，不能认定已修复。** 本轮未运行系统模拟器或真实抖音，也未执行合并、tag、Release 或任何工作流重跑。

## 后续：独立 GitHub 系统验收尝试

新增独立非发布工作流与 test-only APK 构建/矩阵脚本，提交 `92fa37b6f1525fbbd5e246ebff284f71ec0abdf4`。工作流仅 `contents:read`，不读取签名 secrets、不发布、不部署，18 分钟上限；虚拟宿主和未授权调用方各有独立临时签名，实际设置 APK 和测试控制 instrumentation 共用一次性测试签名。测试控制程序不会进入发布 APK。

已启动且仅启动一次：[run 45147038556](https://github.com/LIghtJUNction/douyin-uid-filter/actions/runs/45147038556)，标准 Ubuntu 24.04 runner。KVM 可读写预检失败，日志为 `Runner KVM is unavailable; refusing unsupported software emulation.`。此预检未区分设备缺失与权限不足，不能推广为所有 GitHub runner 不支持 KVM。后续 SDK 安装、APK 构建、模拟器启动、生命周期矩阵均被跳过。未修改 KVM 权限、未重派或重跑，真实系统故障仍未复现。

本地已构建和验签独立测试 APK；这只证明夹具能打包、宿主与设置不同签名，不代表夹具在 Android 系统上通过。下一步需要获准并可实际读写 KVM 的独立测试运行环境。

## 后续：多 UID 与宿主菜单入口

多 UID 每行一个；去除各行首尾空白、忽略空行并去重，数字始终保留字符串精度。任一 UID 命中即保留，复用主评论、回复投影和会话计数；非法非空行拒绝整个编辑。旧 `uid` 设置在没有 `uids` 时回退读取，保存后转为新键，空的新列表不会意外恢复已删除旧值。

宿主入口仅借助公开 Android `Activity.onCreatePanelMenu`，向已有且返回可展示的标准选项菜单追加「UID 评论过滤」，打开已导出的独立设置页。只在原有主抖音进程作用域加载，不申请悬浮窗或其他权限，不遍历/替换私有评论界面。没有标准菜单的版本不显示该入口，保留桌面设置。真实抖音 41.2.0 是否使用此标准菜单尚未验证，不能宣称评论区已有可见入口。

未修改版本属性或 Release 工作流。此功能草稿仍需总控验收，并保留真实宿主和系统重启验证缺口。

多 UID/菜单候选的云端本地结果：`test assembleDebug assembleRelease lintRelease` 成功；Debug、Release 各 75 项测试，0 失败/错误/跳过；版本脚本 7 项通过；Release Lint 无问题；Release APK 仍无权限申请。新增测试包括多 UID 去重与精度、整项非法输入拒绝、删除/切换/关闭、主评论和回复的联合计数、无匹配、配置冷读、旧单 UID 在设置页保留并迁移、菜单去重和打开设置、无菜单/非宿主不修改。Robolectric 测试中调用公开菜单辅助方法，不是 Xposed 或真实抖音渲染验收。
