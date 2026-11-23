package com.shazwan.idash3;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.HashMap;
import java.util.Map;

/** Theme gallery actions and manual Appearance isolation on the real launcher. */
public final class ThemeChecks extends Instrumentation {
    private LauncherActivity launcher;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        SharedPreferences prefs = getTargetContext().getSharedPreferences("idash", 0);
        Map<String, ?> original = new HashMap<>(prefs.getAll());
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            // Exercise an incompatible on-disk type, then retain a non-default manual configuration.
            prefs.edit().remove("appearance_theme_wallpaper_override").putInt(ThemeEngine.KEY, 42).putString("wallpaper", Wallpapers.id(2))
                    .putInt("glass_tint", 3).putInt("glass_reflection", 2).putInt("glass_style", 0)
                    .putBoolean("glass_customized", true).putBoolean("motion", false)
                    .putInt(GlassMaterial.KEY, GlassMaterial.CLASSIC).commit();
            launchAppearance();
            require(launcher.appearanceTheme == ThemeEngine.DEFAULT && !prefs.contains(ThemeEngine.KEY),
                    "wrong-type ID is recovered to Aurora");
            GlassAppearance manual = GlassAppearance.read(launcher);

            verifyWallpapers();
            openGallery();
            require(awaitNode("Next theme page").isEnabled(), "ten themes span two pages");
            require(!awaitNode("Previous theme page").isEnabled(), "already on the first page");
            Rect aurora = bounds("Aurora Glass, active theme");
            Rect midnight = bounds("Midnight AMOLED");
            Rect carbon = bounds("Carbon Sport");
            require(aurora.width() >= launcher.dp(48) && aurora.height() >= launcher.dp(48)
                    && aurora.right <= midnight.left && midnight.right <= carbon.left,
                    "three touch targets are fully laid out in one row");
            require(bounds("Theme Live Preview").height() > launcher.dp(28)
                    && bounds("Apply Theme").height() >= launcher.dp(48), "preview and actions fit");

            Rect ocean = bounds("Ocean Blue");
            require(carbon.right <= ocean.left, "five themes fit in the first row");
            click("Next theme page");
            Rect sunset = bounds("Sunset Drive"), champagne = bounds("Champagne Gold");
            require(sunset.right <= champagne.left && !awaitNode("Next theme page").isEnabled(),
                    "second row fits and ends pagination");
            click("Frost White");
            awaitNode("Live Preview · Frost White");
            click("Previous theme page");
            click("Midnight AMOLED");
            awaitNode("Live Preview · Midnight AMOLED");
            require(!prefs.contains(ThemeEngine.KEY) && launcher.appearanceTheme == ThemeEngine.DEFAULT,
                    "preview does not apply to the launcher");
            click("Cancel theme preview");
            openGallery();
            awaitNode("Live Preview · Aurora Glass");
            click("Midnight AMOLED");
            click("Apply Theme");
            require(ThemeEngine.MIDNIGHT.id.equals(prefs.getString(ThemeEngine.KEY, "")), "Apply stores Midnight");
            require(ThemeEngine.wallpaper(prefs, launcher.appearanceTheme)
                    .equals("theme:" + ThemeEngine.MIDNIGHT.id), "theme wallpaper is selected");
            require(manual.same(GlassAppearance.read(launcher))
                    && GlassMaterial.read(launcher) == GlassMaterial.CLASSIC, "manual effects and material survive Apply");

            runOnMainSync(() -> ThemeEngine.saveManualWallpaper(prefs, CustomWallpaper.PATH));
            require(ThemeEngine.wallpaper(prefs, launcher.appearanceTheme).equals(CustomWallpaper.PATH),
                    "manual wallpaper takes effect after Apply");
            relaunchAppearance();
            require(launcher.appearanceTheme == ThemeEngine.MIDNIGHT
                    && ThemeEngine.wallpaper(prefs, launcher.appearanceTheme).equals(CustomWallpaper.PATH),
                    "selected theme and manual wallpaper survive Activity restart");

            openGallery();
            awaitNode("Midnight AMOLED, active theme");
            click("Carbon Sport");
            click("Apply Theme");
            require(launcher.appearanceTheme == ThemeEngine.CARBON
                    && ThemeEngine.wallpaper(prefs, launcher.appearanceTheme).equals(ThemeEngine.CARBON.wallpaperId()),
                    "explicit Apply selects the original theme wallpaper");
            openGallery();
            awaitNode("Carbon Sport, active theme");
            click("Reset to Default");
            require(launcher.appearanceTheme == ThemeEngine.DEFAULT && !prefs.contains(ThemeEngine.KEY),
                    "theme reset restores Aurora");
            require(ThemeEngine.wallpaper(prefs, launcher.appearanceTheme).equals(ThemeEngine.DEFAULT.wallpaperId())
                    && manual.same(GlassAppearance.read(launcher)), "theme reset restores Aurora artwork and keeps manual effects");

            prefs.edit().putString(ThemeEngine.KEY, "unknown-theme").commit();
            relaunchAppearance();
            require(launcher.appearanceTheme == ThemeEngine.DEFAULT && !prefs.contains(ThemeEngine.KEY),
                    "unknown theme ID also recovers");
            result.putString("stream", "PASS: Theme gallery, layout, draft, Apply, restart, Reset and recovery");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (launcher != null) runOnMainSync(launcher::finish);
            AppearanceChecks.restore(prefs, original);
            finish(code, result);
        }
    }

    private void verifyWallpapers() throws java.io.IOException {
        java.util.Set<Integer> fingerprints = new java.util.HashSet<>();
        for (ThemeEngine.Theme theme : ThemeEngine.CATALOG) {
            android.graphics.Bitmap image = Wallpapers.decode(launcher.getAssets(), theme.wallpaperId(), 112);
            try {
                int width = image.getWidth(), height = image.getHeight();
                require(Math.max(width, height) <= 112 && Math.abs(width / (float) height - 1639f / 960) < .04f,
                        "thumbnail fits bounds and preserves aspect");
                int[] pixels = new int[width * height];
                image.getPixels(pixels, 0, width, 0, 0, width, height);
                require(fingerprints.add(java.util.Arrays.hashCode(pixels)), "distinct artwork: " + theme.name);
            } finally {
                image.recycle();
            }
        }
        require(fingerprints.size() == 10, "all ten wallpapers rendered");
    }

    private void launchAppearance() {
        launcher = (LauncherActivity) startActivitySync(new Intent(getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(launcher::customize);
        waitForIdleSync();
    }

    private void relaunchAppearance() {
        runOnMainSync(launcher::finish);
        waitForIdleSync();
        launchAppearance();
    }

    private void openGallery() {
        runOnMainSync(() -> {
            View themes = findView(launcher.appearanceEditor.root, "Appearance Themes");
            require(themes != null && themes.performClick(), "Themes opens from Appearance");
        });
        awaitNode("Apply Theme");
    }

    private View findView(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findView(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Rect bounds(String description) {
        Rect rect = new Rect();
        awaitNode(description).getBoundsInScreen(rect);
        return rect;
    }

    private void click(String description) {
        require(awaitNode(description).performAction(AccessibilityNodeInfo.ACTION_CLICK), "clicked " + description);
        waitForIdleSync();
    }

    private AccessibilityNodeInfo awaitNode(String description) {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        do {
            AccessibilityNodeInfo node = findNode(getUiAutomation().getRootInActiveWindow(), description);
            if (node != null) return node;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing accessibility node " + description);
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String description) {
        if (node == null) return null;
        CharSequence current = node.getContentDescription();
        CharSequence text = node.getText();
        if (node.isVisibleToUser() && ((current != null && description.contentEquals(current))
                || (text != null && description.contentEquals(text)))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), description);
            if (found != null) return found;
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
