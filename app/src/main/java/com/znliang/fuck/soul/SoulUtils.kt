package com.znliang.fuck.soul

import android.util.Log
import android.view.View
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.hookActivities
import com.znliang.fuck.utils.hookAllCustomViews
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

    Log.d("$TAG:Soul", "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
    hookActivities(lpparam)
    hookDialogs(lpparam)
    hookSoulDialogShow(lpparam)
    hookFragments(lpparam) { className, obj ->
        if (className == "cn.soulapp.android.component.chat.limitdialog.LimitGiftDialogV2") {
            Log.d("$TAG:Soul", "LimitGiftDialogV2 onResume hooked!")
            XposedHelpers.callMethod(obj, "dismiss")
            Log.d("$TAG:Soul", "Dismissed LimitGiftDialogV2")
        }
    }
    hookAllCustomViews(lpparam) { className, child ->
        // 匹配广告相关类名
        if (adViewKeywords.any { className.contains(it, ignoreCase = true) }) {
            child.visibility = View.GONE
            Log.d("$TAG:Soul", "Removed Ad View: $className")
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
                    Log.d("$TAG:Soul", "Blocking SoulDialog.show()")
                    param.result = null
                }
            }
        )
    } catch (e: Throwable) {
        Log.d("$TAG:Soul", "Failed to hook SoulDialog.show()", e)
    }
}