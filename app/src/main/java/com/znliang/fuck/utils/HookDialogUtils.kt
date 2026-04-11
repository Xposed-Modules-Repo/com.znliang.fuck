package com.znliang.fuck.utils

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 精准弹窗拦截
 *
 * 不再一刀切拦截所有 Dialog.show(),
 * 而是根据弹窗类名/内容判断是否为广告弹窗
 */

// 广告弹窗类名特征
private val adDialogKeywords = arrayOf(
    // 穿山甲
    "TTAdDialog", "TTInteractionAd", "TTFullScreenVideo",
    "BDDialog", "BDAdDialog",
    // 优量汇
    "GDTAdDialog", "QQAdDialog",
    // 百度
    "BaiduAdDialog", "BdAdDialog",
    // 快手
    "KsAdDialog", "KsFullScreenVideo",
    // 通用
    "AdDialog", "AdPopup", "AdPrompt",
    "BannerDialog", "SplashDialog",
    "InterstitialDialog", "RewardDialog",
    "FullScreenVideoDialog",
    // 弹窗推广
    "PromotionDialog", "UpgradeDialog",
    "RateDialog", "RatingDialog",
    // Soul 特定
    "LimitGiftDialog", "GiftDialog", "LimitDialog",
    "DailyLimitDialog", "ChatLimitDialog",
)

// 弹窗内部广告文本特征 (用于内容检测)
private val adDialogContentKeywords = arrayOf(
    "立即下载", "下载应用", "安装应用",
    "限时优惠", "领取优惠", "立即领取",
    "去购买", "去逛逛", "去看看",
    "开通VIP", "开通会员", "立即开通",
    "赠送礼物", "送礼物", "送礼",
    "购买金币", "充值", "立即充值",
)

// 白名单 — 不拦截的弹窗类名
private val whitelistDialogClasses = arrayOf(
    "android.app.AlertDialog", "android.app.ProgressDialog",
    "android.app.DatePickerDialog", "android.app.TimePickerDialog",
    "android.content.DialogInterface",
)

// 外部注册的弹窗拦截回调
private val dialogBlockCallbacks = mutableListOf<(className: String, dialog: Any) -> Boolean>()

/**
 * 注册自定义弹窗拦截回调
 * 返回 true 表示拦截该弹窗
 */
fun registerDialogInterceptor(block: (className: String, dialog: Any) -> Boolean) {
    dialogBlockCallbacks.add(block)
}

// ----------------------------------------------------
// 入口
// ----------------------------------------------------
fun hookDialogs(lpparam: XC_LoadPackage.LoadPackageParam) {
    hookAndroidDialog(lpparam)
}

// ----------------------------------------------------
// Hook android.app.Dialog.show()
// ----------------------------------------------------
private fun hookAndroidDialog(lpparam: XC_LoadPackage.LoadPackageParam) {
    XposedHelpers.findAndHookMethod(
        "android.app.Dialog",
        lpparam.classLoader,
        "show",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val dialog = param.thisObject
                val className = dialog.javaClass.name

                Log.d("$TAG:Dialog", "Dialog.show(): $className")

                // 1. 白名单检查 — 不拦截
                if (isWhitelistedDialog(className)) return

                // 2. 类名关键词匹配
                if (isAdDialogByClassName(className)) {
                    dismissDialog(dialog, className)
                    param.result = null
                    return
                }

                // 3. 外部回调检查
                for (callback in dialogBlockCallbacks) {
                    if (callback(className, dialog)) {
                        dismissDialog(dialog, className)
                        param.result = null
                        return
                    }
                }

                // 4. 内容检测 (检查弹窗内是否有广告文本)
                if (isAdDialogByContent(dialog)) {
                    dismissDialog(dialog, className)
                    param.result = null
                    return
                }
            }
        }
    )
}

// ----------------------------------------------------
// Hook AlertDialog.Builder.setMessage/setTitle
// 用于提前记录弹窗内容
// ----------------------------------------------------
fun hookAlertDialogBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val builderClass = XposedHelpers.findClass(
            "android.app.AlertDialog\$Builder", lpparam.classLoader
        )
        for (method in arrayOf("setMessage", "setTitle")) {
            try {
                XposedHelpers.findAndHookMethod(
                    builderClass, method, CharSequence::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val msg = param.args[0]?.toString() ?: return
                            if (adDialogContentKeywords.any { msg.contains(it) }) {
                                Log.d("$TAG:Dialog", "AlertDialog.$method 包含广告内容: $msg")
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}
}

// ----------------------------------------------------
// 判断方法
// ----------------------------------------------------
private fun isWhitelistedDialog(className: String): Boolean {
    return whitelistDialogClasses.any { className == it || className.startsWith(it) }
}

private fun isAdDialogByClassName(className: String): Boolean {
    return adDialogKeywords.any { className.contains(it, ignoreCase = true) }
}

private fun isAdDialogByContent(dialog: Any): Boolean {
    return try {
        val decorView = XposedHelpers.callMethod(
            XposedHelpers.callMethod(dialog, "getWindow"),
            "getDecorView"
        ) as? ViewGroup ?: return false

        val textContent = collectAllText(decorView)
        adDialogContentKeywords.any { textContent.contains(it) }
    } catch (_: Throwable) {
        false
    }
}

/**
 * 递归收集 ViewGroup 内所有 TextView 的文本
 */
private fun collectAllText(viewGroup: ViewGroup): String {
    val sb = StringBuilder()
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i) ?: continue
        if (child is TextView) {
            child.text?.toString()?.let { sb.append(it).append(" ") }
        }
        if (child is ViewGroup) {
            sb.append(collectAllText(child))
        }
    }
    return sb.toString()
}

private fun dismissDialog(dialog: Any, className: String) {
    try {
        XposedHelpers.callMethod(dialog, "dismiss")
        Log.d("$TAG:Dialog", "已关闭广告弹窗: $className")
    } catch (e: Throwable) {
        Log.d("$TAG:Dialog", "关闭弹窗失败: $className", e)
    }
}
