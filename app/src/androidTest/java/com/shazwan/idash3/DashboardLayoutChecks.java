package com.shazwan.idash3;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;
/** Layout/real-date/countdown checks. Does not claim live YouTube playback. */
public class DashboardLayoutChecks extends Instrumentation {
 LauncherActivity a;int n;android.content.SharedPreferences prefs;Map<String,?> before;
 void check(boolean yes,String why){if(!yes)throw new AssertionError(why);n++;}
 void ui(Runnable r){Throwable[] err={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){err[0]=t;}});waitForIdleSync();if(err[0]!=null)throw new AssertionError(err[0]);}
 View find(View v,String desc){if(desc.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View hit=find(((ViewGroup)v).getChildAt(i),desc);if(hit!=null)return hit;}return null;}
 boolean textExists(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText())&&v.getVisibility()==View.VISIBLE)return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(textExists(((ViewGroup)v).getChildAt(i),s))return true;return false;}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();int result=Activity.RESULT_CANCELED;try{
  prefs=getTargetContext().getSharedPreferences("idash",0);before=new HashMap<>(prefs.getAll());prefs.edit().remove("drive_sidebar_apps").putInt("dashboard_panel",3).putBoolean("dashboard_location",false).putString("jakim_zone","SGR02").apply();
  a=(LauncherActivity)startActivitySync(new Intent(getTargetContext(),LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2);a.worker.execute(()->a.main.post(ready::countDown));a.networkWorker.execute(()->a.main.post(ready::countDown));check(ready.await(45,java.util.concurrent.TimeUnit.SECONDS),"workers ready");waitForIdleSync();
  ui(()->{a.page="drive";a.render();});java.util.concurrent.CountDownLatch data=new java.util.concurrent.CountDownLatch(1);a.networkWorker.execute(()->a.main.post(data::countDown));check(data.await(45,java.util.concurrent.TimeUnit.SECONDS),"timetable ready");waitForIdleSync();
  ui(()->{DriveDashboard d=a.driveDashboard;ViewGroup rail=(ViewGroup)d.getChildAt(0);check(find(d,"Dashboard Mod")==null&&find(d,"Dashboard Telefon")==null,"chooser and phone removed");check(d.getChildAt(1) instanceof ScrollView&&d.prayer.getParent()!=null,"cards immediately after rail");check(!textExists(d,"Drive Dashboard")&&!textExists(d,"YouTube web"),"visible title rows removed");
   check(!(rail.getChildAt(1) instanceof ScrollView), "dock has no scrolling");
   check(find(rail,"Dashboard Back")==null && find(rail,"Dashboard Home")==null,"navigation is off the dock");
   ViewGroup toolbar=(ViewGroup)find(d,"Drive top controls");
   for (int[] size : new int[][]{{1024,600},{800,480},{800,400}}) {
    d.measure(View.MeasureSpec.makeMeasureSpec(a.dp(size[0]),View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(a.dp(size[1]),View.MeasureSpec.EXACTLY));
    d.layout(0,0,a.dp(size[0]),a.dp(size[1]));
    check(toolbar.getBottom() <= d.mapHost.getTop() && d.mapHost.getBottom()==d.getHeight(),
        "toolbar reserves its own space above content");
    String[] controls={"Back","Home","Wallpaper"};
    int right=0;
    for (int i=0;i<controls.length;i++) {
     View hit=toolbar.getChildAt(i);
     check(("Dashboard "+controls[i]).contentEquals(hit.getContentDescription()),"control order");
     check(hit.getWidth()==a.dp(46) && hit.getHeight()==a.dp(46) && hit.getLeft()>=right,"touch bounds and spacing");
     check(((ViewGroup)hit).getChildAt(0).getWidth()==a.dp(22),"visible icon button size");
     check(!textExists(hit,controls[i]),"no visible control caption");
     right=hit.getRight();
    }
    check(d.resizeMusic.getRight()==toolbar.getWidth() && d.resizeMusic.getVisibility()==View.INVISIBLE,"right-aligned controls outside BitChord");
    for (String name : new String[]{"Peta","Media","YouTube","Radio FM","BitChord","Add App","Apps"}) {
     View item=find(rail,"Dashboard "+name);
     int bottom=item.getBottom();
     for (View parent=(View)item.getParent(); parent!=rail; parent=(View)parent.getParent()) {
      bottom+=parent.getTop();
     }
     check(item.getWidth()>=a.dp(48) && item.getHeight()>=a.dp(48) && bottom<=rail.getHeight(),"dock target fits "+name);
    }
    check(d.getChildAt(1).getRight()<=d.getChildAt(2).getLeft(),"widgets left of main content");
   }
   find(toolbar,"Dashboard Wallpaper").performClick();
   check(a.wallpaperDialog!=null && a.wallpaperDialog.isShowing(),"toolbar opens existing wallpaper picker");
   a.wallpaperDialog.dismiss();
   check(d.weather.action.getHeight()>=a.dp(48),"weather action retains accessible touch target");
   String t=a.jakimDays.get("2026-11-03").times[0];try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setTimeZone(JakimCalendar.MY);d.tick(f.parse("2026-11-03 "+t).getTime()-20*60000L);check("20 m to Subuh".contentEquals(d.prayer.getText()),"CarPlay-style text uses real countdown");check(d.prayerDetail.getText().toString().contains(t),"actual prayer time retained in subtitle");}catch(java.text.ParseException e){throw new AssertionError(e);}
   Calendar c=Calendar.getInstance();c.set(2026,10,3,12,0,0);d.tick(c.getTimeInMillis());check("3".equals(d.dateBadge.day),"date badge uses real day");c.add(Calendar.DAY_OF_MONTH,1);d.tick(c.getTimeInMillis());check("4".equals(d.dateBadge.day),"date badge rolls to next day");d.tick(System.currentTimeMillis());
   find(d,"Dashboard Radio FM").performClick();check(d.mode==2&&d.fm!=null&&d.chooser==null,"radio rail opens FM directly");check(d.fm.getParent()==d.mapHost,"FM fills native main area");find(d,"Dashboard Media").performClick();check(d.mode==1&&d.largeMedia!=null,"media rail opens audio directly");find(d,"Dashboard Peta").performClick();check(d.mode==0&&d.map!=null,"map rail opens map directly");find(d,"Dashboard YouTube").performClick();check(d.mode==3&&d.web!=null,"YouTube replaces phone directly");
   find(d,"Dashboard Add App").performClick();
   check(d.chooser!=null && d.chooser.isShowing(),"Add App opens sidebar picker");
   d.chooser.dismiss();
  });
  android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();if(shot!=null){try(java.io.OutputStream file=new java.io.FileOutputStream(new java.io.File(a.getFilesDir(),"dashboard-layout.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}shot.recycle();}
  out.putString("stream","PASS: "+n+" dashboard layout assertions\n");result=Activity.RESULT_OK;
 }catch(Throwable t){out.putString("stream","FAIL after "+n+": "+t+"\n");}finally{if(a!=null)ui(a::finish);if(prefs!=null&&before!=null)AppearanceChecks.restore(prefs,before);}finish(result,out);}
}
