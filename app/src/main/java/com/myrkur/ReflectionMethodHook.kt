package com.myrkur

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ReflectionMethodHook(
    val hookName: String,
    val className: String,
    val methodName: String,
    val parameterTypes: Array<Class<*>>,
    val beforeHook: ((param: XC_MethodHook.MethodHookParam) -> Unit)? = null,
    val afterHook: ((param: XC_MethodHook.MethodHookParam) -> Unit)? = null
) {
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            className,
            lpparam.classLoader,
            methodName,
            *parameterTypes,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    beforeHook?.invoke(param)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    afterHook?.invoke(param)
                }
            }
        )
    }
}
