package com.jpitsg.sysman;

import android.content.Context;

interface SystemTask {
    String id();

    TaskResult run(Context context, String reason);
}
