package com.tanuki276.ativs;

import java.util.HashMap;
import java.util.Map;

/** Reduces chronological foreground events to a clipped, rolling time window. */
final class UsageAccumulator {
    static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long ACTIVITY_TRANSITION_MS = 1000L;

    static final class Record {
        long timeMs;
        long lastUsed;
        int launches;
    }

    private final long start;
    private final long end;
    private final Map<String, Record> records = new HashMap<>();
    private String foreground;
    private long resumedAt;
    private String previousPackage;
    private long pausedAt = Long.MIN_VALUE;

    UsageAccumulator(long start, long end) {
        if (end < start) throw new IllegalArgumentException("Invalid time window");
        this.start = start;
        this.end = end;
    }

    void resume(String packageName, long timestamp) {
        if (packageName == null || timestamp >= end) return;
        if (packageName.equals(foreground)) return; // Duplicate resume.
        boolean activityTransition = foreground == null
                && packageName.equals(previousPackage)
                && timestamp >= pausedAt
                && timestamp - pausedAt <= ACTIVITY_TRANSITION_MS;
        close(timestamp);
        foreground = packageName;
        resumedAt = timestamp;
        if (timestamp >= start) {
            Record record = record(packageName);
            if (!activityTransition) record.launches++;
            record.lastUsed = Math.max(record.lastUsed, timestamp);
        }
    }

    void pause(String packageName, long timestamp) {
        if (packageName != null && packageName.equals(foreground)) {
            previousPackage = foreground;
            pausedAt = timestamp;
            close(timestamp);
        }
    }

    void interrupt(long timestamp) {
        close(timestamp);
        previousPackage = null;
        pausedAt = Long.MIN_VALUE;
    }

    Map<String, Record> finish() {
        close(end);
        return records;
    }

    private Record record(String packageName) {
        Record record = records.get(packageName);
        if (record == null) {
            record = new Record();
            records.put(packageName, record);
        }
        return record;
    }

    private void close(long timestamp) {
        if (foreground == null) return;
        long from = Math.max(start, resumedAt);
        long to = Math.min(end, timestamp);
        if (to > from) {
            Record record = record(foreground);
            record.timeMs += to - from;
            record.lastUsed = Math.max(record.lastUsed, to);
        }
        foreground = null;
    }
}
