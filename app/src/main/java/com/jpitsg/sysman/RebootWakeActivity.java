package com.jpitsg.sysman;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public final class RebootWakeActivity extends Activity {
    static final String EXTRA_REASON = "reason";
    private static final long FINISH_DELAY_MILLIS = 1_200L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String reason = "";
        if (getIntent() != null && getIntent().getStringExtra(EXTRA_REASON) != null) {
            reason = getIntent().getStringExtra(EXTRA_REASON);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        LogStore.append(this, "reboot", "Wake activity opened reason=" + reason);
        window.getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
                overridePendingTransition(0, 0);
            }
        }, FINISH_DELAY_MILLIS);
    }
}
