package com.znliang.fuck.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private val mainHandler = Handler(Looper.getMainLooper())
@Volatile private var splashSkipped = false

// 扩展跳过关键词
private val skipKeywords = arrayOf(
    "跳过", "跳过广告", "skip", "关闭", "close",
    "关闭广告", "dismiss", "跳过>", ">跳过",
    "SKIP", "CLOSE",
)

// 开屏 Activity 类名特征
private val splashActivityKeywords = arrayOf(
    "Splash", "splash", "LaunchActivity",
    "StartActivity", "WelcomeActivity",
    "AdActivity", "SplashAdActivity",
    "SplashActivity", "LaunchAdActivity",
)

// ----------------------------------------------------
// 初始化
// ----------------------------------------------------

fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
    log("initHooks()")
    hookActivity(lpparam)
    hookFlutterAd(lpparam)
}

// ----------------------------------------------------
// Activity Hook — Splash 检测 + 跳过
// ----------------------------------------------------

fun hookActivity(lpparam: XC_LoadPackage.LoadPackageParam) {

    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (splashSkipped) return
                val activity = param.thisObject as Activity
                val name = activity.javaClass.name

                if (isSplashActivity(name)) {
                    log(">>> 开屏广告 Activity: $name")
                    scheduleSafeSkip(activity)
                }
            }
        }
    )

    // Hook Activity.onCreate 增强: 直接 finish 已知的广告 Activity
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onCreate", android.os.Bundle::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val name = activity.javaClass.name

                // 穿山甲/优量汇等全屏广告 Activity 直接关闭（精确匹配类名后缀）
                val forceFinishExact = arrayOf(
                    "com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",
                    "com.bytedance.sdk.openadsdk.activity.TTFullScreenExpressVideoActivity",
                    "com.bytedance.sdk.openadsdk.activity.TTExpressAdActivity",
                    "com.qq.e.ads.PortraitADActivity",
                    "com.qq.e.ads.LandscapeADActivity",
                    "com.kwad.components.ad.fullscreen.KsFullScreenVideoActivity",
                )
                if (forceFinishExact.any { name == it }) {
                    log(">>> 强制关闭广告 Activity: $name")
                    try {
                        XposedHelpers.callMethod(activity, "finish")
                    } catch (_: Throwable) {}
                }
            }
        }
    )
}

private fun isSplashActivity(className: String): Boolean {
    return splashActivityKeywords.any { className.contains(it, ignoreCase = true) }
}

// ----------------------------------------------------
// Flutter 广告核心 Hook
// ----------------------------------------------------

fun hookFlutterAd(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val clazz = XposedHelpers.findClass(
            "io.flutter.plugin.common.MethodChannel", lpparam.classLoader
        )

        XposedHelpers.findAndHookMethod(
            clazz, "invokeMethod",
            String::class.java, Any::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.args[0] as String

                    when {
                        // 广告事件回调 — BLOCK
                        method.contains("onADFetch", true) ||
                        method.contains("onADPresent", true) ||
                        method.contains("onADExposure", true) ||
                        method.contains("onADShow", true) ||
                        method.contains("onADClick", true) ||
                        method.contains("showAd", true) ||
                        method.contains("loadAd", true) -> {
                            log(">>> BLOCK: $method")
                            param.result = null
                        }

                        // 广告加载成功回调 — BLOCK（阻止 Flutter 侧渲染广告）
                        method.contains("onLoadSuccess", true) ||
                        method.contains("preload_splash", true) ||
                        method.contains("splash_onLoad", true) ||
                        method.contains("onAdLoaded", true) ||
                        method.contains("onSplashAdLoad", true) ||
                        method.contains("PAGCallback", true) -> {
                            log(">>> BLOCK (load success): $method")
                            param.result = null
                        }

                        // 广告关闭/无广告 — ALLOW
                        method.contains("onNoAD", true) ||
                        method.contains("onADDismiss", true) ||
                        method.contains("onADClose", true) ||
                        method.contains("onLoadFail", true) ||
                        method.contains("onError", true) -> {
                            log(">>> ALLOW: $method")
                        }
                        else -> {
                            log("Flutter call: $method")
                        }
                    }
                }
            }
        )

        log("Flutter Hook SUCCESS")
    } catch (t: Throwable) {
        log("Flutter Hook failed: ${t.message}")
    }
}

// ----------------------------------------------------
// 通用跳过：递归查找含"跳过"/"skip"的 View 并点击
// ----------------------------------------------------

fun scheduleSafeSkip(activity: Activity) {
    var delay = 300L

    repeat(8) {
        mainHandler.postDelayed({
            if (splashSkipped) return@postDelayed
            val root = activity.window?.decorView as? ViewGroup ?: return@postDelayed
            if (findAndClickSkip(root)) {
                splashSkipped = true
                log(">>> 开屏广告跳过成功")
            }
        }, delay)
        delay += 400
    }
}

private fun findAndClickSkip(viewGroup: ViewGroup): Boolean {
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i) ?: continue

        if (child is TextView) {
            val text = child.text?.toString() ?: ""
            if (child.visibility == View.VISIBLE &&
                skipKeywords.any { text.contains(it, ignoreCase = true) }
            ) {
                log(">>> 找到跳过按钮: \"$text\" (${child.javaClass.name})")
                child.performClick()
                return true
            }
        }

        if (child is ViewGroup && findAndClickSkip(child)) {
            return true
        }
    }
    return false
}

// ----------------------------------------------------
// 日志
// ----------------------------------------------------

var DEBUG = true

fun log(msg: String) {
    if (DEBUG) Log.d(TAG, msg)
}
