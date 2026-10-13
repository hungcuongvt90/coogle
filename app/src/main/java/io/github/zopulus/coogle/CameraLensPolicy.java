package io.github.zopulus.coogle;

final class CameraLensPolicy {
    static boolean allowed(String mode, boolean front, boolean locked, boolean cameraEntry,
                           boolean capturing, boolean recording) {
        return "common".equals(mode) && !front && !locked && cameraEntry && !capturing && !recording;
    }
    private CameraLensPolicy() {}
}
