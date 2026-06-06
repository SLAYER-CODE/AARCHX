package org.aarchdroid.dragonterminal.bridge;

import android.content.ComponentName;
import android.content.Intent;
import java.util.Objects;

public class Bridge {
    public static final String ACTION_EXECUTE = "neoterm.action.remote.execute";
    public static final String EXTRA_COMMAND = "neoterm.extra.remote.execute.command";
    public static final String EXTRA_FOREGROUND = "neoterm.extra.remote.execute.foreground";
    public static final String EXTRA_SESSION_ID = "neoterm.extra.remote.execute.session";
    private static final String NEOTERM_PACKAGE = "org.aarchdroid";
    private static final String NEOTERM_REMOTE_INTERFACE = "org.aarchdroid.dragonterminal.ui.term.NeoTermRemoteInterface";
    private static final ComponentName NEOTERM_COMPONENT = new ComponentName(NEOTERM_PACKAGE, NEOTERM_REMOTE_INTERFACE);

    private Bridge() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    public static Intent createExecuteIntent(String command) {
        return createExecuteIntent(SessionId.NEW_SESSION, command);
    }

    public static Intent createExecuteIntent(String command, boolean foreground) {
        return createExecuteIntent(SessionId.NEW_SESSION, command, foreground);
    }

    public static Intent createExecuteIntent(SessionId sessionId, String command) {
        return createExecuteIntent(sessionId, command, true);
    }

    public static Intent createExecuteIntent(SessionId sessionId, String command, boolean foreground) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(sessionId, "session id");
        Intent intent = new Intent(ACTION_EXECUTE);
        intent.setComponent(NEOTERM_COMPONENT);
        intent.putExtra(EXTRA_COMMAND, command);
        intent.putExtra(EXTRA_SESSION_ID, sessionId.getSessionId());
        intent.putExtra(EXTRA_FOREGROUND, foreground);
        return intent;
    }

    public static SessionId parseResult(Intent intent) {
        Objects.requireNonNull(intent, "data");
        if (intent.hasExtra(EXTRA_SESSION_ID)) {
            return SessionId.of(intent.getStringExtra(EXTRA_SESSION_ID));
        }
        return null;
    }
}
