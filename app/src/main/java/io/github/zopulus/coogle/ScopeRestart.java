package io.github.zopulus.coogle;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

/** Restart selected apps without terminating system_server. */
final class ScopeRestart {
    static String command(List<String> packages, int user) throws IOException {
        StringBuilder command = new StringBuilder();
        for (String name : new LinkedHashSet<>(packages)) {
            if ("system".equals(name) || "io.github.zopulus.coogle".equals(name)) continue;
            if (!name.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) throw new IOException("Invalid scope package name; nothing was restarted.");
            if ("com.android.systemui".equals(name))
                command.append("for p in $(pidof com.android.systemui); do kill -TERM \"$p\" || exit 1; done; ");
            else command.append("am force-stop --user ").append(user).append(" '").append(name).append("' || exit 1; ");
        }
        if (command.isEmpty()) throw new IOException("No application scopes can be restarted. The system framework scope requires a device reboot.");
        return command.toString();
    }
    private ScopeRestart() { }
}
