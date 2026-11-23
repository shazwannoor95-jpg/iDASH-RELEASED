package com.shazwan.idash3;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.view.*;import java.io.*;import java.nio.file.*;import java.util.*;
/** Disposable-device image import and real gallery/lifecycle checks. */
public class WallpaperChecks extends Instrumentation {
 int count;LauncherActivity a;
 void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 View find(View v,String name){if(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),name);if(result!=null)return result;}}return null;}
 void driveWallpaper(byte[] originalHome) throws Exception {
  File source = new File(a.getCacheDir(), "test-drive-wallpaper.png");
  Bitmap blue = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888);
  blue.eraseColor(0xff0af075);
  try (OutputStream file = new FileOutputStream(source)) {
   blue.compress(Bitmap.CompressFormat.PNG, 100, file);
  } finally {
   blue.recycle();
  }
  String homeSelection = a.prefs.getString("wallpaper", "");
  Intent picked = new Intent().setData(android.net.Uri.fromFile(source));
  try {
   runOnMainSync(() -> {
    a.customize();
    a.appearanceEditor.more();
    AlertDialog more = a.appearanceEditor.extra;
    more.getListView().performItemClick(null, 4, 4);
    check(a.wallpaperDialog != null && a.wallpaperDialog.isShowing(), "Wallpaper Drive opens from Appearance");
    a.wallpaperDialog.dismiss();
    a.prefs.edit().remove("drive_wallpaper_custom").apply();
    a.onActivityResult(LauncherActivity.DRIVE_WALLPAPER_REQUEST, Activity.RESULT_CANCELED, picked);
    check(!a.prefs.getBoolean("drive_wallpaper_custom", false), "cancel keeps Drive default");
    a.onActivityResult(LauncherActivity.DRIVE_WALLPAPER_REQUEST, Activity.RESULT_OK, picked);
   });
   awaitWorker();
   check(a.prefs.getBoolean("drive_wallpaper_custom", false), "Drive selection persisted");
   check(Arrays.equals(originalHome, Files.readAllBytes(CustomWallpaper.file(a).toPath())),
       "Drive import preserves the original Home image");
   check(homeSelection.equals(a.prefs.getString("wallpaper", "")), "Home selection preserved");
   runOnMainSync(() -> {a.page = "drive"; a.render();});
   awaitWorker();
   runOnMainSync(() -> check(greenScene(), "custom image reaches Drive shell"));
   Bitmap screenshot = getUiAutomation().takeScreenshot();
   check(screenshot != null, "custom Drive wallpaper screenshot");
   try (OutputStream output = new FileOutputStream(new File(a.getFilesDir(), "drive-custom-wallpaper.png"))) {
    screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
   } finally {
    screenshot.recycle();
   }
   runOnMainSync(() -> {
    a.goHome();
    a.page = "drive";
    a.render();
   });
   awaitWorker();
   runOnMainSync(() -> {
    check(greenScene(), "Drive wallpaper survives leaving and reopening Drive");
    a.driveWallpaperPicker();
    a.wallpaperDialog.getListView().performItemClick(null, 1, 1);
    check(!greenScene() && !a.prefs.getBoolean("drive_wallpaper_custom", false),
        "restore applies the original Drive scene");
   });
  } finally {
   source.delete();
  }
 }

 void awaitWorker() throws Exception {
  java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
  a.worker.execute(() -> a.main.post(done::countDown));
  check(done.await(45, java.util.concurrent.TimeUnit.SECONDS), "wallpaper worker completes");
  waitForIdleSync();
 }

 boolean greenScene() {
  Bitmap probe = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888);
  android.graphics.drawable.Drawable scene = a.shell.getBackground();
  Rect previous = scene.copyBounds();
  try {
   scene.setBounds(0, 0, 100, 60);
   scene.draw(new Canvas(probe));
   int color = probe.getPixel(50, 30);
   return Color.green(color) > 150 && Color.green(color) > Color.blue(color);
  } finally {
   scene.setBounds(previous);
   probe.recycle();
  }
 }
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  a=(LauncherActivity)startActivitySync(new Intent(getTargetContext(),LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  Bitmap image=Bitmap.createBitmap(1600,900,Bitmap.Config.ARGB_8888);image.eraseColor(0xffe33d18);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle();
  CustomWallpaper.save(a,new ByteArrayInputStream(bytes.toByteArray()));byte[] saved=Files.readAllBytes(CustomWallpaper.file(a).toPath());
  check(Arrays.equals(saved,bytes.toByteArray()),"user image copied without recompression");Bitmap decoded=CustomWallpaper.decode(CustomWallpaper.file(a),1280);check(Math.max(decoded.getWidth(),decoded.getHeight())<=1280,"custom decode bounded");check(decoded.getPixel(20,20)==0xffe33d18,"decoded image retains content");decoded.recycle();
  boolean rejected=false;try{CustomWallpaper.save(a,new ByteArrayInputStream(new byte[]{1,2,3}));}catch(IOException expected){rejected=true;}check(rejected,"corrupt image rejected");check(Arrays.equals(saved,Files.readAllBytes(CustomWallpaper.file(a).toPath())),"failed import preserves previous image");
  rejected=false;try{CustomWallpaper.save(a,new InputStream(){int remaining=CustomWallpaper.MAX_BYTES+1;public int read(){return remaining-->0?0:-1;}public int read(byte[] buffer,int offset,int length){if(remaining==0)return -1;int n=Math.min(remaining,length);Arrays.fill(buffer,offset,offset+n,(byte)0);remaining-=n;return n;}});}catch(IOException expected){rejected=true;}check(rejected,"oversized input rejected");check(Arrays.equals(saved,Files.readAllBytes(CustomWallpaper.file(a).toPath())),"oversized import preserves previous image");
  runOnMainSync(()->{a.selectWallpaper(CustomWallpaper.PATH);check(CustomWallpaper.PATH.equals(a.prefs.getString("wallpaper","")),"custom selection saved");});
  long end=SystemClock.elapsedRealtime()+20000;while((a.wallpaperBitmap==null||a.wallpaperBitmap.getPixel(20,20)!=0xffe33d18||!CustomWallpaper.PATH.equals(a.requestedWallpaper))&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);waitForIdleSync();
  runOnMainSync(()->{check(a.wallpaperBitmap!=null&&a.wallpaperBitmap.getPixel(20,20)==0xffe33d18,"custom wallpaper reaches actual backdrop");a.panelVisible=true;a.prefs.edit().putInt("panel_mode",0).apply();a.render();DashWebPlayer panel=a.webPanel;a.selectWallpaper(Wallpapers.DEFAULT);check(a.webPanel==panel,"wallpaper selection preserves existing panel view");a.wallpaperPicker();check(a.wallpaperDialog!=null&&a.wallpaperDialog.isShowing(),"wallpaper gallery opens");check(find(a.wallpaperDialog.getWindow().getDecorView(),"Wallpaper "+Wallpapers.name(Wallpapers.id(0)))!=null&&find(a.wallpaperDialog.getWindow().getDecorView(),"Wallpaper "+Wallpapers.name(Wallpapers.id(Wallpapers.count()-1)))!=null,"gallery contains first and last collection entries");a.wallpaperDialog.dismiss();a.panelVisible=false;a.prefs.edit().putInt("glass_style",0).apply();a.render();});
  waitForIdleSync();runOnMainSync(()->check(a.wallpaperDialog==null,"gallery resources dismissed after dismissal callback"));
  driveWallpaper(saved);
  result.putString("stream","PASS: "+count+" iDash wallpaper assertions\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){result.putString("stream","FAIL after "+count+": "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null)runOnMainSync(()->a.finish());}}
}
