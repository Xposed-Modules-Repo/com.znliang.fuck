# FuckApp

一个基于 LSPosed/Xposed Framework 的 Android 广告拦截与体验优化模块。

## 功能概览

| 目标 App | 包名 | 功能 |
|---------|------|------|
| Soul | `cn.soulapp.android` | 拦截送礼弹窗、每日对话上限弹窗、广告 Dialog、广告 View 移除 |
| 爱壹帆 | `com.cqcsy.ifvod` | 强制 VIP（到期时间 2099 年）、VIP 等级 10、无广告观看 |
| 波点音乐 | `cn.wenyu.bodian` | Flutter 开屏广告拦截（MethodChannel + View 树双重拦截）、自动点击跳过按钮 |
| 携程 | `ctrip.android.view` | 开屏广告跳过、弹窗广告拦截、Flutter 广告回调阻断、View 树广告移除 |
| 小红书 | `com.xingin.xhs` | 开屏广告自动跳过（跳过按钮 + 广告覆盖层移除）、Dialog 广告拦截 |
| 通用 | 所有 App | 广告 SDK 拦截（穿山甲/优量汇/百度/快手等 10+ SDK）、网络层广告域名过滤、WebView 广告拦截 |

## 通用拦截能力

### 广告 SDK 层

覆盖以下广告 SDK 的加载/展示方法，直接阻断广告初始化：

- 穿山甲 (ByteDance/CSJ) / Pangle
- 优量汇 (腾讯广告/GDT)
- 百度联盟 (Baidu Mob Ads)
- 快手广告 (Kuaishou)
- Mintegral (汇量科技)
- AppLovin / Unity Ads / IronSource
- TopOn (AnyThink 聚合)
- AdMob / Google Ads
- 美团广告 / SIGMob

同时精确匹配并直接 finish 广告 SDK 的全屏 Activity（如 `TTSplashActivity`、`PortraitADActivity` 等），含白名单机制避免误杀正常 App。

### 网络层

- **OkHttp**：拦截 `newCall()` / `execute()` / `enqueue()`，广告域名请求直接返回 null
- **HttpURLConnection**：`openConnection()` 兜底拦截
- **WebView**：`loadUrl()` / `postUrl()` / `shouldOverrideUrlLoading()` 拦截广告 URL
- **X5 内核 WebView**：腾讯 X5 WebView 同样拦截
- 内置 50+ 广告域名黑名单 + URL 路径特征匹配

## 项目结构

```
app/src/main/java/com/znliang/fuck/
├── MainHook.kt              # 入口，按包名分发 Hook
├── soul/SoulUtils.kt        # Soul 专属 Hook
├── iyifan/IYiFanUtils.kt    # 爱壹帆 VIP 伪造
├── bodian/BodianUtils.kt    # 波点音乐 Flutter 广告拦截
├── ctrip/CtripUtils.kt      # 携程广告拦截
├── xhs/XhsUtils.kt          # 小红书广告拦截
└── utils/
    ├── HookAdSdkUtils.kt    # 通用广告 SDK 拦截
    ├── HookNetworkUtils.kt  # 网络层广告域名过滤
    ├── HookDialogUtils.kt   # 通用弹窗拦截工具
    ├── HookFragmentUtils.kt # Fragment 生命周期 Hook
    ├── HookActivityUtils.kt # Activity 生命周期 Hook
    ├── HookViewGroup.kt     # ViewGroup addView/遍历 Hook
    └── HookMethodUtils.kt   # 通用方法 Hook 工具
```

## 技术细节

### 架构

- **包名分发**：`MainHook.handleLoadPackage()` 根据 `packageName` 路由到对应的 Hook 模块
- **通用 Hook 全局生效**：`HookAdSdkUtils` 和 `HookNetworkUtils` 对所有 App 生效（不限包名）
- **多进程安全**：过滤非主进程（`processName != packageName` 时直接 return）

### 各 App Hook 策略

**Soul** — Dialog/Fragment 拦截
- Hook `SoulDialog.show()` 直接阻断弹窗展示
- Hook `LimitGiftDialogV2.onResume()` 自动 dismiss
- 关键词匹配移除广告 View（`ad.views`、`SplashAdView` 等）

**爱壹帆** — Bean 属性伪造
- `VideoItemBean.isVip()` → true，`setVip()` → 空操作
- `UserInfoBean.isGiveVip()` → true，`getVipLevel()` → 10，`getEDate()` → 2099 年

**波点音乐** — Flutter 多层拦截
- MethodChannel 双向拦截：阻断 Native→Flutter 广告回调 + Dart→Native 广告加载请求
- FlutterView.addView 拦截：阻止原生广告 View 注入 Flutter 容器
- Activity.onResume：延迟多次扫描 View 树，自动点击跳过按钮或移除广告覆盖层

**携程** — 全链路拦截
- Activity 生命周期（onResume + onWindowFocusChanged）检测广告
- Dialog.show 内容分析：提取弹窗内文本，判断是否含"广告"+"关闭/跳过"组合特征
- ViewGroup.addView 拦截广告 View 注入
- Flutter MethodChannel 同波点音乐方案

**小红书** — 原生 View 广告移除
- setContentView / onResume / onWindowFocusChanged 多时机触发
- 递归查找跳过按钮（TextView 文本 + contentDescription）
- 全屏广告覆盖层检测与移除

## 编译

```bash
./gradlew assembleRelease
```

输出 APK：`app/release/app-release.apk`

## 安装使用

1. 安装 APK 到已 Root 的设备
2. 在 LSPosed 管理器中启用本模块
3. 勾选需要生效的目标 App
4. 强制停止目标 App 后重新打开

## 环境要求

- Android 7.0+ (API 24)
- 已安装 LSPosed / Xposed Framework
- Root 权限

## 声明

本项目仅供学习研究使用，请勿用于商业用途。使用本模块产生的任何问题由使用者自行承担。
