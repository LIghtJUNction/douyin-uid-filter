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
