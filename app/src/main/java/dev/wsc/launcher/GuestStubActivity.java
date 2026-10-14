package dev.wsc.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Runs only when bootstrap failed or the hook did not replace the registered stub. */
public final class GuestStubActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView message = new TextView(this);
        message.setPadding(32, 32, 32, 32);
        message.setText("Não foi possível hospedar o jogo.\n\n" + GuestRuntime.failure + "\n\nVolte ao launcher para consultar o diagnóstico.");
        setContentView(message);
    }
}
