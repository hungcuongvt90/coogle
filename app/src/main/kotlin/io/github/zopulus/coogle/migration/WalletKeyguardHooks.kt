package io.github.zopulus.coogle.migration

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Message
import io.github.zopulus.coogle.FeatureConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.WalletDismissQueue
import io.github.zopulus.coogle.migration.compat.ModernHookBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect

/** Deliver the original Wallet dismissal message after the native occlusion reset, not before it. */
internal object WalletKeyguardHooks {
    fun hook(loader: ClassLoader) {
        runCatching {
            val mediatorClass = ModernReflect.findClass("com.android.systemui.keyguard.KeyguardViewMediator", loader)
            val handlerField = ModernReflect.findField(mediatorClass, "mHandler")
            require(Handler::class.java.isAssignableFrom(handlerField.type))
            val ownerField = handlerField.type.declaredFields.single { it.type == mediatorClass }.apply { isAccessible = true }
            val contextField = ModernReflect.findField(mediatorClass, "mContext")
            require(Context::class.java.isAssignableFrom(contextField.type))
            val showing = ModernReflect.findField(mediatorClass, "mShowing")
            val occluded = ModernReflect.findField(mediatorClass, "mOccluded")
            require(showing.type == Boolean::class.javaPrimitiveType && occluded.type == Boolean::class.javaPrimitiveType)
            val users = ModernReflect.findField(mediatorClass, "mSelectedUserInteractor")
            val selectedUser = ModernReflect.findMethodExact(users.type, "getSelectedUserId", emptyArray())
            require(selectedUser.returnType == Int::class.javaPrimitiveType)
            val taskUser = ModernReflect.findField(ActivityManager.RunningTaskInfo::class.java, "userId")
            require(taskUser.type == Int::class.javaPrimitiveType)
            val dismissClass = ModernReflect.findClass("com.android.systemui.keyguard.KeyguardViewMediator\$DismissMessage", loader)
            val callback = ModernReflect.findField(dismissClass, "mCallback")
            require(callback.type.name == "com.android.internal.policy.IKeyguardDismissCallback")
            val resetClass = ModernReflect.findClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager", loader)
            val handle = ModernReflect.findMethodExact(handlerField.type, "handleMessage", arrayOf(Message::class.java))
            val reset = ModernReflect.findMethodExact(resetClass, "reset", arrayOf(Boolean::class.javaPrimitiveType!!))
            require(handle.returnType == Void.TYPE && reset.returnType == Void.TYPE)
            var state: Pending? = null
            ModernHookBridge.atomic {
                ModernHookBridge.hookMethod(handle, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val handler = param.thisObject as? Handler ?: return
                        val message = param.args[0] as? Message ?: return
                        if (!dismissClass.isInstance(message.obj)) return
                        val current = state
                        // Already accepted requests must reach the original handler even if the switch changes.
                        if (current?.handler === handler && current.queue.consumeForwarded(message)) return
                        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                        if (callback.get(message.obj) == null) return
                        val mediator = ownerField.get(handler) ?: return
                        if (!showing.getBoolean(mediator) || occluded.getBoolean(mediator)) return
                        val context = contextField.get(mediator) as? Context ?: return
                        val task = context.getSystemService(ActivityManager::class.java)?.getRunningTasks(1)?.firstOrNull() ?: return
                        if (task.topActivity?.packageName != "com.google.android.apps.walletnfcrel" ||
                            task.baseIntent.action != "com.google.android.apps.wallet.main.QUICKDRAW" ||
                            taskUser.getInt(task) != selectedUser.invoke(users.get(mediator))) return
                        if (current != null && current.handler !== handler) return
                        val pending = current ?: Pending(handler, mediator).also { state = it }
                        if (pending.defer(message)) {
                            param.result = null
                            HookLog.i("WalletKeyguardHooks", "Queued native Wallet dismissal until occlusion reset")
                        }
                    }
                })
                ModernHookBridge.hookMethod(reset, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val pending = state ?: return
                        if (pending.queue.hasPending() &&
                            (!showing.getBoolean(pending.mediator) || occluded.getBoolean(pending.mediator))) {
                            pending.queue.release()
                            HookLog.i("WalletKeyguardHooks", "Forwarded original Wallet dismissal after native reset")
                        }
                    }
                })
            }
            HookLog.i("WalletKeyguardHooks", "Installed native Wallet dismissal ordering")
        }.onFailure { HookLog.w("WalletKeyguardHooks", "Native keyguard binding unavailable; original behavior retained", it) }
    }

    private class Pending(val handler: Handler, val mediator: Any) {
        val queue = WalletDismissQueue(object : WalletDismissQueue.Scheduler {
            override fun postDelayed(callback: Runnable, delay: Long) = handler.postDelayed(callback, delay)
            override fun remove(callback: Runnable) = handler.removeCallbacks(callback)
        })

        fun defer(message: Message): Boolean {
            val copy = Message.obtain(message)
            try {
                val accepted = queue.defer(copy, Runnable {
                    val sent = runCatching { handler.sendMessage(copy) }
                        .onFailure { HookLog.w("WalletKeyguardHooks", "Native dismissal dispatch failed", it) }
                        .getOrDefault(false)
                    if (!sent) {
                        queue.consumeForwarded(copy)
                        copy.recycle()
                    }
                })
                if (!accepted) copy.recycle()
                return accepted
            } catch (failure: RuntimeException) {
                copy.recycle()
                throw failure
            }
        }
    }
}
