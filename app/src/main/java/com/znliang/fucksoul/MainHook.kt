package com.znliang.fucksoul

import com.znliang.fucksoul.soul.hookSoul
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val TAG = "FUCK"

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookSoul(lpparam)
    }
}
