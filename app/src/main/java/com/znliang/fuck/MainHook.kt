package com.znliang.fuck

import com.znliang.fuck.iyifan.hookIYiFan
import com.znliang.fuck.soul.hookSoul
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val TAG = "FUCK"

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookSoul(lpparam)
        hookIYiFan(lpparam)
    }
}
