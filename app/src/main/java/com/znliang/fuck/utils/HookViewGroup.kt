package com.znliang.fuck.utils

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * ViewGroup.addView Hook — 广告 View 拦截
 *
 * 扩展关键词匹配 + 尺寸/内容检测 + 更精准过滤
 */

// 广告 View 类名关键词 (完整匹配或包含)
val adViewKeywords = arrayOf(
    // Soul 广告
    "ad.views", "soulad.ad.views",
    "SplashAdView", "ExpressView", "NativeExpressView",
    "LoveView", "FunctionCardView", "BridgeWebViewX5",

    // 穿山甲
    "TTAdSdk", "TTNativeAdView", "TTBannerAdView",
    "TTInteractionAdView", "TTSplashAdView",
    "BDDrawableView", "BDBannerView", "BDNativeView",

    // 优量汇
    "GDTNativeAdView", "GDTBannerAdView",
    "GDTSplashAdView", "GDTMediaView",
    "QQNativeAdView", "BannerAdView",

    // 百度
    "BaiduNativeAdView", "BDBannerAdView",
    "BDSplashAdView", "BaiduMobAdsView",

    // 快手
    "KsAdView", "KsBannerView", "KsSplashView",
    "KsDrawAdView", "KsNativeAdView",

    // 通用广告
    "AdView", "AdLayout", "AdContainer", "AdBanner",
    "AdCard", "AdFeedView", "AdWrapper",
    "BannerView", "BannerLayout", "SplashAdLayout",
    "InterstitialAdView", "RewardedAdView",
    "NativeAdLayout", "NativeAdView",

    // 聚合 SDK
    "TopOnAdView", "AnyThinkAdView",
    "MTGAdView", "MTGBannerView",

    // AppLovin
    "AppLovinAdView",

    // 通用标记
    "ad_container", "ad_wrapper", "ad_banner",
    "ad_card", "ad_layout", "ad_frame",
    "aditem", "ad_cell", "ad_row",

    // Flutter 广告
    "FlutterAdView", "NativeAdFlutterView",
)

// 广告 View 可能的文本特征
private val adTextKeywords = arrayOf(
    "广告", "AD", "ad", "sponsored", "推广", "赞助",
    "下载", "立即下载", "免费下载", "安装",
    "了解详情", "查看详情", "去看看",
    "立即购买", "领取优惠", "限时优惠",
    "点击下载", "下载应用",
)

// 白名单 — 不拦截的 View 类名前缀
private val whitelistPrefixes = arrayOf(
    "android.", "androidx.", "com.google.android.",
    "com.google.android.material",
)

// 白名单 — 不拦截的具体类名
private val whitelistExactClasses = mutableSetOf<String>()

/**
 * 注册白名单类名 (供外部调用)
 */
fun addToViewWhitelist(className: String) {
    whitelistExactClasses.add(className)
}

// ----------------------------------------------------
// Hook 容器类
// ----------------------------------------------------
private val containerClasses = arrayOf(
    "android.view.ViewGroup",
    "android.widget.FrameLayout",
    "android.widget.LinearLayout",
    "android.widget.RelativeLayout",
    "android.widget.ScrollView",
    "androidx.constraintlayout.widget.ConstraintLayout",
    "androidx.recyclerview.widget.RecyclerView",
    "androidx.viewpager.widget.ViewPager",
    "androidx.viewpager2.widget.ViewPager2",
)

private val addViewSignatures = arrayOf(
    arrayOf<Any?>(View::class.java),
    arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType),
    arrayOf<Any?>(View::class.java, ViewGroup.LayoutParams::class.java),
    arrayOf<Any?>(View::class.java, Int::class.javaPrimitiveType, ViewGroup.LayoutParams::class.java),
)

// ----------------------------------------------------
// 入口
// ----------------------------------------------------
fun hookAllCustomViews(
    lpparam: XC_LoadPackage.LoadPackageParam,
    block: ((className: String, view: View) -> Unit)? = null
) {
    for (clsName in containerClasses) {
        try {
            val cls = XposedHelpers.findClass(clsName, lpparam.classLoader)

            for (params in addViewSignatures) {
                try {
                    XposedHelpers.findAndHookMethod(
                        cls, "addView", *params,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val child = param.args[0] as? View ?: return
                                val className = child.javaClass.name

                                // 白名单过滤
                                if (isWhitelisted(className)) return

                                // 关键词匹配
                                if (isAdView(className, child)) {
                                    child.visibility = View.GONE
                                    log("移除广告 View: $className")
                                    return
                                }

                                // 传递给外部回调
                                if (!isSystemView(className)) {
                                    block?.invoke(className, child)
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            log("Hook 容器类失败: $clsName")
        }
    }

    // 额外 Hook: RecyclerView Adapter 的广告检测
    hookRecyclerViewAdapter(lpparam)
}

// ----------------------------------------------------
// 广告 View 判断
// ----------------------------------------------------
private fun isAdView(className: String, view: View): Boolean {
    // 1. 类名关键词匹配
    if (adViewKeywords.any { className.contains(it, ignoreCase = true) }) {
        return true
    }

    // 2. View 的 tag 包含广告标记
    val tag = view.tag?.toString() ?: ""
    if (tag.contains("ad", ignoreCase = true) ||
        tag.contains("广告", ignoreCase = true) ||
        tag.contains("banner", ignoreCase = true)
    ) {
        return true
    }

    // 3. contentDescription 包含广告标记
    val desc = view.contentDescription?.toString() ?: ""
    if (adTextKeywords.any { desc.contains(it, ignoreCase = true) }) {
        return true
    }

    return false
}

private fun isWhitelisted(className: String): Boolean {
    if (whitelistExactClasses.contains(className)) return true
    return whitelistPrefixes.any { className.startsWith(it) }
}

private fun isSystemView(className: String): Boolean {
    return whitelistPrefixes.any { className.startsWith(it) }
}

// ----------------------------------------------------
// RecyclerView 广告项检测
// ----------------------------------------------------
private fun hookRecyclerViewAdapter(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val vhClass = XposedHelpers.findClass(
            "androidx.recyclerview.widget.RecyclerView\$ViewHolder",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(
            "androidx.recyclerview.widget.RecyclerView\$Adapter",
            lpparam.classLoader,
            "onBindViewHolder",
            vhClass, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val holder = param.args[0]
                    val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View
                        ?: return
                    val className = itemView.javaClass.name

                    if (!isWhitelisted(className) && isAdView(className, itemView)) {
                        itemView.visibility = View.GONE
                        log("RecyclerView 移除广告项: $className")
                    }
                }
            }
        )
        log("RecyclerView Adapter Hook 成功")
    } catch (t: Throwable) {
        log("RecyclerView Adapter Hook 失败: ${t.message}")
    }
}
