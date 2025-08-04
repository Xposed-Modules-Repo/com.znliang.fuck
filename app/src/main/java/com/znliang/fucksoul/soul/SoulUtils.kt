package com.znliang.fucksoul.soul

import android.util.Log
import com.znliang.fucksoul.TAG
import com.znliang.fucksoul.utils.hookFragments
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookSoul(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "cn.soulapp.android") return

    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")

    hookAllFragmentOnResumeWithFilter(lpparam)
    hookSoulDialogShow(lpparam)
}

fun hookAllFragmentOnResumeWithFilter(lpparam: XC_LoadPackage.LoadPackageParam) {
    val targetClasses = setOf(
        "cn.soulapp.android.component.chat.dialog.NoticePermissionDialog",
        "cn.soulapp.android.component.chat.limitdialog.LimitGiftDialogV2",
        "cn.soul.lib_dialog.SoulDialogFragment"
    )

    try {
        val fragmentClass = XposedHelpers.findClass("androidx.fragment.app.Fragment", lpparam.classLoader)
        val onResumeMethod = fragmentClass.getMethod("onResume")

        XposedBridge.hookMethod(onResumeMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = param.thisObject
                val className = obj.javaClass.name

                Log.d(TAG, "Hooked onResume -> $className")

                if (className in targetClasses) {
                    try {
                        XposedHelpers.callMethod(obj, "dismiss")
                        Log.d(TAG, "Dismissed successfully -> $className")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to dismiss -> $className", e)
                    }
                }
            }
        })
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to hook Fragment.onResume()", e)
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