# xiaodian-ai-app

小店 AI 点餐客服商户 App（安卓唯一，老板模式 + 后厨模式同属一个 App）。

- 代码在 `app-android/`（Kotlin + Compose），第一步已实现老板模式待人工界面：待处理列表、语音/图片/视频查看、老板手动回顾客、已处理标记；第二步已实现后厨订单队列（真实订单、状态推进）与 App 退出/媒体关闭。
- 编译走 GitHub Actions：推送到 `main` 后自动编 Debug APK，**APK 发布到 GitHub Releases**（不放 Actions Artifacts），Release 名如 `小店AI助手 Debug build #N`，tag 如 `build-N`。
- 本地编译说明见 `app-android/README.md`；Muse VM 上 Gradle daemon 本地 TCP 被沙箱拦截，本地编不出包，故用 Actions。
