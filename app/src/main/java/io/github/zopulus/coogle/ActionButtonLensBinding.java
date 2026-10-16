package io.github.zopulus.coogle;

import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Only the scanner destination changes; key dispatch and SpruceLauncher remain native. */
final class ActionButtonLensBinding {
    static final String CACHE_FEATURE = "action-button.lens-v4";
    static final String PACKAGE = "com.oplus.gesture";
    static final String SCANNER = "com.coloros.ocrscanner";
    static final String SCENE = "ocrscanner";
    static final String ACTION = "coloros.ocrscanner.intent.action.OSEE";
    final Method label, launch, availability, shortPress;
    private final Method hintFactory, hint;
    private final Field context;

    ActionButtonLensBinding(List<Method> targets) throws ReflectiveOperationException {
        if (!valid(targets)) throw new NoSuchMethodException("Action button Lens binding incompatible");
        label = targets.get(0); launch = targets.get(1); availability = targets.get(2);
        shortPress = targets.get(3); hintFactory = targets.get(4); hint = targets.get(5);
        Field found = null;
        for (Field field : launch.getDeclaringClass().getDeclaredFields()) {
            if (field.getType() != Context.class || Modifier.isStatic(field.getModifiers())) continue;
            if (found != null) throw new NoSuchFieldException("Ambiguous action button context");
            found = field;
        }
        if (found == null) throw new NoSuchFieldException("Action button context missing");
        context = found;
        context.setAccessible(true);
    }

    static boolean valid(List<Method> targets) {
        if (targets.size() < 6 || targets.size() > 22) return false;
        Method label = targets.get(0), launch = targets.get(1), availability = targets.get(2);
        Method shortPress = targets.get(3), factory = targets.get(4), hint = targets.get(5);
        return label.getDeclaringClass().getName().equals(
                "com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity")
                && label.getReturnType() == String.class && label.getParameterCount() == 0
                && launch.getReturnType() == void.class
                && List.of(launch.getParameterTypes()).equals(List.of(Intent.class, String.class, String.class))
                && !Modifier.isStatic(label.getModifiers()) && !Modifier.isStatic(launch.getModifiers())
                && availability.getReturnType() == boolean.class && Modifier.isStatic(availability.getModifiers())
                && List.of(availability.getParameterTypes()).equals(List.of(String.class, int.class, Context.class))
                && shortPress.getDeclaringClass() == launch.getDeclaringClass() && !Modifier.isStatic(shortPress.getModifiers())
                && shortPress.getReturnType() == void.class
                && List.of(shortPress.getParameterTypes()).equals(List.of(Intent.class, String.class))
                && Modifier.isStatic(factory.getModifiers()) && factory.getReturnType() == hint.getDeclaringClass()
                && List.of(factory.getParameterTypes()).equals(List.of(Context.class))
                && !Modifier.isStatic(hint.getModifiers()) && hint.getReturnType() == void.class
                && List.of(hint.getParameterTypes()).equals(List.of(int.class, String.class, String.class, Object.class, String.class, boolean.class));
    }

    Context context(Object owner) throws IllegalAccessException { return (Context) context.get(owner); }

    void showHint(Context context, int icon) throws ReflectiveOperationException {
        hint.invoke(hintFactory.invoke(null, context), 3, "io.github.zopulus.coogle", "长按使用智能镜头",
                Integer.valueOf(icon), "长按使用智能镜头", false);
    }

    static boolean matchesShortPress(Intent intent, String scene) {
        if (intent == null || !SCENE.equals(scene) || intent.getComponent() == null) return false;
        return SCANNER.equals(intent.getComponent().getPackageName())
                && "com.oplus.scanner.ui.keymagic.KeyMagicService".equals(intent.getComponent().getClassName());
    }

    static boolean matches(String scene, String owner, String action) {
        return SCENE.equals(scene) && SCANNER.equals(owner) && ACTION.equals(action);
    }
}
