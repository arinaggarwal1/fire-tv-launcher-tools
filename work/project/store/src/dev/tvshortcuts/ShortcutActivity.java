package dev.tvshortcuts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** Invisible, single-purpose launcher. No network, storage, or background services. */
public final class ShortcutActivity extends Activity {
    private static final String TAG = "TVShortcut";
    private static final String TARGET_PACKAGE = "com.amazon.venezia";
    private static final String TARGET_CLASS = "com.amazon.venezia.ade.ADEHomeActivity";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final ComponentName target = new ComponentName(TARGET_PACKAGE, TARGET_CLASS);
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(target, 0);
            Log.i(TAG, "Target " + target.flattenToShortString() + " exported="
                    + info.exported + " permission=" + info.permission);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "Direct launch accepted: " + target.flattenToShortString());
            close();
        } catch (SecurityException denied) {
            fail("Amazon blocked this shortcut. See TVShortcut log for the required permission.", denied.toString());
        } catch (ActivityNotFoundException missing) {
            fail("Target activity is missing or disabled.", missing.toString());
        } catch (RuntimeException error) {
            fail("Unable to open target activity. See TVShortcut log.", error.toString());
        }
    }

    private void fail(String message, String details) {
        Log.e(TAG, message + " " + details);
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        close();
    }
    private void close() { finish(); overridePendingTransition(0, 0); }
}
