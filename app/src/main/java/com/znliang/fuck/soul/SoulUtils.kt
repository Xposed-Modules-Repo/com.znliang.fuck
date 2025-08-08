package com.znliang.fuck.soul

import android.util.Log
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.hookDialogs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookSoul(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "cn.soulapp.android") return

    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")

    hookAndDismissOnResume(lpparam)
    hookDialogs(lpparam)
    hookSoulDialogShow(lpparam)
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