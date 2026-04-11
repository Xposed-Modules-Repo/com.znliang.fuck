package com.znliang.fuck.xhs

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 小红书 (com.xingin.xhs) 专用 Hook
 *
 * 小红书不是 Flutter 应用，开屏广告是原生 Android View。
 * 广告形式:
 * - 开屏广告: 全屏广告覆盖层，叠加在主 Activity 上
 * - 可能含 "跳过" 按钮 (倒计时圆圈或文字)
 *
 * 方案:
 * 1. Activity.onResume + onWindowFocusChanged 检测广告覆盖层
 * 2. 递归查找 "跳过" 按钮并自动点击
 * 3. 检测全屏 ImageView/FrameLayout 广告并直接移除
 * 4. Dialog 广告弹窗拦截
 */

private val mainHandler = Handler(Looper.getMainLooper())

private val skipKeywords = arrayOf(
    "跳过", "跳过广告", "skip", "关闭", "close",
    "SKIP", "CLOSE", "跳过>", ">跳过",
    "×", "✕", "✖", "dismiss", "跳过|",
)

// ----------------------------------------------------
// 入口
// ----------------------------------------------------

fun hookXhs(lpparam: XC_LoadPackage.LoadPackageParam) {
    log("小红书 Hook 开始")
    hookActivityLifecycle(lpparam)
    hookDialogShow(lpparam)
    log("小红书 Hook 完成")
}

// ----------------------------------------------------
// 1. Activity 生命周期 Hook
// ----------------------------------------------------

private var lastResumeTime = 0L

private fun hookActivityLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val name = activity.javaClass.name
                val now = System.currentTimeMillis()

                log("onResume: $name")

                // 开屏广告跳过（不限制频率，因为可能多次触发）
                scheduleAdRemoval(activity, isResume = true)

                // 5 秒内不重复处理（防抖）
                lastResumeTime = now
            }
        }
    )

    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onWindowFocusChanged",
        Boolean::class.javaPrimitiveType,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val hasFocus = param.args[0] as Boolean
                if (!hasFocus) return

                val activity = param.thisObject as Activity
                log("onWindowFocusChanged: ${activity.javaClass.name}")
                scheduleAdRemoval(activity, isResume = false)
            }
        }
    )

    // Hook setContentView — 在内容设置后立即检测
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "setContentView",
        View::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                log("setContentView: ${activity.javaClass.name}")
            }
        }
    )
}

// ----------------------------------------------------
// 2. Dialog 拦截
// ----------------------------------------------------

private fun hookDialogShow(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Dialog", lpparam.classLoader,
        "show",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val dialog = param.thisObject
                val dialogClass = dialog.javaClass.name

                // 检查是否是广告弹窗
                val adKeywords = arrayOf("Ad", "ad", "Advert", "Splash", "splash", "Banner", "Promotion", "promotion")
                if (adKeywords.any { dialogClass.contains(it) }) {
                    log("拦截广告弹窗: $dialogClass")
                    param.result = null
                    try {
                        XposedHelpers.callMethod(dialog, "dismiss")
                    } catch (_: Throwable) {}
                }
            }
        }
    )
}

// ----------------------------------------------------
// 广告移除核心逻辑
// ----------------------------------------------------

private fun scheduleAdRemoval(activity: Activity, isResume: Boolean) {
    var delay = 100L
    val maxAttempts = if (isResume) 20 else 10

    repeat(maxAttempts) { attempt ->
        mainHandler.postDelayed({
            try {
                val root = activity.window?.decorView as? ViewGroup ?: return@postDelayed

                // 1. 优先查找并点击跳过按钮
                if (findAndClickSkip(root)) {
                    log(">>> 小红书: 跳过广告成功 (attempt=$attempt)")
                    return@postDelayed
                }

                // 2. 检测并移除全屏广告覆盖层
                if (attempt >= 2 && removeSplashAdOverlay(root, activity)) {
                    log(">>> 小红书: 移除开屏广告覆盖层成功 (attempt=$attempt)")
                    return@postDelayed
                }

            } catch (t: Throwable) {
                log("广告移除异常: ${t.message}")
            }
        }, delay)
        delay += 250
    }
}

// 查找并点击跳过按钮
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

        // 检查 View 的 contentDescription（有些跳过按钮不用文字而是用 accessibility label）
        val desc = child.contentDescription?.toString() ?: ""
        if (child.visibility == View.VISIBLE &&
            child.isClickable &&
            skipKeywords.any { desc.contains(it, ignoreCase = true) }
        ) {
            log("找到跳过按钮(desc): \"$desc\" [${child.javaClass.name}]")
            child.performClick()
            return true
        }

        if (child is ViewGroup && findAndClickSkip(child)) {
            return true
        }
    }
    return false
}

// 检测并移除开屏广告覆盖层
private fun removeSplashAdOverlay(root: ViewGroup, activity: Activity): Boolean {
    val decorChild = root.getChildAt(0) ?: return false

    // 遍历 decorView 的直接子 View
    for (i in root.childCount - 1 downTo 0) {
        val child = root.getChildAt(i) ?: continue
        val className = child.javaClass.name

        // 系统和 Flutter View 不处理
        if (isSystemClass(className)) continue

        // 检测特征:
        // 1. 全屏 FrameLayout 且包含 ImageView（广告图片）
        // 2. 可能有倒计时文字
        // 3. 广告相关类名
        if (isAdViewByClass(className)) {
            log("检测到广告覆盖层: $className (visibility=${child.visibility}, w=${child.width}, h=${child.height})")
            child.visibility = View.GONE
            return true
        }
    }

    // 深度遍历查找广告 View
    return deepRemoveAdViews(decorChild as? ViewGroup ?: return false, 0)
}

private val adClassKeywords = arrayOf(
    "AdView", "AdLayout", "AdContainer", "AdBanner",
    "SplashAd", "SplashView", "LaunchAd", "LaunchAdView",
    "BannerView", "BannerLayout", "SplashAdLayout",
    "InterstitialAdView", "NativeAdLayout",
    // 穿山甲
    "TTAd", "TTSplash", "TTFullScreen", "TTExpress",
    "BDDrawable", "BDNative",
    // 优量汇
    "GDTAd", "GDTNative", "GDTSplash",
    // 百度
    "BaiduAd", "BDSplash", "BDNative",
    // 快手
    "KsAd", "KsSplash", "KsFullScreen",
    // Pangle
    "PAGAd", "PAGSplash",
    // 通用
    "AdSdkView", "AdWrapper", "AdFrame",
    "csj_ad", "csjad",
)

private fun isAdViewByClass(className: String): Boolean {
    return adClassKeywords.any { className.contains(it, ignoreCase = true) }
}

private fun deepRemoveAdViews(viewGroup: ViewGroup, depth: Int): Boolean {
    if (depth > 15) return false
    var removed = false

    for (i in viewGroup.childCount - 1 downTo 0) {
        val child = viewGroup.getChildAt(i) ?: continue
        val className = child.javaClass.name

        if (!isSystemClass(className) && isAdViewByClass(className)) {
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

        if (child is ViewGroup && deepRemoveAdViews(child, depth + 1)) {
            removed = true
        }
    }
    return removed
}

// ----------------------------------------------------
// 工具方法
// ----------------------------------------------------

private fun isSystemClass(className: String): Boolean {
    return className.startsWith("android.") ||
           className.startsWith("androidx.") ||
           className.startsWith("com.google.android.")
}

private fun log(msg: String) {
    Log.d(TAG, "[XHS] $msg")
}
