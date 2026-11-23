package com.simplehome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Signing in to Jellyfin, step by step. Settings and first-time setup both draw these stages:
 * looking for servers, the list of servers found, then the Quick Connect code (or a password).
 */
final class JellyfinLogin {

    enum Stage { SEARCHING, SERVERS, CONNECTING, CODE, DONE, ERROR }

    interface Listener {
        /** Something changed: redraw. */
        void changed();

        /** Signed in. */
        void signedIn();
    }

    Stage stage = Stage.SEARCHING;
    List<Jellyfin.Server> servers = new ArrayList<>();
    Jellyfin.Server server;
    Jellyfin.QuickConnect code;
    String message;

    private final Activity activity;
    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;   // stops old searches and code checks when you move on

    JellyfinLogin(Activity activity, Listener listener) {
        this.activity = activity;
        this.app = activity.getApplicationContext();
        this.listener = listener;
    }

    /** Looks for servers on the network. */
    void search() {
        final int gen = ++generation;
        stage = Stage.SEARCHING;
        server = null;
        code = null;
        listener.changed();
        new Thread(() -> {
            final List<Jellyfin.Server> found = Jellyfin.discover(2500);
            main.post(() -> {
                if (gen != generation) return;
                servers = found;
                stage = Stage.SERVERS;
                listener.changed();
            });
        }).start();
    }

    /** Use this server: Quick Connect if it's switched on there, otherwise a password. */
    void choose(final Jellyfin.Server s) {
        final int gen = ++generation;
        server = s;
        stage = Stage.CONNECTING;
        message = "Connecting to " + s.name + "…";
        listener.changed();
        new Thread(() -> {
            Jellyfin.QuickConnect q = null;
            Jellyfin.Server checked = s;
            String error = null;
            try {
                checked = Jellyfin.check(app, s.address);
                if (Jellyfin.quickConnectEnabled(app, s.address)) q = Jellyfin.startQuickConnect(app, s.address);
            } catch (Exception e) {
                error = "Couldn't reach " + s.name + " at " + s.address + ".";
            }
            final Jellyfin.QuickConnect qc = q;
            final Jellyfin.Server srv = checked;
            final String err = error;
            main.post(() -> {
                if (gen != generation) return;
                if (err != null) {
                    fail(err);
                    return;
                }
                server = srv;
                if (qc == null) {
                    // Quick Connect is off on this server: ask for a username and password instead.
                    stage = Stage.CODE;
                    code = null;
                    listener.changed();
                    askPassword("Quick Connect is switched off on this server. Sign in with your Jellyfin username and password.");
                    return;
                }
                code = qc;
                stage = Stage.CODE;
                listener.changed();
                waitForApproval(gen);
            });
        }).start();
    }

    private void waitForApproval(final int gen) {
        final Jellyfin.Server srv = server;
        final Jellyfin.QuickConnect q = code;
        new Thread(() -> {
            long deadline = SystemClock.elapsedRealtime() + 10 * 60_000;
            while (gen == generation && SystemClock.elapsedRealtime() < deadline) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                if (gen != generation) return;
                if (Jellyfin.quickConnectApproved(app, srv.address, q)) {
                    String error = null;
                    try {
                        Jellyfin.finishQuickConnect(app, srv, q);
                    } catch (Exception e) {
                        error = "Jellyfin approved the code but didn't let SimpleLauncher sign in. Try again.";
                    }
                    final String err = error;
                    main.post(() -> {
                        if (gen != generation) return;
                        if (err != null) fail(err);
                        else done();
                    });
                    return;
                }
            }
            main.post(() -> {
                if (gen == generation) fail("That code has expired. Try again for a new one.");
            });
        }).start();
    }

    /** Asks for the server's address with the TV keyboard. */
    void typeAddress() {
        final EditText field = new EditText(activity);
        field.setHint("e.g. 192.168.1.10:8096");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Your Jellyfin server's address")
                .setView(padded(field))
                .setPositiveButton("Connect", (d, w) -> {
                    String address = Jellyfin.normalise(field.getText().toString());
                    if (address == null) return;
                    Jellyfin.Server s = new Jellyfin.Server();
                    s.address = address;
                    s.name = address.replaceFirst("^https?://", "");
                    choose(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Signs in with a username and password instead of Quick Connect. */
    void askPassword(String why) {
        if (server == null) return;
        generation++;   // stop waiting for the code
        final EditText user = new EditText(activity);
        user.setHint("Username");
        user.setSingleLine(true);
        final EditText pass = new EditText(activity);
        pass.setHint("Password");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(user);
        box.addView(pass);
        final Jellyfin.Server srv = server;
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Sign in to " + srv.name)
                .setMessage(why)
                .setView(padded(box))
                .setPositiveButton("Sign in", (d, w) -> {
                    final int gen = ++generation;
                    stage = Stage.CONNECTING;
                    message = "Signing in to " + srv.name + "…";
                    listener.changed();
                    final String u = user.getText().toString().trim(), p = pass.getText().toString();
                    new Thread(() -> {
                        String error = null;
                        try {
                            Jellyfin.signInWithPassword(app, srv, u, p);
                        } catch (Exception e) {
                            error = e.getMessage() != null && e.getMessage().contains("401")
                                    ? "That username or password wasn't right." : "Couldn't sign in to " + srv.name + ".";
                        }
                        final String err = error;
                        main.post(() -> {
                            if (gen != generation) return;
                            if (err != null) fail(err);
                            else done();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    if (stage == Stage.CONNECTING) return;
                    if (code != null) {
                        choose(srv);              // Quick Connect works here: back to a fresh code
                    } else {
                        stage = Stage.SERVERS;    // otherwise back to the list of servers
                        listener.changed();
                    }
                })
                .show();
    }

    /** Stops everything (leaving the page). */
    void cancel() {
        generation++;
    }

    private void fail(String why) {
        stage = Stage.ERROR;
        message = why;
        listener.changed();
    }

    private void done() {
        stage = Stage.DONE;
        Apps.toast(app, "Signed in to Jellyfin" + (Jellyfin.user(app) != null ? " as " + Jellyfin.user(app) : ""));
        listener.changed();
        listener.signedIn();
    }

    private LinearLayout padded(android.view.View v) {
        LinearLayout box = new LinearLayout(activity);
        int pad = Ui.dp(activity, 20);
        box.setPadding(pad, Ui.dp(activity, 8), pad, 0);
        box.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }
}
