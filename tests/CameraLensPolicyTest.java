package io.github.zopulus.coogle;

public final class CameraLensPolicyTest {
    public static void main(String[] args) {
        check(CameraLensPolicy.allowed("common", false, false, true, false, false));
        check(!CameraLensPolicy.allowed("common", true, false, true, false, false));
        check(!CameraLensPolicy.allowed("common", false, true, true, false, false));
        check(!CameraLensPolicy.allowed("common", false, false, false, false, false));
        check(!CameraLensPolicy.allowed("common", false, false, true, true, false));
        check(!CameraLensPolicy.allowed("common", false, false, true, false, true));
        for (String mode : new String[]{"night", "portrait", "video", "highPixel", "groupshot", "", null})
            check(!CameraLensPolicy.allowed(mode, false, false, true, false, false));
        System.out.println("PASS: Camera Lens rear photo, lock, entry and capture restrictions");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
