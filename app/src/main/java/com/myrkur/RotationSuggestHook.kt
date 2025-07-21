package com.myrkur

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class RotationSuggestHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        XposedBridge.log("RotationSuggest: starting hooks")
        Log.d("RotationSuggest","starting hooks")
        try {
            SystemUiHookRegistry.HookRegistry.allHooks().forEach { hook ->
                Log.d("RotationSuggest", "Applying Hook ${hook.hookName} to ${hook.methodName}")
                XposedBridge.log("RotationSuggest: Applying Hook ${hook.hookName} to ${hook.methodName}")
                hook.apply(lpparam)
            }
        } catch (t: Throwable) {
            XposedBridge.log("RotationSuggest: Error hooking SystemUI")
            XposedBridge.log(t)
        }
    }
}