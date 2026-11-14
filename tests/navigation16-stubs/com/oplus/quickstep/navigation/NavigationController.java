package com.oplus.quickstep.navigation;

public final class NavigationController {
    public static final Holder INSTANCE = new Holder();
    public boolean gesture = true, hidden = true, circle = true, broken;
    public boolean isSwipeSideGesture() { return gesture; }
    public boolean isGestureNavbarHidden() { return hidden; }
    public boolean isCircleToSearchGestureEnable() {
        if (broken) throw new IllegalStateException("observer unavailable");
        return circle;
    }
    public static final class Holder {
        public NavigationController current = new NavigationController();
        public NavigationController get() { return current; }
    }
}
