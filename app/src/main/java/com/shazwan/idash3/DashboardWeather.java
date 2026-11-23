package com.shazwan.idash3;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in, foreground-only weather. No inferred device position or placeholder temperatures. */
final class DashboardWeather extends LinearLayout implements AutoCloseable {
 static final int LOCATION_REQUEST=75;
 static final long CACHE_FRESH=15*60_000L, CACHE_LIMIT=24*60*60_000L;
 final LauncherActivity a;
 final TextView place,temperature,condition,range,status,action,source;
 final WeatherGlyph glyph;
 final SharedPreferences cache;
 final ExecutorService worker=Executors.newFixedThreadPool(2);
 boolean resumed,closed,watching,listening,fetching;
 int generation;
 long lastAttempt,minute=-1;
 LocationManager locations;Location fix;
 Data data;String city="",notice="";
 Future<?> fetchJob,geoJob;AlertDialog optionsDialog;
 final Runnable poll=new Runnable(){public void run(){if(!watching)return;ensureLocation();refresh(false);render();a.main.postDelayed(this,60_000);}};
 final LocationListener listener=new LocationListener(){
  public void onLocationChanged(Location value){if(!watching||!permitted()||!usable(value))return;boolean moved=fix==null||fix.distanceTo(value)>2000;fix=new Location(value);if(moved){city="";resolveCity(value);}notice="";refresh(false);render();}
  public void onProviderEnabled(String provider){if(!watching)return;notice="Menunggu lokasi peranti…";render();}
  public void onProviderDisabled(String provider){if(!watching)return;notice="Semak GPS/lokasi Android";render();}
  public void onStatusChanged(String provider,int state,Bundle extras){}
 };
 DashboardWeather(LauncherActivity owner){
  super(owner);a=owner;cache=a.getSharedPreferences("dashboard_weather",Context.MODE_PRIVATE);setOrientation(VERTICAL);setContentDescription("Dashboard weather and location");setFocusable(true);setOnClickListener(v->options());
  place=label("Cuaca + lokasi",12);place.setTypeface(IosStyle.font(a,true));addView(place);
  LinearLayout top = a.row();
  top.setBaselineAligned(false);
  top.setMinimumHeight(a.dp(30));
  glyph = new WeatherGlyph(a);
  top.addView(glyph, new LayoutParams(a.dp(26), a.dp(26)));

  temperature = label("—°", 23);
  temperature.setTypeface(IosStyle.font(a, true));
  temperature.setSingleLine(true);
  LayoutParams temp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
  temp.leftMargin = a.dp(5);
  top.addView(temperature, temp);

  condition = label("Belum tersedia", 11);
  condition.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
  LayoutParams conditionBounds = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
  conditionBounds.leftMargin = a.dp(6);
  top.addView(condition, conditionBounds);
  addView(top);

  range = label("Min/maks — · Lembap —", 10);
  addView(range);
  status = label("Izinkan lokasi untuk cuaca", 10);
  addView(status);

  action = label("Gunakan lokasi", 11);
  action.setGravity(Gravity.CENTER);
  action.setMinHeight(a.dp(48));
  action.setFocusable(true);
  action.setBackground(a.glass(0x66394c77, 10));
  action.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
  action.setOnClickListener(v -> useLocation());
  LayoutParams button = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  button.topMargin = a.dp(7);
  addView(action, button);
  source=label("Open-Meteo · data model cuaca",9);source.setAlpha(.75f);source.setFocusable(true);source.setOnClickListener(v->a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/"))));LayoutParams attribution = new LayoutParams(-1, -2);
  attribution.topMargin = a.dp(4);
  addView(source, attribution);
  setOnLongClickListener(v->{options();return true;});
  try{String saved=cache.getString("payload",null);if(saved!=null)data=Data.parse(saved,cache.getLong("fetched",0),cache.getFloat("lat",Float.NaN),cache.getFloat("lon",Float.NaN));city=cache.getString("city","");}catch(Exception invalid){cache.edit().clear().apply();}
  render();
 }
 TextView label(String text, float size) {
  TextView label = a.text(text, size, Color.WHITE);
  label.setIncludeFontPadding(false);
  return label;
 }
 boolean enabled(){return a.prefs.getBoolean("dashboard_weather_location",false);}
 boolean permitted(){return a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 static boolean usable(Location loc){long age=SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos();return Double.isFinite(loc.getLatitude())&&Double.isFinite(loc.getLongitude())&&Math.abs(loc.getLatitude())<=90&&Math.abs(loc.getLongitude())<=180&&age>=0&&age<=2*60_000_000_000L&&loc.hasAccuracy()&&loc.getAccuracy()<=20_000;}
 void useLocation(){
  a.prefs.edit().putBoolean("dashboard_weather_location",true).apply();
  if(!permitted()){
   if(a.prefs.getBoolean("weather_permission_requested",false)&&!a.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)&&!a.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
    a.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())));return;
   }
   a.prefs.edit().putBoolean("weather_permission_requested",true).apply();a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);render();return;
  }
  sync();ensureLocation();refresh(true);render();
 }
 void permissionResult(){notice=permitted()?"Menunggu lokasi peranti…":"Akses lokasi belum dibenarkan";sync();render();}
 @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(a!=null)sync();}
 void resume(){if(closed)return;resumed=true;sync();render();}
 void pause(){resumed=false;sync();if(optionsDialog!=null){optionsDialog.dismiss();optionsDialog=null;}}
 void sync(){boolean wanted=resumed&&!closed&&isShown()&&enabled();if(wanted==watching)return;watching=wanted;if(wanted){ensureLocation();refresh(false);a.main.removeCallbacks(poll);a.main.postDelayed(poll,60_000);}else{generation++;a.main.removeCallbacks(poll);stopLocation();if(fetchJob!=null)fetchJob.cancel(true);if(geoJob!=null)geoJob.cancel(true);fetching=false;}}
 @android.annotation.SuppressLint("MissingPermission") void ensureLocation(){
  if(!watching||!permitted())return;if(locations==null)locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);if(locations==null){notice="Lokasi tidak tersedia";return;}
  if(listening)return;boolean started=false;Location best=null;
  for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER})try{
   if(locations.isProviderEnabled(provider)){locations.requestLocationUpdates(provider,60_000,0,listener,Looper.getMainLooper());started=true;Location last=locations.getLastKnownLocation(provider);if(last!=null&&usable(last)&&(best==null||last.getElapsedRealtimeNanos()>best.getElapsedRealtimeNanos()))best=last;}
  }catch(RuntimeException unavailable){}
  listening=started;if(best!=null)listener.onLocationChanged(best);else notice=started?"Menunggu lokasi peranti…":"Hidupkan GPS/lokasi Android";
 }
 void stopLocation(){if(locations!=null&&listening)try{locations.removeUpdates(listener);}catch(RuntimeException ignored){}listening=false;}
 void resolveCity(Location value){
  if(!Geocoder.isPresent()||(geoJob!=null&&!geoJob.isDone()))return;final int token=generation;final Location point=new Location(value);
  geoJob=worker.submit(()->{try{List<Address> results=new Geocoder(a.getApplicationContext(),Locale.getDefault()).getFromLocation(Math.rint(point.getLatitude()*100)/100,Math.rint(point.getLongitude()*100)/100,1);String label="";if(results!=null&&!results.isEmpty()){Address address=results.get(0);label=address.getLocality();if(label==null||label.isEmpty())label=address.getSubAdminArea();if(label==null)label="";}final String name=label;a.main.post(()->{if(closed||!watching||token!=generation||fix==null||fix.distanceTo(point)>2000)return;city=name;cache.edit().putString("city",city).apply();render();});}catch(Exception unavailable){}});
 }
 void refresh(boolean force){
  if(!watching||!permitted()||fetching)return;if(fix==null||!usable(fix)){notice="Menunggu lokasi terkini…";return;}
  long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();double lat=Math.rint(fix.getLatitude()*100)/100,lon=Math.rint(fix.getLongitude()*100)/100;
  if(!force&&data!=null&&data.fresh(now)&&distance(lat,lon,data.lat,data.lon)<10_000)return;
  if(lastAttempt>0&&elapsed-lastAttempt<60_000){if(force)notice="Cuba semula selepas seminit";return;}
  fetching=true;notice="";lastAttempt=elapsed;final int token=generation;render();
  fetchJob=worker.submit(()->{try{String payload=fetch(lat,lon);Data result=Data.parse(payload,System.currentTimeMillis(),lat,lon);a.main.post(()->{if(closed||!watching||token!=generation)return;data=result;fetching=false;notice="";cache.edit().putString("payload",payload).putLong("fetched",result.fetched).putFloat("lat",(float)lat).putFloat("lon",(float)lon).putString("city",city).apply();render();});}catch(Exception error){a.main.post(()->{if(closed||!watching||token!=generation)return;fetching=false;notice="Cuaca gagal dimuat · semak internet";render();});}});
 }
 static double distance(double lat,double lon,double otherLat,double otherLon){float[] result=new float[1];Location.distanceBetween(lat,lon,otherLat,otherLon,result);return result[0];}
 static String fetch(double lat,double lon)throws IOException{
  String url=String.format(Locale.US,"https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&temperature_unit=celsius&timezone=auto&timeformat=unixtime&forecast_days=1",lat,lon);
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setRequestProperty("User-Agent","iDash4-weather/0.8.2");connection.setInstanceFollowRedirects(false);
  try{if(connection.getResponseCode()!=200)throw new IOException("Weather HTTP "+connection.getResponseCode());try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");if(out.size()+count>64*1024)throw new IOException("Weather response too large");out.write(buffer,0,count);}return out.toString("UTF-8");}}finally{connection.disconnect();}
 }
 void tick(long now){if(now/60_000!=minute){minute=now/60_000;render();}}
 void render(){
  if(temperature==null)return;long now=System.currentTimeMillis();boolean valid=data!=null&&data.fetched>0&&now>=data.fetched&&now-data.fetched<=CACHE_LIMIT;
  if(valid){temperature.setText(Math.round(data.temp)+"°");condition.setText(description(data.code));glyph.code=data.code;glyph.day=data.day;range.setText((Double.isFinite(data.high)&&Double.isFinite(data.low)?"↑"+Math.round(data.high)+"° ↓"+Math.round(data.low)+"°":"Min/maks —")+" · Lembap "+(data.humidity>=0?data.humidity+"%":"—"));}
  else{temperature.setText("—°");condition.setText("Belum tersedia");range.setText("Min/maks — · Lembap —");glyph.code=-1;}
  glyph.invalidate();
  boolean near=fix!=null&&valid&&distance(fix.getLatitude(),fix.getLongitude(),data.lat,data.lon)<10_000;
  place.setText(valid&&fix!=null&&!near?"Cache lokasi terdahulu":fix!=null&&permitted()?(city.isEmpty()?String.format(Locale.US,"Lokasi peranti · %.2f, %.2f",fix.getLatitude(),fix.getLongitude()):city):valid?"Lokasi tersimpan":"Cuaca + lokasi");
  String age=valid?(near&&enabled()&&permitted()&&data.fresh(now)&&notice.isEmpty()&&!fetching?"Data ":"Cache ")+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(data.observed)):"";
  String state=fetching?"Mengemas kini…":!notice.isEmpty()?notice:!enabled()?"Ketik untuk guna lokasi":!permitted()?"Benarkan lokasi":!valid?"Menunggu lokasi…":"";
  status.setText(age+(age.isEmpty()||state.isEmpty()?"":" · ")+state);
  if(valid&&!near&&fix!=null)status.setText("Cache lokasi terdahulu · "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(data.observed)));
  action.setText(!enabled()?"Gunakan lokasi":!permitted()?"Benarkan lokasi":fetching?"Sedang dikemas kini…":"Kemas kini");action.setEnabled(!fetching);action.setAlpha(fetching?.6f:1f);
 }
 void options(){optionsDialog=new AlertDialog.Builder(a).setTitle("Cuaca + lokasi").setItems(new String[]{"Kemas kini lokasi/cuaca","Nyahaktif dan padam cache","Tetapan lokasi Android","Sumber: Open-Meteo"},(dialog,which)->{if(which==0)useLocation();else if(which==1){a.prefs.edit().putBoolean("dashboard_weather_location",false).apply();cache.edit().clear().apply();data=null;fix=null;city="";notice="";sync();render();}else if(which==2)a.openSettings(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);else a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")));}).setNegativeButton("Tutup",null).show();}
 public void close(){if(closed)return;pause();closed=true;sync();worker.shutdownNow();}
 static String description(int code){if(code==0)return "Cerah";if(code==1||code==2)return "Berawan sebahagian";if(code==3)return "Berawan";if(code==45||code==48)return "Berkabus";if(code>=51&&code<=57)return "Hujan renyai";if(code>=61&&code<=67||code>=80&&code<=82)return "Hujan";if(code>=71&&code<=77||code==85||code==86)return "Salji";if(code==95||code==96||code==99)return "Ribut petir";return "Keadaan tidak diketahui";}
 static final class Data{
  final double temp,low,high,lat,lon;final int code,humidity;final boolean day;final long observed,fetched;
  Data(double t,double low,double high,int code,int humidity,boolean day,long observed,long fetched,double lat,double lon){this.temp=t;this.low=low;this.high=high;this.code=code;this.humidity=humidity;this.day=day;this.observed=observed;this.fetched=fetched;this.lat=lat;this.lon=lon;}
  boolean fresh(long now){return now>=fetched&&now-fetched<CACHE_FRESH&&now>=observed&&now-observed<60*60_000;}
  static Data parse(String raw,long fetched,double lat,double lon)throws JSONException{JSONObject root=new JSONObject(raw),current=root.getJSONObject("current");if(!"°C".equals(root.getJSONObject("current_units").optString("temperature_2m")))throw new JSONException("Expected Celsius");double temp=current.optDouble("temperature_2m",Double.NaN);long observed=current.optLong("time",0)*1000;if(!Double.isFinite(temp)||temp< -100||temp>70||observed<=0||fetched<=0||observed>fetched+5*60_000||fetched-observed>CACHE_LIMIT||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("Invalid weather data");int humidity=current.optInt("relative_humidity_2m",-1);if(humidity<0||humidity>100)humidity=-1;JSONObject daily=root.optJSONObject("daily");JSONArray lows=daily==null?null:daily.optJSONArray("temperature_2m_min"),highs=daily==null?null:daily.optJSONArray("temperature_2m_max");return new Data(temp,lows==null?Double.NaN:lows.optDouble(0,Double.NaN),highs==null?Double.NaN:highs.optDouble(0,Double.NaN),current.optInt("weather_code",-1),humidity,current.optInt("is_day",1)==1,observed,fetched,lat,lon);}
 }
 static final class WeatherGlyph extends View{
  final Paint paint=new Paint(3);final Path cloud=new Path();int code=-1;boolean day=true;
  WeatherGlyph(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  protected void onDraw(Canvas canvas){int save=canvas.save();canvas.scale(getWidth()/40f,getHeight()/40f);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);paint.setColor(code==0?0xffffd76c:0xffbdf7ff);if(code==0&&day){canvas.drawCircle(20,20,8,paint);for(int i=0;i<8;i++){double angle=i*Math.PI/4;canvas.drawLine(20+(float)Math.cos(angle)*12,20+(float)Math.sin(angle)*12,20+(float)Math.cos(angle)*16,20+(float)Math.sin(angle)*16,paint);}}else if(code==0){cloud.reset();cloud.moveTo(27,7);cloud.cubicTo(5,4,5,34,28,32);cloud.cubicTo(15,31,10,17,27,7);canvas.drawPath(cloud,paint);}else{cloud.reset();cloud.moveTo(10,27);cloud.cubicTo(0,27,1,15,11,16);cloud.cubicTo(13,4,29,5,30,17);cloud.cubicTo(40,17,39,29,30,29);cloud.lineTo(10,29);canvas.drawPath(cloud,paint);if(code>=51){canvas.drawLine(13,33,11,37,paint);canvas.drawLine(24,33,22,37,paint);}if(code<0){paint.setStyle(Paint.Style.FILL);paint.setTextSize(13);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("?",21,24,paint);}}canvas.restoreToCount(save);}
 }
}
