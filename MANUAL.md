# Android TV (arm64) 项目骨架与打包手册

## 已完成事项（截至 2026-03-14）

1. 在当前空目录初始化了最小 Android TV 项目骨架。
2. 生成并接入 Gradle Wrapper（`8.7`）。
3. 配置了 TV 启动入口（`LEANBACK_LAUNCHER`）和基础页面。
4. 在构建配置中限定 ABI 为 `arm64-v8a`。
5. 固定构建 JDK 为 17，并写入本机 Android SDK 路径。
6. 已完成一次 `assembleDebug` 打包验证并产出 APK。

## 1. 项目说明

本目录已生成一个最小 Android TV APK 项目骨架（无业务功能，仅用于验证打包流程）。

- 应用包名：`com.example.tvmediaplayer`
- 最低版本：`minSdk 24`
- 目标版本：`targetSdk 34`
- 编译版本：`compileSdk 34`
- ABI 约束：`arm64-v8a`（在 `app/build.gradle` 的 `ndk.abiFilters`）

## 2. 关键文件结构

```text
tv-media-player/
├─ settings.gradle
├─ build.gradle
├─ gradle.properties
├─ local.properties
├─ gradlew
├─ gradlew.bat
├─ gradle/
│  └─ wrapper/
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties
└─ app/
   ├─ build.gradle
   └─ src/
      └─ main/
         ├─ AndroidManifest.xml
         ├─ java/com/example/tvmediaplayer/MainActivity.kt
         └─ res/
            ├─ layout/activity_main.xml
            ├─ values/{strings.xml,colors.xml,themes.xml}
            ├─ drawable/{ic_launcher_foreground.xml,tv_banner.xml}
            └─ mipmap-anydpi-v26/{ic_launcher.xml,ic_launcher_round.xml}
```

## 3. 已执行过的关键操作（记录）

1. 初始化目录结构（`app` 模块、资源目录、wrapper 目录）。
2. 生成 Gradle Wrapper（版本 `8.7`）。
3. 写入 Android 应用构建脚本与 TV 启动入口（`LEANBACK_LAUNCHER`）。
4. 固定构建 JDK 到 17：
   - `gradle.properties` 中设置：`org.gradle.java.home=C:/D/Develop/Java/jdk-17.0.16+8`
5. 写入本机 SDK 路径：
   - `local.properties` 中设置：`sdk.dir=C\\:\\AppInstall\\develop\\Android\\Sdk`
6. 执行打包验证：`assembleDebug` 成功。

## 4. 常用命令

以下命令在项目根目录执行（Windows PowerShell）：

```powershell
# 清理
.\gradlew.bat clean

# 打 debug 包
.\gradlew.bat assembleDebug

# 打 release 包（未配置签名，仅演示流程）
.\gradlew.bat assembleRelease
```

如果你当前终端 `java -version` 不是 17，可临时指定：

```powershell
cmd /c "set JAVA_HOME=C:\D\Develop\Java\jdk-17.0.16+8&& set PATH=%JAVA_HOME%\bin;%PATH%&& .\gradlew.bat assembleDebug"
```

## 5. 产物位置

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 6. 安装到 Android TV 设备（可选）

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 7. 注意事项

1. 当前项目用于“流程验证”，没有实际播放功能。
2. `assembleRelease` 需要你后续补签名配置（keystore）才能用于正式分发。
3. 若后续引入 Native `.so`，`arm64-v8a` ABI 约束会直接生效。
