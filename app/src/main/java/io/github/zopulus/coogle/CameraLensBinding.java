package io.github.zopulus.coogle;

import android.app.Activity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Restores the international common-mode branch through the camera's own state APIs. */
final class CameraLensBinding {
    static final int TARGET_COUNT = 8;
    static final String UI_STATE = "com.oplus.camera.protocal.ui.context.EventMessageUIContainerContext";
    final Method eligibility;
    private final Method supported, children, entry, locked, uiState, refresh;
    private final Method mode, front, activity, ui;
    private final Field listener, secure;

    private CameraLensBinding(List<Method> targets) throws ReflectiveOperationException {
        eligibility = targets.get(1);
        supported = targets.get(2); children = targets.get(3); entry = targets.get(4);
        locked = targets.get(5); uiState = targets.get(6); refresh = targets.get(7);
        Class<?> presenter = eligibility.getDeclaringClass();
        mode = accessor(presenter, "getCameraMode", String.class);
        front = accessor(presenter, "isFrontCamera", boolean.class);
        activity = accessor(presenter, "getActivityContext", Activity.class);
        ui = presenter.getMethod("getMainUIContainer");
        if (Modifier.isStatic(ui.getModifiers()) || !uiState.getDeclaringClass().isAssignableFrom(ui.getReturnType()))
            throw new NoSuchMethodException("Camera UI context incompatible");
        listener = field(presenter, "mFeatureListener");
        if (Modifier.isStatic(listener.getModifiers()) || !supported.getDeclaringClass().isAssignableFrom(listener.getType())
                || !entry.getDeclaringClass().isAssignableFrom(targets.get(0).getDeclaringClass())
                || !listener.getType().isAssignableFrom(entry.getDeclaringClass()))
            throw new NoSuchFieldException("Camera listener incompatible");
        secure = uiState.getReturnType().getDeclaredField("b");
        if (secure.getType() != boolean.class || Modifier.isStatic(secure.getModifiers()))
            throw new NoSuchFieldException("Camera secure UI state incompatible");
        for (Method method : targets.subList(1, TARGET_COUNT)) method.setAccessible(true);
        listener.setAccessible(true); secure.setAccessible(true);
    }
    static CameraLensBinding bind(List<Method> targets) throws ReflectiveOperationException {
        if (!valid(targets)) throw new NoSuchMethodException("Native Lens targets incompatible");
        return new CameraLensBinding(targets);
    }
    static boolean valid(List<Method> targets) {
        if (targets.size() < TARGET_COUNT || targets.size() > TARGET_COUNT + 64) return false;
        return shape(targets.get(0), boolean.class, false, String.class)
                && shape(targets.get(1), boolean.class, false)
                && shape(targets.get(2), boolean.class, false)
                && shape(targets.get(3), boolean.class, false, Activity.class)
                && targets.get(2).getDeclaringClass() == targets.get(3).getDeclaringClass()
                && shape(targets.get(4), boolean.class, false)
                && shape(targets.get(5), boolean.class, true)
                && targets.get(6).getReturnType().getName().equals(UI_STATE)
                && shape(targets.get(6), targets.get(6).getReturnType(), false)
                && shape(targets.get(7), void.class, false)
                && targets.get(7).getDeclaringClass() == targets.get(2).getDeclaringClass();
    }
    boolean isCommon(Object presenter) throws ReflectiveOperationException {
        return "common".equals(mode.invoke(presenter));
    }
    boolean allowed(Object presenter) throws ReflectiveOperationException {
        Object state = listener.get(presenter);
        Activity host = (Activity) activity.invoke(presenter);
        if (state == null || host == null || !(boolean) supported.invoke(state)
                || (boolean) children.invoke(state, host)) return false;
        refresh.invoke(state);
        if (!(boolean) entry.invoke(state) || (boolean) front.invoke(presenter)) return false;
        if (!(boolean) locked.invoke(null)) return true;
        Object container = ui.invoke(presenter);
        Object context = container == null ? null : uiState.invoke(container);
        return context != null && !secure.getBoolean(context);
    }
    private static boolean shape(Method method, Class<?> result, boolean isStatic, Class<?>... parameters) {
        return method.getReturnType() == result && List.of(method.getParameterTypes()).equals(List.of(parameters))
                && Modifier.isStatic(method.getModifiers()) == isStatic;
    }
    private static Method accessor(Class<?> type, String name, Class<?> result) throws NoSuchMethodException {
        Method method = type.getMethod(name);
        if (!shape(method, result, false)) throw new NoSuchMethodException("Camera accessor incompatible: " + name);
        return method;
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            try { return parent.getDeclaredField(name); } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }
}
