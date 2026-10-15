# 玩家视界助手（WanjiaAuto）

小米 **HyperOS 2 / HyperOS 3** 上的每日自动任务 APP：定时亮屏解锁 → 打开小米钱包（玩家视界模块）→ 点击「做任务 领时长」→ 点击「完成任务」→ **OCR 识别屏幕倒计时，等待 时长+5S** → 返回玩家视界 → 点击「开奖」。

## 一、需求对照

| # | 你的需求                                 | 实现方式                                                                                                                                                                    |
| - | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | 每天定时亮屏及解锁手机                          | `AlarmManager.setAlarmClock` + `PendingIntent.getActivity`（豁免后台启动限制）→ `LauncherActivity`（`showWhenLocked`/`turnScreenOn`）→ `WakeLock` 亮屏 → 上滑手势 / PIN 节点点击解锁            |
| 2 | 打开玩家视界，点「做任务 领时长」                    | `getLaunchIntentForPackage` 启动小米钱包（玩家视界是钱包内模块）→ 无障碍节点查找点击，失败时 OCR 定位坐标点击                                                                                                |
| 3 | OCR 识别屏幕，点「完成任务」；按识别到的时长 +5S 返回点「开奖」 | 点击「完成任务」后进入 **WATCH 状态**：每秒 `AccessibilityService.takeScreenshot` 截图 + ML Kit 中文 OCR → 解析「剩余 25 秒 / 1分25秒 / 04:23」→ 等待 `识别秒数 + extraWaitSec(默认5)` → 返回钱包 → 点击「开奖」→ 排程次日 |

## 二、系统适配基线

| 系统        | Android 版本 | API |
| --------- | ---------- | --- |
| HyperOS 2 | Android 15 | 35  |
| HyperOS 3 | Android 16 | 36  |

- 工程 `compileSdk 35 / targetSdk 35 / minSdk 30`（Android 11+），在 HyperOS 2/3 上均可运行；`targetSdk 35` 意味着在 Android 16 设备上沿用 Android 15 的后台策略，对自动化唤醒链路更友好。
- 如需完全采用 Android 16 新行为：升级 AGP 至 8.9+ 后将 `compileSdk/targetSdk` 改为 36。

## 三、构建

1. 用 Android Studio（Ladybug 或更高）打开 `WanjiaAuto/` 目录；
2. 等待 Gradle Sync（需 JDK 17，Gradle 8.9）；
3. 连接小米手机，Run `app`；或命令行构建（本仓库未提交 gradle wrapper，需先 `gradle wrapper` 生成，或直接用已安装的 Gradle 8.9 执行）：

   ```bash
   gradle assembleDebug
   # 产物：app/build/outputs/apk/debug/app-debug.apk
   ```

> 没有本机 Android Studio / 网络受限？见下方 **第十节：通过 GitHub Actions 云端自动出包**。

## 十、通过 GitHub Actions 云端自动出包（无需本机 Android Studio）

本仓库已包含 `.github/workflows/build-apk.yml`，可在 GitHub 免费 Runner 上自动编译出**可安装的 debug APK**（自带 debug 签名，可直接装到手机）。

步骤：
1. 在 GitHub 新建一个**空**仓库（不要勾选初始化 README / .gitignore）；
2. 把 `WanjiaAuto/` 目录下的**全部内容**推送到仓库根目录（确保 `settings.gradle.kts`、`build.gradle.kts`、`.github/` 都在根目录）；
3. 进入仓库 `Actions` → 找到 `Build Debug APK` → `Run workflow`（或 push 到 `main`/`master` 自动触发）；
4. 等待约 3–6 分钟，进入该次运行的 **Artifacts** 下载 `wanjia-auto-debug-apk`（内含 `app-debug.apk`）；
5. 把 APK 传到手机安装（需开启「未知来源」安装权限）。

说明：
- GitHub Runner 有完整外网，能正常拉取 AndroidX / ML Kit（中文 OCR）等依赖，绕开本机网络限制；
- 产物为 debug 签名，仅供个人测试；若要正式发布请自行配置 release 签名；
- 若构建失败，把 Actions 页面的报错日志发给我，我来定位修复。

## 四、HyperOS 必开权限（缺一不可）

| 位置                        | 设置项                                       |
| ------------------------- | ----------------------------------------- |
| 设置 → 更多设置 → 无障碍 → 已下载的应用  | 开启 **玩家视界自动任务**，并打开 **「屏幕截图」** 开关（OCR 必需） |
| 设置 → 应用设置 → 应用管理 → 玩家视界助手 | **自启动 = 开**                               |
| 同上 → 权限管理                 | **后台弹出界面 = 允许**                           |
| 同上 → 省电策略                 | **无限制**                                   |
| 锁屏                        | 若用 PIN 自动解锁，需在 APP 内填写 PIN；无密码锁只上滑即可      |

> 「后台弹出界面」是 HyperOS 特有开关，不开的话闹钟触发的 Activity 可能被拦截。

## 五、验证玩家视界入口（重要）

玩家视界是**小米钱包内的活动模块**（钱包首页下拉 / 添加「玩家视界」小组件进入），默认包名 `com.mipay.wallet`。如果你的机型上是独立 APP 或入口不同，用 ADB 确认当前前台应用：

```bash
adb shell dumpsys window | findstr mCurrentFocus   # Windows
adb shell dumpsys window | grep mCurrentFocus      # Mac/Linux
adb shell pm list packages | findstr wallet
```

拿到实际包名后，在 APP 的「任务配置 → 目标应用包名」里修改。按钮文案（做任务/领时长、完成任务、开奖）同样可在配置里改，活动改版时只需改文字，不用改代码。

## 六、使用步骤

1. 打开 APP → ① 中开启无障碍服务（记得打开「屏幕截图」子开关）；
2. ② 打开「启用每日定时任务」，选择时间（默认 08:00）；
3. ③ 确认包名与按钮文案，填写锁屏 PIN（可选）；
4. 点「保存配置」→「立即手动执行一次」测试整条链路；
5. 观察 ⑤ 日志：`OCR 识别剩余 XXs → 等待 XX+5s 后返回` 即表示 OCR 值守正常；
6. 建议点「启动保活前台服务」防进程被杀。

## 七、已知限制与调优

- **PIN 解锁是灰度方案**：优先走无障碍节点点击数字键；节点读取失败时降级为坐标点击（不同机型键盘位置有偏差）。追求稳定可在「设置 → 密码与安全 → 扩展解锁」添加受信任位置/设备，让闹钟时刻锁屏已自动解除。
- **OCR 精度**：ML Kit 中文模型对白底黑字识别率高；倒计时若为艺术字/动态图可能解析失败，此时按「OCR 监控上限」（默认 240s）兜底返回。
- **点击降级链**：无障碍节点 → OCR 文字坐标 → 手势点击。任何一步命中即继续。
- **任务频率**：建议每天 1 次。过于频繁可能触发小米钱包风控（收回时长/限制参与），请自行评估。
- **息屏后亮屏**：部分 HyperOS 机型对「锁屏上显示活动」有额外开关，若测试发现黑屏，请检查通知/锁屏相关权限。

## 八、代码结构

```
app/src/main/java/com/wanjia/auto/
├── MainActivity.kt                          # 配置 UI + 日志
├── LauncherActivity.kt                      # 闹钟透明入口（亮屏/解锁/启动任务）
├── Prefs.kt                                 # SharedPreferences 配置
├── service/
│   ├── AutomationAccessibilityService.kt    # ★ 核心：状态机 + 点击 + OCR 值守
│   └── KeepAliveService.kt                  # 前台保活
├── ocr/
│   ├── OcrHelper.kt                         # ML Kit 中文 OCR + 倒计时解析
│   └── ScreenCapture.kt                     # 无障碍截图
├── core/
│   ├── TaskScheduler.kt                     # setAlarmClock 每日排程
│   ├── ScreenWake.kt                        # WakeLock 亮屏
│   └── Unlocker.kt                          # 上滑 + PIN 解锁（含坐标兜底）
└── receiver/BootReceiver.kt                 # 开机恢复定时
```

## 九、状态机

```
IDLE → LAUNCH(启动钱包) → WAIT_APP → CLICK_ENTRY(做任务 领时长)
     → CLICK_COMPLETE(完成任务) → WATCH(每秒 OCR，等待 时长+5S)
     → RETURN(回到钱包) → WAIT_APP2 → CLICK_LOTTERY(开奖)
     → 完成，释放 WakeLock，排程次日
```
