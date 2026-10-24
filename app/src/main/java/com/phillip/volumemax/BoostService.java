package com.phillip.volumemax;
import android.app.*; import android.content.*; import android.content.pm.ServiceInfo; import android.media.audiofx.LoudnessEnhancer; import android.os.*;
public class BoostService extends Service {
  static LoudnessEnhancer le; static int gainMb = 0; static String lastError = null;
  static boolean running = false;
  public IBinder onBind(Intent i){ return null; }
  public int onStartCommand(Intent intent, int flags, int id){
    if (intent != null && "STOP".equals(intent.getAction())) { release(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
    if (intent != null) gainMb = intent.getIntExtra("gain", gainMb);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("boost","Volume boost",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,BoostService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
    Notification n = new Notification.Builder(this,"boost").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
      .setContentTitle("Volume Max boost on").setContentText("Boost +"+(gainMb/20)+"%").setOngoing(true).setContentIntent(open)
      .addAction(new Notification.Action.Builder(null,"Stop boost",stop).build()).build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(1,n);
    apply(); running = true;
    return START_STICKY;
  }
  static boolean apply(){
    try { if (le == null) le = new LoudnessEnhancer(0);
      le.setTargetGain(gainMb); le.setEnabled(gainMb > 0); lastError = null; return true;
    } catch (Throwable t) { lastError = t.getClass().getSimpleName()+": "+t.getMessage(); le = null; return false; }
  }
  static void release(){ running = false; if (le != null) { try { le.setEnabled(false); le.release(); } catch (Throwable t) {} le = null; } }
  public void onDestroy(){ release(); super.onDestroy(); }
}
