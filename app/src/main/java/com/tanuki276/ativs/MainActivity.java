package com.tanuki276.ativs;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    private LinearLayout content;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Future<?> pendingLoad;
    private int loadGeneration;
    private static final int[] GOAL_HOURS = {1, 3, 5, 8, 12};

    // Quiet, matte colors. No glow / neon effects.
    private final int ink = Color.rgb(29, 31, 35);
    private final int muted = Color.rgb(100, 104, 111);
    private final int accent = Color.rgb(61, 72, 86);
    private final int reward = Color.rgb(128, 99, 47);
    private final int surface = Color.rgb(250, 249, 247);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        loadGeneration++;
        if (pendingLoad != null) pendingLoad.cancel(true);
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        final int generation = ++loadGeneration;
        if (pendingLoad != null) pendingLoad.cancel(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(surface);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        scroll.requestApplyInsets();

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_brand);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoParams.setMargins(0, 0, dp(12), 0);
        brand.addView(logo, logoParams);
        brand.addView(text("Ativs", 32, ink, true));
        content.addView(brand);
        TextView subtitle = text("アプリの使用記録を、実績に。", 15, muted, false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        content.addView(subtitle);

        if (!hasUsageAccess()) {
            showPermissionCard();
            return;
        }
        LinearLayout actions = new LinearLayout(this);
        Button refresh = actionButton("更新", this::render);
        Button settings = actionButton("アクセス設定", this::openUsageSettings);
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1f));
        actions.addView(settings, new LinearLayout.LayoutParams(0, dp(48), 1f));
        content.addView(actions);
        TextView loading = text("使用記録を読み込んでいます…", 15, muted, false);
        loading.setPadding(0, dp(24), 0, dp(24));
        loading.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(loading);
        pendingLoad = loader.submit(() -> {
            try {
                long end = System.currentTimeMillis();
                List<AppRecord> records = loadUsage(end);
                runOnUiThread(() -> {
                    if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                    if (!hasUsageAccess()) {
                        render();
                        return;
                    }
                    content.removeView(loading);
                    showUsage(records, end);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                    content.removeView(loading);
                    if (!hasUsageAccess()) {
                        render();
                        return;
                    }
                    LinearLayout failure = card();
                    failure.addView(text("記録を読み込めませんでした", 18, ink, true));
                    failure.addView(text("端末のロックを解除し、しばらくしてから更新してください。", 15, muted, false));
                    failure.addView(actionButton("再試行", this::render));
                    content.addView(failure);
                });
            }
        });
    }

    private Button actionButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setTextColor(accent);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void openUsageSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "端末の設定から「使用状況へのアクセス」を開いてください。", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showPermissionCard() {
        LinearLayout card = card();
        card.addView(text("使用状況へのアクセスが必要です", 20, ink, true));

        TextView body = text(
                "Ativsは端末内でアプリの使用時間と起動回数を読み取り、記録とアチーブメントを表示します。設定からAtivsを許可してください。",
                15, muted, false);
        body.setPadding(0, dp(10), 0, dp(16));
        card.addView(body);

        card.addView(actionButton("使用状況へのアクセスを開く", this::openUsageSettings));
        card.addView(text("記録は端末内で処理され、外部には送信されません。許可はいつでも設定から取り消せます。", 13, muted, false));
        content.addView(card);
        animateIn(card, 0);
    }

    private List<AppRecord> loadUsage(long end) {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) throw new IllegalStateException("Usage service unavailable");
        long start = end - UsageAccumulator.DAY_MS;
        // Read a warm-up day to recover sessions already running at the window boundary.
        UsageEvents events = manager.queryEvents(start - UsageAccumulator.DAY_MS, end);
        if (events == null) throw new IllegalStateException("Usage events unavailable");
        UsageAccumulator accumulator = new UsageAccumulator(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Load cancelled");
            events.getNextEvent(event);
            switch (event.getEventType()) {
                case UsageEvents.Event.MOVE_TO_FOREGROUND:
                    accumulator.resume(event.getPackageName(), event.getTimeStamp());
                    break;
                case UsageEvents.Event.MOVE_TO_BACKGROUND:
                    accumulator.pause(event.getPackageName(), event.getTimeStamp());
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                case UsageEvents.Event.KEYGUARD_SHOWN:
                case UsageEvents.Event.DEVICE_SHUTDOWN:
                case UsageEvents.Event.DEVICE_STARTUP:
                    accumulator.interrupt(event.getTimeStamp());
                    break;
                default:
                    break;
            }
        }
        PackageManager pm = getPackageManager();
        List<AppRecord> records = new ArrayList<>();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo home = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        String homePackage = home != null && home.activityInfo != null ? home.activityInfo.packageName : "";
        for (Map.Entry<String, UsageAccumulator.Record> entry : accumulator.finish().entrySet()) {
            String packageName = entry.getKey();
            UsageAccumulator.Record usage = entry.getValue();
            if (packageName.equals(getPackageName()) || packageName.equals(homePackage)
                    || (usage.timeMs <= 0 && usage.launches == 0)
                    || pm.getLaunchIntentForPackage(packageName) == null) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                records.add(new AppRecord(pm.getApplicationLabel(info).toString(), packageName,
                        usage.timeMs, usage.lastUsed, usage.launches, pm.getApplicationIcon(info)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // An app can be uninstalled while the query is running.
            }
        }
        records.sort(Comparator.comparingLong((AppRecord record) -> record.timeMs).reversed()
                .thenComparing(record -> record.packageName));
        return records;
    }

    private void showUsage(List<AppRecord> records, long end) {
        long total = 0;
        for (AppRecord r : records) total += r.timeMs;

        LinearLayout summary = card();
        summary.addView(text("過去24時間", 13, muted, true));
        TextView totalView = text(formatDuration(total), 36, ink, true);
        totalView.setPadding(0, dp(5), 0, dp(3));
        summary.addView(totalView);
        summary.addView(text(records.size() + "個のアプリを記録", 14, muted, false));
        content.addView(summary);
        animateIn(summary, 0);
        SimpleDateFormat windowFormat = new SimpleDateFormat("M/d HH:mm", Locale.getDefault());
        TextView window = text(windowFormat.format(new Date(end - UsageAccumulator.DAY_MS))
                + " 〜 " + windowFormat.format(new Date(end)), 12, muted, false);
        window.setPadding(0, dp(8), 0, 0);
        summary.addView(window);
        TextView note = text("ホーム画面とAtivsを除くアプリの前面使用を集計。起動回数はアプリを前面に開いた回数の目安です。", 12, muted, false);
        note.setPadding(0, dp(8), 0, 0);
        summary.addView(note);

        showAchievements(records);

        if (records.isEmpty()) {
            TextView empty = text("まだ使用記録がありません。アプリを使ったあとに戻ってきてください。", 15, muted, false);
            empty.setPadding(0, dp(20), 0, 0);
            content.addView(empty);
            return;
        }

        AppRecord top = records.get(0);

        addAchievement("1", "過去24時間のトップ", top.label + "  ·  " + formatDuration(top.timeMs), false, false);

        addNextGoal(top);

        TextView recordHeading = sectionTitle("記録");
        recordHeading.setPadding(0, dp(26), 0, dp(10));
        content.addView(recordHeading);

        int limit = records.size();
        for (int i = 0; i < limit; i++) {
            addAppRow(records.get(i), i);
        }
    }

    private void showAchievements(List<AppRecord> records) {
        content.addView(sectionTitle("アチーブメント"));
        SharedPreferences prefs = getSharedPreferences("achievements", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        java.util.Set<String> fresh = new java.util.HashSet<>();
        for (AppRecord record : records) {
            editor.putString("label_" + record.packageName, record.label);
            for (int hours : GOAL_HOURS) {
                String key = "usage_" + hours + "h_" + record.packageName;
                if (record.timeMs >= hours * 3600000L && !prefs.getBoolean(key, false)) {
                    editor.putBoolean(key, true);
                    fresh.add(key);
                }
            }
            String key = "launch_10_" + record.packageName;
            if (record.launches >= 10 && !prefs.getBoolean(key, false)) {
                editor.putBoolean(key, true);
                fresh.add(key);
            }
        }
        editor.apply();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())
                    && (entry.getKey().startsWith("usage_") || entry.getKey().startsWith("launch_10_"))) {
                keys.add(entry.getKey());
            }
        }
        Collections.sort(keys, (left, right) -> {
            int freshOrder = Boolean.compare(fresh.contains(right), fresh.contains(left));
            return freshOrder != 0 ? freshOrder : left.compareTo(right);
        });
        if (keys.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("実績はここに残ります", 17, ink, true));
            empty.addView(text("過去24時間で1つのアプリを1時間使用、または10回開くと獲得できます。自分のペースで記録しましょう。", 14, muted, false));
            content.addView(empty);
        }
        LinearLayout achievementList = new LinearLayout(this);
        achievementList.setOrientation(LinearLayout.VERTICAL);
        content.addView(achievementList);
        addAchievementPage(achievementList, keys, fresh, prefs, 0);
    }

    private void addAchievementPage(LinearLayout list, List<String> keys, java.util.Set<String> fresh,
            SharedPreferences prefs, int offset) {
        int end = Math.min(keys.size(), offset + 8);
        for (int index = offset; index < end; index++) {
            String key = keys.get(index);
            int separator = key.indexOf('_', key.indexOf('_') + 1);
            if (separator < 0 || separator == key.length() - 1) continue;
            String packageName = key.substring(separator + 1);
            String label = prefs.getString("label_" + packageName, packageName);
            boolean launch = key.startsWith("launch_");
            String milestone = key.substring(key.indexOf('_') + 1, separator);
            addAchievement(list, launch ? "10×" : milestone,
                    launch ? "10回起動" : milestone.replace("h", "時間") + "達成",
                    label + " · 獲得済み", true, fresh.contains(key));
        }
        if (end < keys.size()) {
            Button more = actionButton("実績をもっと見る（残り" + (keys.size() - end) + "件）", () -> {});
            more.setOnClickListener(view -> {
                list.removeView(more);
                addAchievementPage(list, keys, fresh, prefs, end);
            });
            list.addView(more);
        }
    }

    private void addNextGoal(AppRecord top) {
        long target = 0;
        for (int hours : GOAL_HOURS) {
            long goal = hours * 3600000L;
            if (!getSharedPreferences("achievements", MODE_PRIVATE)
                    .getBoolean("usage_" + hours + "h_" + top.packageName, false)) {
                target = goal;
                break;
            }
        }
        if (target == 0) {
            LinearLayout completed = card();
            completed.addView(text("時間の実績をすべて達成", 17, ink, true));
            completed.addView(text(top.label + " · 12時間までの実績を獲得しました。", 14, muted, false));
            content.addView(completed);
            return;
        }

        int progressValue = (int) Math.min(1000L, (top.timeMs * 1000L) / Math.max(1L, target));
        int percent = progressValue / 10;

        LinearLayout goal = card();
        goal.setPadding(dp(18), dp(16), dp(18), dp(16));
        goal.addView(text("次の実績", 12, muted, true));

        String targetLabel = formatDuration(target).replace(" 0分", "");
        TextView line = text(top.label + "  " + percent + "%", 17, ink, true);
        line.setPadding(0, dp(5), 0, dp(6));
        goal.addView(line);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(progressValue);
        progress.setProgressTintList(ColorStateList.valueOf(reward));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(238, 236, 231)));
        progress.setMinimumHeight(dp(6));
        goal.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)));

        TextView detail = text(formatDuration(top.timeMs) + " / " + targetLabel, 13, muted, false);
        detail.setPadding(0, dp(7), 0, 0);
        goal.addView(detail);
        content.addView(goal);
        animateIn(goal, 2);

    }

    private void addAppRow(AppRecord r, int index) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));

        if (r.icon != null) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(r.icon);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            iconParams.setMargins(0, 0, dp(13), 0);
            row.addView(icon, iconParams);
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(r.label, 16, ink, true));
        info.addView(text(formatDuration(r.timeMs) + "  ·  起動 " + r.launches + "回", 13, accent, true));
        info.addView(text("最終使用 " + new SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(new Date(r.lastUsed)), 12, muted, false));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        content.addView(row);
        animateIn(row, Math.min(index + 3, 10));
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        try {
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (SecurityException error) {
            return false;
        }
    }

    private void addAchievement(String mark, String title, String description, boolean unlocked, boolean fresh) {
        addAchievement(content, mark, title, description, unlocked, fresh);
    }

    private void addAchievement(LinearLayout parent, String mark, String title, String description,
            boolean unlocked, boolean fresh) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView badge = text(mark, mark.length() > 2 ? 12 : 14, unlocked ? Color.WHITE : ink, true);
        badge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setColor(unlocked ? reward : Color.rgb(238, 236, 231));
        badgeBg.setCornerRadius(dp(13));
        badge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        badgeParams.setMargins(0, 0, dp(13), 0);
        card.addView(badge, badgeParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        topLine.addView(text(title, 16, ink, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (fresh) {
            TextView newTag = text("NEW", 10, reward, true);
            newTag.setPadding(dp(8), dp(2), dp(8), dp(2));
            android.graphics.drawable.GradientDrawable tagBg = new android.graphics.drawable.GradientDrawable();
            tagBg.setColor(Color.rgb(246, 239, 224));
            tagBg.setCornerRadius(dp(20));
            newTag.setBackground(tagBg);
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tagParams.setMargins(dp(9), 0, 0, 0);
            topLine.addView(newTag, tagParams);
        }

        copy.addView(topLine);
        TextView desc = text(description, 13, muted, false);
        desc.setPadding(0, dp(3), 0, 0);
        copy.addView(desc);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parent.addView(card);

        if (fresh && ValueAnimator.areAnimatorsEnabled()) {
            card.setAlpha(0f);
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .withStartAction(() -> card.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                            ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY))
                    .start();
        } else {
            animateIn(card, 1);
        }
    }

    private TextView sectionTitle(String value) {
        TextView heading = text(value, 21, ink, true);
        heading.setPadding(0, dp(24), 0, dp(10));
        return heading;
    }

    private void animateIn(View view, int order) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(order, 10) * 25L)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(229, 227, 222));
        card.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        view.setIncludeFontPadding(false);
        view.setTextLocale(Locale.JAPANESE);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private String formatDuration(long ms) {
        if (ms > 0 && ms < 60000L) return "1分未満";
        long minutes = Math.max(0L, ms) / 60000L;
        long hours = minutes / 60L;
        long remaining = minutes % 60L;
        if (hours > 0) return hours + "時間 " + remaining + "分";
        return minutes + "分";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class AppRecord {
        final String label;
        final String packageName;
        final long timeMs;
        final long lastUsed;
        final int launches;
        final Drawable icon;

        AppRecord(String label, String packageName, long timeMs, long lastUsed, int launches, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.timeMs = timeMs;
            this.lastUsed = lastUsed;
            this.launches = launches;
            this.icon = icon;
        }
    }
}
