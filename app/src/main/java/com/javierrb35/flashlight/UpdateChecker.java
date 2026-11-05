package com.javierrb35.flashlight;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Public signed GitHub releases, no credentials, never downloads in background. */
final class UpdateChecker {
    private static final String PREFS="flashlight_updates";
    private static final String CHANNEL="flashlight_update_notices";
    private static final int JOB_ID=80561;
    private static final long CHECK_INTERVAL=12L*60*60*1000;
    private static final long ERROR_RETRY=5L*60*1000;
    private UpdateChecker(){}

    static final class Result {
        final UpdateRelease update;
        final String message;
        Result(UpdateRelease update,String message){this.update=update;this.message=message;}
    }

    static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    static int installedCode(Context c){
        try{
            PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            return (int)(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);
        }catch(Exception ignored){return 0;}
    }

    static String installedName(Context c){
        try {
            String name=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
            return name==null?"":name;
        } catch(Exception ignored){return "";}
    }

    static UpdateRelease cached(Context c) {
        UpdateRelease release=UpdateRelease.fromCache(prefs(c).getString("latest",""));
        return release!=null&&ReleaseVersions.newer(
                release.tag,installedName(c),installedCode(c))?release:null;
    }

    /** Android controls the actual interval; no network or UI on the main thread. */
    static void schedule(Context c){
        try{
            JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(scheduler==null)return;
            JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true).setPeriodic(CHECK_INTERVAL).build();
            scheduler.schedule(job);
        }catch(RuntimeException problem){
            android.util.Log.w("FlashlightUpdates","No se pudo programar comprobación",problem);
        }
    }

    /** Run only on a worker thread. */
    static synchronized Result check(Context c,boolean force){
        SharedPreferences p=prefs(c);
        long now=System.currentTimeMillis();
        long last=p.getLong("last_checked",0);
        if(!force&&last>0&&now>=last&&now-last<CHECK_INTERVAL){
            return new Result(cached(c),"No hay versiones nuevas.");
        }
        long error=p.getLong("last_error_time",0);
        if(!force&&error>0&&now>=error&&now-error<ERROR_RETRY)
            return new Result(cached(c),"Sin conexión con el canal de actualizaciones.");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(UpdateAssetRules.API).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("User-Agent","FlashLight-Android-Updates");
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            int code=connection.getResponseCode();
            if(code!=200)throw new IllegalStateException("Error HTTP "+code);
            if(connection.getContentLengthLong()>1_000_000)
                throw new IllegalStateException("Respuesta demasiado grande");
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] block=new byte[8192];
                int read;
                while((read=in.read(block))!=-1){
                    if(output.size()+read>1_000_000)
                        throw new IllegalStateException("Respuesta demasiado grande");
                    output.write(block,0,read);
                }
            }
            JSONArray releases=new JSONArray(output.toString(StandardCharsets.UTF_8.name()));
            UpdateRelease newest=null;
            ReleaseVersions best=null;
            String installed=installedName(c);
            int currentCode=installedCode(c);
            for(int i=0;i<releases.length();i++){
                UpdateRelease candidate=UpdateRelease.parse(releases.optJSONObject(i));
                if(candidate==null||!ReleaseVersions.newer(candidate.tag,installed,currentCode))
                    continue;
                ReleaseVersions version=ReleaseVersions.parse(candidate.tag);
                if(best==null||version.compareTo(best)>0){
                    newest=candidate;best=version;
                }
            }
            SharedPreferences.Editor e=p.edit().putLong("last_checked",now)
                    .remove("last_error_time");
            if(newest==null)e.remove("latest");
            else e.putString("latest",newest.toJson().toString());
            e.apply();
            String info=releases.length()==0 ?
                    "Todavía no hay versiones oficiales publicadas en GitHub Releases." :
                    newest==null?"La aplicación está actualizada." :
                    "Nueva versión disponible: "+newest.name+".";
            return new Result(newest,info);
        }catch(Exception e){
            p.edit().putLong("last_error_time",now).apply();
            return new Result(null,
                    "No se pudieron comprobar las actualizaciones. Revisa la conexión.");
        }finally{
            if(connection!=null)connection.disconnect();
        }
    }

    static boolean shouldPrompt(Context c,UpdateRelease update){
        if(update==null)return false;
        SharedPreferences p=prefs(c);
        return !update.tag.equals(p.getString("last_prompted",""));
    }

    static void markPrompted(Context c,UpdateRelease update){
        if(update!=null)prefs(c).edit().putString("last_prompted",update.tag).apply();
    }

    static void maybeNotify(Context c,UpdateRelease update){
        if(update==null)return;
        if(Build.VERSION.SDK_INT>=33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        !=PackageManager.PERMISSION_GRANTED)return;
        SharedPreferences p=prefs(c);
        if(update.tag.equals(p.getString("last_notified","")))return;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Actualizaciones FlashLight",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class);
        open.setAction(UpdateUi.ACTION_UPDATES);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,80561,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_flashlight)
                .setContentTitle("Actualización de FlashLight")
                .setContentText("Disponible "+update.name+" · toca para actualizar")
                .setContentIntent(pending).setAutoCancel(true).build();
        manager.notify(JOB_ID,notification);
        p.edit().putString("last_notified",update.tag).apply();
    }
}
