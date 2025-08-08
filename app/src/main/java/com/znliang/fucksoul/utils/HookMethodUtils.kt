package com.znliang.fucksoul.utils

import android.util.Log
import com.znliang.fucksoul.TAG
import de.robv.android.xposed.XposedHelpers


fun printAllMethods(className: String, classLoader: ClassLoader) {
    try {
        val clazz = XposedHelpers.findClass(className, classLoader)
        val methods = clazz.declaredMethods
        Log.d(TAG, "Methods of $className:")
        for (method in methods) {
            Log.d(TAG, "  ${method.name}(${method.parameterTypes.joinToString()}) -> ${method.returnType.simpleName}")
        }
    } catch (e: Throwable) {
        Log.d(TAG, "Failed to print methods for $className", e)
    }
}