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
            if (!name.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) throw new IOException("作用域包名无效，未执行重启。");
            if ("com.android.systemui".equals(name))
                command.append("for p in $(pidof com.android.systemui); do kill -TERM \"$p\" || exit 1; done; ");
            else command.append("am force-stop --user ").append(user).append(" '").append(name).append("' || exit 1; ");
        }
        if (command.isEmpty()) throw new IOException("没有应用作用域可重启。系统框架作用域需要重启设备。");
        return command.toString();
    }
    private ScopeRestart() { }
}
