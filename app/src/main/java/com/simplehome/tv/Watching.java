package com.simplehome.tv;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Continue Watching row: Plex and Jellyfin together, most recently watched first.
 * Its title says where shows come from: "(Plex)", "(Jellyfin)", or neither when it's both.
 */
final class Watching {

    static final int MAX_CARDS = 16;

    /** One card in the row. */
    static final class Card {
        String source;        // "plex" or "jellyfin"
        String key;           // unique, for keeping focus
        String title;
        String meta;
        String imageUrl;
        int progress;
        long lastViewed;      // seconds
        Plex.Item plex;
        Jellyfin.Item jellyfin;
    }

    private Watching() {
    }

    /** Plex fills the row: installed, signed in and switched on in External Services. */
    static boolean plexOn(Context c) {
        return Plex.installed(c) && Prefs.plexToken(c) != null && Prefs.plexInRow(c);
    }

    static boolean jellyfinOn(Context c) {
        return Jellyfin.installed(c) && Jellyfin.signedIn(c) && Prefs.jellyfinInRow(c);
    }

    /** Is there anything that could fill the row? (Otherwise the row is hidden.) */
    static boolean available(Context c) {
        return Plex.installed(c) || Jellyfin.installed(c);
    }

    /** Whether the row shows at all. */
    static boolean shown(Context c) {
        return Prefs.showPlex(c) && available(c);
    }

    /** "CONTINUE WATCHING (PLEX)", "(JELLYFIN)", or just "CONTINUE WATCHING" for both. */
    static String title(Context c) {
        boolean plex = plexOn(c), jf = jellyfinOn(c);
        if (!plex && !jf) {             // nothing signed in yet: go by what's installed
            plex = Plex.installed(c);
            jf = Jellyfin.installed(c);
        }
        return titleFor(plex, jf);
    }

    static String titleFor(boolean plex, boolean jellyfin) {
        if (plex && !jellyfin) return "CONTINUE WATCHING (PLEX)";
        if (jellyfin && !plex) return "CONTINUE WATCHING (JELLYFIN)";
        return "CONTINUE WATCHING";
    }

    /** What to say when the row has nothing to show yet. */
    static String emptyMessage(Context c) {
        boolean pi = Plex.installed(c), ji = Jellyfin.installed(c);
        boolean ps = Prefs.plexToken(c) != null, js = Jellyfin.signedIn(c);
        if (!(pi && ps) && !(ji && js)) {
            String which = pi && ji ? "Plex or Jellyfin" : pi ? "Plex" : "Jellyfin";
            return "Sign in to " + which + " in Settings › External Services to see what you're watching here.";
        }
        if (!plexOn(c) && !jellyfinOn(c)) return "Switch a service on in Settings › External Services.";
        return "Nothing in progress right now.";
    }

    static List<Card> merge(List<Plex.Item> plex, List<Jellyfin.Item> jellyfin) {
        List<Card> out = new ArrayList<>();
        if (plex != null) {
            for (Plex.Item p : plex) {
                Card c = new Card();
                c.source = "plex";
                c.key = "plex:" + p.machineId + ":" + p.ratingKey;
                c.title = p.title;
                c.meta = p.meta;
                c.imageUrl = p.imageUrl;
                c.progress = p.progress;
                c.lastViewed = p.lastViewed;
                c.plex = p;
                out.add(c);
            }
        }
        if (jellyfin != null) {
            for (Jellyfin.Item j : jellyfin) {
                Card c = new Card();
                c.source = "jellyfin";
                c.key = "jf:" + j.id;
                c.title = j.title;
                c.meta = j.meta;
                c.imageUrl = j.imageUrl;
                c.progress = j.progress;
                c.lastViewed = j.lastViewed;
                c.jellyfin = j;
                out.add(c);
            }
        }
        // Stable sort: equal times keep Plex first, then Jellyfin, each in its own order.
        Collections.sort(out, (a, b) -> Long.compare(b.lastViewed, a.lastViewed));
        return out.size() > MAX_CARDS ? new ArrayList<>(out.subList(0, MAX_CARDS)) : out;
    }

    static void open(Context c, Card card) {
        if (card.plex != null) Plex.open(c, card.plex);
        else if (card.jellyfin != null) Jellyfin.open(c, card.jellyfin);
    }

    // ------------------------------------------------------------------ sizes (Settings › Home screen)

    static final String[] SIZE_NAMES = {"Smallest", "Small", "Medium", "Large", "Largest"};

    /** App tile width in dp for each size step. */
    static int appTileWidth(boolean grid, int size) {
        int[] w = grid ? new int[]{96, 112, 128, 148, 172} : new int[]{120, 138, 156, 180, 204};
        return w[clamp(size)];
    }

    /** Continue Watching card width in dp for each size step. */
    static int cardWidth(int size) {
        return new int[]{150, 172, 195, 224, 256}[clamp(size)];
    }

    static int clamp(int size) {
        return Math.max(0, Math.min(4, size));
    }
}
