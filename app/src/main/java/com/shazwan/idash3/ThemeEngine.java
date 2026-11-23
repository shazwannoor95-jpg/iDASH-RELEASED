package com.shazwan.idash3;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Theme presets are a visual layer over the saved manual Appearance settings. */
final class ThemeEngine {
    static final String KEY = "appearance_theme";
    private static final String WALLPAPER_OVERRIDE = "appearance_theme_wallpaper_override";
    static final String WALLPAPER_PREFIX = "theme:";
    static final int PAGE_SIZE = 5;

    static final class Theme {
        final String id, name;
        final int primary, secondary, accent, text, mutedText, wallpaperText;
        final int panelTint, dockTint, buttonTint, iconTop, iconBottom;

        Theme(String id, String name, int primary, int secondary, int accent, int text,
              int mutedText, int panelTint, int dockTint, int buttonTint, int iconTop,
              int iconBottom, int wallpaperText) {
            this.id = id;
            this.name = name;
            this.primary = primary;
            this.secondary = secondary;
            this.accent = accent;
            this.text = text;
            this.mutedText = mutedText;
            this.panelTint = panelTint;
            this.dockTint = dockTint;
            this.buttonTint = buttonTint;
            this.iconTop = iconTop;
            this.iconBottom = iconBottom;
            this.wallpaperText = wallpaperText;
        }

        String wallpaperId() { return WALLPAPER_PREFIX + id; }
        String wallpaperAsset() { return "themes/" + id + ".png"; }
    }

    // Catalog order controls the gallery. Saved IDs stay stable across catalog changes.
    static final Theme DEFAULT = new Theme("aurora-glass", "Aurora Glass",
            0xff00c9f5, 0xff944beb, 0xffc694ff, 0xffffffff, 0xffd2e0ef,
            0xff283c54, 0xff4b5379, 0xff4b5379, 0xff7777f0, 0xff3542a5, 0xffffffff);
    static final Theme MIDNIGHT = new Theme("midnight-amoled", "Midnight AMOLED",
            0xff000000, 0xff5b6478, 0xffb9d8ff, 0xfff5f8ff, 0xffd0d9ea,
            0xff080a0f, 0xff050609, 0xff333e52, 0xff465772, 0xff171d29, 0xffffffff);
    static final Theme CARBON = new Theme("carbon-sport", "Carbon Sport",
            0xffd22e43, 0xff24262b, 0xffff9c9e, 0xfffff6f6, 0xffead3d6,
            0xff4a1922, 0xff291319, 0xff76262e, 0xff9d303b, 0xff340e17, 0xffffffff);
    private static final Theme CYBER = new Theme("cyber-neon", "Cyber Neon",
            0xff00e2d4, 0xffed45b7, 0xff68ffe9, 0xfff2fffd, 0xffc7f2ee,
            0xff153e4c, 0xff221533, 0xff282054, 0xff126b71, 0xff50205a, 0xffffffff);
    private static final Theme OCEAN = new Theme("ocean-blue", "Ocean Blue",
            0xff0879d1, 0xff20b7ba, 0xff8cfff1, 0xfff1fcff, 0xffd0eef3,
            0xff225f83, 0xff153b58, 0xff194c70, 0xff2274ad, 0xff10586d, 0xffffffff);
    private static final Theme SUNSET = new Theme("sunset-drive", "Sunset Drive",
            0xfff18b37, 0xffdc5592, 0xffffc094, 0xfffff6ef, 0xffffded7,
            0xff7e3c4c, 0xff4b2343, 0xff703451, 0xffb0563a, 0xff63304e, 0xffffffff);
    private static final Theme FROST = new Theme("frost-white", "Frost White",
            0xffd7e8f5, 0xff8b9cb7, 0xffc6e6ff, 0xfff9fcff, 0xffe1eff9,
            0xffd5e4f0, 0xffa0b3ca, 0xffc8d9eb, 0xff496787, 0xff314059, 0xff172238);
    private static final Theme EMERALD = new Theme("emerald-luxe", "Emerald Luxe",
            0xff087e62, 0xffc0a04e, 0xffe5c77e, 0xfff7fff5, 0xffddeddb,
            0xff174932, 0xff102d22, 0xff255435, 0xff257451, 0xff33462c, 0xffffffff);
    private static final Theme RETRO = new Theme("retro-synthwave", "Retro Synthwave",
            0xff8546e8, 0xffef519a, 0xffffa9d5, 0xfffff2fc, 0xffeed6f5,
            0xff5b2d79, 0xff39205a, 0xff5e2666, 0xff7340a7, 0xff641d68, 0xffffffff);
    private static final Theme CHAMPAGNE = new Theme("champagne-gold", "Champagne Gold",
            0xffc3a16b, 0xffe6d3af, 0xfff5dda8, 0xfffffbf4, 0xfff1e3cc,
            0xffb69a70, 0xff80684d, 0xffd6b98c, 0xff806341, 0xff584132, 0xff172238);
    static final List<Theme> CATALOG = Collections.unmodifiableList(Arrays.asList(
            DEFAULT, MIDNIGHT, CARBON, CYBER, OCEAN, SUNSET, FROST, EMERALD, RETRO, CHAMPAGNE));

    private ThemeEngine() {}

    static Theme find(String id) {
        for (Theme theme : CATALOG) {
            if (theme.id.equals(id)) return theme;
        }
        return DEFAULT;
    }

    static Theme restore(SharedPreferences prefs) {
        try {
            String id = prefs.getString(KEY, null);
            Theme theme = find(id);
            if (theme.id.equals(id)) return theme;
            if (id == null && !prefs.contains(KEY)) {
                return DEFAULT;
            }
        } catch (ClassCastException invalid) {
            // Old, corrupted or incompatible values recover to the original palette.
        }
        save(prefs, DEFAULT);
        return DEFAULT;
    }

    private static void save(SharedPreferences prefs, Theme theme) {
        setId(prefs.edit(), theme).apply();
    }

    static void apply(SharedPreferences prefs, Theme theme) {
        setId(prefs.edit(), theme).putString("wallpaper", Wallpapers.DEFAULT)
                .remove(WALLPAPER_OVERRIDE).apply();
    }

    private static SharedPreferences.Editor setId(SharedPreferences.Editor edit, Theme theme) {
        return theme == DEFAULT ? edit.remove(KEY) : edit.putString(KEY, theme.id);
    }

    static void saveManualWallpaper(SharedPreferences prefs, String path) {
        SharedPreferences.Editor edit = prefs.edit().putString("wallpaper", path);
        edit.putBoolean(WALLPAPER_OVERRIDE, true);
        edit.apply();
    }

    static void useThemeWallpaper(SharedPreferences prefs) {
        prefs.edit().putString("wallpaper", Wallpapers.DEFAULT).remove(WALLPAPER_OVERRIDE).apply();
    }

    static String wallpaper(SharedPreferences prefs, Theme theme) {
        try {
            String path = prefs.getString("wallpaper", Wallpapers.DEFAULT);
            // A custom image from an earlier build may predate the explicit override flag.
            if (path != null && (CustomWallpaper.PATH.equals(path)
                    || GlassAppearance.bool(prefs, WALLPAPER_OVERRIDE, false)
                    || (theme == DEFAULT && !Wallpapers.DEFAULT.equals(path)))) {
                return path;
            }
        } catch (ClassCastException invalid) {
            // A malformed wallpaper value falls back to the selected theme's artwork.
        }
        return theme.wallpaperId();
    }

    static boolean isThemeWallpaper(String path) {
        return path != null && path.startsWith(WALLPAPER_PREFIX);
    }

    static int pageCount() {
        return (CATALOG.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }
}
