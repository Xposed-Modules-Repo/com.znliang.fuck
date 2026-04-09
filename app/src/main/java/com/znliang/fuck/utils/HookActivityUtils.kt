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

private val skipKeywords = arrayOf("跳过", "跳过广告", "skip", "关闭")

// ----------------------------------------------------
// 初始化
// ----------------------------------------------------

fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
    log("initHooks()")

    hookActivity(lpparam)
    hookFlutterAd(lpparam)
}

// ----------------------------------------------------
// Activity Hook（仅 Splash 检测 + 跳过）
// ----------------------------------------------------

fun hookActivity(lpparam: XC_LoadPackage.LoadPackageParam) {

    XposedHelpers.findAndHookMethod(
        "android.app.Activity",
        lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (splashSkipped) return
                val activity = param.thisObject as Activity
                val name = activity.javaClass.name

                if (name.contains("Splash", true) || name.contains("Ad", true)) {
                    log(">>> Splash detected: $name")
                    scheduleSafeSkip(activity)
                }
            }
        }
    )
}

// ----------------------------------------------------
// Flutter 广告核心 Hook（关键）
// ----------------------------------------------------

fun hookFlutterAd(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val clazz = XposedHelpers.findClass(
            "io.flutter.plugin.common.MethodChannel",
            lpparam.classLoader
        )

        XposedHelpers.findAndHookMethod(
            clazz,
            "invokeMethod",
            String::class.java,
            Any::class.java,
            object : XC_MethodHook() {

                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.args[0] as String

                    when {
                        method.contains("onADFetch", true) ||
                        method.contains("onADPresent", true) ||
                        method.contains("onADExposure", true) -> {
                            log(">>> BLOCK: $method")
                            param.result = null
                        }
                        method.contains("onNoAD", true) -> {
                            log(">>> ALLOW (重要): $method")
                        }
                        method.contains("onADDismiss", true) -> {
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
    var delay = 500L

    repeat(5) {
        mainHandler.postDelayed({
            if (splashSkipped) return@postDelayed
            val root = activity.window?.decorView as? ViewGroup ?: return@postDelayed
            if (findAndClickSkip(root)) {
                splashSkipped = true
                log(">>> Splash skipped successfully")
            }
        }, delay)
        delay += 500
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
                log(">>> Found skip view: \"$text\" (${child.javaClass.name})")
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
// 日志（可通过 DEBUG 开关关闭）
// ----------------------------------------------------

var DEBUG = true

fun log(msg: String) {
    if (DEBUG) Log.d(TAG, msg)
}