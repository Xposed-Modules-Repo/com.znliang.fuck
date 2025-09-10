package com.znliang.fuck.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private val mainHandler = Handler(Looper.getMainLooper())

fun hookActivities(
    lpparam: XC_LoadPackage.LoadPackageParam,
    block: ((className: String, activity: Any) -> Unit)? = null
) {
    XposedHelpers.findAndHookMethod(
        "android.app.Activity",
        lpparam.classLoader,
        "onResume",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val className = activity.javaClass.name
                Log.d("$TAG:Activity", "Activity onResume(): $className")

                block?.invoke(className, activity)

                if (className.contains("splash", ignoreCase = true)) {
                    // 延迟点击，确保布局绘制完成
                    mainHandler.postDelayed({
                        autoClickSkip(activity)
                    }, 300)
                }
            }
        }
    )
}

fun autoClickSkip(activity: Activity, skipKeywords: List<String> = listOf("跳过", "skip")) {
    val rootView = activity.window.decorView as? ViewGroup ?: return
    val skipButton = findSkipButton(rootView, skipKeywords)
    skipButton?.performClick()
    Log.d("$TAG:Activity", "Auto clicked skip button: ${skipButton?.javaClass?.name}")
}

fun findSkipButton(view: View, skipKeywords: List<String>): View? {
    if (view is Button || view is TextView) {
        val text = view.text?.toString()?.lowercase() ?: ""
        if (skipKeywords.any { keyword -> text.contains(keyword.lowercase()) }) {
            return view
        }
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findSkipButton(view.getChildAt(i), skipKeywords)
            if (found != null) return found
        }
    }
    return null
}