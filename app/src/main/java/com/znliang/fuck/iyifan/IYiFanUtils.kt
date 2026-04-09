package com.znliang.fuck.iyifan

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook IYiFan
 */
fun hookIYiFan(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
    val classLoader = lpparam.classLoader
    hookVideoItemBean(classLoader)
    hookUserInfoBean(classLoader)
}

/**
 * Hook VideoItemBean
 */
private fun hookVideoItemBean(classLoader: ClassLoader) {
    val clazz = XposedHelpers.findClass("com.ppde.ppcd.video.bean.VideoItemBean", classLoader)

    XposedHelpers.findAndHookMethod(clazz, "isVip", XC_MethodReplacement.returnConstant(true))
    XposedHelpers.findAndHookMethod(clazz, "getEnbale", XC_MethodReplacement.returnConstant(true))
    XposedHelpers.findAndHookMethod(clazz, "setVip", Boolean::class.javaPrimitiveType, XC_MethodReplacement.DO_NOTHING)
    XposedHelpers.findAndHookMethod(clazz, "setEnbale", Boolean::class.javaPrimitiveType, XC_MethodReplacement.DO_NOTHING)
}

/**
 * Hook UserInfoBean
 */
private fun hookUserInfoBean(classLoader: ClassLoader) {
    val clazz = XposedHelpers.findClass("com.ppde.library.bean.UserInfoBean", classLoader)

    XposedHelpers.findAndHookMethod(clazz, "isGiveVip", XC_MethodReplacement.returnConstant(true))
    XposedHelpers.findAndHookMethod(clazz, "getVipLevel", XC_MethodReplacement.returnConstant(10))
    XposedHelpers.findAndHookMethod(clazz, "getBigV", XC_MethodReplacement.returnConstant(true))
    XposedHelpers.findAndHookMethod(clazz, "isEnable", XC_MethodReplacement.returnConstant(true))
    XposedHelpers.findAndHookMethod(clazz, "setGiveVip", Boolean::class.javaPrimitiveType, XC_MethodReplacement.DO_NOTHING)
    XposedHelpers.findAndHookMethod(clazz, "setBigV", Boolean::class.javaPrimitiveType, XC_MethodReplacement.DO_NOTHING)
    XposedHelpers.findAndHookMethod(clazz, "setEnable", Boolean::class.javaPrimitiveType, XC_MethodReplacement.DO_NOTHING)

    XposedHelpers.findAndHookMethod(clazz, "setVipLevel", Int::class.javaPrimitiveType,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = 10
            }
        }
    )

    XposedHelpers.findAndHookMethod(clazz, "setEDate", String::class.java,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = "2099-02-06T13:53:00Z"
            }
        }
    )

    XposedHelpers.findAndHookMethod(clazz, "getEDate",
        XC_MethodReplacement.returnConstant("2099-02-06T13:53:00Z")
    )
}