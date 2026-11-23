package com.shazwan.idash3;
import android.app.*;import android.graphics.Rect;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.location.Location;import org.json.*;import java.util.*;
/** Weather contract, opt-in/lifecycle and real layout checks. Test weather is never a live-weather claim. */
public class WeatherChecks extends Instrumentation {
 LauncherActivity a;int n;android.content.SharedPreferences prefs,cache;Map<String,?> saved,savedCache;
 void check(boolean yes,String why){if(!yes)throw new AssertionError(why);n++;}
 void ui(Runnable r){Throwable[] failure={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
 String fixture(long observed)throws Exception{return new JSONObject().put("current_units",new JSONObject().put("temperature_2m","°C")).put("current",new JSONObject().put("time",observed/1000).put("temperature_2m",29.5).put("relative_humidity_2m",84).put("weather_code",95).put("is_day",1)).put("daily",new JSONObject().put("temperature_2m_min",new JSONArray().put(24)).put("temperature_2m_max",new JSONArray().put(32))).toString();}
 void layoutDrive(DriveDashboard dashboard, int widthDp, int heightDp) {
  int width = a.dp(widthDp), height = a.dp(heightDp);
  dashboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
  dashboard.layout(0, 0, width, height);
 }
 void assertCardLayout(DriveDashboard dashboard, String size) {
  ScrollView viewport = (ScrollView) dashboard.getChildAt(1);
  ViewGroup cards = (ViewGroup) viewport.getChildAt(0);
  View prayerCard = cards.getChildAt(0);
  ViewGroup weatherCard = (ViewGroup) cards.getChildAt(1);
  ViewGroup dateCard = (ViewGroup) cards.getChildAt(2);
  int firstGap = weatherCard.getTop() - prayerCard.getBottom();
  int secondGap = dateCard.getTop() - weatherCard.getBottom();
  check(firstGap == secondGap && firstGap >= a.dp(12) && firstGap <= a.dp(16),
      "consistent card spacing at " + size);
  for (View card : new View[]{weatherCard, dateCard}) {
   check(card.getLeft() == prayerCard.getLeft() && card.getRight() == prayerCard.getRight(),
       "shared card grid at " + size);
  }
  check(prayerCard.getLeft() == cards.getWidth() - prayerCard.getRight(),
      "balanced horizontal card margins at " + size);
  PrayerJourneyView journey = dashboard.journey;
  check(journey.art != null && journey.getWidth() == prayerCard.getWidth()
          && Math.abs(journey.getHeight() - journey.getWidth()
              * (journey.art.crop.height() / journey.art.crop.width())) <= 1,
      "rounded JAKIM art fills the card proportionally at " + size);
  DashboardWeather weather = dashboard.weather;
  for (TextView label : new TextView[]{weather.place, weather.temperature, weather.condition,
      weather.range, weather.status, weather.action, weather.source}) {
   assertSafeText(weatherCard, label);
  }
  assertSafeText(dateCard, dashboard.calendar);
  assertSafeText(dateCard, dashboard.calendarDetail);
  check(weather.action.getHeight() >= a.dp(48), "location action retains its touch target");
  check(dateCard.getBottom() <= viewport.getHeight() && cards.getHeight() <= viewport.getHeight(),
      "all three cards fit without scrolling at " + size);
  View mapArea = (View) dashboard.mapHost.getParent();
  check(dashboard.getChildAt(0).getRight() <= viewport.getLeft()
          && viewport.getRight() <= mapArea.getLeft(),
      "cards do not overlap the sidebar or map at " + size);
 }
 void assertSafeText(ViewGroup card, TextView text) {
  Rect bounds = new Rect(0, 0, text.getWidth(), text.getHeight());
  card.offsetDescendantRectToMyCoords(text, bounds);
  int inset = a.dp(12);
  check(bounds.left >= inset && bounds.top >= inset && bounds.right <= card.getWidth() - inset
          && bounds.bottom <= card.getHeight() - inset,
      "label stays inside the glass safe area: " + text.getText());
  android.text.Layout layout = text.getLayout();
  check(layout != null && layout.getHeight() <= text.getHeight() - text.getCompoundPaddingTop()
          - text.getCompoundPaddingBottom()
          && layout.getLineEnd(layout.getLineCount() - 1) >= text.getText().length(),
      "text fits its measured bounds: " + text.getText());
  for (int line = 0; line < layout.getLineCount(); line++) {
   check(layout.getEllipsisCount(line) == 0, "no truncated card text: " + text.getText());
  }
 }
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();int result=Activity.RESULT_CANCELED;try{
  prefs=getTargetContext().getSharedPreferences("idash",0);cache=getTargetContext().getSharedPreferences("dashboard_weather",0);saved=new HashMap<>(prefs.getAll());savedCache=new HashMap<>(cache.getAll());
  prefs.edit().putBoolean("dashboard_weather_location",false).putBoolean("dashboard_location",false).putBoolean("motion",false).putInt("dashboard_panel",0).apply();cache.edit().clear().apply();
  long now=System.currentTimeMillis();String raw=fixture(now-300_000);DashboardWeather.Data data=DashboardWeather.Data.parse(raw,now,3.14,101.69);
  check(data.temp==29.5&&data.humidity==84,"parse real API temperature/humidity fields");check(data.low==24&&data.high==32,"parse daily min/max");check(data.code==95&&"Ribut petir".equals(DashboardWeather.description(data.code)),"WMO thunderstorm label");check("Keadaan tidak diketahui".equals(DashboardWeather.description(999)),"unknown weather is not guessed");check(data.fresh(now),"recent model observation is fresh");check(!data.fresh(now+16*60_000),"cache expires after fifteen minutes");check(!data.fresh(now-1000),"future fetched time is not fresh");
  try{JSONObject bad=new JSONObject(raw);bad.getJSONObject("current_units").put("temperature_2m","°F");DashboardWeather.Data.parse(bad.toString(),now,3.14,101.69);throw new AssertionError("accepted Fahrenheit");}catch(JSONException expected){n++;}
  try{JSONObject bad=new JSONObject(raw);bad.getJSONObject("current").put("temperature_2m",JSONObject.NULL);DashboardWeather.Data.parse(bad.toString(),now,3.14,101.69);throw new AssertionError("accepted null temperature");}catch(JSONException expected){n++;}
  try{DashboardWeather.Data.parse(fixture(now+20*60_000),now,3.14,101.69);throw new AssertionError("accepted future observation");}catch(JSONException expected){n++;}
  try{DashboardWeather.Data.parse(raw,now,Double.NaN,101.69);throw new AssertionError("accepted invalid coordinate");}catch(JSONException expected){n++;}
  Location fix=new Location("test fixture");fix.setLatitude(3.14);fix.setLongitude(101.69);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());fix.setAccuracy(1000);check(DashboardWeather.usable(fix),"fresh approximate fix accepted");fix.setAccuracy(50_000);check(!DashboardWeather.usable(fix),"very inaccurate fix rejected");fix.setAccuracy(1000);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()+60_000_000_000L);check(!DashboardWeather.usable(fix),"future fix rejected");
  ActivityMonitor monitor=addMonitor(LauncherActivity.class.getName(),null,false);getTargetContext().startActivity(new Intent(getTargetContext(),LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=(LauncherActivity)monitor.waitForActivityWithTimeout(45000);removeMonitor(monitor);check(a!=null,"launcher started");java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);a.worker.execute(()->a.main.post(ready::countDown));check(ready.await(45,java.util.concurrent.TimeUnit.SECONDS),"apps loaded");
  ui(()->{a.page="drive";a.render();DriveDashboard d=a.driveDashboard;if(d.map!=null)d.map.setUseDataConnection(false);DashboardWeather weather=d.weather;
   check(!weather.enabled()&&!weather.listening&&!weather.watching&&weather.lastAttempt==0,"no GPS or weather request before opt-in");check("—°".contentEquals(weather.temperature.getText()),"empty state contains no invented temperature");check("Gunakan lokasi".contentEquals(weather.action.getText()),"explicit location action visible");
   ViewGroup button=(ViewGroup)d.modeButtons[4];check(button.getChildAt(0) instanceof ImageView&&((ImageView)button.getChildAt(0)).getDrawable()!=null,"BitChord uses packaged official icon instead of letter B");
   for (int[] size : new int[][]{{1024,600},{1008,584},{800,480},{640,360}}) {
    layoutDrive(d, size[0], size[1]);
    assertCardLayout(d, size[0] + "x" + size[1]);
   }
   weather.data=data;weather.render();check("30°".contentEquals(weather.temperature.getText()),"fixture renders rounded Celsius");check(weather.status.getText().toString().startsWith("Cache "),"stored data without current fix is marked cache");check("Lokasi tersimpan".contentEquals(weather.place.getText()),"cached point is not claimed as device position");
   weather.data=new DashboardWeather.Data(29.5,24,32,95,84,true,now-26*60*60_000L,now-26*60*60_000L,3.14,101.69);weather.render();check("—°".contentEquals(weather.temperature.getText()),"day-old weather is not shown as current");weather.data=null;weather.render();
   weather.place.setText("Bandar Baru Sri Petaling, Wilayah Persekutuan Kuala Lumpur");
   weather.condition.setText("Hujan lebat dan ribut petir");
   d.calendarDetail.setText("22 November 2026 · Wilayah Persekutuan Kuala Lumpur");
   layoutDrive(d, 800, 480);
   assertCardLayout(d, "800x480 with wrapped labels");
   check(weather.place.getLineCount()>1, "long city name wraps instead of truncating");
   weather.render();
   d.minute=Long.MIN_VALUE;
   d.tick(System.currentTimeMillis());
   d.pause();check(!weather.resumed&&!weather.watching&&!weather.listening,"pause stops weather pipeline");d.resume();check(weather.resumed&&!weather.watching,"resume does not override opt-in");d.measure(View.MeasureSpec.makeMeasureSpec(a.dp(1024),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(a.dp(600),View.MeasureSpec.EXACTLY));d.layout(0,0,a.dp(1024),a.dp(600));
  });
  SystemClock.sleep(600);android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image!=null){try(java.io.OutputStream file=new java.io.FileOutputStream(new java.io.File(a.getFilesDir(),"weather-empty-dashboard.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}image.recycle();}
  ui(()->{DashboardWeather weather=a.driveDashboard.weather;a.page="home";a.render();check(weather.closed&&weather.worker.isShutdown(),"leaving dashboard releases weather worker");});
  out.putString("stream","PASS: "+n+" weather contract, lifecycle and layout assertions (fixtures; live provider not exercised)\n");result=Activity.RESULT_OK;
 }catch(Throwable t){out.putString("stream","FAIL after "+n+": "+t+"\n");}finally{if(a!=null)ui(a::finish);if(prefs!=null&&saved!=null)AppearanceChecks.restore(prefs,saved);if(cache!=null&&savedCache!=null)AppearanceChecks.restore(cache,savedCache);}finish(result,out);}
}
