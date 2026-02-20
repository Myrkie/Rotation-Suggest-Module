package com.myrkur

import android.content.Context
import android.os.Binder
import android.os.Handler
import android.util.Log
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class SystemUiHookRegistry {
    object HookRegistry {
        /**
         * Hooks `RotationButtonController#setRotateSuggestionButtonState(boolean, boolean)`
         * Summary: Force enables rotation suggestion on next activation and disables future animation playback.
         */
        fun disableRotateSuggestion(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "disableRotateSuggestion()",
                className = "com.android.systemui.navigationbar.RotationButtonController",
                methodName = "setRotateSuggestionButtonState",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!),
                beforeHook = { param ->
                    param.args[0] = true
                    param.args[1] = false
                }
            )
        }
        /**
         * Hooks `com.android.systemui.navigationbar.RotationButtonController#rescheduleRotationTimeout(boolean)`
         * Summary: cancels entire method allowing rotation icon to show indefinably
         */
        fun disableRotationButtonTimeout(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "disableRotationButtonTimeout()",
                className = "com.android.systemui.navigationbar.RotationButtonController",
                methodName = "rescheduleRotationTimeout",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
                beforeHook = { param ->
                    param.result = null
                }
            )
        }
        /**
         * Hooks KeyButtonDrawable#canAnimate().
         * Summary: Blocks animation if the call comes from RotationContextButton#setVisibility or getNewDrawable.
         */
        fun blockCanAnimateWhenCalledByRotationContext(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "blockCanAnimateWhenCalledByRotationContext()",
                className = "com.android.systemui.navigationbar.buttons.KeyButtonDrawable",
                methodName = "canAnimate",
                parameterTypes = emptyArray(),
                beforeHook = { param ->
                    val stack = Throwable().stackTrace
                    val targetCaller = stack.firstOrNull {
                        it.className == "com.android.systemui.navigationbar.buttons.RotationContextButton" &&
                                (it.methodName == "setVisibility" || it.methodName == "getNewDrawable")
                    }

                    if (targetCaller != null) {
                        Log.d("RotationSuggest", "canAnimate() blocked from ${targetCaller.className}.${targetCaller.methodName}")
                        param.result = false
                    }
                }
            )
        }

        /**
         * Hooks RotationButtonController#onNavigationBarWindowVisibilityChange(boolean).
         * Summary: Hides the rotation suggestion button when the navbar is hidden.
         */
        fun hideSuggestionWhenNavbarHidden(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "hideSuggestionWhenNavbarHidden()",
                className = "com.android.systemui.navigationbar.RotationButtonController",
                methodName = "onNavigationBarWindowVisibilityChange",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
                afterHook = { param ->
                    try {
                        val isNavVisible = param.args[0] as Boolean
                        val controller = param.thisObject
                        val rotationButton = XposedHelpers.getObjectField(controller, "mRotationButton")

                        if (!isNavVisible) {

                            XposedHelpers.callMethod(rotationButton, "hide")

                            Log.d("RotationSuggest","RotationSuggest: Navbar hidden, hiding suggestion button")
                        }else{
                            val handler = XposedHelpers.getObjectField(controller, "mMainThreadHandler") as Handler
                            handler.post {
                                try {
                                    XposedHelpers.callMethod(controller, "setRotateSuggestionButtonState", true)
                                    Log.d("RotationSuggest", "Navbar visible, showing suggestion button")
                                } catch (e: Throwable) {
                                    Log.e("RotationSuggest", "Error showing suggestion button after delay", e)
                                    XposedBridge.log(e)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.d("RotationSuggest","Error in navbar visibility hook", e)
                        XposedBridge.log(e)
                    }
                }
            )
        }

        private var lastSuggestedRotation = -1
        private var sameSuggestionCount = 0
        /**
         * Hooks RotationButtonController#onRotateSuggestionClick(View).
         * Summary: Overrides the rotation logic to reset to 0 after receiving the same suggestion twice.
         */
        fun overrideRotateSuggestionLogic(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "overrideRotateSuggestionLogic()",
                className = "com.android.systemui.navigationbar.RotationButtonController",
                methodName = "onRotateSuggestionClick",
                parameterTypes = arrayOf(android.view.View::class.java),
                beforeHook = { param ->
                    try {
                        val controller = param.thisObject
                        var suggestedRotation = XposedHelpers.getIntField(controller, "mLastRotationSuggestion")
                        val rotationLockController = XposedHelpers.getObjectField(controller, "mRotationLockController")

                        if ((suggestedRotation == 1 || suggestedRotation == 3) && suggestedRotation == lastSuggestedRotation) {
                            sameSuggestionCount++
                            if (sameSuggestionCount >= 2) {
                                suggestedRotation = 0
                                sameSuggestionCount = 0
                                Log.d("RotationSuggest", "Same angle twice, resetting to 0")
                            }
                        } else {
                            sameSuggestionCount = 1
                            lastSuggestedRotation = suggestedRotation
                        }

                        XposedHelpers.callMethod(rotationLockController, "setRotationLockedAtAngle", true, suggestedRotation)

                        Log.d("RotationSuggest", "Rotated to angle $suggestedRotation")
                        param.result = null
                    } catch (e: Throwable) {
                        Log.d("RotationSuggest", "Error applying suggested rotation", e)
                        XposedBridge.log(e)
                    }
                }
            )
        }
        /**
         * Hooks RotationButtonController#onNavigationBarWindowVisibilityChange(Boolean).
         * Summary: Adds a long-press listener to the rotation button to open the system keyboard picker.
         *          Uses a minimal IInputMethodClient stub to call the internal showInputMethodPickerFromSystem method.
         */
        fun addLongPressKeyboardPicker(): ReflectionMethodHook {
            return ReflectionMethodHook(
                hookName = "addLongPressKeyboardPicker()",
                className = "com.android.systemui.navigationbar.RotationButtonController",
                methodName = "onNavigationBarWindowVisibilityChange",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
                afterHook = { param ->
                    try {
                        val controller = param.thisObject
                        val rotationButton = XposedHelpers.getObjectField(controller, "mRotationButton")

                        val view = XposedHelpers.callMethod(rotationButton, "getCurrentView") as? android.view.View
                            ?: return@ReflectionMethodHook

                        view.setOnLongClickListener {
                            try {
                                val context = view.context
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                val service = XposedHelpers.getObjectField(imm, "mService")

                                val stubClass = XposedHelpers.findClass(
                                    "com.android.internal.view.IInputMethodClient\$Stub",
                                    null
                                )
                                val client = XposedHelpers.callStaticMethod(stubClass, "asInterface",
                                    Binder()
                                )

                                XposedHelpers.callMethod(
                                    service,
                                    "showInputMethodPickerFromSystem",
                                    client,
                                    0,
                                    0
                                )

                                Log.d("RotationSuggest", "Keyboard picker opened via long press")
                                true
                            } catch (e: Throwable) {
                                Log.e("RotationSuggest", "Error showing IM picker", e)
                                XposedBridge.log(e)
                                false
                            }
                        }

                    } catch (e: Throwable) {
                        Log.e("RotationSuggest", "Error attaching long press", e)
                        XposedBridge.log(e)
                    }
                }
            )
        }


        fun allHooks(): List<ReflectionMethodHook> = listOf(
            disableRotateSuggestion(),
            disableRotationButtonTimeout(),
            blockCanAnimateWhenCalledByRotationContext(),
            hideSuggestionWhenNavbarHidden(),
            overrideRotateSuggestionLogic(),
            addLongPressKeyboardPicker()
        )
    }
}