package com.shazwan.idash3;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Ordered, optional Drive shortcuts. The permanent system actions live outside this list. */
final class SidebarApps {
    private static final String KEY = "drive_sidebar_apps";
    static final String[] NAMES = {"Peta", "Media", "Radio FM", "YouTube", "BitChord"};
    private static final int[] DEFAULT_MODES = {0, 1, 3, 2, 4};
    private final LauncherActivity owner;

    SidebarApps(LauncherActivity owner) {
        this.owner = owner;
    }

    static String panel(int mode) {
        return "panel:" + mode;
    }

    static int mode(String key) {
        for (int mode = 0; mode < NAMES.length; mode++) {
            if (panel(mode).equals(key)) return mode;
        }
        return -1;
    }

    LinkedHashMap<String, String> catalog() {
        LinkedHashMap<String, String> choices = new LinkedHashMap<>();
        for (int mode : DEFAULT_MODES) choices.put(panel(mode), NAMES[mode]);
        for (LauncherActivity.App app : owner.apps) choices.put(app.component, app.label);
        return choices;
    }

    List<String> selected(Map<String, String> catalog) {
        SharedPreferences prefs = owner.prefs;
        LinkedHashSet<String> order = new LinkedHashSet<>();
        try {
            String saved = prefs.getString(KEY, null);
            if (saved == null) {
                for (int mode : DEFAULT_MODES) order.add(panel(mode));
                String legacy = prefs.getString("dashboard_app", "");
                if (ComponentName.unflattenFromString(legacy) != null) order.add(legacy);
            } else {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String key = array.optString(i);
                    if (mode(key) >= 0 || ComponentName.unflattenFromString(key) != null) order.add(key);
                }
            }
        } catch (JSONException | ClassCastException invalid) {
            for (int mode : DEFAULT_MODES) order.add(panel(mode));
        }
        // An incomplete catalog must not erase selections during startup or a failed refresh.
        if (owner.loaded && order.retainAll(catalog.keySet())) save(new ArrayList<>(order));
        return new ArrayList<>(order);
    }

    void save(List<String> selected) {
        owner.prefs.edit().putString(KEY, new JSONArray(selected).toString()).apply();
    }

    AlertDialog edit(Runnable changed) {
        Map<String, String> choices = catalog();
        List<String> selected = selected(choices);
        ListView list = new ListView(owner);
        list.setItemsCanFocus(true);
        BaseAdapter adapter = new BaseAdapter() {
            private List<String> rows = orderedRows();
            private List<String> orderedRows() {
                LinkedHashSet<String> order = new LinkedHashSet<>();
                for (String key : selected) if (choices.containsKey(key)) order.add(key);
                order.addAll(choices.keySet());
                return new ArrayList<>(order);
            }
            @Override public void notifyDataSetChanged() {
                rows = orderedRows();
                super.notifyDataSetChanged();
            }
            @Override public int getCount() { return rows.size(); }
            @Override public String getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                String key = getItem(position);
                int index = selected.indexOf(key);
                LinearLayout row = owner.row();
                row.setPadding(owner.dp(12), 0, owner.dp(12), 0);
                CheckBox check = new CheckBox(owner);
                check.setText(choices.get(key));
                check.setMinHeight(owner.dp(48));
                check.setChecked(index >= 0);
                check.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) selected.add(key);
                    else selected.remove(key);
                    notifyDataSetChanged();
                });
                row.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
                if (index >= 0) {
                    row.addView(moveButton("↑", "Naikkan " + choices.get(key), index > 0,
                            () -> { Collections.swap(selected, index, index - 1); notifyDataSetChanged(); }));
                    row.addView(moveButton("↓", "Turunkan " + choices.get(key), index < selected.size() - 1,
                            () -> { Collections.swap(selected, index, index + 1); notifyDataSetChanged(); }));
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        return new AlertDialog.Builder(owner).setTitle("Urus Aplikasi Sidebar")
                .setView(list).setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    save(selected);
                    changed.run();
                }).show();
    }

    private TextView moveButton(String arrow, String description, boolean enabled, Runnable action) {
        TextView button = owner.button(arrow, action);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .35f);
        button.setLayoutParams(new LinearLayout.LayoutParams(owner.dp(48), owner.dp(48)));
        return button;
    }
}
