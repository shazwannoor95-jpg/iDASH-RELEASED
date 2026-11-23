package com.shazwan.idash3;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
/** Landscape, draft-only editor. Preview owns two surfaces and a private debounced frost cache. */
final class AppearanceEditor {
 static final int FULL_HEIGHT_DP = 342;
 private LinearLayout header, tabs, footer;
 private ThemeGallery themes;
 private LauncherActivity.Wallpaper previewBase;
 final boolean panelAtOpen;final LauncherActivity a;final Handler handler=new Handler(Looper.getMainLooper());final GlassBackdrop cache=new GlassBackdrop();
 GlassAppearance saved,draft;GlassSurface surface,buttonSurface;LinearLayout root,form,sample;TextView hint,cacheLabel,applyButton;ControlGlyph icon;TextView sampleTitle,sampleText,sampleButton;WallpaperSample wallpaper;
 boolean dirty,closed,active=true,cacheReady,updating,committing,renderPending;int tab,generation,preparedCount;Bitmap source;AlertDialog extra;
 final java.util.ArrayList<GlassSurface> chromeSurfaces=new java.util.ArrayList<>();
 final Runnable cacheJob=this::prepareCache;final Runnable paintJob=()->{renderPending=false;render();};
 AppearanceEditor(LauncherActivity a){this.a=a;panelAtOpen=a.panelVisible;saved=GlassAppearance.read(a);draft=saved;}
 TextView text(String s,int size){TextView t=a.text(s,size,Color.WHITE);t.setIncludeFontPadding(false);t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
 android.graphics.drawable.Drawable chrome(int radius){GlassSurface glass=new GlassSurface(a.getResources().getDisplayMetrics().density,IosStyle.CC_BASE,radius,0,false,cache,draft.quality,draft.tone,true);glass.material(GlassMaterial.read(a)).themeTint(IosStyle.panelThemeTint(a));chromeSurfaces.add(glass);return glass.appearance(effective());}
 TextView button(String s,Runnable click){TextView t=text(s,11);t.setGravity(Gravity.CENTER);t.setPadding(a.dp(8),0,a.dp(8),0);t.setMinHeight(a.dp(36));t.setBackground(chrome(12));t.setContentDescription("Appearance "+s);t.setOnClickListener(v->click.run());return t;}
 void mount(LinearLayout parent){
  // Keep the header and footer reachable even when the window is shorter than the form.
  root = new LinearLayout(a) {
   @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int limit = a.dp(348);
    if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
     limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
    }
    setTargetHeights(a.dp(limit >= a.dp(FULL_HEIGHT_DP) ? 48 : 36));
    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.EXACTLY));
   }
  };
  root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
  root.setBackground(chrome(18));
  root.setContentDescription("Appearance editor");
  parent.addView(root, new LinearLayout.LayoutParams(-1, -1));
  header=a.row();TextView title=text("Appearance",18);title.setTypeface(IosStyle.font(a,true));header.addView(title,new LinearLayout.LayoutParams(0,a.dp(36),1));
  header.addView(button("Themes",this::openThemes),new LinearLayout.LayoutParams(a.dp(68),a.dp(36)));
  for(String label:new String[]{"More","Back"}){LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(a.dp(56),a.dp(36));hp.leftMargin=a.dp(4);header.addView(button(label,()->{if(label.equals("More"))more();else new DashSettings(a).open("");}),hp);}root.addView(header);
  hint=text("Saved appearance · changes preview only",10);root.addView(hint,new LinearLayout.LayoutParams(-1,a.dp(20)));
  LinearLayout middle=a.row();middle.setBaselineAligned(false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,0,1);mp.bottomMargin=a.dp(6);root.addView(middle,mp);
  LinearLayout preview=a.column();preview.addView(text("Live Preview",13),new LinearLayout.LayoutParams(-1,a.dp(22)));FrameLayout stage=new FrameLayout(a);stage.setClipChildren(true);android.graphics.drawable.GradientDrawable frame=new android.graphics.drawable.GradientDrawable();frame.setColor(0xff263a54);frame.setCornerRadius(a.dp(18));stage.setBackground(frame);stage.setClipToOutline(true);stage.setContentDescription("Live glass preview");previewBase=a.new Wallpaper(a);stage.addView(previewBase,new FrameLayout.LayoutParams(-1,-1));wallpaper=new WallpaperSample();stage.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout.LayoutParams card=new FrameLayout.LayoutParams(-1,-1);card.setMargins(a.dp(5),a.dp(4),a.dp(5),a.dp(4));
  sample=a.column();sample.setGravity(Gravity.CENTER_VERTICAL);sample.setPadding(a.dp(10),a.dp(2),a.dp(10),a.dp(2));sample.setContentDescription("Preview glass card");stage.addView(sample,card);
  LinearLayout name=a.row();icon=new ControlGlyph(a,9);name.addView(icon,new LinearLayout.LayoutParams(a.dp(24),a.dp(26)));sampleTitle=text("Liquid Glass",14);sampleTitle.setTypeface(IosStyle.font(a,true));name.addView(sampleTitle,new LinearLayout.LayoutParams(0,a.dp(26),1));sample.addView(name);
  sampleText=text("Readable. Calm. Yours.",10);sample.addView(sampleText,new LinearLayout.LayoutParams(-1,a.dp(20)));sampleButton=text("Example button",11);sampleButton.setGravity(Gravity.CENTER);sampleButton.setContentDescription("Preview example button");sampleButton.setOnClickListener(v->Toast.makeText(a,"Preview only",Toast.LENGTH_SHORT).show());sample.addView(sampleButton,new LinearLayout.LayoutParams(-1,a.dp(36)));
  preview.addView(stage,new LinearLayout.LayoutParams(-1,0,1));cacheLabel=text("BALANCED · cached wallpaper",10);preview.addView(cacheLabel,new LinearLayout.LayoutParams(-1,a.dp(20)));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-1,.40f);pp.rightMargin=a.dp(8);middle.addView(preview,pp);
  LinearLayout controls = a.column();
  controls.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6));
  controls.setBackground(chrome(16));

  tabs = a.row();
  tabs.setBaselineAligned(false);
  for (int n = 0; n < 2; n++) {
   final int selected = n;
   LinearLayout.LayoutParams tabButton = new LinearLayout.LayoutParams(0, a.dp(36), 1);
   if (n == 1) tabButton.leftMargin = a.dp(4);
   tabs.addView(button(n == 0 ? "Material" : "Effects", () -> selectTab(selected)), tabButton);
  }
  LinearLayout.LayoutParams tabBounds = new LinearLayout.LayoutParams(-1, -2);
  tabBounds.bottomMargin = a.dp(4);
  controls.addView(tabs, tabBounds);

  // The three rows split the viewport left after the header and safe padding.
  form = a.column();
  form.setContentDescription("Appearance controls");
  controls.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
  middle.addView(controls, new LinearLayout.LayoutParams(0, -1, .60f));
  footer=a.row();for(String label:new String[]{"Reset","Cancel","Apply"}){TextView b=button(label,()->{if(label.equals("Reset"))reset();else if(label.equals("Cancel"))cancel();else apply();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(36),1);lp.setMargins(a.dp(2),0,a.dp(2),0);footer.addView(b,lp);if(label.equals("Apply"))applyButton=b;}root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  surface=new GlassSurface(a.getResources().getDisplayMetrics().density,IosStyle.CC_BASE,18,0,false,cache,draft.quality,draft.tone,true);buttonSurface=new GlassSurface(a.getResources().getDisplayMetrics().density,IosStyle.CC_BASE,12,0,false,cache,draft.quality,draft.tone,true);surface.material(GlassMaterial.read(a)).themeTint(IosStyle.panelThemeTint(a));buttonSurface.material(GlassMaterial.read(a)).themeTint(a.appearanceTheme.buttonTint);sample.setBackground(surface);sampleButton.setBackground(buttonSurface);selectTab(0);wallpaperChanged();
 }
 private void setTargetHeights(int height) {
  for (LinearLayout group : new LinearLayout[]{header, tabs, footer}) {
   for (int i = 0; i < group.getChildCount(); i++) {
    setTargetHeight(group.getChildAt(i), height);
   }
  }
  setTargetHeight(sampleButton, height);
 }

 private void setTargetHeight(View target, int height) {
  ViewGroup.LayoutParams bounds = target.getLayoutParams();
  if (bounds.height == height) return;
  bounds.height = height;
  target.setLayoutParams(bounds);
 }

 void selectTab(int selected){tab=selected;form.removeAllViews();updating=true;if(tab==0){choice("Glass style",new String[]{"Adaptive Glass","Light Glass","Dark Glass"},draft.tone,0);transparency();choice("Glass tint",new String[]{"Adaptive","Neutral","Soft Blue","Soft Rose","Soft Mint"},draft.tint,3);}else{choice("Glass quality",new String[]{"LOW","BALANCED","HIGH"},draft.quality,2);choice("Reflection",new String[]{"Low","Normal","High"},draft.reflection,4);LinearLayout row=row("Animation");Switch motion=new Switch(a);motion.setTextSize(11);motion.setMinimumHeight(0);motion.setText(draft.motion?"On":"Off");motion.setTextColor(Color.WHITE);motion.setChecked(draft.motion);motion.setContentDescription("Appearance animation");row.addView(motion,new LinearLayout.LayoutParams(0,-1,1));motion.setOnCheckedChangeListener((v,on)->{if(!updating){motion.setText(on?"On":"Off");change(5,on?1:0);sample.animate().cancel();sample.setAlpha(1);if(draft.animationAllowed(a)){sample.setAlpha(.65f);sample.animate().alpha(1).setDuration(140).start();}}});}updating=false;render();}
 LinearLayout row(String label) {
  LinearLayout row = a.row();
  row.setBaselineAligned(false);
  TextView name = text(label, 11);
  // Measure the shared label column with the current font scale.
  int labelWidth = (int) Math.ceil(Math.max(name.getPaint().measureText("Transparency"),
      name.getPaint().measureText("Glass quality"))) + a.dp(12);
  row.addView(name, new LinearLayout.LayoutParams(labelWidth, -1));
  form.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
  return row;
 }
 void choice(String label,String[] choices,int value,int field){LinearLayout row=row(label);Spinner spinner=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,choices){TextView compact(View view,boolean dropdown){TextView v=(TextView)view;v.setTextColor(dropdown?Color.WHITE:chromeColor());v.setTextSize(dropdown?12:11);v.setIncludeFontPadding(false);v.setMinHeight(0);v.setMinimumHeight(0);v.setPadding(a.dp(6),0,a.dp(6),0);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);v.setMaxLines(1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dropdown?a.dp(36):ViewGroup.LayoutParams.WRAP_CONTENT));return v;}public View getView(int n,View convert,ViewGroup parent){return compact(super.getView(n,convert,parent),false);}public View getDropDownView(int n,View convert,ViewGroup parent){return compact(super.getDropDownView(n,convert,parent),true);}};spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff26324b));spinner.setAdapter(adapter);spinner.setSelection(value);spinner.setMinimumHeight(0);spinner.setContentDescription("Appearance "+label);row.addView(spinner,new LinearLayout.LayoutParams(0,-1,1));spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int index,long id){if(!updating&&index!=current(field))change(field,index);}public void onNothingSelected(AdapterView<?> p){}});}
 int current(int field){return field==0?draft.tone:field==2?draft.quality:field==3?draft.tint:draft.reflection;}
 void transparency(){LinearLayout row=row("Transparency");SeekBar slider=new SeekBar(a);slider.setMax(GlassAppearance.MAX_TRANSPARENCY-GlassAppearance.MIN_TRANSPARENCY);slider.setProgress(draft.transparency-10);slider.setContentDescription("Appearance transparency");row.addView(slider,new LinearLayout.LayoutParams(0,-1,1));TextView value=text(draft.transparency+"%",11);value.setGravity(Gravity.CENTER);row.addView(value,new LinearLayout.LayoutParams(a.dp(34),-1));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int n,boolean user){if(!updating){value.setText((n+10)+"%");change(1,n+10);}}});}
 void change(int field,int value){int before=draft.quality;draft=draft.change(field,value);dirty=!draft.same(saved);if(before!=draft.quality)requestCache();if(field==1){if(!renderPending){renderPending=true;handler.postDelayed(paintJob,32);}}else render();}
 void reset(){draft=GlassAppearance.defaults();dirty=true;selectTab(tab);requestCache();render();}
 void cancel(){close();a.settingsSection="";a.page="settings";a.render();}
 void apply(){if(closed||!dirty||!cacheReady)return;committing=true;draft.save(a);IosStyle.publishGlass(a,cache.frame);a.render();}
 void wallpaperChanged(){if(closed||!active||committing)return;previewBase.setVisibility(ThemeEngine.isThemeWallpaper(a.requestedWallpaper)?View.GONE:View.VISIBLE);source=a.wallpaperBitmap;wallpaper.source=source;wallpaper.invalidate();requestCache();render();}
 void requestCache(){handler.removeCallbacks(cacheJob);generation++;if(source==null){cache.publish(new GlassBackdrop.Frame(null,IosStyle.backdrop(a).frame.tint,draft.quality));cacheReady=true;return;}GlassBackdrop.Frame global=IosStyle.backdrop(a).frame;if(global.bitmap!=null&&global.quality==draft.quality){cache.publish(global);cacheReady=true;return;}if(cacheReady&&cache.frame.bitmap!=null&&cache.frame.quality==draft.quality&&wallpaper.preparedSource==source)return;cacheReady=false;cache.publish(new GlassBackdrop.Frame(null,global.tint,draft.quality));if(active)handler.postDelayed(cacheJob,250);}
 void prepareCache(){if(closed||!active)return;final int request=generation,q=draft.quality;final Bitmap image=source;a.worker.execute(()->{GlassBackdrop.Frame frame;try{frame=GlassBackdrop.prepare(image,q);}catch(OutOfMemoryError oom){handler.post(()->{if(!closed&&active&&request==generation){cacheReady=false;cacheLabel.setText("Preview cache unavailable");}});return;}handler.post(()->{if(closed||!active||a.destroyed||request!=generation){if(frame.bitmap!=null)frame.bitmap.recycle();return;}preparedCount++;wallpaper.preparedSource=image;cache.publish(frame);cacheReady=true;render();});});}
 GlassAppearance effective(){GlassAppearance e=Readability.apply(a,draft);return e.customized?e:null;}
 int chromeColor(){GlassAppearance e=Readability.apply(a,draft);return e.tone==IosStyle.LIGHT||(e.tone==IosStyle.AUTO&&Color.luminance(cache.frame.tint)>.35)?IosStyle.INK:Color.WHITE;}
 void inputContrast(View view,int color){android.content.res.ColorStateList ink=android.content.res.ColorStateList.valueOf(color);if(view instanceof SeekBar){SeekBar bar=(SeekBar)view;bar.setThumbTintList(ink);bar.setProgressTintList(ink);bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf((color&0xffffff)|0x99000000));}if(view instanceof Spinner){view.setBackgroundTintList(ink);View selected=((Spinner)view).getSelectedView();if(selected instanceof TextView)((TextView)selected).setTextColor(color);}if(view instanceof Switch){Switch toggle=(Switch)view;toggle.setTextColor(color);toggle.setThumbTintList(ink);toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff3388dd,(color&0xffffff)|0x99000000}));}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)inputContrast(group.getChildAt(i),color);}}
 void render(){if(closed||surface==null)return;if(source==null&&draft.customized&&cache.frame.tint!=IosStyle.gradientTint(a))cache.publish(new GlassBackdrop.Frame(null,IosStyle.gradientTint(a),draft.quality));GlassAppearance applied=effective();surface.appearance(applied);buttonSurface.appearance(applied);for(GlassSurface chrome:chromeSurfaces)chrome.appearance(applied);IosStyle.contrast(root,surface.foregroundColor()==IosStyle.INK);inputContrast(root,chromeColor());int color=surface.foregroundColor();sampleTitle.setTextColor(color);sampleText.setTextColor(color);sampleButton.setTextColor(buttonSurface.foregroundColor());icon.color=color;icon.invalidate();hint.setText(dirty?"Unsaved preview · Apply to save":"Saved appearance · changes preview only");cacheLabel.setText((draft.quality==0?"LOW":draft.quality==2?"HIGH":"BALANCED")+(cacheReady?" · contrast protected":" · preparing cache…"));applyButton.setEnabled(dirty&&cacheReady);applyButton.setAlpha(dirty&&cacheReady?1:.55f);}
 void pause(){active=false;generation++;handler.removeCallbacksAndMessages(null);renderPending=false;if(sample!=null){sample.animate().cancel();sample.setAlpha(1);}if(extra!=null){extra.dismiss();extra=null;}if(themes!=null){themes.close();themes=null;}}
 void resume(){if(closed)return;active=true;wallpaperChanged();}
 void close(){if(closed)return;closed=true;pause();if(sample!=null)sample.setBackground(null);if(sampleButton!=null)sampleButton.setBackground(null);surface=null;buttonSurface=null;chromeSurfaces.clear();cache.publish(new GlassBackdrop.Frame(null,0xff526783));source=null;if(wallpaper!=null){wallpaper.source=null;wallpaper.preparedSource=null;}if(a.appearanceEditor==this)a.appearanceEditor=null;}
 private void openThemes() {
  if(dirty){Toast.makeText(a,"Apply or Cancel your draft first",Toast.LENGTH_SHORT).show();return;}
  themes=new ThemeGallery(a,()->themes=null);
  themes.show();
 }
 void more(){extra=new AlertDialog.Builder(a).setTitle("Appearance options").setItems(new String[]{"Wallpaper gallery · applies now","Gradient palette · applies now","Legacy material & icons","Android display settings","Wallpaper Drive"},(d,i)->{if(dirty){Toast.makeText(a,"Apply or Cancel your draft first",Toast.LENGTH_SHORT).show();return;}if(i==0)a.wallpaperPicker();else if(i==1)extra=new AlertDialog.Builder(a).setTitle("Gradient palette").setItems(new String[]{"Blue","Rose"},(v,index)->{a.prefs.edit().putInt("theme",index).apply();a.theme=index;a.requestedWallpaper=null;a.selectWallpaper("");a.render();}).setNegativeButton("Cancel",null).show();else if(i==2){DashSettings legacy=new DashSettings(a);legacy.preview();extra=legacy.previewDialog;}else if(i==3)a.openSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS);else a.driveWallpaperPicker();}).setNegativeButton("Close",null).show();}
 final class WallpaperSample extends View {Bitmap source,preparedSource;final Paint p=new Paint(3);final Matrix matrix=new Matrix();final int[] at=new int[2];WallpaperSample(){super(a);setBackgroundColor(Color.TRANSPARENT);}protected void onDraw(Canvas c){if(source==null||source.isRecycled())return;getLocationOnScreen(at);android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();float scale=Math.max(metrics.widthPixels/(float)source.getWidth(),metrics.heightPixels/(float)source.getHeight());matrix.setScale(scale,scale);matrix.postTranslate((metrics.widthPixels-source.getWidth()*scale)/2-at[0],(metrics.heightPixels-source.getHeight()*scale)/2-at[1]);c.drawBitmap(source,matrix,p);}}
}
