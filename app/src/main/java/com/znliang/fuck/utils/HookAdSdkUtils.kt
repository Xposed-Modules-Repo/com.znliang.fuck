package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 常见广告 SDK Hook 工具
 *
 * 覆盖:
 * - 穿山甲 (CSJ / ByteDance)
 * - 优量汇 (腾讯广告 / GDT)
 * - 百度联盟 (Baidu Mob Ads)
 * - 快手广告 (Kwai / Kuaishou)
 * - Mintegral (汇量科技)
 * - AppLovin
 * - Unity Ads
 * - IronSource
 * - TopOn / AnyThink (聚合 SDK)
 */
object HookAdSdkUtils {

    // ----------------------------------------------------
    // 广告 SDK 类名特征
    // ----------------------------------------------------
    private val adSdkClasses = mapOf(

        // 穿山甲 (ByteDance / CSJ)
        "com.bytedance.sdk.openadsdk" to "穿山甲",
        "com.bytedance.mcs.ad" to "穿山甲",
        "com.ss.android.ad" to "穿山甲",
        "com.pangolin" to "穿山甲",

        // 优量汇 (腾讯广告 / GDT)
        "com.qq.e.ads" to "优量汇",
        "com.tencent.mm.opensdk" to "优量汇",

        // 百度联盟
        "com.baidu.mobads" to "百度联盟",
        "com.baidu.mobads.sdk" to "百度联盟",

        // 快手广告
        "com.kwad.sdk" to "快手广告",
        "com.kuaishou" to "快手广告",

        // Mintegral (汇量科技)
        "com.mintegral" to "Mintegral",

        // AppLovin
        "com.applovin" to "AppLovin",
        "com.applovin.mediation" to "AppLovin",

        // Unity Ads
        "com.unity3d.ads" to "Unity Ads",
        "com.unity3d.services" to "Unity Ads",

        // IronSource
        "com.ironsource" to "IronSource",
        "com.supersonic" to "IronSource",

        // TopOn (聚合)
        "com.anythink" to "TopOn",
        "com.anythink.china" to "TopOn",

        // AdMob / Google Ads
        "com.google.android.gms.ads" to "AdMob",

        // 美团广告
        "com.meituan.android.ad" to "美团广告",

        // SIGMob
        "com.sigmob" to "SIGMob",

        // 章鱼算法
        "com.octopus" to "章鱼",

        // 穿山甲海外版 (Pangle)
        "com.pangle" to "Pangle",
    )

    // ----------------------------------------------------
    // 广告加载/展示关键方法名
    // ----------------------------------------------------
    private val adLoadMethods = arrayOf(
        "loadAd", "loadAds", "preloadAd", "requestAd", "fetchAd",
        "loadBannerAd", "loadInterstitialAd", "loadNativeAd",
        "loadRewardedVideoAd", "loadFullScreenVideoAd",
        "renderAd", "showAd", "presentAd",
    )

    private val adShowMethods = arrayOf(
        "showAd", "showBanner", "showInterstitial", "showNative",
        "showRewardedVideo", "showFullScreenVideo", "presentAd",
        "onAdShow", "onAdExposure", "onADExposure",
    )

    // ----------------------------------------------------
    // 入口
    // ----------------------------------------------------
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("HookAdSdkUtils: 开始 Hook 广告 SDK...")

        hookAdSdkClasses(lpparam)
        hookSplashAdActivities(lpparam)
    }

    // ----------------------------------------------------
    // Hook 广告 SDK 类
    // ----------------------------------------------------
    private fun hookAdSdkClasses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        for ((className, sdkName) in adSdkClasses) {
            try {
                // 尝试 Hook 广告加载入口类
                hookSdkAdLoader(classLoader, className, sdkName)
            } catch (t: Throwable) {
                // SDK 不存在则跳过
            }
        }
    }

    private fun hookSdkAdLoader(
        classLoader: ClassLoader,
        basePackage: String,
        sdkName: String
    ) {
        // 常见的广告加载类后缀
        val loaderSuffixes = arrayOf(
            ".AdManager", ".AdLoader", ".AdSdk", ".SplashAd",
            ".BannerAd", ".InterstitialAd", ".NativeAd",
            ".RewardedVideoAd", ".FullScreenVideoAd",
            ".TTAdSdk", ".TTAdManager", ".TTAdNative",
            ".GDTAdManager", ".BaiduAdManager",
            ".AdManagerWrapper", ".AdLoaderWrapper",
        )

        for (suffix in loaderSuffixes) {
            try {
                val fullClassName = basePackage + suffix
                val clazz = XposedHelpers.findClassIfExists(fullClassName, classLoader)
                    ?: continue

                // Hook 所有广告加载方法
                for (method in clazz.declaredMethods) {
                    val methodName = method.name
                    if (adLoadMethods.any { methodName.equals(it, ignoreCase = true) } ||
                        adShowMethods.any { methodName.equals(it, ignoreCase = true) }
                    ) {
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, methodName,
                                *method.parameterTypes.map { it as java.lang.reflect.Type }.toTypedArray(),
                                object : XC_MethodReplacement() {
                                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                                        log("拦截广告 SDK[$sdkName]: ${clazz.simpleName}.$methodName()")
                                        return null
                                    }
                                }
                            )
                            log("成功 Hook $sdkName: ${clazz.simpleName}.$methodName()")
                        } catch (_: Throwable) {
                            // 部分方法可能无法 Hook
                        }
                    }
                }
            } catch (_: Throwable) {
                // 类不存在
            }
        }
    }

    // ----------------------------------------------------
    // Hook 广告 SDK 全屏广告 Activity (精确匹配，不误杀)
    // ----------------------------------------------------
    private fun hookSplashAdActivities(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只匹配广告 SDK 的精确 Activity 类名，不使用模糊关键词
        val exactAdActivities = arrayOf(
            // 穿山甲
            "com.bytedance.sdk.openadsdk.activity.TTSplashActivity",
            "com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTFullScreenExpressVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTExpressAdActivity",
            "com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity",
            // 优量汇
            "com.qq.e.ads.PortraitADActivity",
            "com.qq.e.ads.LandscapeADActivity",
            // 百度
            "com.baidu.mobads.openad.SplashAdActivity",
            // 快手
            "com.kwad.components.ad.splash.SplashAdActivity",
            "com.kwad.components.ad.fullscreen.KsFullScreenVideoActivity",
            "com.kwad.components.ad.reward.KsRewardVideoActivity",
        )

        // 白名单：包含这些包名的 Activity 不拦截（正常 App 的启动页）
        val whitelistPrefixes = arrayOf(
            "com.alibaba.",   // 淘宝/支付宝等
            "com.taobao.",
            "com.tencent.mm", // 微信
            "com.tencent.mobileqq", // QQ
            "com.sina.weibo", // 微博
        )

        val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)

        XposedHelpers.findAndHookMethod(
            activityClass, "onCreate",
            android.os.Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject
                    val className = activity.javaClass.name

                    // 白名单直接放行
                    if (whitelistPrefixes.any { className.startsWith(it) }) return

                    // 精确匹配广告 SDK Activity（完整类名相等）
                    if (exactAdActivities.any { className == it }) {
                        log("检测到广告 SDK Activity: $className, 直接 finish")
                        try {
                            XposedHelpers.callMethod(activity, "finish")
                        } catch (t: Throwable) {
                            log("finish 广告 Activity 失败: ${t.message}")
                        }
                    }
                }
            }
        )
    }
}
