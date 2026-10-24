package com.phillip.volumemax;
import android.app.*; import android.content.*; import android.graphics.*; import android.graphics.drawable.GradientDrawable; import android.media.AudioManager; import android.os.*; import android.provider.Settings; import android.view.*; import android.widget.*;
public class MainActivity extends Activity {
  AudioManager am; LinearLayout root; TextView status, boostLbl;
  final int[] streams = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM, AudioManager.STREAM_VOICE_CALL};
  final String[] names = {"Media","Ring","Notifications","Alarm","Calls"};
  SeekBar[] bars = new SeekBar[5]; TextView[] lbls = new TextView[5];
  int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }
  TextView text(String s,int sp,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(0,dp(6),0,dp(6)); return t; }
  protected void onCreate(Bundle b){
    super.onCreate(b); am = getSystemService(AudioManager.class);
    getWindow().setStatusBarColor(0xFF0D0F14);
    ScrollView sv = new ScrollView(this); sv.setBackgroundColor(0xFF0D0F14);
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(24),dp(20),dp(40)); sv.addView(root);
    root.addView(text("Volume Max",30,Color.WHITE));
    TextView warn = text("⚠ Warning: very high volume can damage your hearing and your phone's speakers. Turn it down if audio distorts or crackles.",15,0xFFFFD54F);
    GradientDrawable wb=new GradientDrawable(); wb.setColor(0xFF3A2A00); wb.setCornerRadius(dp(12)); warn.setBackground(wb); warn.setPadding(dp(14),dp(12),dp(14),dp(12)); root.addView(warn);
    Button max = new Button(this); max.setText("MAX ALL"); max.setTextSize(32); max.setTextColor(Color.WHITE); max.setTypeface(Typeface.DEFAULT_BOLD);
    GradientDrawable mb=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFFF3D00,0xFF7B1FA2}); mb.setCornerRadius(dp(24)); max.setBackground(mb);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(130)); lp.setMargins(0,dp(18),0,dp(10)); root.addView(max,lp);
    status = text("",15,0xFF80CBC4); root.addView(status);
    max.setOnClickListener(v -> maxAll());
    for (int i=0;i<5;i++){ final int idx=i; lbls[i]=text("",18,Color.WHITE); root.addView(lbls[i]);
      SeekBar s=new SeekBar(this); s.setMax(am.getStreamMaxVolume(streams[i])); s.setMinimumHeight(dp(40)); bars[i]=s; root.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));
      s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        public void onProgressChanged(SeekBar sb,int p,boolean user){ if(user) setVol(idx,p); updateLbl(idx); }
        public void onStartTrackingTouch(SeekBar sb){} public void onStopTrackingTouch(SeekBar sb){} }); }
    root.addView(text("Boost (beyond normal max)",22,0xFFFF8A65));
    boostLbl = text("Boost: off",18,Color.WHITE); root.addView(boostLbl);
    SeekBar boost=new SeekBar(this); boost.setMax(100); boost.setProgress(BoostService.prefs(this).getInt("gain",0)/20); boostLbl.setText(boost.getProgress()==0?"Boost: off":"Boost: +"+boost.getProgress()+"%"); root.addView(boost,new LinearLayout.LayoutParams(-1,dp(48)));
    boost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar sb,int p,boolean u){ boostLbl.setText(p==0?"Boost: off":"Boost: +"+p+"%"); }
      public void onStartTrackingTouch(SeekBar sb){} public void onStopTrackingTouch(SeekBar sb){ BoostService.prefs(MainActivity.this).edit().putInt("gain",sb.getProgress()*20).apply(); sync(); } });
    root.addView(text("Bass",22,0xFFFF8A65));
    final TextView bassLbl = text("",18,Color.WHITE); root.addView(bassLbl);
    SeekBar bass=new SeekBar(this); bass.setMax(100); bass.setProgress(BoostService.prefs(this).getInt("bass",0)); root.addView(bass,new LinearLayout.LayoutParams(-1,dp(48)));
    bassLbl.setText("Bass: "+(bass.getProgress()==0?"off":bass.getProgress()+"%"));
    bass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar sb,int p,boolean u){ bassLbl.setText("Bass: "+(p==0?"off":p+"%")); }
      public void onStartTrackingTouch(SeekBar sb){} public void onStopTrackingTouch(SeekBar sb){ BoostService.prefs(MainActivity.this).edit().putInt("bass",sb.getProgress()).apply(); sync(); } });
    final Button warm = new Button(this); warm.setTextSize(22); warm.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-1,dp(80)); wl.setMargins(0,dp(10),0,dp(4)); root.addView(warm,wl);
    styleWarm(warm);
    warm.setOnClickListener(v -> { boolean on=!BoostService.prefs(this).getBoolean("warm",false); BoostService.prefs(this).edit().putBoolean("warm",on).apply(); styleWarm(warm); sync(); });
    root.addView(text("Warm adds a gentle lift to the lows (60-250 Hz), softens harsh highs (4-8 kHz) and uses a limiter so the bass stays round and doesn't boom or distort.",13,0xFF9E9E9E));
    root.addView(text("Boost and bass work on most phones, but some (including some Motorola models) ignore effects on the whole-phone output. If the app says one isn't supported, that effect can't work on your phone. They run as a notification so they keep working in other apps. Tap \"Stop all\" in the notification to turn everything off.",13,0xFF9E9E9E));
    setContentView(sv);
    if (Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
    if (BoostService.anyOn(this)) sync();
  }
  protected void onResume(){ super.onResume(); for(int i=0;i<5;i++){ bars[i].setProgress(am.getStreamVolume(streams[i])); updateLbl(i);} }
  void updateLbl(int i){ lbls[i].setText(names[i]+": "+am.getStreamVolume(streams[i])+" / "+am.getStreamMaxVolume(streams[i])); }
  boolean setVol(int i,int v){ try{ am.setStreamVolume(streams[i],v,0); return true;}catch(SecurityException e){ return false; } }
  void maxAll(){
    StringBuilder skipped=new StringBuilder();
    for(int i=0;i<5;i++){ if(!setVol(i,am.getStreamMaxVolume(streams[i]))) skipped.append(names[i]).append(", "); bars[i].setProgress(am.getStreamVolume(streams[i])); updateLbl(i); }
    if(skipped.length()==0){ status.setText("All volumes set to max ✔"); return; }
    status.setText("Maxed what I could. Do Not Disturb blocked: "+skipped.substring(0,skipped.length()-2)+". Tap here to allow access, or turn off Do Not Disturb.");
    status.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
  }
  void styleWarm(Button w){ boolean on=BoostService.prefs(this).getBoolean("warm",false);
    w.setText(on?"WARM: ON ✔":"WARM: OFF"); GradientDrawable g=new GradientDrawable(); g.setCornerRadius(dp(20)); g.setColor(on?0xFFE65100:0xFF2A2D35); w.setBackground(g); }
  void sync(){
    Intent i=new Intent(this,BoostService.class);
    if(!BoostService.anyOn(this)){ startService(i); status.setText("Boost, bass and warm are off"); return; }
    startForegroundService(i);
    new Handler(Looper.getMainLooper()).postDelayed(() -> { StringBuilder m=new StringBuilder();
      if(BoostService.errBoost!=null) m.append("Boost unsupported on this phone. ");
      if(BoostService.errBass!=null) m.append("Bass unsupported on this phone. ");
      if(BoostService.errWarm!=null) m.append("Warm unsupported on this phone. ");
      status.setText(m.length()==0?"Effects on ✔":m.toString().trim()); },700);
  }
}
