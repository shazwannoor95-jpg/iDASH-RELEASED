package com.shazwan.idash3;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Same warm navigation sequence on the baseline and fix pack, with offline maps. */
public class FixPackPerformanceChecks extends Instrumentation {
    private final List<Long> frames = new ArrayList<>();
    private final List<Long> transitions = new ArrayList<>();
    private LauncherActivity owner;
    private int dropped;
    private boolean measuring;
    private final Window.OnFrameMetricsAvailableListener listener = (window, metrics, skipped) -> {
        if (measuring) {
            long duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
            if (duration > 0) frames.add(duration);
            dropped += skipped;
        }
    };

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("idash", 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        int code = Activity.RESULT_CANCELED;
        try {
            prefs.edit().putInt("dashboard_panel", 0).putBoolean("dashboard_location", false)
                    .putBoolean("dashboard_weather_location", false).apply();
            owner = (LauncherActivity) startActivitySync(new Intent(getTargetContext(), LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline = SystemClock.elapsedRealtime() + 45000;
            while (!owner.loaded && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            if (!owner.loaded) throw new AssertionError("catalog not ready");
            runOnMainSync(() -> {
                owner.page = "drive";
                owner.render();
                offlineMap();
                owner.getWindow().addOnFrameMetricsAvailableListener(listener, owner.main);
            });
            cycles(2);
            capture("mem-start.txt", "dumpsys meminfo com.shazwan.idash3");
            capture("gfx-reset.txt", "dumpsys gfxinfo com.shazwan.idash3 reset");
            runOnMainSync(() -> measuring = true);
            cycles(10);
            capture("mem-10.txt", "dumpsys meminfo com.shazwan.idash3");
            cycles(10);
            capture("mem-20.txt", "dumpsys meminfo com.shazwan.idash3");
            cycles(10);
            waitForIdleSync();
            runOnMainSync(() -> measuring = false);
            capture("mem-30.txt", "dumpsys meminfo com.shazwan.idash3");
            capture("gfx-30.txt", "dumpsys gfxinfo com.shazwan.idash3 framestats");
            capture("trace-logcat.txt", "logcat -d -t 400 -s Choreographer ActivityManager chromium");
            long overBudget = frames.stream().filter(frame -> frame > 16_666_667L).count();
            boolean sampled = !frames.isEmpty();
            result.putString("stream", String.format(Locale.US,
                    "30 cycles, %d transitions. Frame samples %d (dropped callbacks %d), >16.67ms %d. "
                    + "Frame p50/p95/p99 %.2f/%.2f/%.2f ms, main-thread navigation p50/p95/p99 %.2f/%.2f/%.2f ms.\n",
                    transitions.size(), frames.size(), dropped, overBudget,
                    percentile(frames, .50, 1_000_000), percentile(frames, .95, 1_000_000),
                    percentile(frames, .99, 1_000_000), percentile(transitions, .50, 1),
                    percentile(transitions, .95, 1), percentile(transitions, .99, 1)));
            if (sampled) code = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", "Benchmark incomplete: " + error + "\n");
        } finally {
            if (owner != null) runOnMainSync(() -> {
                owner.getWindow().removeOnFrameMetricsAvailableListener(listener);
                owner.finish();
            });
            AppearanceChecks.restore(prefs, saved);
        }
        finish(code, result);
    }

    private void cycles(int repeats) {
        for (int i = 0; i < repeats; i++) {
            transition(owner::showApps);
            transition(() -> {
                owner.closeApps();
                offlineMap();
            });
        }
    }

    private void transition(Runnable action) {
        runOnMainSync(() -> {
            long start = SystemClock.elapsedRealtime();
            action.run();
            if (measuring) transitions.add(SystemClock.elapsedRealtime() - start);
        });
        waitForIdleSync();
        SystemClock.sleep(120);
    }

    private void offlineMap() {
        if (owner.driveDashboard == null || owner.driveDashboard.map == null) {
            throw new AssertionError("expected native map");
        }
        owner.driveDashboard.map.setUseDataConnection(false);
    }

    private void capture(String name, String command) throws IOException {
        File output = new File(owner.getFilesDir(), "fixpack-" + name);
        try (ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(command);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        }
    }

    private static double percentile(List<Long> values, double percentile, int scale) {
        if (values.isEmpty()) return Double.NaN;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(percentile * sorted.size()) - 1) / (double) scale;
    }
}
