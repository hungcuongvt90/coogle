package io.github.zopulus.coogle.migration

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import dalvik.system.BaseDexClassLoader
import io.github.zopulus.coogle.migration.compat.ModernHookRegistry
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Adds the existing clock plugin models to its native editor callback. */
internal object WallpaperQuickEntryHooks {
    private var installed = false
    private val entryIds = listOf("assistant", "wallet", "qr_code_scanner", "home")

    /** Plugin classes are obfuscated per firmware: 17 keeps d1/i5 names, 16 uses a1/b/u4. */
    private class PluginBinding(
        val manager: String, val model: String, val callback: String, val helper: String, val helperMethod: String)

    private val bindings = listOf(
        PluginBinding("d1.c", "d1.a", "b.j", "i5.d", "i"),
        PluginBinding("a1.c", "a1.b", "b.g", "u4.e", "g"),
    )

    fun install(loader: ClassLoader) {
        for (binding in bindings) {
            if (ModernReflect.findClassIfExists(binding.manager, loader) != null && bind(loader, binding)) return
        }
        BaseDexClassLoader::class.java.declaredConstructors.forEach { constructor ->
            ModernHookRegistry.installCompat("quick-entry:plugin-loader:$constructor", constructor,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.none { it is String && it.contains("KeyguardClockBase") }) return
                        val pluginLoader = param.thisObject as? ClassLoader ?: return
                        for (binding in bindings) {
                            if (bind(pluginLoader, binding)) {
                                ModernHookRegistry.unhookPrefix("quick-entry:plugin-loader:")
                                return
                            }
                        }
                    }
                })
        }
    }

    @Synchronized
    private fun bind(loader: ClassLoader): Boolean {
        for (binding in bindings) if (bind(loader, binding)) return true
        return false
    }

    @Synchronized
    private fun bind(loader: ClassLoader, binding: PluginBinding): Boolean {
        if (installed) return true
        return runCatching {
            val manager = ModernReflect.findClass(binding.manager, loader)
            val model = ModernReflect.findClass(binding.model, loader)
            val callbackType = ModernReflect.findClass(binding.callback, loader)
            val constructor = manager.getDeclaredConstructor(Context::class.java, Context::class.java, callbackType)
            val modelConstructor = model.getDeclaredConstructor(String::class.java, String::class.java,
                Int::class.javaPrimitiveType, String::class.java, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val key = ModernReflect.findField(model, "a")
            val pluginContextField = ModernReflect.findField(manager, "b")
            val callbackField = ModernReflect.findField(manager, "c")
            val callbackKind = ModernReflect.findField(callbackType, "a")
            val isSystemApp = ModernReflect.findMethodExact(ModernReflect.findClass(binding.helper, loader),
                binding.helperMethod, arrayOf(Context::class.java, String::class.java))
            val invoke = ModernReflect.findMethodExact(callbackType, "invoke", arrayOf(Any::class.java))
            val titleIds = Collections.synchronizedMap(WeakHashMap<Resources, Map<String, Int>>())
            val owners = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())
            ModernHookRegistry.installCompat("quick-entry:clock-editor", constructor, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val owner = param.thisObject ?: return
                    val callback = callbackField.get(owner) ?: return
                    owners[callback] = WeakReference(owner)
                }
            })
            ModernHookRegistry.installCompat("quick-entry:clock-list", invoke, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val completed = runCatching {
                        appendClockEntries(param, model, key, callbackKind, owners,
                            pluginContextField, modelConstructor, titleIds, isSystemApp)
                    }
                        // Never hand back a partially built list; the native view cannot digest one.
                        .onFailure { HookLog.w("NativeQuickEntries", "Clock entry insert failed; original list kept", it) }
                        .getOrNull() ?: return
                    param.args[0] = completed
                }
            })
            installed = true
            HookLog.i("NativeQuickEntries", "Restored native wallpaper clock editor entries")
            true
        }.getOrElse {
            // A retry must never reuse one hook with another attempt's owner map.
            runCatching { ModernHookRegistry.unhook("quick-entry:clock-list") }
            runCatching { ModernHookRegistry.unhook("quick-entry:clock-editor") }
            HookLog.w("NativeQuickEntries", "Clock editor binding unavailable; original behavior retained", it)
            false
        }
    }

    /** Builds the completed editor list, or null when the callback is not the native editor list. */
    private fun appendClockEntries(
        param: ModernMethodHook.MethodHookParam, model: Class<*>, key: java.lang.reflect.Field,
        callbackKind: java.lang.reflect.Field, owners: Map<Any, WeakReference<Any>>,
        pluginContextField: java.lang.reflect.Field,
        modelConstructor: java.lang.reflect.Constructor<*>,
        titleIds: MutableMap<Resources, Map<String, Int>>,
        isSystemApp: java.lang.reflect.Method,
    ): List<*>? {
        val callback = param.thisObject ?: return null
        if (callbackKind.getInt(callback) != 3) return null
        val owner = owners[callback]?.get() ?: return null
        val original = param.args[0] as? List<*> ?: return null
        // A wrong callback or foreign element type must never receive our models.
        if (original.any { it != null && !model.isInstance(it) }) return null
        val present = HashSet<String>(original.size)
        var insertionIndex = original.size
        original.forEachIndexed { index, entry ->
            val id = entry?.let { key.get(it) as? String } ?: return@forEachIndexed
            present.add(id)
            if (id == "add") insertionIndex = index
        }
        if (entryIds.all(present::contains)) return null
        val pluginContext = pluginContextField.get(owner) as Context
        val resources = pluginContext.resources
        val ids = titleIds[resources] ?: entryIds.associateWith { id ->
            val name = if (id == "home") "base_ui_affordance_homecontrol" else "base_ui_affordance_$id"
            resources.getIdentifier(name, "string", pluginContext.packageName).also {
                require(it != 0) { "Missing native resource $name" }
            }
        }.also { titleIds[resources] = it }
        val completed = ArrayList(original)
        fun add(id: String, icon: String, enabled: Boolean = true, packageName: String = "") {
            if (id in present) return
            val entry = modelConstructor.newInstance(id, resources.getString(ids.getValue(id)), 0,
                "${icon}_icon", "${icon}_new", packageName, true, enabled, 0)
            completed.add(insertionIndex++, entry)
        }
        if ("assistant" !in present) {
            val geminiIsSystemApp = isSystemApp.invoke(null, pluginContext, "com.google.android.apps.bard") == true
            add("assistant", if (geminiIsSystemApp) "affordance_digital_assistant" else "affordance_assistant",
                packageName = "com.google.android.apps.bard")
        }
        // Use SystemUI's current status, not the plugin observer's initialization snapshot.
        if ("wallet" !in present)
            add("wallet", "affordance_pay", Settings.System.getInt(pluginContext.contentResolver,
                "oplus_wallet_enable_state", 0) == 1)
        add("qr_code_scanner", "affordance_scanner")
        if ("home" !in present)
            add("home", "affordance_homecontrol", Settings.System.getInt(pluginContext.contentResolver,
                "oplus_home_controller_enable_state", 0) == 1)
        return completed
    }
}
