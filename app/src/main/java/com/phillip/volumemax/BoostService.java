package com.phillip.volumemax;
import android.app.*; import android.content.*; import android.content.pm.ServiceInfo; import android.media.audiofx.*; import android.os.*;
public class BoostService extends Service {
  static LoudnessEnhancer le; static BassBoost bb; static DynamicsProcessing dp; static Equalizer eq;
  static String errBoost, errBass, errWarm; static boolean running = false;
  static SharedPreferences prefs(Context c){ return c.getSharedPreferences("vm", MODE_PRIVATE); }
  static boolean anyOn(Context c){ SharedPreferences p=prefs(c); return p.getInt("gain",0)>0||p.getInt("bass",0)>0||p.getBoolean("warm",false); }
  public IBinder onBind(Intent i){ return null; }
  public int onStartCommand(Intent intent, int flags, int id){
    if ((intent != null && "STOP".equals(intent.getAction())) || !anyOn(this)) {
      if (intent != null && "STOP".equals(intent.getAction())) prefs(this).edit().putInt("gain",0).putInt("bass",0).putBoolean("warm",false).apply();
      releaseAll(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
    SharedPreferences p = prefs(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("boost","Volume boost",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,BoostService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
    String txt = "Boost +"+(p.getInt("gain",0)/20)+"% · Bass "+p.getInt("bass",0)+"%"+(p.getBoolean("warm",false)?" · Warm":"");
    Notification n = new Notification.Builder(this,"boost").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
      .setContentTitle("Volume Max is on").setContentText(txt).setOngoing(true).setContentIntent(open)
      .addAction(new Notification.Action.Builder(null,"Stop all",stop).build()).build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(1,n);
    applyAll(p); running = true;
    return START_STICKY;
  }
  static void applyAll(SharedPreferences p){ applyBoost(p.getInt("gain",0)); applyBass(p.getInt("bass",0)); applyWarm(p.getBoolean("warm",false)); }
  static boolean applyBoost(int mb){
    try { if (le == null) le = new LoudnessEnhancer(0); le.setTargetGain(mb); le.setEnabled(mb > 0); errBoost = null; return true;
    } catch (Throwable t) { errBoost = t.getClass().getSimpleName(); le = null; return false; } }
  static boolean applyBass(int pct){
    try { if (bb == null) bb = new BassBoost(0, 0);
      if (!bb.getStrengthSupported()) throw new UnsupportedOperationException("strength not supported");
      bb.setStrength((short)(pct*10)); bb.setEnabled(pct > 0); errBass = null; return true;
    } catch (Throwable t) { errBass = t.getClass().getSimpleName()+(t.getMessage()!=null?": "+t.getMessage():""); if(bb!=null)try{bb.release();}catch(Throwable x){} bb = null; return false; } }
  static boolean applyWarm(boolean on){
    if (!on) { if (dp!=null){try{dp.setEnabled(false);dp.release();}catch(Throwable t){} dp=null;} if(eq!=null){try{eq.setEnabled(false);eq.release();}catch(Throwable t){} eq=null;} errWarm=null; return true; }
    if (Build.VERSION.SDK_INT >= 28) {
      try { if (dp == null) {
          DynamicsProcessing.Config.Builder b = new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, 5, false, 0, false, 0, true);
          DynamicsProcessing.Config cfg = b.build();
          // upper cutoff (Hz) -> gain (dB): low lift, flat mids, gentle cut of harsh 4-8 kHz
          float[][] bands = {{60,2.5f},{250,3.5f},{4000,0f},{8000,-3f},{20000,-1.5f}};
          DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, 5);
          for (int i=0;i<5;i++) pre.setBand(i, new DynamicsProcessing.EqBand(true, bands[i][0], bands[i][1]));
          cfg.setPreEqAllChannelsTo(pre);
          cfg.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 5f, 120f, 8f, -3f, 0f));
          cfg.setInputGainAllChannelsTo(-3f); // headroom so lifted lows don't clip
          dp = new DynamicsProcessing(0, 0, cfg); }
        dp.setEnabled(true); errWarm = null; return true;
      } catch (Throwable t) { if(dp!=null)try{dp.release();}catch(Throwable x){} dp = null; }
    }
    try { if (eq == null) eq = new Equalizer(0, 0);
      short[] r = eq.getBandLevelRange();
      for (short i=0;i<eq.getNumberOfBands();i++){ int hz = eq.getCenterFreq(i)/1000; int g = hz<=250?350: (hz>=3500&&hz<=9000?-300:0);
        eq.setBandLevel(i,(short)Math.max(r[0],Math.min(r[1],g))); }
      eq.setEnabled(true); errWarm = null; return true;
    } catch (Throwable t) { errWarm = t.getClass().getSimpleName(); eq = null; return false; } }
  static void releaseAll(){ running = false;
    for (AudioEffect e : new AudioEffect[]{le,bb,dp,eq}) if (e != null) try { e.setEnabled(false); e.release(); } catch (Throwable t) {}
    le=null; bb=null; dp=null; eq=null; }
  public void onDestroy(){ releaseAll(); super.onDestroy(); }
}
