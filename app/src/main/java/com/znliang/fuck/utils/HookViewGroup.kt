package com.znliang.fuck.utils

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private val containerClasses = arrayOf(
    "android.view.ViewGroup",
    "android.widget.FrameLayout",
    "android.widget.LinearLayout",
    "android.widget.RelativeLayout",
    "android.widget.ScrollView",
    "androidx.constraintlayout.widget.ConstraintLayout"
)

private val addViewSignatures = arrayOf(
    arrayOf<Any?>(View::class.java),
    arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType),
    arrayOf<Any?>(View::class.java, ViewGroup.LayoutParams::class.java),
    arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType, ViewGroup.LayoutParams::class.java)
)

fun hookAllCustomViews(
    lpparam: XC_LoadPackage.LoadPackageParam,
    block: ((className: String, view: View) -> Unit)? = null
) {
    for (clsName in containerClasses) {
        try {
            val cls = XposedHelpers.findClass(clsName, lpparam.classLoader)

            for (params in addViewSignatures) {
                try {
                    XposedHelpers.findAndHookMethod(
                        cls,
                        "addView",
                        *params,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val child = param.args[0] as? View ?: return
                                val className = child.javaClass.name

                                // 过滤系统和常见库，打印自定义 View
                                if (!(className.startsWith("android.") ||
                                            className.startsWith("androidx.") ||
                                            className.startsWith("com.google.android.")
                                            )) {
                                    Log.d("$TAG:ViewGroup", "Custom View added: $className")
                                    block?.invoke(className, child)
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {
                    // 某些重载可能不存在，直接跳过
                }
            }
        } catch (_: Throwable) {
            Log.e("$TAG:ViewGroup", "Failed to hook $clsName")
        }
    }
}

