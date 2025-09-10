package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun hookFragments(
    lpparam: XC_LoadPackage.LoadPackageParam,
    block: ((className: String, obj: Any) -> Unit)? = null
) {
    val fragmentClasses = listOf(
        "androidx.fragment.app.Fragment",
        "android.app.Fragment"
    )

    fragmentClasses.forEach { className ->
        try {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
            if (clazz != null) {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "onResume",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val fragment = param.thisObject
                            val name = fragment.javaClass.name
                            Log.d("$TAG:Fragment", "Hooked Fragment onResume(): $name")
                            block?.invoke(name, fragment)
                        }
                    }
                )
                Log.d("$TAG:Fragment", "Successfully hooked $className.onResume()")
            } else {
                Log.d("$TAG:Fragment", "Class not found: $className")
            }
        } catch (e: Throwable) {
            Log.e("$TAG:Fragment", "Failed to hook $className.onResume()", e)
        }
    }
}