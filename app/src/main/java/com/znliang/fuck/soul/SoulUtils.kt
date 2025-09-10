package com.znliang.fuck.soul

import android.util.Log
import android.view.View
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.hookDialogs
import com.znliang.fuck.utils.hookFragments
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage


private val adViewKeywords = listOf(
    "ad.views",
    "soulad.ad.views",
    "SplashAdView",
    "ExpressView",
    "NativeExpressView",
    "LoveView",
    "FunctionCardView",
    "BridgeWebViewX5",
)

fun hookSoul(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "cn.soulapp.android") return

    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
    hookAllCustomViews(lpparam)
    hookDialogs(lpparam)
    hookSoulDialogShow(lpparam)
    hookFragments(lpparam) { className, obj ->
        if (className == "cn.soulapp.android.component.chat.limitdialog.LimitGiftDialogV2") {
            Log.d(TAG, "LimitGiftDialogV2 onResume hooked!")
            XposedHelpers.callMethod(obj, "dismiss")
            Log.d(TAG, "Dismissed LimitGiftDialogV2")
        }
    }
}


private fun hookSoulDialogShow(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val fmClass = XposedHelpers.findClass("androidx.fragment.app.FragmentManager", lpparam.classLoader)
        XposedHelpers.findAndHookMethod(
            "cn.soul.lib_dialog.SoulDialog",
            lpparam.classLoader,
            "show",
            fmClass,
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "Blocking SoulDialog.show()")
                    param.result = null
                }
            }
        )
    } catch (e: Throwable) {
        Log.d(TAG, "Failed to hook SoulDialog.show()", e)
    }
}

private fun hookAllCustomViews(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val viewGroupClass = XposedHelpers.findClass("android.view.ViewGroup", lpparam.classLoader)
        XposedHelpers.findAndHookMethod(
            viewGroupClass,
            "addView",
            View::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val child = param.args[0] as? View ?: return
                    val className = child.javaClass.name

                    // 过滤掉系统/常见库的 View，只打印自定义的
                    if (!(className.startsWith("android.") ||
                                className.startsWith("androidx.") ||
                                className.startsWith("com.google.android."))) {
                        Log.d("XposedHook", "Custom View added: $className")
                        // 匹配广告相关类名
                        if (adViewKeywords.any { className.contains(it, ignoreCase = true) }) {
                            child.visibility = View.GONE
                            Log.d("XposedHook", "Removed Ad View: $className")
                        }
                    }

                }
            }
        )
    } catch (e: Throwable) {
        Log.e("XposedHook", "Failed to hook addView", e)
    }
}
