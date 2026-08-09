# StorageSpoof

> **免费提供，禁止倒卖、付费下载、商业打包和收费安装。** 发现未经授权的商业销售，请通过 `yuholt.0757@qq.com` 联系作者。

面向 Android 16 的现代 Xposed API 102 模块，用于修改系统设置中逐应用显示的存储占用。模块只改变设置应用读取到的 `StorageStats` 返回值，不会修改真实文件或磁盘空间。

## 功能

- 注入 `com.android.settings` 与小米安全中心 `com.miui.securitycenter`
- 按包名分别配置：
  - 应用大小（`codeBytes`）
  - 数据大小（`dataBytes`）
  - 缓存大小（`cacheBytes`）
- 未配置或已停用的应用保持真实数据
- 通过 libxposed Remote Preferences 在配置应用与设置进程之间同步
- 两套配置界面：
  - 自定义卡片式界面（默认）
  - 标准 Material 3 列表界面
- 两种配色：
  - 莫奈/系统动态色
  - 带实时预览的可视化自定义主题颜色选择器
- 新建配置时可读取应用真实的应用、数据和缓存大小作为滑动条默认值；查询失败时使用安全非零默认值
- API 102 自动热重载支持

## 技术配置

- `targetSdk = 36`：目标行为为 Android 16
- `compileSdk = 37`：`io.github.libxposed:service:102.0.0` 的 AAR 元数据要求 API 37，仅影响编译
- Android Gradle Plugin 9.2.1
- Gradle 9.5.1
- Java 17
- libxposed API/Service 102.0.0

现代模块元数据位于：

```text
app/src/main/resources/META-INF/xposed/java_init.list
app/src/main/resources/META-INF/xposed/module.prop
app/src/main/resources/META-INF/xposed/scope.list
```

## 构建

准备 Android SDK Platform 37 与 Build Tools 37，然后执行：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建需要自行配置签名：

```bash
./gradlew :app:assembleRelease
```

## 使用

1. 安装 APK。
2. 在支持现代 Xposed API 102 的框架中启用模块。
3. 模块采用静态作用域，请勾选“系统设置”（`com.android.settings`）；小米澎湃 OS 还需手动勾选“小米安全中心”（`com.miui.securitycenter`）。
4. 重新启动对应宿主进程或设备。
5. 打开模块配置应用，通过启动页检查框架 API、作用域和使用情况访问权限。后者仅用于读取新配置的真实默认值，不影响 Hook 本身。
6. 选择应用，使用滑动条分别配置应用、数据与缓存大小，并在右侧切换 MB/GB。
7. 打开应用详情的存储页面查看结果；澎湃 OS 全局存储页的顶部已使用/剩余摘要也会同步显示伪装差额。

配置在设置进程内缓存约 2 秒。如果设置页面自身缓存旧结果，请退出后重开，或强制停止设置应用。

## 实现说明

AOSP Android 16 的 `StorageStats` 内部字段为：

```java
long codeBytes;
long dataBytes;
long cacheBytes;
```

设置的单应用存储页通过 `StorageStatsSource.getStatsForPackage()` 查询，最终调用 `StorageStatsManager.queryStatsForPackage()`。模块在该方法返回后修改上述字段。

为兼容部分厂商改名，反射同时尝试：

- `codeBytes` / `mCodeBytes` / `appBytes` / `mAppBytes`
- `dataBytes` / `mDataBytes`
- `cacheBytes` / `mCacheBytes`

Hook 使用 `ExceptionMode.PROTECTIVE`；字段布局不兼容时只写入框架日志，不让设置应用崩溃。

## 许可

本项目采用 [StorageSpoof Source-Available Non-Commercial License 1.0](LICENSE)：

- 允许个人学习、审计、修改和自用。
- 原版可在保留作者、许可和官方仓库链接的前提下免费分享。
- 禁止倒卖、付费下载、商业打包、收费安装、订阅解锁及其他直接或间接商业分发。
- 未经作者书面许可，不得公开分发修改版。

该许可为了明确禁止商业倒卖，**不是 OSI 认可的开源许可证**。公开源码不代表放弃著作权或允许商业使用。

## 官方仓库与下载

- 官方仓库：https://github.com/yuhold/storage-spoof
- 官方 APK：https://github.com/yuhold/storage-spoof/releases/latest
- 推荐下载 `universal`；`arm64-v8a` 是为常见 64 位 ARM 设备提供的同签名兼容副本。当前项目不含原生 `.so`，因此二者内容及体积相同。
- 每个版本同时提供 SHA-256 校验文件；其他收费来源均未获得作者授权。
- 官方 APK 签名证书 SHA-256：`5F:73:C4:3C:1A:01:A1:43:D7:95:39:8C:6A:61:B4:A2:95:B1:D6:86:B1:E6:6B:AB:E1:06:76:D7:4D:B4:62:05`。
- 从旧的 Debug/非官方签名构建切换到官方 Release 时，Android 可能要求先卸载旧版（这会清除模块配置）；安装 1.1.0 官方版后，后续官方版本可正常覆盖升级。

## 已知限制

- 默认覆盖 AOSP 设置包 `com.android.settings` 和小米安全中心 `com.miui.securitycenter`；其他厂商使用独立宿主时仍需专门适配。
- 只伪装目标宿主中通过 `queryStatsForPackage()` 获取的统计；不会改变 `df`、文件管理器、系统服务或真实可用空间。
- 澎湃 OS 全局页的顶部摘要仅在该页面原生刷新调用期间应用显示差额，不影响清理和低空间判断。
- API 102 框架支持情况取决于所使用的 Xposed 框架版本。
