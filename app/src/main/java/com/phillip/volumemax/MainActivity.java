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
    SeekBar boost=new SeekBar(this); boost.setMax(100); boost.setProgress(BoostService.gainMb/20); root.addView(boost,new LinearLayout.LayoutParams(-1,dp(48)));
    boost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar sb,int p,boolean u){ boostLbl.setText(p==0?"Boost: off":"Boost: +"+p+"%"); }
      public void onStartTrackingTouch(SeekBar sb){} public void onStopTrackingTouch(SeekBar sb){ setBoost(sb.getProgress()*20); } });
    root.addView(text("Boost works on most phones, but some (including some Motorola models) ignore boosts on the whole-phone output. If the app says it isn't supported, the boost can't work on your phone. Boost runs as a notification so it keeps working in other apps. Tap \"Stop boost\" in the notification to turn it off.",13,0xFF9E9E9E));
    setContentView(sv);
    if (Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
    if (!BoostService.apply()) status.setText("Boost isn't supported on this phone ("+BoostService.lastError+"). Volume buttons still work."); else if(!BoostService.running) BoostService.release();
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
  void setBoost(int mb){
    Intent i=new Intent(this,BoostService.class);
    if(mb==0){ startService(i.setAction("STOP")); status.setText("Boost off"); return; }
    startForegroundService(i.putExtra("gain",mb));
    new Handler(Looper.getMainLooper()).postDelayed(() -> status.setText(BoostService.lastError==null?"Boost on: +"+(mb/20)+"%":"Boost not supported on this phone: "+BoostService.lastError),500);
  }
}
