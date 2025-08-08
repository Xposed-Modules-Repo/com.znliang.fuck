package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookActivities(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Activity",
        lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject
                val className = activity.javaClass.name
                Log.d(TAG, "Activity onResume(): $className")
            }
        }
    )
}
