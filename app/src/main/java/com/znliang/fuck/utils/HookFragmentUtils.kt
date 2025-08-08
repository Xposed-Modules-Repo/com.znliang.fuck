package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookFragments(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "androidx.fragment.app.Fragment",
        lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject
                val className = fragment.javaClass.name
                Log.d(TAG, "Fragment onResume(): $className")
            }
        }
    )
}