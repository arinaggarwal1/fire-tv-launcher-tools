package dev.tvlaunchers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small on-device root utility. Shell commands contain only constants or validated HOME components. */
public final class LauncherActivity extends Activity {
    private static final String AMAZON_HOME = "com.amazon.tv.launcher/.ui.HomeActivity_vNext";
    private static final String FIRE_STARTER = "com.amazon.firehomestarter/.HomeStarterActivity";
    private static final String REFRACT = "com.hydrainnit.refract/.MainActivity";
    private static final String RESOLVE_HOME = "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME";
    private static final String DUMPSYS_ACTIVITIES = "dumpsys activity activities";
    private static final int BG = Color.rgb(15, 19, 25), PANEL = Color.rgb(35, 42, 51);
    private static final int SELECTED_PANEL = Color.rgb(53, 62, 70);
    private static final int TEXT = Color.rgb(242, 245, 250), MUTED = Color.rgb(183, 194, 210);
    private static final int ORANGE = Color.rgb(255, 174, 105), BLUE = Color.rgb(255, 174, 76);
    private static final int ON_BLUE = Color.rgb(12, 26, 46), FOCUS_RING = Color.rgb(232, 241, 255);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<HomeChoice> choices = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();
    private LinearLayout page;
    private TextView reportView;
    private Button detailsButton;
    private boolean detailsVisible;
    private ScrollView choiceScroll;
    private LinearLayout choiceContainer;
    private final ArrayList<Button> choiceRows = new ArrayList<>();
    private int focusedPosition = -1;
    private Button actionButton;
    private TextView statusView;
    private HomeChoice selected;
    private int selectedPosition = -1;
    private String currentHomeComponent;
    private boolean switchMode;
    private boolean busy;
    private boolean restoring;
    private Button restoreButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            switchMode = "switch".equals(ai.metaData.getString("dev.tvlaunchers.MODE"));
        } catch (Exception ignored) { }
        buildScreen();
    }

    private void buildScreen() {
        int accent = switchMode ? BLUE : ORANGE;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(dp(54), dp(28), dp(54), dp(28));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(page, new LinearLayout.LayoutParams(0, -1, 1.15f));
        TextView title = text(switchMode ? "Home launcher" : "Restore Fire TV Home", 34, TEXT, true);
        page.addView(title, matchWrap());
        View accentLine = new View(this); accentLine.setBackgroundColor(BLUE);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(88), dp(4)); lineParams.topMargin = dp(12); page.addView(accentLine, lineParams);
        TextView intro = text(switchMode ? "Choose the app used for Home." : "Return to the original Fire TV Home screen.", 17, MUTED, false);
        LinearLayout.LayoutParams introParams = matchWrap(); introParams.topMargin = dp(8); page.addView(intro, introParams);
        if (switchMode) buildChoices(accent);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(30), dp(24), dp(30), dp(24));
        panel.setBackground(round(Color.rgb(30, 37, 44), dp(16), 0));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(0, -1, .85f);
        panelParams.leftMargin = dp(36); shell.addView(panel, panelParams);
        page = panel;
        TextView panelTitle = text("Switch launcher", 25, TEXT, true);
        page.addView(panelTitle, matchWrap());
        statusView = text(switchMode ? "Selected: " + (selected == null ? "None" : selected.label) : "Fire TV Home", 17, MUTED, false);
        LinearLayout.LayoutParams statusParams = matchWrap(); statusParams.topMargin = dp(14); page.addView(statusView, statusParams);
        TextView hint = text("Confirm to set your Home screen.", 15, MUTED, false);
        LinearLayout.LayoutParams hintParams = matchWrap(); hintParams.topMargin = dp(8); page.addView(hint, hintParams);
        actionButton = new Button(this);
        actionButton.setId(View.generateViewId());
        actionButton.setAllCaps(false); actionButton.setTextSize(18);
        actionButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        actionButton.setMinHeight(dp(58));
        actionButton.setOnFocusChangeListener((v, focused) -> styleActionButton(focused, accent));
        actionButton.setOnClickListener(v -> confirmAndRun());
        LinearLayout.LayoutParams bp = matchWrap(); bp.topMargin = dp(24); page.addView(actionButton, bp);
        if (switchMode) actionButton.setEnabled(selected != null && selected.validHome);
        if (switchMode) {
            restoreButton = new Button(this);
            restoreButton.setText("Restore Fire TV Home"); restoreButton.setAllCaps(false);
            restoreButton.setTextSize(16); restoreButton.setTextColor(TEXT);
            restoreButton.setBackground(round(BG, dp(12), Color.rgb(91, 103, 119)));
            restoreButton.setMinHeight(dp(56));
            restoreButton.setOnFocusChangeListener((v, focused) -> {
                restoreButton.setTextColor(focused ? ON_BLUE : TEXT);
                restoreButton.setBackground(round(focused ? BLUE : BG, dp(12), focused ? FOCUS_RING : Color.rgb(91, 103, 119)));
            });
            restoreButton.setOnClickListener(v -> confirmRestore());
            LinearLayout.LayoutParams rp = matchWrap(); rp.topMargin = dp(12); page.addView(restoreButton, rp);
        }
        reportView = text("", 13, MUTED, false);
        reportView.setPadding(dp(12), dp(8), dp(12), dp(8));
        reportView.setBackground(round(PANEL, dp(8), 0));
        reportView.setTextIsSelectable(true);
        reportView.setVisibility(View.GONE);
        page.addView(reportView, matchWrap());
        detailsButton = new Button(this);
        detailsButton.setAllCaps(false); detailsButton.setTextSize(13); detailsButton.setTextColor(MUTED);
        detailsButton.setText("View command details");
        detailsButton.setBackground(round(BG, dp(8), 0));
        detailsButton.setMinHeight(dp(42));
        detailsButton.setVisibility(View.GONE);
        detailsButton.setOnFocusChangeListener((v, focused) -> styleQuietButton(detailsButton, focused));
        detailsButton.setOnClickListener(v -> {
            detailsVisible = !detailsVisible;
            reportView.setVisibility(detailsVisible ? View.VISIBLE : View.GONE);
            detailsButton.setText(detailsVisible ? "Hide command details" : "View command details");
        });
        LinearLayout.LayoutParams dp = matchWrap(); dp.topMargin = dp(2); page.addView(detailsButton, dp);
        setContentView(root);
        styleActionButton(actionButton.hasFocus(), accent);
        if (switchMode && focusedPosition >= 0 && focusedPosition < choiceRows.size()) {
            actionButton.setFocusable(true);
            page.postDelayed(() -> {
                actionButton.setNextFocusUpId(choiceRows.get(focusedPosition).getId());
                choiceRows.get(focusedPosition).requestFocus();
            }, 200);
        }
    }

    private void buildChoices(int accent) {
        discoverHomes();
        TextView label = text("Installed launchers", 14, MUTED, true);
        LinearLayout.LayoutParams lp = matchWrap(); lp.topMargin = dp(26); page.addView(label, lp);
        if (choices.isEmpty()) {
            TextView empty = text("No available Home apps were found.", 15, TEXT, false);
            empty.setPadding(0, dp(2), 0, dp(18)); page.addView(empty, matchWrap());
        } else {
            int current = findCurrentChoice();
            selected = choices.get(current);
            selectedPosition = current;
            focusedPosition = current;
            currentHomeComponent = resolvedHome();
            choiceScroll = new ScrollView(this);
            choiceScroll.setFocusable(false);
            choiceScroll.setVerticalScrollBarEnabled(false);
            choiceContainer = new LinearLayout(this);
            choiceContainer.setOrientation(LinearLayout.VERTICAL);
            choiceScroll.addView(choiceContainer, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams scrollp = matchWrap();
            scrollp.height = dp(Math.min(230, Math.max(64, choices.size() * 66)));
            scrollp.topMargin = dp(8);
            page.addView(choiceScroll, scrollp);
            updateChoiceRows();
        }
    }

    private void setSelected(int position) {
        if (position < 0 || position >= choices.size()) return;
        if (position == selectedPosition && selected == choices.get(position)) return;
        selectedPosition = position;
        selected = choices.get(position);
        updateChoiceRowAppearance();
        if (actionButton != null) actionButton.setEnabled(selected.validHome && !busy);
        if (statusView != null) statusView.setText("Selected: " + selected.label);
    }

    private void updateChoiceRows() {
        if (choiceContainer == null) return;
        choiceContainer.removeAllViews(); choiceRows.clear();
        for (int i = 0; i < choices.size(); i++) {
            final int position = i;
            HomeChoice choice = choices.get(i);
            Button row = new Button(this);
            row.setId(View.generateViewId());
            row.setAllCaps(false); row.setTextSize(16); row.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setPadding(dp(22), dp(5), dp(22), dp(5)); row.setMinHeight(dp(58));
            row.setFocusable(true); row.setFocusableInTouchMode(true);
            row.setText(choice.label);
            row.setOnClickListener(v -> { focusedPosition = position; setSelected(position); });
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    focusedPosition = position;
                    row.setBackground(round(position == selectedPosition ? SELECTED_PANEL : PANEL,
                            dp(12), FOCUS_RING));
                    row.setTextColor(TEXT); row.setElevation(dp(2));
                } else {
                    row.setBackground(round(position == selectedPosition ? SELECTED_PANEL : PANEL,
                            dp(12), position == selectedPosition ? BLUE : Color.TRANSPARENT));
                    row.setTextColor(TEXT); row.setElevation(0);
                }
                row.setNextFocusUpId(position == 0 ? row.getId() : choiceRows.get(position - 1).getId());
                row.setNextFocusDownId(position == choices.size() - 1
                        ? (actionButton == null ? row.getId() : actionButton.getId())
                        : View.NO_ID);
                if (actionButton != null && hasFocus) actionButton.setNextFocusUpId(row.getId());
            });
            choiceRows.add(row);
            LinearLayout.LayoutParams rp = matchWrap(); rp.height = dp(64); rp.bottomMargin = dp(6);
            choiceContainer.addView(row, rp);
        }
        updateChoiceRowAppearance();
    }

    private void updateChoiceRowAppearance() {
        for (int i = 0; i < choiceRows.size(); i++) {
            Button row = choiceRows.get(i);
            HomeChoice choice = choices.get(i);
            boolean isSelected = i == selectedPosition;
            boolean isCurrent = choice.component.equals(currentHomeComponent);
            row.setText(choice.label);
            android.graphics.drawable.GradientDrawable indicator = new android.graphics.drawable.GradientDrawable();
            indicator.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            indicator.setColor(isCurrent ? BLUE : Color.TRANSPARENT);
            if (isSelected) indicator.setStroke(dp(3), FOCUS_RING);
            indicator.setSize(dp(20), dp(20));
            row.setCompoundDrawablePadding(dp(16));
            row.setCompoundDrawablesWithIntrinsicBounds(null, null, (isCurrent || isSelected) ? indicator : null, null);
            row.setContentDescription(choice.label + (isSelected ? ", selected" : "")
                    + (isCurrent ? ", current home screen" : "") + ". Press OK to select.");
            if (!row.hasFocus()) {
                row.setBackground(round(isSelected ? SELECTED_PANEL : PANEL,
                        dp(12), isSelected ? BLUE : Color.TRANSPARENT));
                row.setTextColor(TEXT);
            }
        }
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && !busy && switchMode) {
            int key = event.getKeyCode();
            if ((key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || key == android.view.KeyEvent.KEYCODE_DPAD_DOWN && focusedPosition == choices.size() - 1)
                    && focusedPosition >= 0 && focusedPosition < choiceRows.size()
                    && choiceRows.get(focusedPosition).hasFocus() && selected != null && selected.validHome) {
                actionButton.setFocusable(true);
                actionButton.setNextFocusUpId(choiceRows.get(focusedPosition).getId());
                actionButton.requestFocus();
                return true;
            }
            if ((key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_UP) && actionButton != null && actionButton.hasFocus()
                    && focusedPosition >= 0 && focusedPosition < choiceRows.size()) {
                choiceRows.get(focusedPosition).requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private String currentHomeLabel() {
        for (HomeChoice c : choices) if (c.component.equals(currentHomeComponent)) return c.label;
        return "the current launcher";
    }

    private void styleActionButton(boolean focused, int accent) {
        if (actionButton == null) return;
        boolean active = focused && actionButton.isEnabled();
        actionButton.setText(active ? "Continue" : "Continue");
        actionButton.setTextColor(active ? ON_BLUE : TEXT);
        actionButton.setBackground(round(active ? accent : PANEL, dp(12), active ? FOCUS_RING : Color.rgb(77, 91, 111)));
        actionButton.setElevation(active ? dp(3) : 0);
    }

    private void styleQuietButton(Button button, boolean focused) {
        button.setTextColor(focused ? TEXT : MUTED);
        button.setBackground(round(focused ? PANEL : BG, dp(8), focused ? FOCUS_RING : Color.TRANSPARENT));
    }

    private void discoverHomes() {
        PackageManager pm = getPackageManager();
        choices.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> found = pm.queryIntentActivities(home, 0);
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || "com.amazon.tv.launcher".equals(ai.packageName)
                    || "com.amazon.firehomestarter".equals(ai.packageName)) continue;
            // Keep actual user-added launchers and updated system launchers, not firmware support apps.
            if ((ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
            addChoice(ai, true);
        }
        try {
            ActivityInfo refract = pm.getActivityInfo(ComponentName.unflattenFromString(REFRACT), 0);
            boolean already = false;
            for (HomeChoice c : choices) if (c.component.equals(REFRACT)) already = true;
            if (!already) addChoice(refract, false);
        } catch (PackageManager.NameNotFoundException ignored) { }
        Collections.sort(choices, Comparator.comparing(c -> c.label.toLowerCase()));
    }

    private void addChoice(ActivityInfo ai, boolean isHome) {
        if (ai == null) return;
        ComponentName cn = new ComponentName(ai.packageName, ai.name);
        String flat = cn.flattenToShortString();
        for (HomeChoice c : choices) if (c.component.equals(flat)) return;
        boolean usable = isHome && ai.enabled && ai.exported && TextUtils.isEmpty(ai.permission);
        CharSequence lbl = ai.loadLabel(getPackageManager());
        choices.add(new HomeChoice(flat, lbl == null ? ai.packageName : lbl.toString(), ai.packageName,
                ai.name, usable, ai.loadIcon(getPackageManager())));
    }

    private String resolvedHome() {
        ResolveInfo r = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null ? new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToShortString() : null;
    }

    private int findCurrentChoice() {
        try {
            ResolveInfo preferred = getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (preferred != null && preferred.activityInfo != null) {
                String current = new ComponentName(preferred.activityInfo.packageName, preferred.activityInfo.name).flattenToShortString();
                for (int i = 0; i < choices.size(); i++) if (choices.get(i).component.equals(current)) return i;
            }
        } catch (RuntimeException ignored) { }
        return 0;
    }

    private void confirmRestore() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("Restore Fire TV Home")
                .setMessage("Re-enable Amazon Home and Fire Home Starter, set Amazon Home as preferred, then open it?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) -> { restoring = true; startOperation(); }).show();
    }

    private void confirmAndRun() {
        restoring = false;
        if (busy) return;
        if (switchMode && (selected == null || !selected.validHome || !safeComponent(selected.component))) {
            toast("Select an enabled, exported Home activity first."); return;
        }
        boolean restore = !switchMode || AMAZON_HOME.equals(selected.component);
        String message = !restore
                ? "Set " + selected.label + " as Home? The two Amazon Home activities will be disabled. If any step fails, the app will re-enable them and restore the previous Home where possible."
                : "Re-enable Amazon Home and Fire Home Starter, set Amazon Home as preferred, then open it?";
        new AlertDialog.Builder(this).setTitle(!restore ? "Confirm launcher switch" : "Restore Fire TV Home")
                .setMessage(message).setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> startOperation()).show();
    }

    private void startOperation() {
        busy = true; actionButton.setEnabled(false); if (restoreButton != null) restoreButton.setEnabled(false);
        report.setLength(0); append("Starting…");
        detailsVisible = false;
        reportView.setVisibility(View.GONE);
        detailsButton.setText("View command details"); detailsButton.setVisibility(View.VISIBLE);
        statusView.setText("Working…");
        worker.execute(() -> {
            Snapshot before = captureSnapshot();
            boolean ok;
            if (switchMode && !restoring) ok = switchLauncher(before, selected.component);
            else ok = restoreAmazon(before);
            final boolean success = ok;
            String summary = success ? (switchMode ? "Launcher switch completed." : "Amazon Home restore completed.")
                    : "The operation did not complete. Rollback was attempted where needed.";
            append(summary);
            getPreferences(0).edit().putString("last_report", report.toString()).apply();
            runOnUiThread(() -> {
                reportView.setText(report.toString());
                statusView.setText(success ? (switchMode ? "Home screen changed." : "Amazon Home is ready.")
                        : "The change failed. Check details and try again.");
                actionButton.setEnabled(true); if (restoreButton != null) restoreButton.setEnabled(true); busy = false;
                toast(success ? "Done. Reopen this app to review command results." : "Switch failed. Reopen this app to review results and rollback.");
            });
        });
    }

    private boolean restoreAmazon(Snapshot before) {
        if (!command("pm enable --user 0 " + AMAZON_HOME).ok) return rollback(before, "Amazon Home enable failed");
        if (!command("pm enable --user 0 " + FIRE_STARTER).ok) return rollback(before, "Fire Home Starter enable failed");
        if (!command("pm set-home-activity " + AMAZON_HOME).ok) return rollback(before, "Setting Amazon Home failed");
        if (!command("am start -n " + AMAZON_HOME).ok) return rollback(before, "Launching Amazon Home failed");
        if (!verifyHome(AMAZON_HOME)) return rollback(before, "Amazon Home did not resolve as the preferred Home");
        if (!verifyHomeEvent(AMAZON_HOME)) return rollback(before, "Android HOME key event did not open Amazon Home");
        return true;
    }

    private boolean switchLauncher(Snapshot before, String target) {
        if (!safeComponent(target) || !verifyCandidate(target)) {
            append("Selected activity failed preflight validation; no launcher components were changed."); return false;
        }
        if (!command("pm disable --user 0 " + AMAZON_HOME).ok) return rollback(before, "Disabling Amazon Home failed");
        if (!command("pm disable --user 0 " + FIRE_STARTER).ok) return rollback(before, "Disabling Fire Home Starter failed");
        if (!command("pm set-home-activity " + target).ok) return rollback(before, "Setting selected Home failed");
        if (!command("am start -n " + target).ok) return rollback(before, "Launching selected Home failed");
        if (!verifyHome(target)) return rollback(before, "Selected launcher did not resolve as the preferred Home");
        if (!verifyHomeEvent(target)) return rollback(before, "Android HOME key event did not open the selected launcher");
        return true;
    }

    private boolean verifyHomeEvent(String expected) {
        if (!command("input keyevent KEYCODE_HOME").ok) return false;
        try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        CommandResult r = command(DUMPSYS_ACTIVITIES);
        String activeLine = null;
        for (String line : r.output.split("\\r?\\n")) {
            if (line.contains("mResumedActivity") || line.contains("mFocusedActivity")) { activeLine = line.trim(); break; }
        }
        String actual = findComponent(activeLine);
        boolean match = r.ok && expected.equals(normalize(actual));
        append("Android HOME key-event check: expected " + expected + "; active "
                + (actual == null ? "(not reported)" : actual) + (match ? " — MATCH" : " — MISMATCH"));
        append("This software-generated HOME event cannot confirm Fire TV's physical remote interception path.");
        return match;
    }

    private boolean verifyCandidate(String flat) {
        try {
            ComponentName c = ComponentName.unflattenFromString(flat);
            ActivityInfo i = getPackageManager().getActivityInfo(c, 0);
            if (!i.enabled || !i.exported || !TextUtils.isEmpty(i.permission)) {
                append("Preflight failed: activity is disabled, not exported, or permission-protected."); return false;
            }
            boolean inHomeList = false;
            for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
                if (flat.equals(new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToShortString())) inHomeList = true;
            if (!inHomeList) { append("Preflight failed: activity is not registered for Android HOME."); return false; }
            append("Preflight passed: installed, enabled, exported, unprotected HOME activity.");
            return true;
        } catch (Exception e) { append("Preflight failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()); return false; }
    }

    private boolean verifyHome(String expected) {
        CommandResult r = command(RESOLVE_HOME);
        String resolved = findComponent(r.output);
        boolean match = r.ok && expected.equals(normalize(resolved));
        append("Home verification: expected " + expected + "; resolved " + (resolved == null ? "(no component reported)" : resolved)
                + (match ? " — MATCH" : " — MISMATCH"));
        if (!match) append("Fire OS may override Android's preferred Home resolution.");
        return match;
    }

    private Snapshot captureSnapshot() {
        Snapshot s = new Snapshot();
        try {
            ResolveInfo home = getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (home != null && home.activityInfo != null)
                s.previousHome = new ComponentName(home.activityInfo.packageName, home.activityInfo.name).flattenToShortString();
        } catch (Exception ignored) { }
        try { s.amazonState = getPackageManager().getComponentEnabledSetting(ComponentName.unflattenFromString(AMAZON_HOME)); }
        catch (Exception ignored) { }
        try { s.starterState = getPackageManager().getComponentEnabledSetting(ComponentName.unflattenFromString(FIRE_STARTER)); }
        catch (Exception ignored) { }
        append("Previous preferred Home: " + (s.previousHome == null ? "not reported" : s.previousHome));
        append("Captured Amazon component states: Home=" + stateName(s.amazonState) + ", Starter=" + stateName(s.starterState));
        return s;
    }

    private boolean rollback(Snapshot before, String reason) {
        append("FAILURE: " + reason + ". Beginning rollback.");
        restoreComponent(AMAZON_HOME, before.amazonState);
        restoreComponent(FIRE_STARTER, before.starterState);
        String previous = before.previousHome;
        if (previous == null || !safeComponent(previous) || !isInstalled(previous)) previous = AMAZON_HOME;
        boolean restored = command("pm set-home-activity " + previous).ok;
        if (restored) restored = command("am start -n " + previous).ok;
        if (!restored) {
            append("Could not restore and launch the previous Home. Trying the enabled Amazon Home fallback.");
            command("pm enable --user 0 " + AMAZON_HOME);
            command("pm enable --user 0 " + FIRE_STARTER);
            boolean fallback = command("pm set-home-activity " + AMAZON_HOME).ok;
            if (fallback) fallback = command("am start -n " + AMAZON_HOME).ok;
            previous = AMAZON_HOME;
            if (!fallback) append("Amazon Home fallback command also failed.");
        }
        CommandResult finalHome = command(RESOLVE_HOME);
        String resolved = findComponent(finalHome.output);
        boolean verified = finalHome.ok && previous.equals(normalize(resolved));
        append("Rollback Home verification: expected " + previous + "; resolved "
                + (resolved == null ? "(no component reported)" : resolved) + (verified ? " — MATCH" : " — MISMATCH"));
        if (!verified) append("Fire OS may override Android's preferred Home resolution.");
        append("Rollback attempt finished.");
        return false;
    }

    private void restoreComponent(String component, int state) {
        String cmd;
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) cmd = "pm default-state --user 0 " + component;
        else if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) cmd = "pm enable --user 0 " + component;
        else if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) cmd = "pm disable-user --user 0 " + component;
        else if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) cmd = "pm enable --user 0 " + component;
        else cmd = "pm disable --user 0 " + component;
        command(cmd);
    }

    private boolean isInstalled(String flat) {
        try { getPackageManager().getActivityInfo(ComponentName.unflattenFromString(flat), 0); return true; }
        catch (Exception e) { return false; }
    }

    private CommandResult command(String cmd) {
        append("$ su -c '" + cmd + "'");
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            int code = process.waitFor();
            String result = new String(out.toByteArray(), Charset.forName("UTF-8")).trim();
            String lower = result.toLowerCase();
            boolean shellReportedFailure = lower.contains("error:") || lower.contains("exception")
                    || lower.startsWith("failure") || lower.contains("unknown command");
            boolean ok = code == 0 && !shellReportedFailure;
            String shown = cmd.equals(DUMPSYS_ACTIVITIES)
                    ? "(activity dump captured; resumed-activity line checked below)" : result;
            append((shown.length() == 0 ? "(no output)" : shown) + "\nexit=" + code + (ok ? "  OK" : "  FAILED"));
            return new CommandResult(ok, result);
        } catch (Exception e) {
            if (process != null) process.destroy();
            String m = e.getClass().getSimpleName() + ": " + e.getMessage(); append(m + "  FAILED");
            return new CommandResult(false, m);
        }
    }

    private void append(String line) {
        synchronized (report) { if (report.length() > 0) report.append('\n'); report.append(line); }
        String snapshot; synchronized (report) { snapshot = report.toString(); }
        runOnUiThread(() -> { if (reportView != null) reportView.setText(snapshot); });
    }

    private static String findComponent(String output) {
        if (output == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)").matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }
    private static String normalize(String value) {
        if (value == null) return null;
        ComponentName c = ComponentName.unflattenFromString(value);
        return c == null ? value : c.flattenToShortString();
    }
    private static boolean safeComponent(String value) {
        return value != null && value.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")
                && !value.contains("..") && !value.startsWith(".") && value.indexOf('/') == value.lastIndexOf('/');
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private String stateName(int state) {
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return "enabled";
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return "disabled";
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) return "disabled-user";
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return "disabled-until-used";
        return "default";
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(dp(2), stroke); return d;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing()) worker.shutdown(); }

    private static final class HomeChoice {
        final String component, label, pkg, cls; final boolean validHome; final android.graphics.drawable.Drawable icon;
        HomeChoice(String c, String l, String p, String n, boolean valid, android.graphics.drawable.Drawable i) {
            component = c; label = l; pkg = p; cls = n; validHome = valid; icon = i;
        }
    }
    private static final class Snapshot { String previousHome; int amazonState, starterState; }
    private static final class CommandResult { final boolean ok; final String output; CommandResult(boolean o, String s) { ok = o; output = s; } }
}
