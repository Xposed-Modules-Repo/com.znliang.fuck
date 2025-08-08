package com.znliang.fuck.iyifan

import android.util.Log
import com.znliang.fuck.TAG
import com.znliang.fuck.utils.printAllMethods
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook iYIFAN
 */
fun hookIYiFan(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName != "com.cqcsy.ifvod") return
    Log.d(TAG, "Loaded package: ${lpparam.packageName} in process ${lpparam.processName}")
    val classLoader = lpparam.classLoader
    hookVideoItemBean(classLoader)
    hookUserInfoBean(classLoader)
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


    // --- 关键：hook解析响应的解析器com.base.library.net.parser.a ---
//    val parserClass = "com.base.library.net.parser.a"
//    XposedHelpers.findAndHookMethod(parserClass, classLoader, "a", Object::class.java, object : XC_MethodHook() {
//        override fun afterHookedMethod(param: MethodHookParam) {
//            val response = param.args[0]
//            val parsedObj = param.result ?: return
//            val clazz = parsedObj.javaClass
//            try {
//                // 反射修改字段
//                val eDateField = clazz.getDeclaredField("eDate")
//                eDateField.isAccessible = true
//                eDateField.set(parsedObj, "2099-02-06T13:53:00Z")
//
//                val nickNameField = clazz.getDeclaredField("nickName")
//                nickNameField.isAccessible = true
//                nickNameField.set(parsedObj, "Hooked昵称")
//
//                val vipLevelField = clazz.getDeclaredField("vipLevel")
//                vipLevelField.isAccessible = true
//                vipLevelField.set(parsedObj, 10)
//
//                val giveVipField = clazz.getDeclaredField("isGiveVip")
//                giveVipField.isAccessible = true
//                giveVipField.set(parsedObj, true)
//
//                val bigVField = clazz.getDeclaredField("bigV")
//                bigVField.isAccessible = true
//                bigVField.set(parsedObj, true)
//
//                val enableField = clazz.getDeclaredField("isEnable")
//                enableField.isAccessible = true
//                enableField.set(parsedObj, true)
//
//                // 如果有其他字段，比如 fansCount、attentionCount 等
//                // 也可以类似操作
//            } catch (e: Exception) {
//                Log.e(TAG, "修改UserInfoBean字段失败", e)
//            }
//        }
//    })

