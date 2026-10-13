package io.github.zopulus.coogle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded root execution for explicit controls and known companion status checks. */
final class RootShell {
    static String run(String command, int timeout) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0)
                    if (output.size() < 65536) output.write(buffer, 0, Math.min(n, 65536 - output.size()));
            } catch (Exception ignored) { }
        }, "Coogle-root-output");
        drain.start();
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly(); drain.join(1000); throw new java.io.IOException("操作超时");
        }
        drain.join(1000);
        if (process.exitValue() != 0) throw new java.io.IOException("Root 操作失败，请确认 Coogle 的 Root 授权及系统支持。");
        return output.toString(StandardCharsets.UTF_8);
    }
    private RootShell() { }
}
