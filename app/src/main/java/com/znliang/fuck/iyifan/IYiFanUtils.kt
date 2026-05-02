package com.znliang.fuck.iyifan

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

// 静态单例，避免每次 hook 创建匿名类实例
private val TRUE_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = true
}
private val FALSE_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = false
}
private val BLOCK_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = null
}
private val VIP_LEVEL_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = 10
}
private val OVERRIDE_LEVEL_HOOK = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = 10 }
}

private const val FAR_FUTURE = "2099-02-06T13:53:00Z"
private const val FAR_FUTURE2 = "2099-12-31T23:59:59Z"
private const val PAST = "2020-01-01T00:00:00Z"

private val EDATE_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = FAR_FUTURE
}
private val OVERRIDE_EDATE_HOOK = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = FAR_FUTURE }
}
private val BVEND_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = FAR_FUTURE2
}
private val OVERRIDE_BVEND_HOOK = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = FAR_FUTURE2 }
}
private val BVBEGIN_HOOK = object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam): Any? = PAST
}
private val OVERRIDE_BVBEGIN_HOOK = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = PAST }
}

fun hookIYiFan(lpparam: XC_LoadPackage.LoadPackageParam) {
    val cl = lpparam.classLoader
    hookVideoItemBean(cl)
    hookClarityBean(cl)
    hookUserInfoBean(cl)
}

private fun hookVideoItemBean(cl: ClassLoader) {
    try {
        val c = XposedHelpers.findClass("com.ppde.ppcd.video.bean.VideoItemBean", cl)
        arrayOf("isVip", "getEnbale", "getEnable").forEach { m ->
            try { XposedHelpers.findAndHookMethod(c, m, TRUE_HOOK) } catch (_: Throwable) {}
        }
        arrayOf("setVip", "setEnbale", "setEnable").forEach { m ->
            try { XposedHelpers.findAndHookMethod(c, m, Boolean::class.javaPrimitiveType, BLOCK_HOOK) } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}
}

private fun hookClarityBean(cl: ClassLoader) {
    try {
        val c = XposedHelpers.findClass("com.ppde.ppcd.video.bean.ClarityBean", cl)
        try { XposedHelpers.findAndHookMethod(c, "isVip", FALSE_HOOK) } catch (_: Throwable) {}
        try { XposedHelpers.findAndHookMethod(c, "isBoughtByCoin", TRUE_HOOK) } catch (_: Throwable) {}
        try { XposedHelpers.findAndHookMethod(c, "setVip", Boolean::class.javaPrimitiveType, BLOCK_HOOK) } catch (_: Throwable) {}
        try { XposedHelpers.findAndHookMethod(c, "setBoughtByCoin", Boolean::class.javaPrimitiveType, BLOCK_HOOK) } catch (_: Throwable) {}
    } catch (_: Throwable) {}
}

private fun hookUserInfoBean(cl: ClassLoader) {
    try {
        val c = XposedHelpers.findClass("com.ppde.library.bean.UserInfoBean", cl)

        arrayOf("isGiveVip", "getBigV", "isEnable").forEach { m ->
            XposedHelpers.findAndHookMethod(c, m, TRUE_HOOK)
        }
        arrayOf("setGiveVip", "setBigV", "setEnable").forEach { m ->
            XposedHelpers.findAndHookMethod(c, m, Boolean::class.javaPrimitiveType, BLOCK_HOOK)
        }

        XposedHelpers.findAndHookMethod(c, "getVipLevel", VIP_LEVEL_HOOK)
        XposedHelpers.findAndHookMethod(c, "setVipLevel", Int::class.javaPrimitiveType, OVERRIDE_LEVEL_HOOK)
        XposedHelpers.findAndHookMethod(c, "getEDate", EDATE_HOOK)
        XposedHelpers.findAndHookMethod(c, "setEDate", String::class.java, OVERRIDE_EDATE_HOOK)
        XposedHelpers.findAndHookMethod(c, "getBigVEndTime", BVEND_HOOK)
        XposedHelpers.findAndHookMethod(c, "setBigVEndTime", String::class.java, OVERRIDE_BVEND_HOOK)
        XposedHelpers.findAndHookMethod(c, "getBigVBeginTime", BVBEGIN_HOOK)
        XposedHelpers.findAndHookMethod(c, "setBigVBeginTime", String::class.java, OVERRIDE_BVBEGIN_HOOK)
    } catch (_: Throwable) {}
}
