package com.google.android.gms.ads.internal.client;

import android.content.Context;

/** WSC 468's settings manager captures a context before creating its client. */
public final class zzex {
    public Context captured;
    public Object listener;
    public void zzc(Context context, String id, Object listener) {
        this.captured = context;
        this.listener = listener;
    }
    private void zzC(Context context) { }
}
