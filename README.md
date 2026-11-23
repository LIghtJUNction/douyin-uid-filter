# 抖音 UID 评论过滤

一个小型 LSPosed / Xposed 模块，用于按数字 UID 筛选抖音视频评论。目标效果是只展示任一目标用户的评论，没有匹配项时显示 0。

**当前为待真机验收的预发布。** 工程构建和自动化测试已通过；真实抖音内的注入、显示数量及交互验收尚未完成，具体范围见 [设备验收记录](docs/device-validation.md)。

模块在手机本地处理抖音已经取得的评论数据。原始评论、账号与服务器数据保持原样。模块 APK 不申请联网、存储或无障碍权限。

## 使用

1. 从本仓库 Releases 安装 APK。
2. 在 LSPosed 或 Vector 中启用模块，作用域选择 `com.ss.android.ugc.aweme`（抖音）。
3. 强行停止并重新打开抖音，让框架加载模块。
4. 打开「抖音 UID 过滤」，每行填写一个数字 UID，启用过滤并保存（空白行和重复项自动清理）。
5. 打开抖音视频评论区。更换 UID 或关闭过滤后，关闭并重新打开评论区。

UID 指评论作者的数字标识，不是昵称、抖音号或 `sec_uid`。模块使用字符串精确匹配，避免长数字精度丢失。旧版单 UID 会自动保留；更新模块 APK 后，需强停并重开抖音，让框架加载新代码。

抖音若提供标准 Android 选项菜单，模块会在已有菜单中追加「UID 评论过滤」，点击打开独立设置页。自定义菜单和评论区工具栏不强行注入；抖音 41.2.0 是否展示该入口尚未经真实宿主验证。看不到入口时，请继续从桌面打开模块。

## 工作范围

- 主评论列表按作者 UID 过滤，保留原先顺序。
- 已加载的回复同样过滤；非目标父评论中的目标回复可以保留。
- 原始列表不被就地删除。关闭过滤并重新打开评论区后可恢复原始展示。
- 服务端分页游标和 `has_more` 保留；无匹配的页面会自动续查，每次最多处理 32 页。后续取得的评论继续过滤，关闭评论区时沿用抖音请求链的取消机制。
- 数字表示当前已加载的匹配评论数。抖音尚未下发、折叠或不可见的评论不在本地数据中。
- 仅适配抖音视频评论；直播弹幕、私信、国际版 TikTok 不属于当前模块范围。

## 兼容性

Android 8.1 及以上，支持传统 Xposed API 82 的注入框架。当前适配的评论模型来自抖音 **41.2.0 / 410201**。抖音内部接口会变化，其他版本需单独验证。模块设置页显示最近一次接入结果；适配失败时请检查框架模块日志。

真机验证结果与具体覆盖范围见 [设备验收记录](docs/device-validation.md)。

## 构建

需要 JDK 17、Gradle 8.10.2、Android SDK Platform 35 和 Build Tools 35.0.0：

```sh
gradle --no-daemon test assembleDebug lintDebug
python3 -m unittest discover -s scripts -p 'test_bump_version.py' -v
```

发布构建从环境读取 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`。私钥、APK、截图和构建目录不提交到 Git。

## 一键更新版本并发布

仓库 **Actions → Release → Run workflow** 中选择 `patch`、`minor` 或 `major`。工作流会递增 `VERSION_CODE`，运行测试、签名构建和检查，成功后推送版本提交与标签并发布 APK。指定版本可选 `custom` 并填写 `custom_version`；首次发布已有版本可选 `none`。尚待设备验收的构建可勾选 `prerelease`，发布为预发布版本。

首次配置仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。后续版本沿用同一签名才能覆盖安装。

本地也可调整版本，再提交 `version.properties` 并推送对应标签：

```sh
python3 scripts/bump_version.py patch
```

版本准备和发布在同一次工作流执行。同一失败任务重跑时复用已准备的版本，避免重复递增。已经公开的 Release 附件保留原样。

## 结构

| 文件 | 职责 |
| --- | --- |
| `MainActivity` | 多 UID 设置和接入状态 |
| `ConfigProvider` | 校验调用方 UID，向抖音提供只读配置 |
| `FilterConfig` / `CommentFilter` | 与安卓无关的精确匹配与回复投影 |
| `HostAdapter` | 读取抖音模型，生成过滤后的展示副本 |
| `ModuleEntry` | 注册 Xposed 钩子与分页会话重置 |
| `HostSettingsEntry` | 仅向宿主已有 Android 菜单追加设置入口 |

Release 日志只记录加载数量、匹配数量和适配错误类型，不记录 UID、评论正文或账号信息。设置中仅保存用户填写的目标 UID；模块不另外采集或暂存评论作者信息。

## 开源许可

[MIT](LICENSE)。Xposed API 仅用作 `compileOnly` 编译接口，由设备上的框架提供运行实现。
