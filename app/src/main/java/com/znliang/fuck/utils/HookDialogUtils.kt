package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookDialogs(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Dialog",
        lpparam.classLoader,
        "show",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val dialog = param.thisObject
                val className = dialog.javaClass.name
                Log.d(TAG, "Dialog show(): $className")
                try {
                    XposedHelpers.callMethod(dialog, "dismiss")
                    Log.d(TAG, "Dismissed Dialog: $className")
                } catch (e: Throwable) {
                    Log.d(TAG, "Failed to dismiss Dialog: $className", e)
                }
            }
        }
    )
}