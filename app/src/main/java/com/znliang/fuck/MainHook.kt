package com.znliang.fuck

import com.znliang.fuck.iyifan.hookIYiFan
import com.znliang.fuck.soul.hookSoul
import com.znliang.fuck.utils.initHooks
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val TAG = "FUCK"

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return

        when (lpparam.packageName) {
            "cn.soulapp.android" -> hookSoul(lpparam)
            "com.cqcsy.ifvod" -> hookIYiFan(lpparam)
        }
        initHooks(lpparam)
    }
}
