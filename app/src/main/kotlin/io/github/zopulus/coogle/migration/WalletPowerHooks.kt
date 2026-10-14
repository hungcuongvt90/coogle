package io.github.zopulus.coogle.migration

import android.content.Context
import io.github.zopulus.coogle.FeatureConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect

/** Native GestureLauncherService owns dispatch; only prevent the duplicate vendor wallet route. */
internal object WalletPowerHooks {
    fun hook(loader: ClassLoader) {
        runCatching {
            val target = ModernReflect.findClass("com.android.server.policy.OplusWalletController", loader)
            val contextField = ModernReflect.findField(target, "mContext")
            val userField = ModernReflect.findField(target, "mCurrentUserId")
            require(Context::class.java.isAssignableFrom(contextField.type))
            require(userField.type == Int::class.javaPrimitiveType)
            val gestures = ModernReflect.findClass("com.android.server.GestureLauncherService", loader)
            val nativeSelected = ModernReflect.findMethodExact(gestures, "isWalletDoubleTapPowerSettingEnabled",
                arrayOf(Context::class.java, Int::class.javaPrimitiveType!!))
            require(nativeSelected.returnType == Boolean::class.javaPrimitiveType)
            require(java.lang.reflect.Modifier.isStatic(nativeSelected.modifiers))
            require(ModernReflect.findMethodExact(target, "isQuickLaunchSupport", emptyArray()).returnType == Boolean::class.javaPrimitiveType)
            ModernReflect.findAndHookMethod(target, "isQuickLaunchSupport",
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result != true || !FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                        val wallet = param.thisObject ?: return
                        val context = contextField.get(wallet) as? Context ?: return
                        val userId = userField.getInt(wallet)
                        // Preserve the platform flag, resource mode, selected action and current-user checks.
                        if (nativeSelected.invoke(null, context, userId) == true) param.result = false
                    }
                },
            )
            HookLog.i("WalletPowerHooks", "Installed native Wallet duplicate-route guard")
        }.onFailure { HookLog.w("WalletPowerHooks", "Native wallet binding unavailable; original behavior retained", it) }
    }
}
