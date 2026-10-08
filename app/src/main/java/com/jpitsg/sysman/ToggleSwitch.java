package com.jpitsg.sysman;

import android.content.Context;
import android.widget.Switch;

/**
 * A styled switch that reports every change of state — taps, drags and
 * programmatic changes alike — to a hook, in addition to the ordinary
 * listener. The activity owns the listener for persistence; the hook is what
 * lets a surrounding card restyle itself without displacing it.
 */
final class ToggleSwitch extends Switch {
    interface Hook {
        void onChecked(boolean checked);
    }

    private Hook hook;

    ToggleSwitch(Context context) {
        super(context);
        Ui.styleSwitch(this);
    }

    void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (hook != null) {
            hook.onChecked(isChecked());
        }
    }
}
