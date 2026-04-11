package com.znliang.fuck.utils

import android.util.Log
import com.znliang.fuck.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 网络层广告拦截
 *
 * 通过 Hook OkHttp + WebView 拦截广告请求
 */
object HookNetworkUtils {

    // ----------------------------------------------------
    // 广告域名黑名单
    // ----------------------------------------------------
    private val adDomains = arrayOf(
        // 穿山甲
        "ad.toutiao.com", "ad.snssdk.com", "pangolin-sdk-toutiao.com",
        "is.snssdk.com", "nativeapp.toutiao.com",
        // 优量汇
        "qzs.gdtimg.com", "mi.gdt.qq.com", "pgdt.ugdtimg.com",
        "ad.qunliaotong.com",
        // 百度
        "mobads.baidu.com", "pos.baidu.com", "cpro.baidu.com",
        "cbjs.baidu.com", "hm.baidu.com",
        // 快手
        "ad.kuaishou.com", "e.kuaishou.com",
        // AppLovin
        "rt.applovin.com", "ms.applovin.com", "d.applovin.com",
        // Unity Ads
        "ads.unity3d.com", "publisher.unityads.unity3d.com",
        // IronSource
        "logs.ironsrc.mobi", "config.ironsrc.mobi",
        // Google Ads
        "admob.google.com", "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net", "adservice.google.com",
        "ads.google.com",
        // Mintegral
        "sdk.mintegral.com", "cdn.mintegral.com",
        // TopOn
        "sdk.toponad.com",
        // SIGMob
        "api.sigmob.cn",
        // 通用广告追踪
        "adskeeper.com", "adcolony.com", "vungle.com",
        "chartboost.com", "inmobi.com", "startapp.com",
        // 国内通用
        "adsmogo.com", "adview.cn", "tanx.com", "mmstat.com",
        "umeng.com", "umengcloud.com",
    )

    // ----------------------------------------------------
    // 广告 URL 路径特征
    // ----------------------------------------------------
    private val adUrlPatterns = arrayOf(
        "/ad/", "/ads/", "/adv/", "/advertisement/",
        "/banner", "/popup", "/splash",
        "/track", "/tracking", "/impression", "/click",
        "/sdk/ad", "/api/ad", "/v1/ad", "/v2/ad",
        "ad_type=", "ad_type_id=", "adslot=", "adunit=",
        "placement_id=", "ad_id=",
    )

    // ----------------------------------------------------
    // 入口
    // ----------------------------------------------------
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("HookNetworkUtils: 开始 Hook 网络层...")
        hookOkHttp(lpparam)
        hookWebView(lpparam)
    }

    // ----------------------------------------------------
    // OkHttp 拦截
    // ----------------------------------------------------
    private fun hookOkHttp(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        // Hook OkHttp RealCall.execute (同步请求)
        try {
            val realCallClass = XposedHelpers.findClassIfExists(
                "okhttp3.RealCall", classLoader
            )
            if (realCallClass != null) {
                hookOkHttpCall(realCallClass)
                log("HookNetworkUtils: OkHttp RealCall Hook 成功")
            }
        } catch (t: Throwable) {
            log("HookNetworkUtils: OkHttp Hook 失败: ${t.message}")
        }

        // Hook OkHttp3 CallFactory / OkHttpClient.newCall
        try {
            val clientClass = XposedHelpers.findClassIfExists(
                "okhttp3.OkHttpClient", classLoader
            )
            if (clientClass != null) {
                XposedHelpers.findAndHookMethod(
                    clientClass, "newCall",
                    XposedHelpers.findClass("okhttp3.Request", classLoader),
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val request = param.args[0]
                            val url = XposedHelpers.callMethod(request, "url").toString()
                            if (isAdUrl(url)) {
                                log("OkHttp 拦截广告请求: $url")
                                param.result = null
                            }
                        }
                    }
                )
                log("HookNetworkUtils: OkHttpClient.newCall Hook 成功")
            }
        } catch (t: Throwable) {
            log("HookNetworkUtils: OkHttpClient Hook 失败: ${t.message}")
        }

        // Hook HttpURLConnection (通用网络请求兜底)
        hookHttpURLConnection(classLoader)
    }

    private fun hookOkHttpCall(realCallClass: Class<*>) {
        // Hook execute() 同步请求
        try {
            XposedHelpers.findAndHookMethod(
                realCallClass, "execute",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val request = XposedHelpers.callMethod(
                            XposedHelpers.callMethod(param.thisObject, "request"),
                            "url"
                        ).toString()
                        if (isAdUrl(request)) {
                            log("OkHttp execute 拦截: $request")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook enqueue() 异步请求
        try {
            val callbackClass = XposedHelpers.findClass(
                "okhttp3.Callback", realCallClass.classLoader
            )
            XposedHelpers.findAndHookMethod(
                realCallClass, "enqueue", callbackClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val request = XposedHelpers.callMethod(
                            XposedHelpers.callMethod(param.thisObject, "request"),
                            "url"
                        ).toString()
                        if (isAdUrl(request)) {
                            log("OkHttp enqueue 拦截: $request")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    // ----------------------------------------------------
    // HttpURLConnection 拦截 (兜底)
    // ----------------------------------------------------
    private fun hookHttpURLConnection(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "java.net.URL", classLoader,
                "openConnection",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.thisObject.toString()
                        if (isAdUrl(url)) {
                            log("HttpURLConnection 拦截: $url")
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            log("HookNetworkUtils: HttpURLConnection Hook 失败: ${t.message}")
        }
    }

    // ----------------------------------------------------
    // WebView 广告拦截
    // ----------------------------------------------------
    private fun hookWebView(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        // Hook WebView.loadUrl
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebView", classLoader,
                "loadUrl", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args[0] as String
                        if (isAdUrl(url)) {
                            log("WebView.loadUrl 拦截: $url")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook WebView.loadUrl(String, Map)
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebView", classLoader,
                "loadUrl", String::class.java, java.util.Map::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args[0] as String
                        if (isAdUrl(url)) {
                            log("WebView.loadUrl(headers) 拦截: $url")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook WebView.postUrl
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebView", classLoader,
                "postUrl", String::class.java, ByteArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args[0] as String
                        if (isAdUrl(url)) {
                            log("WebView.postUrl 拦截: $url")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook X5 WebView (腾讯内核)
        try {
            val x5WebView = XposedHelpers.findClassIfExists(
                "com.tencent.smtt.sdk.WebView", classLoader
            )
            if (x5WebView != null) {
                XposedHelpers.findAndHookMethod(
                    x5WebView, "loadUrl", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val url = param.args[0] as String
                            if (isAdUrl(url)) {
                                log("X5 WebView.loadUrl 拦截: $url")
                                param.result = null
                            }
                        }
                    }
                )
                log("HookNetworkUtils: X5 WebView Hook 成功")
            }
        } catch (_: Throwable) {}

        // Hook WebViewClient.shouldOverrideUrlLoading (拦截重定向广告)
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebViewClient", classLoader,
                "shouldOverrideUrlLoading",
                "android.webkit.WebView",
                "android.webkit.WebResourceRequest",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val request = param.args[1]
                        val url = XposedHelpers.callMethod(request, "getUrl").toString()
                        if (isAdUrl(url)) {
                            log("WebView 重定向拦截: $url")
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    // ----------------------------------------------------
    // 广告 URL 判断
    // ----------------------------------------------------
    private fun isAdUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val lowerUrl = url.lowercase()

        // 1. 域名匹配
        if (adDomains.any { lowerUrl.contains(it) }) return true

        // 2. 路径特征匹配
        if (adUrlPatterns.any { lowerUrl.contains(it) }) return true

        return false
    }
}
