package com.znliang.fuck.soul

import android.util.Log
import android.view.View
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.adViewKeywords
import com.znliang.fuck.utils.hookAllCustomViews
import com.znliang.fuck.utils.hookDialogs
import com.znliang.fuck.utils.hookFragments
import com.znliang.fuck.utils.registerDialogInterceptor
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage


private val soulAdViewKeywords = arrayOf(
    "ad.views", "soulad.ad.views",
    "SplashAdView", "ExpressView", "NativeExpressView",
    "LoveView", "FunctionCardView", "BridgeWebViewX5",
)

fun hookSoul(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d("$TAG:Soul", "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
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
        if (soulAdViewKeywords.any { className.contains(it, ignoreCase = true) }) {
            child.visibility = View.GONE
            Log.d("$TAG:Soul", "Removed Ad View: $className")
        }
    }

    // 注册 Soul 专属弹窗拦截
    registerDialogInterceptor { className, _ ->
        val soulAdDialogs = arrayOf(
            "LimitGiftDialog", "GiftDialog", "LimitDialog",
            "DailyLimitDialog", "ChatLimitDialog",
            "SoulDialog",
        )
        soulAdDialogs.any { className.contains(it, ignoreCase = true) }
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
