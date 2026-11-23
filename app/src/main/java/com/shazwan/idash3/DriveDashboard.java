package com.shazwan.idash3;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import java.io.File;
import java.text.DateFormat;
import java.util.*;

/** Native map + callback media + timetable widgets. No split-screen or invented route/vehicle data. */
final class DriveDashboard extends LinearLayout implements AutoCloseable {
 static final int LOCATION_REQUEST=74;
 final LauncherActivity a;final DashboardWeather weather;
 private final LinearLayout cards, weatherCard, dateCard;
 final TextView prayer,prayerDetail,calendar,calendarDetail,locationStatus,credit,locationButton;
 final DashboardDateBadge dateBadge;final PrayerJourneyView journey;
 boolean musicExpanded;
 private final SidebarApps sidebarApps;
 private final LinearLayout rail, railApps;
 private final TextClock railClock;
 private final View addApp, appDrawer;
 final View resizeMusic;
 private int shortcutCapacity;
 final FrameLayout mapHost;DashWebPlayer web;FmPanelHost fm;ControlMedia largeMedia;android.app.AlertDialog chooser;int mode=-1;BitChordPanel bitChord;final View[] modeButtons=new View[5];
 MapView map;Marker marker;LocationManager locations;
 boolean active,closed,listening,follow;
 long minute=Long.MIN_VALUE;String zone="",loaded="";boolean loading;Object timetable;
 final LocationListener position=new LocationListener(){
  public void onLocationChanged(Location loc){if(!active||closed||map==null)return;if(SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos()>120000000000L){locationStatus.setText("Menunggu lokasi terkini…");return;}GeoPoint point=new GeoPoint(loc.getLatitude(),loc.getLongitude());if(!map.getOverlays().contains(marker))map.getOverlays().add(marker);marker.setPosition(point);marker.setTitle("Lokasi peranti");if(follow)map.getController().animateTo(point);map.invalidate();locationStatus.setText("Lokasi peranti · ±"+Math.round(loc.getAccuracy())+" m");}
  public void onProviderEnabled(String p){}public void onProviderDisabled(String p){if(active)locationStatus.setText("Sumber lokasi terhenti");}public void onStatusChanged(String p,int s,Bundle b){}
 };
 DriveDashboard(LauncherActivity owner){
  super(owner);a=owner;setOrientation(HORIZONTAL);setBaselineAligned(false);setContentDescription("Drive Dashboard");
  rail = a.column();
  rail.setGravity(Gravity.CENTER_HORIZONTAL);
  rail.setBackground(a.glass(0xff25324d,18));
  rail.setPadding(0, a.dp(4), 0, a.dp(4));
  addView(rail, new LayoutParams(a.dp(64), -1));
  railClock = new TextClock(a);
  String clockFormat = a.prefs.getBoolean("24h",true) ? "HH:mm" : "h:mm a";
  railClock.setFormat24Hour(clockFormat);
  railClock.setFormat12Hour(clockFormat);
  railClock.setTextColor(Color.WHITE);
  railClock.setTextSize(15);
  railClock.setSingleLine();
  railClock.setGravity(Gravity.CENTER);
  rail.addView(railClock, new LayoutParams(-1, -2));
  sidebarApps = new SidebarApps(a);
  LinearLayout middle = a.column();
  middle.setGravity(Gravity.CENTER_HORIZONTAL);
  rail.addView(middle, new LayoutParams(-1, 0, 1));
  railApps = a.column();
  railApps.setGravity(Gravity.CENTER_HORIZONTAL);
  middle.addView(railApps, new LayoutParams(-1, -2));
  addApp = icon("Add App", null, this::editSidebar);
  middle.addView(addApp, railBounds());
  appDrawer = icon("Apps", null, () -> a.showDriveApps(shortcutCapacity));
  appDrawer.setOnLongClickListener(v -> { editSidebar(); return true; });
  middle.addView(appDrawer, railBounds());
  rebuildRailApps();

  LinearLayout mapArea = a.column();
  LayoutParams mp = new LayoutParams(0, -1, .65f);
  mp.leftMargin = a.dp(8);
  addView(mapArea, mp);
  LinearLayout controls = a.row();
  controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
  controls.setContentDescription("Drive top controls");
  mapArea.addView(controls, new LayoutParams(-1, a.dp(46)));
  addTopControl(controls, "Back", a::handleDashboardBack);
  addTopControl(controls, "Home", a::goHome);
  addTopControl(controls, "Wallpaper", a::driveWallpaperPicker);
  resizeMusic = addTopControl(controls, "Resize BitChord", this::expandMusic);
  updateResizeControl();
  locationButton=button("Lokasi",this::enableLocation);
  locationStatus=text("Peta kawasan · bukan lokasi anda",12);locationStatus.setContentDescription("Dashboard location status");locationStatus.setBackgroundColor(0xcc172335);locationStatus.setPadding(a.dp(6),0,a.dp(6),0);
  mapHost=new FrameLayout(a);mapHost.setContentDescription("Native map area");mapArea.addView(mapHost,new LayoutParams(-1,0,1));
  credit=text("© OpenStreetMap contributors · Internet untuk kawasan baharu",10);credit.setSingleLine(false);mapArea.addView(credit,new LayoutParams(-1,-2));
  ScrollView scroll = new ScrollView(a);
  LayoutParams cardColumn = new LayoutParams(0, -1, .35f);
  cardColumn.leftMargin = a.dp(8);
  addView(scroll, 1, cardColumn);
  cards = a.column();
  cards.setGravity(Gravity.CENTER_HORIZONTAL);
  cards.setPadding(a.dp(4), a.dp(14), a.dp(4), a.dp(6));
  scroll.addView(cards, new ScrollView.LayoutParams(-1, -2));

  LinearLayout prayerCard = a.column();
  cards.addView(prayerCard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
  prayer = text("Waktu solat", 18);
  prayer.setTypeface(IosStyle.font(a, true));
  prayer.setSingleLine(false);
  prayerDetail = text("", 12);
  prayerDetail.setSingleLine(false);
  LinearLayout prayerRow = a.row();
  prayerRow.addView(new PrayerBadge(a), new LayoutParams(a.dp(36), a.dp(36)));
  LinearLayout prayerWords = a.column();
  prayerWords.setPadding(a.dp(8), 0, 0, 0);
  prayerWords.addView(prayer);
  prayerWords.addView(prayerDetail);
  prayerRow.addView(prayerWords, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
  prayerRow.setVisibility(GONE);
  prayerCard.addView(prayerRow);
  journey = new PrayerJourneyView(a, true);
  prayerCard.addView(journey, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
  prayerCard.setFocusable(true);
  prayerCard.setContentDescription("Dashboard prayer; tap for JAKIM zone and timetable");
  prayerCard.setOnClickListener(v -> a.jakimDialog());

  weatherCard = card(cards);
  weather = new DashboardWeather(a);
  weatherCard.addView(weather, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

  dateCard = card(cards);
  LinearLayout dateRow = a.row();
  dateRow.setBaselineAligned(false);
  dateBadge = new DashboardDateBadge(a);
  dateRow.addView(dateBadge, new LayoutParams(a.dp(36), a.dp(40)));
  LinearLayout dateWords = a.column();
  dateWords.setPadding(a.dp(8), 0, 0, 0);
  calendar = a.text("", 14, Color.WHITE);
  calendar.setTypeface(IosStyle.font(a, true));
  calendarDetail = a.text("", 11, Color.WHITE);
  dateWords.addView(calendar);
  dateWords.addView(calendarDetail);
  dateRow.addView(dateWords, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
  dateCard.addView(dateRow);
  dateCard.setContentDescription("Dashboard calendar");
  dateCard.setOnClickListener(v -> a.showCalendar());
  dateCard.setFocusable(true);
  setMode(GlassAppearance.integer(a.prefs,"dashboard_panel",0));a.loadJakim(false);tick(System.currentTimeMillis());if(a.resumed)resume();
 }
 @Override protected void onMeasure(int width,int height){
  fitRail(MeasureSpec.getSize(height));
  // The Drive shell has already applied any visible system bar insets.
  boolean shortViewport = MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED
      && MeasureSpec.getSize(height) < a.dp(420);
  int gap = a.dp(shortViewport ? 12 : 14);
  ((LayoutParams) weatherCard.getLayoutParams()).topMargin = gap;
  ((LayoutParams) dateCard.getLayoutParams()).topMargin = gap;
  int top = a.dp(shortViewport ? 6 : 14);
  if (cards.getPaddingTop() != top) {
   cards.setPadding(cards.getPaddingLeft(), top, cards.getPaddingRight(), cards.getPaddingBottom());
  }
  super.onMeasure(width,height);
 }
 private void fitRail(int height) {
  int inner = Math.max(0, height - rail.getPaddingTop() - rail.getPaddingBottom());
  railClock.measure(MeasureSpec.makeMeasureSpec(a.dp(64), MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
  int clockHeight = Math.min(inner, Math.max(a.dp(36), railClock.getMeasuredHeight()));
  railClock.getLayoutParams().height = clockHeight;
  int target = a.dp(48);
  int middle = inner - clockHeight;
  int drawerMinimum = Math.min(target, middle);
  shortcutCapacity = Math.max(0, (middle - drawerMinimum) / target);
  int visible = Math.min(shortcutCapacity, railApps.getChildCount());
  boolean showAdd = visible == railApps.getChildCount() && visible < shortcutCapacity;
  int slots = visible + 1 + (showAdd ? 1 : 0);
  int spare = Math.max(0, middle - slots * target);
  int gap = Math.min(a.dp(6), spare / (slots * 2));
  int button = Math.min(a.dp(54), (middle - gap * slots) / slots);
  int glyph = Math.min(a.dp(36), button * 2 / 3);
  for (int i = 0; i < railApps.getChildCount(); i++) {
   View item = railApps.getChildAt(i);
   item.setVisibility(i < visible ? VISIBLE : GONE);
   sizeRailItem(item, button, gap, glyph);
  }
  addApp.setVisibility(showAdd ? VISIBLE : GONE);
  sizeRailItem(addApp, button, gap, glyph);
  sizeRailItem(appDrawer, button, gap, glyph);
 }

 private void sizeRailItem(View item, int side, int gap, int glyph) {
  LayoutParams bounds = (LayoutParams) item.getLayoutParams();
  FrameLayout.LayoutParams art = (FrameLayout.LayoutParams)
      ((FrameLayout) item).getChildAt(0).getLayoutParams();
  if (bounds.width == side && bounds.topMargin == gap && art.width == glyph) return;
  bounds.width = bounds.height = side;
  bounds.topMargin = gap;
  art.width = art.height = glyph;
  ((FrameLayout) item).getChildAt(0).forceLayout();
  for (View view = item; view != this; view = (View) view.getParent()) {
   view.forceLayout();
  }
 }

 void rebuildRailApps() {
  if (closed) return;
  railApps.removeAllViews();
  Arrays.fill(modeButtons, null);
  Map<String, String> catalog = sidebarApps.catalog();
  String[] artwork = {"maps", "music", null, "youtube", null};
  for (String key : sidebarApps.selected(catalog)) {
   if (!catalog.containsKey(key)) continue;
   int index = SidebarApps.mode(key);
   if (index >= 0) {
    View button = icon(SidebarApps.NAMES[index], artwork[index], () -> selectPanel(index));
    button.setSelected(mode == index);
    modeButtons[index] = button;
    railApps.addView(button, railBounds());
   } else {
    for (LauncherActivity.App app : a.apps) {
     if (!app.component.equals(key)) continue;
     FrameLayout button = (FrameLayout) icon(app.label, null, () -> launchSidebarApp(app));
     if (app.icon != null) {
      ImageView artworkView = new ImageView(a);
      artworkView.setImageBitmap(app.icon);
      artworkView.setScaleType(ImageView.ScaleType.FIT_CENTER);
      artworkView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      button.removeAllViews();
      button.addView(artworkView, new FrameLayout.LayoutParams(a.dp(32), a.dp(32), Gravity.CENTER));
     }
     railApps.addView(button, railBounds());
     break;
    }
   }
  }
 }

 private void selectPanel(int index) {
  if (musicExpanded) expandMusic();
  if (web != null) web.exitFullscreen();
  setMode(index);
 }

 private void launchSidebarApp(LauncherActivity.App app) {
  if (musicExpanded) expandMusic();
  Toast.makeText(a, "App dibuka berasingan · tekan Back untuk kembali ke iDash", Toast.LENGTH_SHORT).show();
  a.launch(app);
 }

 private void editSidebar() {
  if (!a.loaded) {
   Toast.makeText(a, "Senarai app sedang dimuatkan", Toast.LENGTH_SHORT).show();
   return;
  }
  chooser = sidebarApps.edit(this::rebuildRailApps);
 }
 void captureViewport(){if(map!=null&&map.getWidth()>0&&map.getHeight()>0){org.osmdroid.api.IGeoPoint point=map.getMapCenter();DriveViewport value=DriveViewport.of(point.getLatitude(),point.getLongitude(),map.getZoomLevelDouble());if(value!=null)a.driveViewport=value;}}
 private View addTopControl(LinearLayout controls, String title, Runnable action) {
  FrameLayout button = new FrameLayout(a);
  button.setContentDescription("Dashboard " + title);
  button.setFocusable(true);
  button.setOnClickListener(v -> action.run());
  button.setBackground(new android.graphics.drawable.InsetDrawable(a.glass(0xff25324d, 8), a.dp(5)));
  RailGlyph glyph = new RailGlyph(a, title);
  glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  button.addView(glyph, new FrameLayout.LayoutParams(a.dp(22), a.dp(22), Gravity.CENTER));
  LayoutParams bounds = new LayoutParams(a.dp(46), a.dp(46));
  if (controls.getChildCount() > 0) bounds.leftMargin = a.dp(5);
  controls.addView(button, bounds);
  return button;
 }

 private void updateResizeControl() {
  resizeMusic.setVisibility(mode == 4 ? VISIBLE : INVISIBLE);
  resizeMusic.setContentDescription(musicExpanded ? "Pulihkan saiz BitChord" : "Besarkan BitChord");
  View glyph = ((FrameLayout) resizeMusic).getChildAt(0);
  glyph.setActivated(musicExpanded);
  glyph.invalidate();
 }

 void expandMusic() {
  if (bitChord == null) return;
  musicExpanded = !musicExpanded;
  getChildAt(1).setVisibility(musicExpanded ? GONE : VISIBLE);
  updateResizeControl();
 }
 boolean back(){if(musicExpanded){expandMusic();return true;}if(web!=null){if(web.exitFullscreen())return true;if(web.web!=null&&web.web.canGoBack()){web.web.goBack();return true;}}if(mode!=0){setMode(0);return true;}return false;}
 void disposeMain(){if(bitChord!=null){bitChord.close();bitChord=null;}captureViewport();stopLocation();if(web!=null){web.close();web=null;}if(fm!=null){fm.close();fm=null;}if(largeMedia!=null){largeMedia.pause();largeMedia=null;}if(map!=null){map.onPause();map.onDetach();map=null;}mapHost.removeAllViews();}
 void setMode(int requested){
  if(closed)return;int nextMode=Math.max(0,Math.min(4,requested));if(mode==nextMode&&(mode==0?map!=null:mode==1?largeMedia!=null:mode==2?fm!=null:mode==3?web!=null:bitChord!=null))return;if(musicExpanded&&nextMode!=4)expandMusic();disposeMain();mode=nextMode;updateResizeControl();for(int i=0;i<modeButtons.length;i++)if(modeButtons[i]!=null)modeButtons[i].setSelected(i==mode);mapHost.setContentDescription(mode==0?"Native map area":mode==1?"Dashboard audio":mode==2?"Dashboard FM":mode==3?"Dashboard YouTube":"Dashboard BitChord player");a.prefs.edit().putInt("dashboard_panel",mode).apply();locationButton.setVisibility(mode==0?VISIBLE:GONE);credit.setVisibility(mode==0?VISIBLE:GONE);
  if(mode==0){locationStatus.setText("Peta kawasan · bukan lokasi anda");createMap();FrameLayout.LayoutParams gps=new FrameLayout.LayoutParams(a.dp(72),a.dp(48),Gravity.TOP|Gravity.END);gps.setMargins(0,a.dp(8),a.dp(8),0);mapHost.addView(locationButton,gps);mapHost.addView(locationStatus,new FrameLayout.LayoutParams(-1,a.dp(28),Gravity.BOTTOM));if(active&&map!=null){map.onResume();if(a.prefs.getBoolean("dashboard_location",false))startLocation();}}
  else if(mode==4){bitChord=new BitChordPanel(a);mapHost.addView(bitChord,new FrameLayout.LayoutParams(-1,-1));if(active)bitChord.resume();}
  else if(mode==3){locationStatus.setText("YouTube web");web=new DashWebPlayer(a,640,360);mapHost.addView(web,new FrameLayout.LayoutParams(-1,-1));if(active)web.resume();}
  else if(mode==2){locationStatus.setText("Radio FM · antena head unit");fm=new FmPanelHost(a,a.getResources().getDisplayMetrics().density,()->{chooser=new android.app.AlertDialog.Builder(a).setTitle("FM report").setMessage(DashFmController.report(a)).setPositiveButton("Tutup",null).show();});mapHost.addView(fm,new FrameLayout.LayoutParams(-1,-1));if(active)fm.resume();}
  else {locationStatus.setText("Audio · media session");LinearLayout panel=a.column();panel.setGravity(Gravity.CENTER);mapHost.addView(panel,new FrameLayout.LayoutParams(-1,-1));largeMedia=new ControlMedia(a,null);largeMedia.title=text("Not Playing",24);largeMedia.artist=text("",16);largeMedia.status=text("",13);panel.addView(largeMedia.title);panel.addView(largeMedia.artist);panel.addView(largeMedia.status);LinearLayout row=a.row();largeMedia.transport=row;panel.addView(row,new LayoutParams(-1,a.dp(64)));final ControlMedia bridge=largeMedia;for(int action:new int[]{6,7,8}){LinearLayout hit=a.column();hit.setGravity(Gravity.CENTER);hit.setFocusable(true);hit.setBackground(buttonBackground());ControlGlyph glyph=new ControlGlyph(a,action);hit.addView(glyph,new LayoutParams(a.dp(28),a.dp(28)));hit.addView(text(action==6?"Previous":action==7?"Play":"Next",12));hit.setOnClickListener(v->bridge.action(action));row.addView(hit,new LayoutParams(0,-1,1));if(action==6)bridge.previous=hit;else if(action==7){bridge.play=hit;bridge.playGlyph=glyph;}else bridge.next=hit;}panel.addView(button("Buka app media",this::chooseApp),new LayoutParams(-1,a.dp(48)));panel.addView(button("Akses media",bridge::explain),new LayoutParams(-1,a.dp(48)));bridge.render();if(active)bridge.resume();}
  PageGlassSkin.refreshDriveMain(a,mapHost);
 }
 void createMap(){
  try{
   org.osmdroid.config.IConfigurationProvider config=org.osmdroid.config.Configuration.getInstance();config.setUserAgentValue(BuildConfig.APPLICATION_ID+"/"+BuildConfig.VERSION_NAME);config.setOsmdroidBasePath(new File(a.getFilesDir(),"maps"));config.setOsmdroidTileCache(new File(a.getCacheDir(),"map-tiles"));config.setTileFileSystemCacheMaxBytes(50L*1024*1024);config.setTileFileSystemCacheTrimBytes(40L*1024*1024);config.setCacheMapTileCount((short)32);
   map=new MapView(a);map.setTileSource(TileSourceFactory.MAPNIK);map.setMultiTouchControls(true);DriveViewport camera=a.driveViewport;map.getController().setZoom(camera==null?12.0:camera.zoom);map.getController().setCenter(new GeoPoint(camera==null?3.139:camera.latitude,camera==null?101.6869:camera.longitude));
   map.getOverlays().add(new CopyrightOverlay(a));marker=new Marker(map);marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
   map.setOnTouchListener((v,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)follow=false;return false;});mapHost.addView(map,new FrameLayout.LayoutParams(-1,-1));
  }catch(RuntimeException|LinkageError|OutOfMemoryError unavailable){if(map!=null){map.onDetach();map=null;}mapHost.addView(text("Peta tidak tersedia pada peranti ini",16));}
 }
 void stopLocation(){if(locations!=null)try{locations.removeUpdates(position);}catch(RuntimeException ignored){}listening=false;}
 TextView text(String value,int size){TextView v=a.text(value,size,Color.WHITE);v.setMaxLines(1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
 android.graphics.drawable.Drawable buttonBackground(){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();android.graphics.drawable.GradientDrawable focus=new android.graphics.drawable.GradientDrawable();focus.setColor(0xaa375f83);focus.setCornerRadius(a.dp(10));focus.setStroke(a.dp(2),Color.WHITE);states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},a.glass(0xff25324d,12));return states;}
 TextView button(String title,Runnable action){TextView v=text(title,14);v.setGravity(Gravity.CENTER);v.setContentDescription(title);v.setFocusable(true);v.setMinHeight(a.dp(48));v.setBackground(buttonBackground());v.setOnClickListener(w->action.run());return v;}
 LayoutParams railBounds(){LayoutParams lp=new LayoutParams(a.dp(48),a.dp(48));lp.gravity=Gravity.CENTER_HORIZONTAL;return lp;}
 android.graphics.drawable.Drawable railBackground(){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();android.graphics.drawable.GradientDrawable selected=new android.graphics.drawable.GradientDrawable();selected.setColor(0x663678af);selected.setCornerRadius(a.dp(10));selected.setStroke(a.dp(2),0xff7cecff);android.graphics.drawable.GradientDrawable focused=new android.graphics.drawable.GradientDrawable();focused.setColor(0x44375f83);focused.setCornerRadius(a.dp(10));focused.setStroke(a.dp(2),Color.WHITE);states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{android.R.attr.state_pressed},focused);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));return states;}
 View icon(String title,String name,Runnable action){FrameLayout v=new FrameLayout(a);v.setContentDescription("Dashboard "+title);v.setTooltipText(title);v.setFocusable(true);v.setBackground(railBackground());v.setOnClickListener(w->action.run());android.graphics.Bitmap bitmap=Ios26Icons.named(a,name);View image;if("BitChord".equals(title)||"Radio FM".equals(title)){ImageView i=new ImageView(a);i.setImageResource("BitChord".equals(title)?R.drawable.bitchord_official:R.drawable.ic_radio_neon);i.setScaleType(ImageView.ScaleType.FIT_CENTER);image=i;}else if(bitmap!=null){ImageView i=new ImageView(a);i.setImageBitmap(bitmap);i.setScaleType(ImageView.ScaleType.FIT_CENTER);image=i;}else image=new RailGlyph(a,title);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);v.addView(image,new FrameLayout.LayoutParams(a.dp(32),a.dp(32),Gravity.CENTER));return v;}
 LinearLayout card(LinearLayout parent) {
  LinearLayout card = a.column();
  card.setGravity(Gravity.CENTER_VERTICAL);
  card.setBackground(a.glass(0xff25324d,14));
  int inset = a.dp(12);
  card.setPadding(inset, inset, inset, inset);
  LayoutParams bounds = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  parent.addView(card, bounds);
  return card;
 }
 void chooseApp(){java.util.ArrayList<LauncherActivity.App> apps=new java.util.ArrayList<>(a.apps);if(apps.isEmpty())return;String[] names=new String[apps.size()];for(int i=0;i<names.length;i++)names[i]=apps.get(i).label;chooser=new android.app.AlertDialog.Builder(a).setTitle("Buka app media").setItems(names,(d,i)->a.launch(apps.get(i))).setNegativeButton("Tutup",null).show();}
 void tick(long now){if(closed)return;weather.tick(now);journey.tick(now);String selected=a.prefs.getString("jakim_zone","");if(minute==now/60000&&selected.equals(zone)&&timetable==a.jakimDays&&loaded.equals(a.loadedZone)&&loading==a.jakimLoading)return;minute=now/60000;zone=selected;timetable=a.jakimDays;loaded=a.loadedZone;loading=a.jakimLoading;calendar.setText(new java.text.SimpleDateFormat("EEEE",Locale.getDefault()).format(new Date(now)));calendarDetail.setText(new java.text.SimpleDateFormat("d MMMM yyyy",Locale.getDefault()).format(new Date(now)));dateBadge.date(now);if(zone.isEmpty()){prayer.setText("Pilih zon JAKIM");prayerDetail.setText("Countdown ikut zon pilihan anda");}else if(!zone.equals(a.loadedZone)||!a.jakimDays.containsKey(JakimCalendar.key(now))){prayer.setText(a.jakimLoading?"Memuat jadual…":"Jadual belum tersedia");prayerDetail.setText("JAKIM · "+zone+" · "+JakimCalendar.key(now));}else{JakimCalendar.NextPrayer next=JakimCalendar.nextPrayerTime(a.jakimDays,now);prayer.setText(next==null?"Tiada waktu seterusnya":next.countdown()+" to "+next.name);prayerDetail.setText("JAKIM · "+zone+(next==null?"":" · "+next.time));}}
 static final class DashboardDateBadge extends View {
  final android.graphics.Paint ink=new android.graphics.Paint(3);String day="",weekday="";
  DashboardDateBadge(android.content.Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  void date(long now){String d=new java.text.SimpleDateFormat("d",Locale.getDefault()).format(new Date(now)),w=new java.text.SimpleDateFormat("EEE",Locale.getDefault()).format(new Date(now));if(!day.equals(d)||!weekday.equals(w)){day=d;weekday=w;invalidate();}}
  protected void onDraw(android.graphics.Canvas c){float scale=Math.min(getWidth()/44f,getHeight()/48f);int save=c.save();c.translate((getWidth()-44*scale)/2,(getHeight()-48*scale)/2);c.scale(scale,scale);ink.setStyle(android.graphics.Paint.Style.FILL);ink.setColor(0xfff8f8fa);c.drawRoundRect(0,0,44,48,9,9,ink);ink.setTextAlign(android.graphics.Paint.Align.CENTER);ink.setColor(0xffd04457);ink.setTextSize(10);ink.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText(weekday,22,13,ink);ink.setColor(0xff172335);ink.setTextSize(25);ink.setTypeface(android.graphics.Typeface.DEFAULT);c.drawText(day,22,39,ink);c.restoreToCount(save);}
 }
 static final class PrayerBadge extends View {
  final android.graphics.Paint ink=new android.graphics.Paint(3);final android.graphics.Path crescent=new android.graphics.Path();
  PrayerBadge(android.content.Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);crescent.moveTo(18,3);crescent.cubicTo(5,1,1,19,13,23);crescent.cubicTo(17,24,21,22,23,19);crescent.cubicTo(11,23,8,8,18,3);crescent.close();}
  protected void onDraw(android.graphics.Canvas c){int save=c.save();float scale=Math.min(getWidth(),getHeight())/28f;c.translate((getWidth()-28*scale)/2,(getHeight()-28*scale)/2);c.scale(scale,scale);ink.setStyle(android.graphics.Paint.Style.STROKE);ink.setColor(Color.WHITE);ink.setStrokeWidth(1.5f);c.drawPath(crescent,ink);c.drawLine(23,3,23,11,ink);c.drawLine(19,7,27,7,ink);c.restoreToCount(save);}
 }
 static final class RailGlyph extends View {
  final String type;final android.graphics.Paint ink=new android.graphics.Paint(3);final android.graphics.Path path=new android.graphics.Path();RailGlyph(android.content.Context c,String type){super(c);this.type=type;}
  protected void onDraw(android.graphics.Canvas c){int save=c.save();c.scale(getWidth()/24f,getHeight()/24f);ink.setColor(Color.WHITE);ink.setStrokeWidth(1.6f);ink.setStyle(android.graphics.Paint.Style.STROKE);if("BitChord".equals(type)){ink.setStyle(android.graphics.Paint.Style.FILL);ink.setTextSize(19);ink.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText("B",5,19,ink);}else if("Radio FM".equals(type)){c.drawRoundRect(2,8,22,21,3,3,ink);c.drawLine(5,7,18,2,ink);c.drawCircle(8,14,3,ink);c.drawLine(14,12,19,12,ink);c.drawLine(14,16,19,16,ink);}else if("Add App".equals(type)){c.drawRoundRect(3,3,21,21,5,5,ink);c.drawLine(12,7,12,17,ink);c.drawLine(7,12,17,12,ink);}else if("Back".equals(type)){c.drawLine(14,5,7,12,ink);c.drawLine(7,12,14,19,ink);}else if("Peta".equals(type)){
   path.reset();path.moveTo(12,22);path.cubicTo(10,18,5,14,5,9);path.cubicTo(5,0,19,0,19,9);
   path.cubicTo(19,14,14,18,12,22);c.drawPath(path,ink);c.drawCircle(12,9,2.4f,ink);
  }else if("Media".equals(type)){
   c.drawLine(10,17,10,5,ink);c.drawLine(10,5,19,3,ink);c.drawLine(19,3,19,15,ink);
   c.drawOval(5,15,10,20,ink);c.drawOval(14,13,19,18,ink);
  }else if("Telefon".equals(type)){
   path.reset();path.moveTo(3,5);path.lineTo(8,3);path.lineTo(11,8);path.lineTo(8,11);
   path.quadTo(10,16,15,16);path.lineTo(17,13);path.lineTo(22,17);path.lineTo(20,21);
   path.cubicTo(12,22,3,13,3,5);c.drawPath(path,ink);
  }else if("YouTube".equals(type)){
   c.drawRoundRect(2,4,22,20,4,4,ink);path.reset();path.moveTo(10,8);path.lineTo(16,12);
   path.lineTo(10,16);path.close();c.drawPath(path,ink);
  }else if("Wallpaper".equals(type)){
   c.drawRoundRect(2.5f, 3.5f, 21.5f, 20.5f, 3, 3, ink);
   c.drawCircle(8, 8.5f, 1.4f, ink);
   path.reset();path.moveTo(3, 17);path.lineTo(8.5f, 12.5f);
   path.lineTo(12, 15.5f);path.lineTo(16, 10.5f);path.lineTo(21, 15.5f);
   c.drawPath(path, ink);
  }else if("Resize BitChord".equals(type)){
   resizeArrow(c);
   c.rotate(180, 12, 12);
   resizeArrow(c);
  }else if("Home".equals(type)){path.reset();path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.lineTo(21,22);path.lineTo(14,22);path.lineTo(14,15);path.lineTo(10,15);path.lineTo(10,22);path.lineTo(3,22);path.close();c.drawPath(path,ink);}else{ink.setStyle("Apps".equals(type)?android.graphics.Paint.Style.FILL:android.graphics.Paint.Style.STROKE);int step="Apps".equals(type)?7:11;int side="Apps".equals(type)?4:7;for(int y=2;y<22;y+=step)for(int x=2;x<22;x+=step)c.drawRoundRect(x,y,x+side,y+side,1,1,ink);}c.restoreToCount(save);}
  private void resizeArrow(android.graphics.Canvas c) {
   float tipX = isActivated() ? 14 : 21;
   float tipY = isActivated() ? 10 : 3;
   float arm = isActivated() ? 5 : -5;
   c.drawLine(tipX, tipY, tipX + arm, tipY - arm, ink);
   c.drawLine(tipX, tipY, tipX + arm, tipY, ink);
   c.drawLine(tipX, tipY, tipX, tipY - arm, ink);
  }

 }
 boolean permitted(){return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 void enableLocation(){if(map==null)return;if(!permitted()){a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);return;}a.prefs.edit().putBoolean("dashboard_location",true).apply();follow=true;startLocation();}
 @android.annotation.SuppressLint("MissingPermission") void startLocation(){if(!active||closed||listening||!permitted()||map==null)return;locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);if(locations==null)return;boolean started=false;for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER})try{if(locations.isProviderEnabled(provider)){locations.requestLocationUpdates(provider,5000,10,position);started=true;}}catch(RuntimeException unavailable){}listening=started;locationStatus.setText(started?"Menunggu lokasi peranti…":"Hidupkan lokasi dalam tetapan Android");}
 void resume(){if(closed||active)return;active=true;if(bitChord!=null)bitChord.resume();weather.resume();if(web!=null)web.resume();if(fm!=null)fm.resume();if(largeMedia!=null)largeMedia.resume();if(map!=null)map.onResume();if(a.prefs.getBoolean("dashboard_location",false))startLocation();tick(System.currentTimeMillis());}
 void permissionResult(){if(permitted()){a.prefs.edit().putBoolean("dashboard_location",true).apply();follow=true;startLocation();}else locationStatus.setText("Kebenaran lokasi tidak diberi");}
 void pause(){active=false;if(bitChord!=null)bitChord.pause();journey.stop();weather.pause();if(chooser!=null){chooser.dismiss();chooser=null;}stopLocation();if(web!=null)web.pause();if(fm!=null)fm.pause();if(largeMedia!=null)largeMedia.pause();if(map!=null)map.onPause();}
 public void close(){if(closed)return;pause();closed=true;weather.close();disposeMain();}
}
