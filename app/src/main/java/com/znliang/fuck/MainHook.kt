package com.znliang.fuck

import com.znliang.fuck.bodian.hookBodian
import com.znliang.fuck.ctrip.hookCtrip
import com.znliang.fuck.iyifan.hookIYiFan
import com.znliang.fuck.soul.hookSoul
import com.znliang.fuck.utils.HookAdSdkUtils
import com.znliang.fuck.utils.HookNetworkUtils
import com.znliang.fuck.utils.initHooks
import com.znliang.fuck.xhs.hookXhs
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val TAG = "FUCK"

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return

        when (lpparam.packageName) {
            "cn.soulapp.android" -> hookSoul(lpparam)
            "com.cqcsy.ifvod" -> hookIYiFan(lpparam)
            "cn.wenyu.bodian" -> hookBodian(lpparam)
            "ctrip.android.view" -> hookCtrip(lpparam)
            "com.xingin.xhs" -> hookXhs(lpparam)
        }

        // 通用 Hook (所有 App 生效)
        initHooks(lpparam)
        HookAdSdkUtils.hook(lpparam)
        HookNetworkUtils.hook(lpparam)
    }
}
