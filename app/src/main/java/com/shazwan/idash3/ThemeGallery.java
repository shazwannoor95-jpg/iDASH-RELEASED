package com.shazwan.idash3;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

/** A five-slot gallery with a single live sample and no changes to global state until Apply. */
final class ThemeGallery {
    private final LauncherActivity a;
    private final Dialog dialog;
    private final Runnable onClose;
    private final ThemeEngine.Theme active;
    private final GlassBackdrop backdrop = new GlassBackdrop();
    private final LinearLayout root, tiles, panel, dock;
    private final TextView pageNumber, previous, next, previewTitle, previewButton, reset, applyButton;
    private final TextView clock;
    private final DockGlyph[] dockIcons = new DockGlyph[3];
    private final Map<ThemeEngine.Theme, Bitmap> thumbnails = new HashMap<>();
    private final Map<ThemeEngine.Theme, ImageView> thumbnailViews = new HashMap<>();
    private final ImageView image;
    private ThemeEngine.Theme selected;
    private int page, generation;
    private boolean closed;
    private Future<?> previewJob, thumbnailJob;

    ThemeGallery(LauncherActivity owner, Runnable onClose) {
        a = owner;
        this.onClose = onClose;
        active = selected = a.appearanceTheme;
        page = ThemeEngine.CATALOG.indexOf(active) / ThemeEngine.PAGE_SIZE;

        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> release());
        root = a.column();
        root.setContentDescription("Theme Gallery");
        root.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        root.setBackground(fill(0xff161b27, 0xff424c61, 20));

        LinearLayout header = a.row();
        TextView title = label("Themes", 19);
        title.setTypeface(IosStyle.font(a, true));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        previous = action("‹", "Previous theme page", () -> changePage(-1));
        next = action("›", "Next theme page", () -> changePage(1));
        pageNumber = label("", 12);
        pageNumber.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        header.addView(previous, new LinearLayout.LayoutParams(a.dp(48), -1));
        header.addView(pageNumber, new LinearLayout.LayoutParams(a.dp(56), -1));
        header.addView(next, new LinearLayout.LayoutParams(a.dp(48), -1));
        root.addView(header, new LinearLayout.LayoutParams(-1, a.dp(48)));

        tiles = a.row();
        tiles.setBaselineAligned(false);
        LinearLayout.LayoutParams tilesBounds = new LinearLayout.LayoutParams(-1, a.dp(58));
        tilesBounds.topMargin = a.dp(4);
        root.addView(tiles, tilesBounds);

        FrameLayout sample = new FrameLayout(a);
        sample.setClipToOutline(true);
        sample.setBackground(fill(0xff0a1020, 0, 14));
        image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sample.addView(image, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout preview = a.row();
        preview.setBaselineAligned(false);
        preview.setPadding(a.dp(8), a.dp(4), a.dp(8), a.dp(4));
        preview.setContentDescription("Theme Live Preview");
        panel = a.row();
        panel.setPadding(a.dp(8), a.dp(2), a.dp(8), a.dp(2));
        previewTitle = label("", 14);
        previewTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        previewTitle.setTypeface(IosStyle.font(a, true));
        previewTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout widget = a.column();
        widget.addView(previewTitle, new LinearLayout.LayoutParams(-1, 0, 1));
        clock = label("12:45", 24);
        clock.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        clock.setTypeface(IosStyle.display(a, false));
        widget.addView(clock, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(widget, new LinearLayout.LayoutParams(0, -1, 1));
        previewButton = label("Button", 12);
        panel.addView(previewButton, new LinearLayout.LayoutParams(a.dp(72), -1));
        preview.addView(panel, new LinearLayout.LayoutParams(0, -1, 1));

        dock = a.row();
        dock.setGravity(Gravity.CENTER);
        dock.setContentDescription("Theme dock sample");
        int[] glyphs = {DockGlyph.SETTINGS, DockGlyph.DRIVE, DockGlyph.APPS};
        for (int i = 0; i < dockIcons.length; i++) {
            DockGlyph icon = new DockGlyph(a, glyphs[i]);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dockIcons[i] = icon;
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(a.dp(24), a.dp(24));
            bounds.setMargins(a.dp(2), 0, a.dp(2), 0);
            dock.addView(icon, bounds);
        }
        LinearLayout.LayoutParams dockBounds = new LinearLayout.LayoutParams(a.dp(94), -1);
        dockBounds.leftMargin = a.dp(4);
        preview.addView(dock, dockBounds);
        sample.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams sampleBounds = new LinearLayout.LayoutParams(-1, 0, 1);
        sampleBounds.setMargins(a.dp(2), a.dp(6), a.dp(2), a.dp(6));
        root.addView(sample, sampleBounds);

        LinearLayout footer = a.row();
        reset = action("Reset to Default", "Reset to Default", () -> apply(ThemeEngine.DEFAULT));
        TextView cancel = action("Cancel", "Cancel theme preview", this::close);
        applyButton = action("Apply Theme", "Apply Theme", () -> apply(selected));
        footer.addView(reset, footerBounds());
        footer.addView(cancel, footerBounds());
        footer.addView(applyButton, footerBounds());
        root.addView(footer, new LinearLayout.LayoutParams(-1, a.dp(48)));

        dialog.setContentView(root);
        showPage();
    }

    void show() {
        dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());
        int width = a.stage.getWidth() > 0 ? a.stage.getWidth() : a.getResources().getDisplayMetrics().widthPixels;
        int height = a.stage.getHeight() > 0 ? a.stage.getHeight() : a.getResources().getDisplayMetrics().heightPixels;
        window.setLayout(Math.min(a.dp(740), width - a.dp(16)),
                Math.min(a.dp(322), height - a.dp(16)));
        updatePreview();
        loadThumbnails();
    }

    private void loadThumbnails() {
        thumbnailJob = a.worker.submit(() -> {
            for (ThemeEngine.Theme theme : ThemeEngine.CATALOG) {
                if (Thread.currentThread().isInterrupted()) return;
                Bitmap thumbnail;
                try {
                    thumbnail = a.decodeWallpaper(theme.wallpaperId(), 112);
                } catch (IOException | RuntimeException | OutOfMemoryError unavailable) {
                    continue;
                }
                a.main.post(() -> {
                    if (closed) {
                        thumbnail.recycle();
                        return;
                    }
                    thumbnails.put(theme, thumbnail);
                    ImageView target = thumbnailViews.get(theme);
                    if (target != null) target.setImageBitmap(thumbnail);
                });
            }
        });
    }

    private LinearLayout.LayoutParams footerBounds() {
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(0, -1, 1);
        bounds.setMargins(a.dp(2), 0, a.dp(2), 0);
        return bounds;
    }

    private TextView label(String value, int size) {
        TextView label = a.text(value, size, Color.WHITE);
        label.setTextColor(Color.WHITE);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        // Android requires a strictly increasing autosize range; 10sp labels already use its minimum.
        if (size > 10) {
            label.setAutoSizeTextTypeUniformWithConfiguration(10, size, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        return label;
    }

    private TextView action(String label, String description, Runnable click) {
        TextView button = label(label, 14);
        button.setContentDescription(description);
        button.setBackground(clickableFill(0xff283345, 0xff505d74, 12));
        button.setFocusable(true);
        button.setOnClickListener(ignored -> click.run());
        return button;
    }

    private GradientDrawable fill(int color, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(a.dp(radius));
        if (stroke != 0) background.setStroke(a.dp(1), stroke);
        return background;
    }

    private RippleDrawable clickableFill(int color, int stroke, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x40ffffff), fill(color, stroke, radius), null);
    }

    private void changePage(int delta) {
        page += delta;
        showPage();
    }

    private void showPage() {
        pageNumber.setText((page + 1) + " / " + ThemeEngine.pageCount());
        setEnabled(previous, page > 0);
        setEnabled(next, page + 1 < ThemeEngine.pageCount());
        setEnabled(reset, active != ThemeEngine.DEFAULT);
        tiles.removeAllViews();
        thumbnailViews.clear();
        int start = page * ThemeEngine.PAGE_SIZE;
        int end = Math.min(start + ThemeEngine.PAGE_SIZE, ThemeEngine.CATALOG.size());
        for (int i = start; i < end; i++) {
            ThemeEngine.Theme theme = ThemeEngine.CATALOG.get(i);
            LinearLayout tile = a.column();
            tile.setPadding(a.dp(4), a.dp(5), a.dp(4), a.dp(2));
            tile.setFocusable(true);
            tile.setSelected(theme == selected);
            tile.setContentDescription(theme.name + (theme == active ? ", active theme" : ""));
            tile.setBackground(clickableFill(0xff232b39, theme == selected ? theme.accent : 0xff414c62, 10));
            FrameLayout artwork = new FrameLayout(a);
            artwork.setBackground(fill(0xff161b27, 0, 4));
            artwork.setClipToOutline(true);
            ImageView thumbnail = new ImageView(a);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setImageBitmap(thumbnails.get(theme));
            artwork.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));
            thumbnailViews.put(theme, thumbnail);
            if (theme == active) {
                View indicator = new View(a);
                indicator.setBackground(fill(theme.accent, Color.WHITE, 5));
                FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(a.dp(9), a.dp(9),
                        Gravity.END | Gravity.CENTER_VERTICAL);
                badge.rightMargin = a.dp(4);
                artwork.addView(indicator, badge);
            }
            tile.addView(artwork, new LinearLayout.LayoutParams(-1, a.dp(20)));
            TextView name = label(theme.name, 13);
            tile.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));
            tile.setOnClickListener(v -> {
                if (selected == theme) return;
                selected = theme;
                for (int slot = 0; slot < tiles.getChildCount(); slot++) {
                    View option = tiles.getChildAt(slot);
                    ThemeEngine.Theme entry = ThemeEngine.CATALOG.get(start + slot);
                    option.setSelected(entry == selected);
                    option.setBackground(clickableFill(0xff232b39,
                            entry == selected ? entry.accent : 0xff414c62, 10));
                }
                updatePreview();
            });
            tiles.addView(tile, footerBounds());
        }
    }

    private void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : .5f);
    }

    private void updatePreview() {
        if (previewJob != null) previewJob.cancel(false);
        int request = ++generation;
        ThemeEngine.Theme theme = selected;

        String path = theme.wallpaperId();
        int quality = IosStyle.quality(a);
        int fallback = theme.panelTint;
        display(null, new GlassBackdrop.Frame(null, fallback, quality));
        panel.setBackground(IosStyle.glass(a, IosStyle.CC_BASE, 14, false, backdrop, theme.panelTint));
        previewButton.setBackground(IosStyle.glass(a, 0x39ffffff, 12, false, backdrop, theme.buttonTint));
        dock.setBackground(IosStyle.glass(a, 0x75e5eaff, 14, false, backdrop, theme.dockTint));
        for (DockGlyph icon : dockIcons) {
            icon.setBackground(new Squircle.Tile(theme.iconTop, theme.iconBottom,
                    a.getResources().getDisplayMetrics().density));
        }
        applyButton.setBackground(clickableFill(theme.buttonTint, theme.accent, 12));
        applyButton.setTextColor(Color.luminance(theme.buttonTint) > .18f ? IosStyle.INK : Color.WHITE);
        previewTitle.setText("Live Preview · " + theme.name);
        IosStyle.glassText.remove(previewTitle);
        IosStyle.glassText.remove(clock);
        clock.setTextColor(theme.text);
        IosStyle.glassText.remove(previewButton);
        previewTitle.setTextColor(theme.text);
        previewButton.setTextColor(theme.mutedText);

        previewJob = a.worker.submit(() -> {
            Bitmap sample;
            try {
                sample = a.decodeWallpaper(path, 384);
            } catch (IOException | RuntimeException | OutOfMemoryError unavailable) {
                return; // No substitute image is drawn when an asset cannot be loaded.
            }
            GlassBackdrop.Frame prepared;
            try {
                prepared = GlassBackdrop.prepare(sample, quality);
            } catch (OutOfMemoryError unavailable) {
                prepared = new GlassBackdrop.Frame(null, fallback, quality);
            }
            GlassBackdrop.Frame frame = prepared;
            a.main.post(() -> {
                if (closed || a.destroyed || request != generation) {
                    sample.recycle();
                    return;
                }
                display(sample, frame);
            });
        });
    }

    private void display(Bitmap sample, GlassBackdrop.Frame frame) {
        image.setImageBitmap(sample);
        backdrop.publish(frame);
    }

    private void apply(ThemeEngine.Theme theme) {
        close();
        a.applyTheme(theme);
    }

    void close() {
        release();
        dialog.dismiss();
    }

    private void release() {
        if (closed) return;
        closed = true;
        if (previewJob != null) previewJob.cancel(false);
        if (thumbnailJob != null) thumbnailJob.cancel(true);
        for (ImageView view : thumbnailViews.values()) view.setImageDrawable(null);
        thumbnailViews.clear();
        thumbnails.clear();
        panel.setBackground(null);
        previewButton.setBackground(null);
        dock.setBackground(null);
        display(null, new GlassBackdrop.Frame(null, 0));
        onClose.run();
    }
}
