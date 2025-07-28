package com.znliang.fucksoul.soul

import android.util.Log
import com.znliang.fucksoul.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookSoul(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "cn.soulapp.android") return

    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")

    hookAndDismissOnResume(lpparam)
    hookSoulDialogShow(lpparam)
    hookSplashPage(lpparam)
}

private fun hookAndDismissOnResume(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        XposedHelpers.findAndHookMethod(
            "cn.soulapp.android.component.chat.limitdialog.LimitGiftDialogV2",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val obj = param.thisObject
                    val actualClass = obj.javaClass.name
                    Log.d(TAG, "Hooked onResume -> $actualClass")
                    XposedHelpers.callMethod(obj, "dismiss")
                    Log.d(TAG, "Dismissed successfully -> $actualClass")
                }
            }
        )
    } catch (e: Throwable) {
        Log.d(TAG, "Hook failed for ${lpparam.classLoader}", e)
    }
}

private fun hookSoulDialogShow(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val fmClass =
            XposedHelpers.findClass("androidx.fragment.app.FragmentManager", lpparam.classLoader)
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

private fun hookSplashPage(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "cn.soulapp.android.client.component.middle.platform.push.SoulLaunchActivity",
        lpparam.classLoader,
        "onCreate",
        android.os.Bundle::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject
                val className = activity.javaClass.name
                Log.d(TAG, "Auto finish splash: $className")
                try {
                    XposedHelpers.callMethod(activity, "finish")
                    Log.d(TAG, "Splash finished: $className")
                } catch (e: Throwable) {
                    Log.d(TAG, "Failed to finish splash activity: $className", e)
                }
            }
        }
    )
}