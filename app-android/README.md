# 商户 App（安卓唯一）· 项目骨架

决策（2026-11-03 用户定）：开发只考虑安卓，推广时要求商户仅使用安卓端。
老板模式与后厨模式同属一个安卓原生（Kotlin）App，不做 iOS，不做跨平台。

## 编译方式（GitHub Actions，2026-11-03 定）
Muse VM 上 Gradle daemon 的本地 TCP 被沙箱拦截，本地编不出包，
所以编译走 GitHub Actions：仓库根目录 `.github/workflows/android.yml`，
推送到 GitHub 后自动编 Debug APK，编完在 Actions → Artifacts
里下载 `xiaodian-ai-debug-apk`。本地 `local.properties` 不进仓库，
CI 用 `ANDROID_HOME` 环境变量找 SDK。

## 模式
- 后厨模式：订单队列、TTS 播报、点单子标记状态；耳机语音指令为补充，不是唯一操作。
- 老板模式：菜单（AI 生成与手动输入双轨，同一待确认清单）、改价、停售、营业与暂停、
  异常单收件箱、用量查看、与 AI 对话；交互以文字点按为主，语音只是输入框旁的可选按钮。
- 模式切换：一键切换；后厨模式不能改价、不能退款、不能改菜单；老板模式做钱相关操作
  须 PIN + 复述确认（见 PinGate）。

## 推送与断线
- 服务器是唯一真相源。App 用 WebSocket 接收新单与状态推送（协议见后端 `app/push.py` 的
  JSON 契约：type/order/pickup_no/status/payload），断线后重连并调用同步接口补拉
  未送达订单（见 OfflineQueue：先入本地队列，重连后按序补拉并去重）。
- 不用 FCM；不在本机上验证推送到达率，真实旧机测试留待试点。

## 构建（需 Android 环境，示例）
```bash
cd app-android
# 需 JDK 17、Android SDK（platforms;android-34、build-tools 34）与 Gradle
./gradlew :app:assembleDebug
# 安装到后厨机 / 老板机（二者均为安卓）后，在 App 内填服务器地址与店铺 ID，
# 后厨机另需：通知读取权限、电池白名单、自启动、蓝牙耳机配对。
```
未实现（明确不在本骨架内）：真实通知监听解析（需后厨机上店员微信的到账提醒文案实测）、
蓝牙 SCO 音频调优、真实 TTS 播报调优、真实服务器地址与密钥（用占位配置，不得写真实密钥）。
