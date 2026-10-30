package com.tanuki276.ativs;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private final int accent = Color.rgb(79, 70, 229);

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
        scroll.setBackgroundColor(Color.rgb(247, 248, 252));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = text("Ativs", 32, Color.rgb(20, 20, 30), true);
        content.addView(title);
        TextView subtitle = text("アプリの使用記録を、実績に。", 16, Color.DKGRAY, false);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        content.addView(subtitle);

        if (!hasUsageAccess()) {
            showPermissionCard();
            return;
        }

        showUsage();
    }

    private void showPermissionCard() {
        LinearLayout card = card();
        TextView heading = text("使用状況へのアクセスが必要です", 20, Color.rgb(25, 25, 35), true);
        card.addView(heading);

        TextView body = text(
                "Ativsは端末内だけでアプリの使用時間と起動回数を読み取り、記録とアチーブメントを表示します。設定からAtivsを許可してください。",
                15, Color.DKGRAY, false);
        body.setPadding(0, dp(10), 0, dp(16));
        card.addView(body);

        Button button = new Button(this);
        button.setText("使用状況へのアクセスを開く");
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        card.addView(button);
        content.addView(card);
    }

    private void showUsage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long end = System.currentTimeMillis();
        long start = end - 24L * 60L * 60L * 1000L;

        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        Map<String, Integer> launches = queryLaunchCounts(manager, start, end);
        List<AppRecord> records = new ArrayList<>();

        if (stats != null) {
            PackageManager pm = getPackageManager();
            for (UsageStats stat : stats) {
                if (stat.getTotalTimeInForeground() <= 0) continue;
                String packageName = stat.getPackageName();
                String label = packageName;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                    label = pm.getApplicationLabel(info).toString();
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                records.add(new AppRecord(
                        label,
                        packageName,
                        stat.getTotalTimeInForeground(),
                        stat.getLastTimeUsed(),
                        launches.getOrDefault(packageName, 0)));
            }
        }

        Collections.sort(records, Comparator.comparingLong((AppRecord r) -> r.timeMs).reversed());

        long total = 0;
        for (AppRecord r : records) total += r.timeMs;

        LinearLayout summary = card();
        summary.addView(text("過去24時間", 15, Color.GRAY, true));
        TextView totalView = text(formatDuration(total), 34, accent, true);
        totalView.setPadding(0, dp(4), 0, dp(4));
        summary.addView(totalView);
        summary.addView(text(records.size() + "個のアプリを記録", 14, Color.DKGRAY, false));
        content.addView(summary);

        TextView achievementHeading = text("アチーブメント", 22, Color.rgb(20, 20, 30), true);
        achievementHeading.setPadding(0, dp(24), 0, dp(10));
        content.addView(achievementHeading);

        if (records.isEmpty()) {
            content.addView(text("まだ使用記録がありません。アプリを使ったあとに戻ってきてください。", 15, Color.DKGRAY, false));
            return;
        }

        AppRecord top = records.get(0);
        addAchievement("🏆 今日のトップ", top.label + "  •  " + formatDuration(top.timeMs));
        if (top.timeMs >= 60L * 60L * 1000L) {
            addAchievement("⏱ 1時間達成", top.label + "を24時間内に1時間以上使用");
        }
        if (top.launches >= 10) {
            addAchievement("🚀 10回起動", top.label + "を24時間内に10回以上起動");
        }

        TextView recordHeading = text("記録", 22, Color.rgb(20, 20, 30), true);
        recordHeading.setPadding(0, dp(26), 0, dp(10));
        content.addView(recordHeading);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        int limit = Math.min(records.size(), 30);
        for (int i = 0; i < limit; i++) {
            AppRecord r = records.get(i);
            LinearLayout row = card();
            row.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView name = text(r.label, 17, Color.rgb(25, 25, 35), true);
            row.addView(name);
            row.addView(text(formatDuration(r.timeMs) + "  •  起動 " + r.launches + "回", 14, accent, true));
            row.addView(text("最終使用 " + timeFormat.format(new Date(r.lastUsed)), 13, Color.GRAY, false));
            content.addView(row);
        }
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

    private void addAchievement(String title, String description) {
        LinearLayout card = card();
        card.addView(text(title, 17, Color.rgb(25, 25, 35), true));
        TextView desc = text(description, 14, Color.DKGRAY, false);
        desc.setPadding(0, dp(4), 0, 0);
        card.addView(desc);
        content.addView(card);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(229, 231, 235));
        card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
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

        AppRecord(String label, String packageName, long timeMs, long lastUsed, int launches) {
            this.label = label;
            this.packageName = packageName;
            this.timeMs = timeMs;
            this.lastUsed = lastUsed;
            this.launches = launches;
        }
    }
}
