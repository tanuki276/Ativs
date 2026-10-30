package com.tanuki276.ativs;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private LinearLayout content;

    // Quiet, matte colors. No glow / neon effects.
    private final int ink = Color.rgb(29, 31, 35);
    private final int muted = Color.rgb(100, 104, 111);
    private final int accent = Color.rgb(61, 72, 86);
    private final int reward = Color.rgb(176, 137, 67);
    private final int surface = Color.rgb(250, 249, 247);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(surface);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = text("Ativs", 32, ink, true);
        content.addView(title);

        TextView subtitle = text("アプリの使用記録を、実績に。", 15, muted, false);
        subtitle.setPadding(0, dp(3), 0, dp(22));
        content.addView(subtitle);

        if (!hasUsageAccess()) {
            showPermissionCard();
            return;
        }

        showUsage();
    }

    private void showPermissionCard() {
        LinearLayout card = card();
        card.addView(text("使用状況へのアクセスが必要です", 20, ink, true));

        TextView body = text(
                "Ativsは端末内でアプリの使用時間と起動回数を読み取り、記録とアチーブメントを表示します。設定からAtivsを許可してください。",
                15, muted, false);
        body.setPadding(0, dp(10), 0, dp(16));
        card.addView(body);

        Button button = new Button(this);
        button.setText("使用状況へのアクセスを開く");
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        card.addView(button);
        content.addView(card);
        animateIn(card, 0);
    }

    private void showUsage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long end = System.currentTimeMillis();
        long start = end - 24L * 60L * 60L * 1000L;

        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        Map<String, Integer> launches = queryLaunchCounts(manager, start, end);
        Map<String, AppRecord> byPackage = new HashMap<>();
        PackageManager pm = getPackageManager();

        if (stats != null) {
            for (UsageStats stat : stats) {
                if (stat.getTotalTimeInForeground() <= 0) continue;

                String packageName = stat.getPackageName();
                AppRecord old = byPackage.get(packageName);
                String label = packageName;
                Drawable icon = null;

                if (old != null) {
                    label = old.label;
                    icon = old.icon;
                } else {
                    try {
                        ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                        label = pm.getApplicationLabel(info).toString();
                        icon = pm.getApplicationIcon(info);
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                }

                long combinedTime = stat.getTotalTimeInForeground() + (old == null ? 0 : old.timeMs);
                long lastUsed = Math.max(stat.getLastTimeUsed(), old == null ? 0 : old.lastUsed);
                int launchCount = launches.getOrDefault(packageName, 0);
                byPackage.put(packageName, new AppRecord(label, packageName, combinedTime, lastUsed, launchCount, icon));
            }
        }

        List<AppRecord> records = new ArrayList<>(byPackage.values());
        Collections.sort(records, Comparator.comparingLong((AppRecord r) -> r.timeMs).reversed());

        long total = 0;
        for (AppRecord r : records) total += r.timeMs;

        LinearLayout summary = card();
        summary.addView(text("過去24時間", 13, muted, true));
        TextView totalView = text("0分", 36, ink, true);
        totalView.setPadding(0, dp(5), 0, dp(3));
        summary.addView(totalView);
        summary.addView(text(records.size() + "個のアプリを記録", 14, muted, false));
        content.addView(summary);
        animateIn(summary, 0);
        animateDuration(totalView, total);

        if (records.isEmpty()) {
            TextView empty = text("まだ使用記録がありません。アプリを使ったあとに戻ってきてください。", 15, muted, false);
            empty.setPadding(0, dp(20), 0, 0);
            content.addView(empty);
            return;
        }

        AppRecord top = records.get(0);

        TextView achievementHeading = sectionTitle("アチーブメント");
        content.addView(achievementHeading);

        addAchievement(
                "1",
                "今日のトップ",
                top.label + "  ·  " + formatDuration(top.timeMs),
                false,
                false);

        boolean hasMilestone = false;
        if (top.timeMs >= 60L * 60L * 1000L) {
            boolean fresh = unlockOnce("usage_1h_" + top.packageName);
            addAchievement("1h", "1時間達成", top.label + "を24時間内に1時間以上使用", true, fresh);
            hasMilestone = true;
        }
        if (top.launches >= 10) {
            boolean fresh = unlockOnce("launch_10_" + top.packageName);
            addAchievement("10×", "10回起動", top.label + "を24時間内に10回以上起動", true, fresh);
            hasMilestone = true;
        }

        if (!hasMilestone) {
            TextView hint = text("もう少しで最初の実績です。", 13, muted, false);
            hint.setPadding(dp(4), 0, 0, dp(4));
            content.addView(hint);
        }

        addNextGoal(top);

        TextView recordHeading = sectionTitle("記録");
        recordHeading.setPadding(0, dp(26), 0, dp(10));
        content.addView(recordHeading);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        int limit = Math.min(records.size(), 30);
        for (int i = 0; i < limit; i++) {
            addAppRow(records.get(i), timeFormat, i);
        }
    }

    private void addNextGoal(AppRecord top) {
        long[] goals = {
                60L * 60L * 1000L,
                3L * 60L * 60L * 1000L,
                5L * 60L * 60L * 1000L,
                8L * 60L * 60L * 1000L,
                12L * 60L * 60L * 1000L
        };

        long target = goals[goals.length - 1];
        for (long goal : goals) {
            if (top.timeMs < goal) {
                target = goal;
                break;
            }
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
        progress.setProgress(0);
        progress.setMinimumHeight(dp(6));
        goal.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)));

        TextView detail = text(formatDuration(top.timeMs) + " / " + targetLabel, 13, muted, false);
        detail.setPadding(0, dp(7), 0, 0);
        goal.addView(detail);
        content.addView(goal);
        animateIn(goal, 2);

        ValueAnimator animator = ValueAnimator.ofInt(0, progressValue);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> progress.setProgress((int) a.getAnimatedValue()));
        animator.start();
    }

    private void addAppRow(AppRecord r, SimpleDateFormat timeFormat, int index) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));

        if (r.icon != null) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(r.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            iconParams.setMargins(0, 0, dp(13), 0);
            row.addView(icon, iconParams);
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(r.label, 16, ink, true));
        info.addView(text(formatDuration(r.timeMs) + "  ·  起動 " + r.launches + "回", 13, accent, true));
        info.addView(text("最終使用 " + timeFormat.format(new Date(r.lastUsed)), 12, muted, false));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        content.addView(row);
        animateIn(row, Math.min(index + 3, 10));
    }

    private Map<String, Integer> queryLaunchCounts(UsageStatsManager manager, long start, long end) {
        Map<String, Integer> counts = new HashMap<>();
        UsageEvents events = manager.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                String pkg = event.getPackageName();
                counts.put(pkg, counts.getOrDefault(pkg, 0) + 1);
            }
        }
        return counts;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean unlockOnce(String key) {
        SharedPreferences prefs = getSharedPreferences("achievements", MODE_PRIVATE);
        if (prefs.getBoolean(key, false)) return false;
        prefs.edit().putBoolean(key, true).apply();
        return true;
    }

    private void addAchievement(String mark, String title, String description, boolean unlocked, boolean fresh) {
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
        topLine.addView(text(title, 16, ink, true));

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

        content.addView(card);

        if (fresh) {
            card.setAlpha(0f);
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .withStartAction(() -> card.performHapticFeedback(HapticFeedbackConstants.CONFIRM))
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

    private void animateDuration(TextView view, long targetMs) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float fraction = (float) a.getAnimatedValue();
            view.setText(formatDuration((long) (targetMs * fraction)));
        });
        animator.start();
    }

    private void animateIn(View view, int order) {
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
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60000L;
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
