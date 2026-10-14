package io.github.zopulus.coogle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class RootShellTest {
    private static final String READY = "__COOGLE_ROOT_READY__\n";
    public static void main(String[] args) throws Exception {
        check(RootShell.read(new Result(READY + "0\nnull\n", 0, true), 1).equals("0\nnull\n"), "Root handshake changed settings protocol output");
        expect(new Result("su: permission denied\n", 1, true), RootShell.Reason.AUTHORIZATION);
        expect(new Result(READY + "permission denied\n", 1, true), RootShell.Reason.EXECUTION);
        expect(new Result(READY + "unknown command\n", 127, true), RootShell.Reason.EXECUTION);
        Result timeout = new Result("", 0, false);
        expect(timeout, RootShell.Reason.TIMEOUT);
        check(timeout.destroyed, "Timed-out root command kept running");
        Result broken = new Result(READY, 0, true) {
            @Override public InputStream getInputStream() {
                return new InputStream() { @Override public int read() throws IOException { throw new IOException("broken pipe"); } };
            }
        };
        expect(broken, RootShell.Reason.EXECUTION);
        for (RootShell.Reason reason : RootShell.Reason.values())
            check(new RootShell.Failure(reason).getMessage().matches(".*[\\u4e00-\\u9fff].*"), "Untranslated root failure");
        System.out.println("PASS: root refusal, command failure, timeout cleanup, broken output, and intact settings protocol");
    }
    private static void expect(Result process, RootShell.Reason reason) throws Exception {
        try { RootShell.read(process, 1); throw new AssertionError("Failure accepted: " + reason); }
        catch (RootShell.Failure failure) { check(failure.reason == reason, "Wrong failure category: " + failure.reason); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static class Result extends Process {
        final InputStream output;
        final int code;
        final boolean finished;
        boolean destroyed;
        Result(String output, int code, boolean finished) {
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            this.code = code; this.finished = finished;
        }
        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public int waitFor() { return code; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return finished; }
        @Override public int exitValue() { return code; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }
}
