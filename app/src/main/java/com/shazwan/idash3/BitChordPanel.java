/* SPDX-License-Identifier: GPL-3.0-only. Native host of the vendored BitChord Compose UI. */
package com.shazwan.idash3;
import android.view.View;
import android.widget.LinearLayout;

final class BitChordPanel extends LinearLayout implements AutoCloseable {
 final View content;
 boolean closed;

 BitChordPanel(LauncherActivity a) {
  super(a);
  setOrientation(VERTICAL);
  setBaselineAligned(false);
  setContentDescription("BitChord full native music UI");

  content = com.music.bitchord.BitChordEmbedded.create(a);
  addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
 }
 // Weighted Android layouts may make an unbounded provisional pass. Compose lists require finite bounds.
 @Override protected void onMeasure(int width,int height){if(MeasureSpec.getMode(width)==MeasureSpec.UNSPECIFIED||MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED){setMeasuredDimension(MeasureSpec.getSize(width),MeasureSpec.getSize(height));return;}super.onMeasure(width,height);}
 void resume(){} void pause(){}
 public void close(){closed=true;com.music.bitchord.BitChordEmbedded.dispose(content);removeAllViews();}
}
