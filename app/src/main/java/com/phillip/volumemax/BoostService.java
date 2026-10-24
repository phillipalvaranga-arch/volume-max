package com.phillip.volumemax;
import android.app.*; import android.content.*; import android.content.pm.ServiceInfo; import android.media.audiofx.*; import android.os.*;
public class BoostService extends Service {
  static LoudnessEnhancer le; static BassBoost bb; static DynamicsProcessing dp; static Equalizer eq;
  static String errBoost, errBass, errWarm; static boolean running = false;
  static SharedPreferences prefs(Context c){ return c.getSharedPreferences("vm", MODE_PRIVATE); }
  static boolean anyOn(Context c){ SharedPreferences p=prefs(c); return p.getInt("gain",0)>0||p.getInt("bass",0)>0||preset(c)!=0||p.getInt("mid",0)!=0||p.getInt("treble",0)!=0; }
  // 0 Off, 1 Warm, 2 Warm & Crisp (recommended default)
  static int preset(Context c){ SharedPreferences p=prefs(c); if(!p.contains("preset")) p.edit().putInt("preset", p.getBoolean("warm",false)?1:2).apply(); return p.getInt("preset",2); }
  static final String[] PRESETS={"Off","Warm","Warm & Crisp"};
  static final float[] CUT={60,150,250,400,800,3000,6000,9000,13000,20000};
  static final float[][] PG={{0,0,0,0,0,0,0,0,0,0},
    {2.5f,3.5f,2f,0,0,0,0,-3f,-3f,-1.5f},
    {2.5f,3f,1f,-1.5f,0,1.5f,0.5f,1.5f,2.5f,1.5f}};
  static float[] gains(int preset,int mid,int tre){ float[] g=PG[preset].clone();
    g[4]+=mid*0.2f; g[5]+=mid; g[6]+=mid*0.4f; g[7]+=tre*0.7f; g[8]+=tre; g[9]+=tre*0.8f; return g; }
  public IBinder onBind(Intent i){ return null; }
  public int onStartCommand(Intent intent, int flags, int id){
    if ((intent != null && "STOP".equals(intent.getAction())) || !anyOn(this)) {
      if (intent != null && "STOP".equals(intent.getAction())) prefs(this).edit().putInt("gain",0).putInt("bass",0).putInt("preset",0).putInt("mid",0).putInt("treble",0).apply();
      releaseAll(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
    SharedPreferences p = prefs(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("boost","Volume boost",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,BoostService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
    String txt = "Boost +"+(p.getInt("gain",0)/20)+"% · Bass "+p.getInt("bass",0)+"%"+" · "+PRESETS[preset(this)];
    Notification n = new Notification.Builder(this,"boost").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
      .setContentTitle("Volume Max is on").setContentText(txt).setOngoing(true).setContentIntent(open)
      .addAction(new Notification.Action.Builder(null,"Stop all",stop).build()).build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(1,n);
    applyAll(p); running = true;
    return START_STICKY;
  }
  static void applyAll(SharedPreferences p){ applyBoost(p.getInt("gain",0)); applyBass(p.getInt("bass",0)); applyEq(p.getInt("preset",2),p.getInt("mid",0),p.getInt("treble",0)); }
  static boolean applyBoost(int mb){
    try { if (le == null) le = new LoudnessEnhancer(0); le.setTargetGain(mb); le.setEnabled(mb > 0); errBoost = null; return true;
    } catch (Throwable t) { errBoost = t.getClass().getSimpleName(); le = null; return false; } }
  static boolean applyBass(int pct){
    try { if (bb == null) bb = new BassBoost(0, 0);
      if (!bb.getStrengthSupported()) throw new UnsupportedOperationException("strength not supported");
      bb.setStrength((short)(pct*10)); bb.setEnabled(pct > 0); errBass = null; return true;
    } catch (Throwable t) { errBass = t.getClass().getSimpleName()+(t.getMessage()!=null?": "+t.getMessage():""); if(bb!=null)try{bb.release();}catch(Throwable x){} bb = null; return false; } }
  static boolean applyEq(int preset,int mid,int tre){
    if (dp!=null){try{dp.setEnabled(false);dp.release();}catch(Throwable t){} dp=null;}
    if (eq!=null){try{eq.setEnabled(false);eq.release();}catch(Throwable t){} eq=null;}
    errWarm=null; float[] g=gains(preset,mid,tre); float mx=0; for(float x:g) mx=Math.max(mx,x);
    if (mx==0 && preset==0) { boolean flat=true; for(float x:g) if(x!=0) flat=false; if(flat) return true; }
    if (Build.VERSION.SDK_INT >= 28) {
      try { DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, CUT.length, false, 0, false, 0, true).build();
        DynamicsProcessing.Eq pre = new DynamicsProcessing.Eq(true, true, CUT.length);
        for (int i=0;i<CUT.length;i++) pre.setBand(i, new DynamicsProcessing.EqBand(true, CUT[i], g[i]));
        cfg.setPreEqAllChannelsTo(pre);
        cfg.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 5f, 120f, 8f, -3f, 0f));
        cfg.setInputGainAllChannelsTo(-Math.max(2f, mx)); // headroom = biggest lift
        dp = new DynamicsProcessing(0, 0, cfg); dp.setEnabled(true); return true;
      } catch (Throwable t) { if(dp!=null)try{dp.release();}catch(Throwable x){} dp = null; }
    }
    try { eq = new Equalizer(0, 0); short[] r = eq.getBandLevelRange();
      for (short i=0;i<eq.getNumberOfBands();i++){ float hz = eq.getCenterFreq(i)/1000f; int k=0; while(k<CUT.length-1 && hz>CUT[k]) k++;
        int lvl=(int)(g[k]*100) - (int)(Math.max(0,mx-3)*100); // keep headroom
        eq.setBandLevel(i,(short)Math.max(r[0],Math.min(r[1],lvl))); }
      eq.setEnabled(true); return true;
    } catch (Throwable t) { errWarm = t.getClass().getSimpleName(); eq = null; return false; } }
  static void releaseAll(){ running = false;
    for (AudioEffect e : new AudioEffect[]{le,bb,dp,eq}) if (e != null) try { e.setEnabled(false); e.release(); } catch (Throwable t) {}
    le=null; bb=null; dp=null; eq=null; }
  public void onDestroy(){ releaseAll(); super.onDestroy(); }
}
