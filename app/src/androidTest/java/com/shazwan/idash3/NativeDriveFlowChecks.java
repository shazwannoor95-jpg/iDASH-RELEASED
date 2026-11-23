package com.shazwan.idash3;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.osmdroid.util.GeoPoint;import java.util.*;
/** Real view identity, source selection, map camera and return-path regressions. */
public class NativeDriveFlowChecks extends Instrumentation {
 LauncherActivity a;int n;android.content.SharedPreferences prefs;Map<String,?> saved;
 void check(boolean b,String why){if(!b)throw new AssertionError(why);n++;}
 void ui(Runnable r){Throwable[] t={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){t[0]=e;}});waitForIdleSync();if(t[0]!=null)throw new AssertionError(t[0]);}
 View find(View v,String name){if(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View hit=find(((ViewGroup)v).getChildAt(i),name);if(hit!=null)return hit;}return null;}
 void selected(DriveDashboard d,int mode){int count=0;for(int i=0;i<4;i++){if(d.modeButtons[i].isSelected())count++;check(d.modeButtons[i].isSelected()==(i==mode),"correct selected rail source "+i);}check(count==1,"exactly one selected source");}
 void noNetwork(DriveDashboard d){if(d.map!=null)d.map.setUseDataConnection(false);}
 void checkDock(View view, android.graphics.Rect dock, int[] lastBottom) {
  if (!view.isShown()) return;
  check(!(view instanceof android.widget.ScrollView), "Drive dock has no scroll container");
  CharSequence label = view.getContentDescription();
  if (label != null && label.toString().startsWith("Dashboard ")) {
   android.graphics.Rect bounds = new android.graphics.Rect();
   check(view.isClickable() && view.getGlobalVisibleRect(bounds) &&
       bounds.width() == view.getWidth() && bounds.height() == view.getHeight() &&
       dock.contains(bounds), "complete clickable bounds for " + label);
   check(bounds.height() >= a.dp(48) && bounds.width() >= a.dp(48), "48dp target");
   check(bounds.top >= lastBottom[0], "dock buttons do not overlap");
   check(Math.abs(bounds.centerX() - dock.centerX()) <= 1, "buttons horizontally centered");
   lastBottom[0] = bounds.bottom;
  }
  if (view instanceof ViewGroup) {
   ViewGroup group = (ViewGroup) view;
   for (int i = 0; i < group.getChildCount(); i++) checkDock(group.getChildAt(i), dock, lastBottom);
  }
 }

 void checkAdaptiveDock() throws Exception {
  List<String> shortcuts = new ArrayList<>();
  ui(() -> {
   a.closeApps();
   noNetwork(a.driveDashboard);
   shortcuts.addAll(Arrays.asList(SidebarApps.panel(0), SidebarApps.panel(3),
       SidebarApps.panel(2), SidebarApps.panel(4), SidebarApps.panel(1)));
   for (LauncherActivity.App app : a.apps) {
    if (shortcuts.size() >= 12) break;
    shortcuts.add(app.component);
   }
   check(shortcuts.size() > 10, "installed app catalog provides overflow fixture");
  });
  int largeIcon = 0;
  for (int count : new int[]{3, 4, 5, 6, shortcuts.size(), 3, shortcuts.size()}) {
   ui(() -> {
    new SidebarApps(a).save(shortcuts.subList(0, count));
    a.driveDashboard.rebuildRailApps();
   });
   waitForIdleSync();
   final int previousSize = largeIcon;
   int[] size = {0};
   ui(() -> {
    DriveDashboard d = a.driveDashboard;
    View rail = d.getChildAt(0);
    android.graphics.Rect bounds = new android.graphics.Rect();
    check(rail.getGlobalVisibleRect(bounds), "dock visible");
    checkDock(rail, bounds, new int[]{bounds.top});
    check(find(rail, "Dashboard Home") == null && find(rail, "Dashboard Back") == null,
        "Back and Home no longer reserve rail space");
    check(find(rail, "Dashboard Apps").isShown(), "drawer always visible");
    ViewGroup mapButton = (ViewGroup) find(rail, "Dashboard Peta");
    size[0] = mapButton.getChildAt(0).getWidth();
    if (previousSize != 0) check(size[0] <= previousSize, "icons grow only as room permits");
    SidebarApps sidebar = new SidebarApps(a);
    check(sidebar.selected(sidebar.catalog()).equals(shortcuts.subList(0, count)),
        "layout preserves saved order and selections");
   });
   if (count == 3) largeIcon = size[0];
  }
  android.graphics.Bitmap shot = getUiAutomation().takeScreenshot();
  check(shot != null, "emulator screenshot");
  try (java.io.OutputStream file = new java.io.FileOutputStream(new java.io.File(a.getFilesDir(),
      "drive-dock-" + shot.getWidth() + "x" + shot.getHeight() + ".png"))) {
   shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
  } finally {
   shot.recycle();
  }
  ui(() -> {
   DriveDashboard d = a.driveDashboard;
   for (String name : new String[]{"Radio FM", "YouTube", "BitChord", "Peta"}) {
    View button = find(d, "Dashboard " + name);
    check(button.isShown() && button.performClick(), "dock opens " + name);
    noNetwork(d);
   }
   find(d, "Dashboard Apps").performClick();
   check(find(a.shell, "Lagi app") != null, "overflow is available inside App Drawer");
   find(a.shell, "Lagi app").performClick();
  });
  android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
  check(root != null && !root.findAccessibilityNodeInfosByText("Aplikasi lebihan Sidebar").isEmpty(),
      "overflow list opens");
  sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
  List<String> reordered = new ArrayList<>(shortcuts);
  reordered.remove(SidebarApps.panel(4));
  reordered.add(SidebarApps.panel(4));
  ui(() -> {
   find(a.shell, "Done").performClick();
   noNetwork(a.driveDashboard);
   new SidebarApps(a).save(reordered);
   a.driveDashboard.rebuildRailApps();
  });
  ui(() -> {
   check(!find(a.driveDashboard, "Dashboard BitChord").isShown(), "overflow leaves dock");
   find(a.driveDashboard, "Dashboard Apps").performClick();
   find(a.shell, "Lagi app").performClick();
  });
  android.view.accessibility.AccessibilityNodeInfo dialog = getUiAutomation().getRootInActiveWindow();
  List<android.view.accessibility.AccessibilityNodeInfo> matches =
      dialog.findAccessibilityNodeInfosByText("BitChord");
  check(!matches.isEmpty(), "built-in panel remains available in overflow");
  android.view.accessibility.AccessibilityNodeInfo item = matches.get(0);
  while (item != null && !item.isClickable()) item = item.getParent();
  check(item != null && item.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),
      "overflow shortcut can be tapped");
  ui(() -> check("drive".equals(a.page) && a.driveDashboard.mode == 4, "overflow opens native BitChord"));
  check(prefs.edit().commit(), "selection flushed to disk");
  ui(a::finish);
  a = (LauncherActivity) startActivitySync(new Intent(getTargetContext(), LauncherActivity.class)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
  a.worker.execute(() -> a.main.post(loaded::countDown));
  check(loaded.await(45, java.util.concurrent.TimeUnit.SECONDS), "restarted app catalog ready");
  ui(() -> {
   SidebarApps sidebar = new SidebarApps(a);
   check(sidebar.selected(sidebar.catalog()).equals(reordered),
       "priority and overflow selections survive restart");
  });
 }
 void checkTopBar(boolean music) {
  DriveDashboard d = a.driveDashboard;
  ViewGroup bar = (ViewGroup) find(d, "Drive top controls");
  check(bar != null && bar.isShown() && bar.getChildCount() == 4, "one row of four controls");
  android.graphics.Rect barBounds = new android.graphics.Rect();
  android.graphics.Rect panel = new android.graphics.Rect();
  check(bar.getGlobalVisibleRect(barBounds) && d.mapHost.getGlobalVisibleRect(panel)
      && barBounds.bottom <= panel.top, "toolbar clear of main panel");
  android.graphics.Rect safe = new android.graphics.Rect();
  check(d.getGlobalVisibleRect(safe) && safe.contains(barBounds)
      && barBounds.top == safe.top && barBounds.right == safe.right, "bar aligned to safe top-right");
  String[] titles = {"Dashboard Back", "Dashboard Home", "Dashboard Wallpaper"};
  int edge = barBounds.left;
  for (int i = 0; i < bar.getChildCount(); i++) {
   ViewGroup button = (ViewGroup) bar.getChildAt(i);
   check(button.isShown() == (i < 3 || music), "resize visibility follows BitChord");
   if (!button.isShown()) continue;
   check(i == 3 ? button.getContentDescription().toString().contains("BitChord")
       : titles[i].contentEquals(button.getContentDescription()), "accessible labels in the requested order");
   check(button.getChildCount() == 1 && button.getChildAt(0) instanceof DriveDashboard.RailGlyph,
       "icon only");
   android.graphics.Rect bounds = new android.graphics.Rect();
   check(button.getGlobalVisibleRect(bounds) && barBounds.contains(bounds)
       && bounds.left >= edge && bounds.width() >= a.dp(44) && bounds.height() >= a.dp(44),
       "touch target fully inside toolbar with no overlap");
   edge = bounds.right;
  }
 }

 void checkTopControls() throws Exception {
  ui(() -> {
   a.page = "drive";
   a.render();
   a.driveDashboard.setMode(0);
   noNetwork(a.driveDashboard);
  });
  ui(() -> {
   checkTopBar(false);
   find(a.driveDashboard, "Dashboard Wallpaper").performClick();
   check(a.wallpaperDialog != null && a.wallpaperDialog.isShowing(), "top Wallpaper opens existing picker");
   a.wallpaperDialog.dismiss();
   a.driveDashboard.setMode(4);
  });
  ui(() -> {
   checkTopBar(true);
   check(a.driveDashboard.bitChord.getChildCount() == 1, "old BitChord toolbar removed");
   a.driveDashboard.resizeMusic.performClick();
  });
  ui(() -> {
   check(a.driveDashboard.musicExpanded, "top Resize expands native BitChord");
   checkTopBar(true);
  });
  android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
  check(image != null, "expanded toolbar screenshot");
  try (java.io.OutputStream file = new java.io.FileOutputStream(new java.io.File(a.getFilesDir(),
      "drive-top-" + image.getWidth() + "x" + image.getHeight() + ".png"))) {
   image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
  } finally {
   image.recycle();
  }
  ui(() -> {
   DriveDashboard d = a.driveDashboard;
   d.resizeMusic.performClick();
   check(!d.musicExpanded && d.mode == 4, "resize icon restores cards");
   d.resizeMusic.performClick();
   find(d, "Dashboard Back").performClick();
   check(!d.musicExpanded && d.mode == 4, "Back first restores expanded BitChord");
   find(d, "Dashboard Back").performClick();
   check(d.mode == 0 && "drive".equals(a.page), "Back returns to Map");
   noNetwork(d);
   find(d, "Dashboard Home").performClick();
   check("home".equals(a.page), "top Home goes home");
  });
 }
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;try{
 prefs=getTargetContext().getSharedPreferences("idash",0);saved=new HashMap<>(prefs.getAll());prefs.edit().putBoolean("motion",false).putBoolean("dashboard_location",false).putInt("dashboard_panel",0).remove("drive_sidebar_apps").apply();a=(LauncherActivity)startActivitySync(new Intent(getTargetContext(),LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2);a.worker.execute(()->a.main.post(ready::countDown));a.networkWorker.execute(()->a.main.post(ready::countDown));check(ready.await(45,java.util.concurrent.TimeUnit.SECONDS),"data ready");
 ui(()->{a.page="drive";a.render();DriveDashboard d=a.driveDashboard;noNetwork(d);selected(d,0);Object map=d.map;find(d,"Dashboard Peta").performClick();check(d.map==map,"selected Map preserves live map object");d.map.getController().setZoom(14.0);d.map.getController().setCenter(new GeoPoint(3.2,101.7));});
 ui(()->{DriveDashboard d=a.driveDashboard;d.captureViewport();check(a.driveViewport!=null&&Math.abs(a.driveViewport.latitude-3.2)<.001,"camera records visible map position");d.setMode(1);selected(d,1);Object media=d.largeMedia;find(d,"Dashboard Media").performClick();check(d.largeMedia==media,"selected Media preserves session bridge");a.onBackPressed();check("drive".equals(a.page)&&d.mode==0,"Back from media returns map inside Drive");noNetwork(d);});
 ui(()->{DriveDashboard d=a.driveDashboard;check(Math.abs(d.map.getMapCenter().getLatitude()-3.2)<.001&&Math.abs(d.map.getZoomLevelDouble()-14)<.01,"return restores camera and zoom");
 d.setMode(3);selected(d,3);Object web=d.web;find(d,"Dashboard YouTube").performClick();check(d.web==web,"selected YouTube preserves browser panel");a.onBackPressed();check(d.mode==0,"Back from idle YouTube returns map");noNetwork(d);
 d.setMode(2);selected(d,2);Object fm=d.fm;find(d,"Dashboard Radio FM").performClick();check(d.fm==fm,"selected FM does not release/rebuild controller");
 find(d,"Dashboard Apps").performClick();check("apps".equals(a.page)&&"drive".equals(a.libraryReturnPage),"Apps remembers Drive origin");find(a.shell,"Done").performClick();check("drive".equals(a.page)&&a.driveDashboard.mode==2,"Done restores Drive and selected FM source");selected(a.driveDashboard,2);
 a.showApps();a.onBackPressed();check("drive".equals(a.page),"Back from Apps restores Drive");a.showApps();find(a.shell,"Apps").performClick();check("drive".equals(a.page),"Apps dock toggle restores origin");
 a.onBackPressed();check("drive".equals(a.page)&&a.driveDashboard.mode==0,"first Back returns map");noNetwork(a.driveDashboard);a.onBackPressed();check("home".equals(a.page),"Back from map returns Home");a.showApps();find(a.shell,"Done").performClick();check("home".equals(a.page),"Home-origin Apps still returns Home");
 a.page="drive";a.render();noNetwork(a.driveDashboard);
 SidebarApps sidebar=new SidebarApps(a);
 Map<String,String> catalog=sidebar.catalog();
 sidebar.save(Arrays.asList(SidebarApps.panel(4),"dev.missing.app/.Missing",SidebarApps.panel(0)));
 check(sidebar.selected(catalog).equals(Arrays.asList(SidebarApps.panel(4),SidebarApps.panel(0))),"saved sidebar order retained with removed components pruned");
 a.driveDashboard.rebuildRailApps();
 check(find(a.driveDashboard,"Dashboard BitChord")!=null&&find(a.driveDashboard,"Dashboard Radio FM")==null,"only selected shortcuts visible");
 sidebar.save(Collections.emptyList());
 a.driveDashboard.rebuildRailApps();
 check(find(a.driveDashboard,"Dashboard BitChord")==null&&find(a.driveDashboard,"Dashboard Home")!=null&&find(a.driveDashboard,"Dashboard Apps")!=null,"empty selection keeps Apps and Home");
 sidebar.save(Arrays.asList(SidebarApps.panel(0),SidebarApps.panel(1)));
 a.driveDashboard.rebuildRailApps();
 a.driveDashboard.setMode(1);a.showApps();Bundle state=new Bundle();a.onSaveInstanceState(state);check("drive".equals(state.getString("library_return"))&&state.getBoolean("drive_viewport"),"instance state preserves return origin and camera");
 });
 checkAdaptiveDock();
 checkTopControls();
 out.putString("stream","PASS: "+n+" native Drive flow assertions\n");code=Activity.RESULT_OK;
 }catch(Throwable t){out.putString("stream","FAIL after "+n+": "+t+"\n");}finally{if(a!=null)ui(a::finish);if(prefs!=null&&saved!=null)AppearanceChecks.restore(prefs,saved);}finish(code,out);}
}
