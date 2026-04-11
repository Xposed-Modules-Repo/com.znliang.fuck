package com.znliang.fuck.bodian

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 波点音乐 (cn.wenyu.bodian) 专用 Hook
 *
 * 问题: 波点音乐是 Flutter 应用，开屏广告由 Flutter Widget 渲染，
 *       hook MethodChannel 回调无法阻止广告显示。
 *
 * 方案:
 * 1. Hook MethodChannel — 拦截广告加载成功的回调，阻止 Flutter 侧知道广告已就绪
 * 2. Hook FlutterView.addView — 检测广告 View 并立即移除/隐藏
 * 3. Hook Activity.onResume — 从后台回前台时，快速检测并跳过广告
 * 4. Hook Flutter 引擎的 Platform Views — 拦截原生广告 View 注入
 */

private val mainHandler = Handler(Looper.getMainLooper())

// 跳过按钮关键词
private val skipKeywords = arrayOf(
    "跳过", "跳过广告", "skip", "关闭", "close",
    "SKIP", "CLOSE", "跳过>", ">跳过",
    "跳过|", "|跳过", "×",
)

// 已知的广告 View 类名特征
private val adViewSignatures = arrayOf(
    "SplashAd", "splash_ad", "AdView", "AdLayout",
    "AdContainer", "BannerView", "TTAd", "GDTAd",
    "BaiduAd", "KsAd", "PAGAd", "Pangle",
    "csj_ad", "csjad", "rewarded", "InterstitialAd",
)

// ----------------------------------------------------
// 入口
// ----------------------------------------------------

fun hookBodian(lpparam: XC_LoadPackage.LoadPackageParam) {
    log("波点音乐 Hook 开始")
    hookFlutterMethodChannel(lpparam)
    hookFlutterViewLifecycle(lpparam)
    hookActivityResume(lpparam)
    log("波点音乐 Hook 完成")
}

// ----------------------------------------------------
// 1. Hook Flutter MethodChannel — 拦截广告加载成功回调
//    同时模拟"无广告"通知
// ----------------------------------------------------

private fun hookFlutterMethodChannel(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val clazz = XposedHelpers.findClass(
            "io.flutter.plugin.common.MethodChannel", lpparam.classLoader
        )

        // Hook invokeMethod (原生 -> Flutter)
        XposedHelpers.findAndHookMethod(
            clazz, "invokeMethod",
            String::class.java, Any::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.args[0] as? String ?: return

                    // 拦截广告加载成功 — 阻止 Flutter 渲染广告
                    if (method.contains("onLoadSuccess", true) ||
                        method.contains("preload_splash", true) ||
                        method.contains("splash_onLoad", true) ||
                        method.contains("onAdLoaded", true) ||
                        method.contains("onSplashAdLoad", true) ||
                        method.contains("PAGCallback", true) ||
                        method.contains("onADPresent", true) ||
                        method.contains("onADExposure", true) ||
                        method.contains("onADFetch", true) ||
                        method.contains("onADShow", true) ||
                        method.contains("showAd", true)
                    ) {
                        log("BLOCK Flutter->Native: $method")
                        param.result = null
                        return
                    }

                    // 只在非广告相关时打印日志
                    if (!method.contains("TextInput", true) &&
                        !method.contains("flutter", true) &&
                        !method.contains("Accessibility", true)
                    ) {
                        log("MethodChannel: $method")
                    }
                }
            }
        )

        // Hook MethodChannel$IncomingMethodCallHandler.onMethodCall
        // 这是 Flutter(Dart) -> 原生 的调用
        try {
            val incomingClass = XposedHelpers.findClass(
                "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler",
                lpparam.classLoader
            )
            val methodCallClass = XposedHelpers.findClass(
                "io.flutter.plugin.common.MethodCall",
                lpparam.classLoader
            )

            // Result 类通过反射获取
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

                        // 拦截广告加载请求
                        if (method.contains("loadAd", true) ||
                            method.contains("loadSplash", true) ||
                            method.contains("showSplash", true) ||
                            method.contains("requestAd", true) ||
                            method.contains("fetchAd", true) ||
                            method.contains("preloadAd", true)
                        ) {
                            log("BLOCK Dart->Native: $method")
                            // 返回失败结果，让 Flutter 侧认为广告加载失败
                            val result = param.args[1]
                            try {
                                XposedHelpers.callMethod(result, "error", "AD_BLOCKED", "blocked", null)
                            } catch (_: Throwable) {
                                try {
                                    param.result = null
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            )
            log("IncomingMethodCallHandler Hook 成功")
        } catch (t: Throwable) {
            log("IncomingMethodCallHandler Hook 失败: ${t.message}")
        }

        log("波点音乐 MethodChannel Hook 成功")
    } catch (t: Throwable) {
        log("波点音乐 MethodChannel Hook 失败: ${t.message}")
    }
}

// ----------------------------------------------------
// 2. Hook FlutterView — 检测并移除广告 View
// ----------------------------------------------------

private fun hookFlutterViewLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
    val classLoader = lpparam.classLoader

    // Hook FlutterView / FlutterSurfaceView / FlutterImageView 的 addView
    val flutterViewClasses = arrayOf(
        "io.flutter.embedding.android.FlutterView",
        "io.flutter.embedding.android.FlutterSurfaceView",
        "io.flutter.embedding.android.FlutterImageView",
        "io.flutter.view.FlutterView",
    )

    for (viewClassName in flutterViewClasses) {
        try {
            val viewClass = XposedHelpers.findClassIfExists(viewClassName, classLoader) ?: continue

            // Hook addView — 检测原生广告 View 被添加到 Flutter 容器中
            for (method in viewClass.declaredMethods) {
                if (method.name == "addView") {
                    try {
                        val paramTypes = method.parameterTypes
                        XposedHelpers.findAndHookMethod(
                            viewClass, "addView", *paramTypes.map { it as java.lang.reflect.Type }.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val child = param.args[0] as? View ?: return
                                    val childClass = child.javaClass.name

                                    if (isAdNativeView(childClass)) {
                                        log("拦截 FlutterView 广告子 View: $childClass")
                                        param.result = null
                                    }
                                }
                            }
                        )
                    } catch (_: Throwable) {}
                }
            }
            log("Hook $viewClassName 成功")
        } catch (_: Throwable) {}
    }

    // Hook PlatformViewsController — Flutter 混合开发中注入原生 View 的入口
    try {
        val pvcClass = XposedHelpers.findClassIfExists(
            "io.flutter.embedding.engine.systemchannels.PlatformViewsController",
            classLoader
        )
        if (pvcClass != null) {
            // Hook createForPlatformView or similar methods
            for (method in pvcClass.declaredMethods) {
                if (method.name.contains("create", ignoreCase = true)) {
                    try {
                        val paramTypes = method.parameterTypes
                        XposedHelpers.findAndHookMethod(
                            pvcClass, method.name,
                            *paramTypes.map { it as java.lang.reflect.Type }.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    log("PlatformViewsController: ${method.name} 被调用")
                                }
                            }
                        )
                    } catch (_: Throwable) {}
                }
            }
        }
    } catch (_: Throwable) {}
}

// ----------------------------------------------------
// 3. Hook Activity.onResume — 后台回前台时快速跳过广告
// ----------------------------------------------------

private var lastResumeTime = 0L

private fun hookActivityResume(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Activity", lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val now = System.currentTimeMillis()

                // 防止重复处理（10秒内只处理一次）
                if (now - lastResumeTime < 10_000) return
                lastResumeTime = now

                log("波点音乐 onResume: ${activity.javaClass.name}")

                // 延迟检测并处理开屏广告
                scheduleAdRemoval(activity)
            }
        }
    )
}

// ----------------------------------------------------
// 广告移除核心逻辑
// ----------------------------------------------------

private fun scheduleAdRemoval(activity: Activity) {
    // 快速多次尝试移除广告
    var delay = 100L
    repeat(10) { attempt ->
        mainHandler.postDelayed({
            try {
                val root = activity.window?.decorView as? ViewGroup ?: return@postDelayed

                // 1. 查找并点击跳过按钮
                if (findAndClickSkip(root)) {
                    log(">>> 波点音乐: 跳过广告成功")
                    return@postDelayed
                }

                // 2. 检查是否有全屏广告覆盖层并移除
                removeFullScreenAdOverlay(root)

                // 3. 如果到了后面的尝试还没有跳过，尝试更激进的方式
                if (attempt >= 5) {
                    tryRemoveFlutterAdView(root)
                }
            } catch (t: Throwable) {
                log("广告移除异常: ${t.message}")
            }
        }, delay)
        delay += 200
    }
}

private fun findAndClickSkip(viewGroup: ViewGroup): Boolean {
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i) ?: continue

        if (child is TextView) {
            val text = child.text?.toString() ?: ""
            val hint = (child as? TextView)?.hint?.toString() ?: ""
            val content = text + hint

            if (child.visibility == View.VISIBLE &&
                skipKeywords.any { content.contains(it, ignoreCase = true) }
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

// 检测全屏广告覆盖层
private fun removeFullScreenAdOverlay(root: ViewGroup) {
    for (i in 0 until root.childCount) {
        val child = root.getChildAt(i) ?: continue

        // 全屏 FrameLayout/ViewGroup，可能是广告覆盖层
        if (child is FrameLayout || child is ViewGroup) {
            val childClass = child.javaClass.name

            if (isAdNativeView(childClass)) {
                log("移除全屏广告覆盖层: $childClass (visibility=${child.visibility})")
                child.visibility = View.GONE
                root.removeView(child)
                return
            }
        }
    }
}

// 尝试移除 Flutter 广告 View
private fun tryRemoveFlutterAdView(root: ViewGroup) {
    val decorChild = root.getChildAt(0) ?: return

    // 遍历查找广告相关的 View
    traverseAndRemoveAds(decorChild as? ViewGroup ?: return, 0)
}

private fun traverseAndRemoveAds(viewGroup: ViewGroup, depth: Int): Boolean {
    if (depth > 15) return false  // 防止递归太深

    var removed = false
    for (i in viewGroup.childCount - 1 downTo 0) {
        val child = viewGroup.getChildAt(i) ?: continue
        val className = child.javaClass.name

        // 检查是否是广告 View
        if (!isSystemClass(className) && isAdNativeView(className)) {
            log("移除广告 View: $className")
            try {
                viewGroup.removeView(child)
                removed = true
            } catch (_: Throwable) {
                child.visibility = View.GONE
                removed = true
            }
            continue
        }

        // 递归检查子 View
        if (child is ViewGroup) {
            if (traverseAndRemoveAds(child, depth + 1)) {
                removed = true
            }
        }
    }
    return removed
}

// ----------------------------------------------------
// 工具方法
// ----------------------------------------------------

private fun isAdNativeView(className: String): Boolean {
    return adViewSignatures.any { className.contains(it, ignoreCase = true) }
}

private fun isSystemClass(className: String): Boolean {
    return className.startsWith("android.") ||
           className.startsWith("androidx.") ||
           className.startsWith("com.google.android.") ||
           className.startsWith("io.flutter.")
}

private fun log(msg: String) {
    Log.d(TAG, "[Bodian] $msg")
}
