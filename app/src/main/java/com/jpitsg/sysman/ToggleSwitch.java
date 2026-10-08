package com.jpitsg.sysman;

import android.content.Context;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;

/**
 * A styled switch that reports every change of state — taps, drags and
 * programmatic changes alike — to its hooks, in addition to the ordinary
 * listener. The activity owns the listener for persistence; hooks are how a
 * surrounding card restyles itself, or an overview tile mirrors the switch,
 * without displacing it.
 */
final class ToggleSwitch extends Switch {
    interface Hook {
        void onChecked(boolean checked);
    }

    private final List<Hook> hooks = new ArrayList<>();

    ToggleSwitch(Context context) {
        super(context);
        Ui.styleSwitch(this);
    }

    void addHook(Hook hook) {
        hooks.add(hook);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        // CompoundButton's constructor calls this before the field above exists.
        if (hooks != null) {
            for (int i = 0; i < hooks.size(); i++) {
                hooks.get(i).onChecked(isChecked());
            }
        }
    }
}
