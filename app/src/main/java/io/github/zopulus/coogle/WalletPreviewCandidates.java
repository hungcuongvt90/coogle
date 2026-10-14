package io.github.zopulus.coogle;

import android.app.Application;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Discovery only: these descriptors never authorize an authentication Hook. */
public final class WalletPreviewCandidates {
    static final String CACHE_FEATURE = "wallet.preview-candidates-v2";

    public static List<Method> locate(ClassLoader loader, String apk, String dataDir) throws Exception {
        try (var targets = new AppDexTargets(loader, apk, dataDir)) { return targets.resolve(CACHE_FEATURE); }
    }

    static List<Method> select(Collection<Method> getters, Collection<Method> callbacks) {
        List<Method> selected = List.of();
        for (Method getter : new LinkedHashSet<>(getters)) {
            if (!isModelGetter(getter)) continue;
            for (Method callback : new LinkedHashSet<>(callbacks)) {
                List<Method> pair = List.of(getter, callback);
                if (!valid(pair)) continue;
                if (!selected.isEmpty()) return List.of();
                selected = pair;
            }
        }
        return selected;
    }

    static boolean valid(List<Method> methods) {
        if (methods.size() != 2 || !isModelGetter(methods.get(0))) return false;
        Method callback = methods.get(1);
        if (!concreteInstance(callback) || callback.getReturnType() != Object.class
                || !List.of(callback.getParameterTypes()).equals(List.of(Object.class))) return false;
        int captures = 0;
        for (Field field : callback.getDeclaringClass().getDeclaredFields())
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == methods.get(0).getDeclaringClass()) captures++;
        return captures == 1;
    }

    static boolean isModelGetter(Method method) {
        if (!concreteInstance(method) || method.getParameterCount() != 0 || method.getReturnType().isPrimitive()) return false;
        var constructors = method.getDeclaringClass().getDeclaredConstructors();
        if (constructors.length != 1 || constructors[0].getParameterCount() == 0
                || constructors[0].getParameterTypes()[0] != Application.class) return false;
        int cards = 0, lists = 0;
        for (Field field : method.getReturnType().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType().getName().equals("com.google.android.gms.tapandpay.firstparty.CardInfo")) cards++;
            if (field.getType() == List.class) lists++;
        }
        return cards == 2 && lists == 2;
    }

    private static boolean concreteInstance(Method method) {
        int flags = method.getModifiers();
        return !Modifier.isStatic(flags) && !Modifier.isAbstract(flags) && !Modifier.isNative(flags) && !method.isSynthetic();
    }
    private WalletPreviewCandidates() {}
}
