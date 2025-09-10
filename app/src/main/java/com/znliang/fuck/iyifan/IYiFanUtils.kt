package com.znliang.fuck.iyifan

import android.util.Log
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.hookActivities
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook IYiFan
 */
fun hookIYiFan(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "com.cqcsy.ifvod") return
    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
    val classLoader = lpparam.classLoader
    hookVideoItemBean(classLoader)
    hookUserInfoBean(classLoader)
    hookActivities(lpparam)
}

/**
 * Hook VideoItemBean
 */
private fun hookVideoItemBean(classLoader: ClassLoader) {
    val videoItemClass = "com.ppde.ppcd.video.bean.VideoItemBean"

    XposedHelpers.findAndHookMethod(
        videoItemClass,
        classLoader,
        "isVip",
        XC_MethodReplacement.returnConstant(true)
    )
    XposedHelpers.findAndHookMethod(
        videoItemClass,
        classLoader,
        "getEnbale",
        XC_MethodReplacement.returnConstant(true)
    )
    XposedHelpers.findAndHookMethod(
        videoItemClass,
        classLoader,
        "setVip",
        Boolean::class.javaPrimitiveType,
        XC_MethodReplacement.DO_NOTHING
    )
    XposedHelpers.findAndHookMethod(
        videoItemClass,
        classLoader,
        "setEnbale",
        Boolean::class.javaPrimitiveType,
        XC_MethodReplacement.DO_NOTHING
    )
}

/**
 * Hook UserInfoBean
 */
private fun hookUserInfoBean(classLoader: ClassLoader) {
    val userInfoClass = "com.ppde.library.bean.UserInfoBean"

    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "isGiveVip",
        XC_MethodReplacement.returnConstant(true)
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "getVipLevel",
        XC_MethodReplacement.returnConstant(10)
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "getBigV",
        XC_MethodReplacement.returnConstant(true)
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "isEnable",
        XC_MethodReplacement.returnConstant(true)
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "setGiveVip",
        Boolean::class.javaPrimitiveType,
        XC_MethodReplacement.DO_NOTHING
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "setBigV",
        Boolean::class.javaPrimitiveType,
        XC_MethodReplacement.DO_NOTHING
    )
    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "setEnable",
        Boolean::class.javaPrimitiveType,
        XC_MethodReplacement.DO_NOTHING
    )

    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "setVipLevel",
        Int::class.javaPrimitiveType,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = 10
            }
        }
    )

    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "setEDate",
        String::class.java,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = "2099-02-06T13:53:00Z"
            }
        }
    )

    XposedHelpers.findAndHookMethod(
        userInfoClass,
        classLoader,
        "getEDate",
        object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return "2099-02-06T13:53:00Z"
            }
        }
    )
}