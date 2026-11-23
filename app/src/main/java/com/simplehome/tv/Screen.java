package com.simplehome.tv;

import android.app.Activity;
import android.content.Intent;

/**
 * Base for screens that slide in over the one below (Settings, App Selection, the pickers, setup):
 * they slide in from the right while the screen underneath dims and shrinks back slightly,
 * and slide away again when you go back.
 */
public abstract class Screen extends Activity {

    /** Opens another screen with the slide-in animation. */
    static void open(Activity from, Intent intent) {
        from.startActivity(intent);
        from.overridePendingTransition(R.anim.screen_in, R.anim.screen_push);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_pop, R.anim.screen_out);
    }
}
