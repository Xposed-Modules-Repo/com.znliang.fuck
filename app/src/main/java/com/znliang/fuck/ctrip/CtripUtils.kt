package com.znliang.fuck.ctrip

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 携程 (ctrip.android.view) 专用 Hook
 *
 * 携程广告特征:
 * - 开屏广告: 在主 Activity 上叠加的全屏广告 View
 * - 弹窗广告: Dialog 形式的广告弹窗
 * - 信息流广告: RecyclerView 中的广告条目
 *
 * 方案:
 * 1. Activity.onResume 检测广告覆盖层
 * 2. Dialog.show 拦截广告弹窗
 * 3. ViewGroup.addView 拦截广告 View
 * 4. Flutter MethodChannel 拦截广告回调
 * 5. View 点击跳过按钮
 */

private val mainHandler = Handler(Looper.getMainLooper())

private val skipKeywords = arrayOf(
    "跳过", "跳过广告", "skip", "关闭", "close",
    "SKIP", "CLOSE", "跳过>", ">跳过",
    "×", "✕", "✖", "dismiss",
)

// 携程广告 View 类名特征
private val ctripAdViewKeywords = arrayOf(
    "AdView", "AdLayout", "AdContainer", "AdBanner",
    "SplashAd", "BannerView", "BannerLayout",
    "SplashAdLayout", "InterstitialAdView",
    "NativeAdLayout", "NativeAdView",
    "ad_container", "ad_wrapper", "ad_banner",
    "ad_card", "ad_layout", "ad_frame",
    "CtripAd", "ctrip_ad", "CTAd",
    "SplashView", "LaunchAdView",
    "AdCardView", "AdFeedView",
    // 穿山甲
    "TTAd", "TTSplash", "TTFullScreen",
    // 优量汇
    "GDTAd", "GDTNative", "GDTSplash",
    // 百度
    "BaiduAd", "BDSplash", "BDNative",
    // 快手
    "KsAd", "KsSplash",
    // PAG
    "PAGAd", "PAGSplash",
)

// ----------------------------------------------------
// 入口
// ----------------------------------------------------

fun hookCtrip(lpparam: XC_LoadPackage.LoadPackageParam) {
    log("携程 Hook 开始")
    hookActivityResume(lpparam)
    hookDialogShow(lpparam)
    hookFlutterMethodChannel(lpparam)
    hookViewGroupAddView(lpparam)
    log("携程 Hook 完成")
}

// ----------------------------------------------------
// 1. Activity.onResume — 检测广告覆盖层 + 跳过
// ----------------------------------------------------

private var lastResumeTime = 0L

private fun hookActivityResume(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val name = activity.javaClass.name
                val now = System.currentTimeMillis()

                if (now - lastResumeTime < 5_000) return
                lastResumeTime = now

                log("携程 onResume: $name")
                scheduleAdRemoval(activity)
            }
        }
    )

    // Hook onWindowFocusChanged — 更精确的时机
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onWindowFocusChanged",
        Boolean::class.javaPrimitiveType,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val hasFocus = param.args[0] as Boolean
                if (!hasFocus) return

                val activity = param.thisObject as Activity
                val name = activity.javaClass.name
                log("携程 onWindowFocusChanged: $name")
                scheduleAdRemoval(activity)
            }
        }
    )
}

// ----------------------------------------------------
// 2. Dialog.show — 拦截广告弹窗
// ----------------------------------------------------

private fun hookDialogShow(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Dialog", lpparam.classLoader,
        "show",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val dialog = param.thisObject as Dialog
                val dialogClass = dialog.javaClass.name

                // 检查 dialog 的内容是否含广告特征
                try {
                    val window = dialog.window ?: return
                    val decorView = window.decorView as? ViewGroup ?: return

                    if (isAdDialog(dialogClass, decorView)) {
                        log("拦截广告弹窗: $dialogClass")
                        param.result = null
                        try { dialog.dismiss() } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        }
    )
}

private fun isAdDialog(className: String, contentView: ViewGroup): Boolean {
    if (ctripAdViewKeywords.any { className.contains(it, ignoreCase = true) }) {
        return true
    }

    // 检查弹窗内的文本是否含广告关键词
    val adTexts = arrayOf("广告", "AD", "立即下载", "安装", "了解详情", "领取优惠")
    val foundTexts = mutableListOf<String>()
    collectTexts(contentView, foundTexts, 5)

    // 弹窗中有"关闭"或"跳过"按钮说明可能是广告
    val hasSkip = foundTexts.any { skipKeywords.any { k -> it.contains(k, ignoreCase = true) } }
    val hasAdContent = foundTexts.any { adTexts.any { k -> it.contains(k, ignoreCase = true) } }

    return hasSkip && hasAdContent
}

private fun collectTexts(view: View, texts: MutableList<String>, depth: Int) {
    if (depth <= 0) return
    if (view is TextView) {
        view.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            view.getChildAt(i)?.let { collectTexts(it, texts, depth - 1) }
        }
    }
}

// ----------------------------------------------------
// 3. Flutter MethodChannel — 拦截广告回调
// ----------------------------------------------------

private fun hookFlutterMethodChannel(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val clazz = XposedHelpers.findClass(
            "io.flutter.plugin.common.MethodChannel", lpparam.classLoader
        )

        XposedHelpers.findAndHookMethod(
            clazz, "invokeMethod",
            String::class.java, Any::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.args[0] as? String ?: return

                    when {
                        // 广告加载成功/展示 — BLOCK
                        method.contains("onLoadSuccess", true) ||
                        method.contains("preload_splash", true) ||
                        method.contains("splash_onLoad", true) ||
                        method.contains("onAdLoaded", true) ||
                        method.contains("onSplashAdLoad", true) ||
                        method.contains("PAGCallback", true) ||
                        method.contains("onADFetch", true) ||
                        method.contains("onADPresent", true) ||
                        method.contains("onADExposure", true) ||
                        method.contains("onADShow", true) ||
                        method.contains("showAd", true) ||
                        method.contains("loadAd", true) -> {
                            log("BLOCK Flutter: $method")
                            param.result = null
                        }
                        // 广告关闭/无广告 — ALLOW
                        method.contains("onNoAD", true) ||
                        method.contains("onADDismiss", true) ||
                        method.contains("onADClose", true) -> {
                            log("ALLOW Flutter: $method")
                        }
                        else -> {
                            // 只记录非 TextInput 等常见调用
                            if (!method.contains("TextInput", true) &&
                                !method.contains("Accessibility", true)
                            ) {
                                log("Flutter call: $method")
                            }
                        }
                    }
                }
            }
        )

        // Hook Dart -> 原生 的广告加载请求
        try {
            val incomingClass = XposedHelpers.findClass(
                "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler",
                lpparam.classLoader
            )
            val methodCallClass = XposedHelpers.findClass(
                "io.flutter.plugin.common.MethodCall",
                lpparam.classLoader
            )
            val resultClass = XposedHelpers.findClass(
                "io.flutter.plugin.common.MethodChannel\$Result",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                incomingClass, "onMethodCall",
                methodCallClass, resultClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val call = param.args[0]
                        val method = XposedHelpers.callMethod(call, "method") as? String ?: return

                        if (method.contains("loadAd", true) ||
                            method.contains("loadSplash", true) ||
                            method.contains("showSplash", true) ||
                            method.contains("requestAd", true) ||
                            method.contains("fetchAd", true) ||
                            method.contains("preloadAd", true)
                        ) {
                            log("BLOCK Dart->Native: $method")
                            val result = param.args[1]
                            try {
                                XposedHelpers.callMethod(result, "error", "AD_BLOCKED", "blocked", null)
                            } catch (_: Throwable) {
                                param.result = null
                            }
                        }
                    }
                }
            )
            log("携程 IncomingMethodCallHandler Hook 成功")
        } catch (t: Throwable) {
            log("携程 IncomingMethodCallHandler Hook 失败: ${t.message}")
        }

        log("携程 Flutter MethodChannel Hook 成功")
    } catch (t: Throwable) {
        log("携程 Flutter Hook 失败: ${t.message}")
    }
}

// ----------------------------------------------------
// 4. ViewGroup.addView — 拦截广告 View 注入
// ----------------------------------------------------

private fun hookViewGroupAddView(lpparam: XC_LoadPackage.LoadPackageParam) {
    val addViewSigs = arrayOf(
        arrayOf<Any?>(View::class.java),
        arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType),
        arrayOf<Any?>(View::class.java, ViewGroup.LayoutParams::class.java),
        arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType, ViewGroup.LayoutParams::class.java),
    )

    val containers = arrayOf(
        "android.widget.FrameLayout",
        "android.widget.LinearLayout",
        "android.widget.RelativeLayout",
    )

    for (clsName in containers) {
        try {
            val cls = XposedHelpers.findClass(clsName, lpparam.classLoader)
            for (params in addViewSigs) {
                try {
                    XposedHelpers.findAndHookMethod(
                        cls, "addView", *params,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val child = param.args[0] as? View ?: return
                                val childClass = child.javaClass.name

                                // 白名单
                                if (isSystemClass(childClass)) return
                                // Flutter 相关 View 不拦截
                                if (childClass.startsWith("io.flutter.")) return

                                if (isCtripAdView(childClass)) {
                                    log("拦截广告 View addView: $childClass")
                                    param.result = null
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }
}

// ----------------------------------------------------
// 广告移除核心逻辑
// ----------------------------------------------------

private fun scheduleAdRemoval(activity: Activity) {
    var delay = 200L
    repeat(15) { attempt ->
        mainHandler.postDelayed({
            try {
                val root = activity.window?.decorView as? ViewGroup ?: return@postDelayed

                // 1. 查找并点击跳过按钮
                if (findAndClickSkip(root)) {
                    log(">>> 携程: 跳过广告成功")
                    return@postDelayed
                }

                // 2. 移除全屏广告覆盖层
                if (attempt >= 3 && removeFullScreenAdOverlay(root)) {
                    log(">>> 携程: 移除广告覆盖层成功")
                    return@postDelayed
                }

                // 3. 后期尝试 — 深度遍历移除广告
                if (attempt >= 8) {
                    traverseAndRemoveAds(root, 0)
                }
            } catch (t: Throwable) {
                log("广告移除异常: ${t.message}")
            }
        }, delay)
        delay += 300
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
                log("找到跳过按钮: \"$text\" [${child.javaClass.name}]")
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

private fun removeFullScreenAdOverlay(root: ViewGroup): Boolean {
    // 检查 decorView 的子 View 中是否有全屏广告覆盖
    for (i in 0 until root.childCount) {
        val child = root.getChildAt(i) ?: continue

        if (child is FrameLayout) {
            val childClass = child.javaClass.name
            if (!isSystemClass(childClass) && isCtripAdView(childClass)) {
                log("移除广告覆盖层: $childClass")
                child.visibility = View.GONE
                return true
            }
        }
    }
    return false
}

private fun traverseAndRemoveAds(viewGroup: ViewGroup, depth: Int): Boolean {
    if (depth > 12) return false
    var removed = false

    for (i in viewGroup.childCount - 1 downTo 0) {
        val child = viewGroup.getChildAt(i) ?: continue
        val className = child.javaClass.name

        if (!isSystemClass(className) && !className.startsWith("io.flutter.") && isCtripAdView(className)) {
            log("深度移除广告 View: $className")
            try {
                viewGroup.removeView(child)
                removed = true
            } catch (_: Throwable) {
                child.visibility = View.GONE
                removed = true
            }
            continue
        }

        if (child is ViewGroup && traverseAndRemoveAds(child, depth + 1)) {
            removed = true
        }
    }
    return removed
}

// ----------------------------------------------------
// 工具方法
// ----------------------------------------------------

private fun isCtripAdView(className: String): Boolean {
    return ctripAdViewKeywords.any { className.contains(it, ignoreCase = true) }
}

private fun isSystemClass(className: String): Boolean {
    return className.startsWith("android.") ||
           className.startsWith("androidx.") ||
           className.startsWith("com.google.android.")
}

private fun log(msg: String) {
    Log.d(TAG, "[Ctrip] $msg")
}
