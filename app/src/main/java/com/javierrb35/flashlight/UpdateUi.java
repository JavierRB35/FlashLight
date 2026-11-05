package com.javierrb35.flashlight;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Non-blocking in-app checks, optional prompts and a manual update control. */
final class UpdateUi {
    static final String ACTION_UPDATES="com.javierrb35.flashlight.action.UPDATES";
    private static final int NOTIFICATION_PERMISSION_REQUEST=8056;
    private final Activity activity;
    private final BooleanSupplier lightOrTransmissionActive;
    private final UpdateInstaller installer;
    private UpdateRelease deferred;
    private final AtomicBoolean checking=new AtomicBoolean(false);

    UpdateUi(Activity activity, BooleanSupplier lightOrTransmissionActive){
        this.activity=activity;
        this.lightOrTransmissionActive=lightOrTransmissionActive;
        this.installer=new UpdateInstaller(activity);
    }

    void onOpen(){
        UpdateChecker.schedule(activity);
        Intent intent=activity.getIntent();
        if(intent!=null && ACTION_UPDATES.equals(intent.getAction())){
            activity.setIntent(new Intent(activity,MainActivity.class));
            UpdateRelease remembered=UpdateChecker.cached(activity);
            if(remembered!=null)
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(()->prompt(remembered));
            else check(true);
            return; // Do not display a duplicate prompt from the automatic check.
        }
        check(false);
    }

    void onResume(){
        installer.onResume();
        onControlsRefreshed();
    }

    /** A check must never obscure the STOP button during active light signalling. */
    void onControlsRefreshed(){
        if(deferred==null || lightOrTransmissionActive.getAsBoolean() ||
                !activity.hasWindowFocus() || activity.isFinishing())return;
        UpdateRelease pending=deferred;
        deferred=null;
        activity.getWindow().getDecorView().post(()->prompt(pending));
    }

    void onNewIntent(Intent intent){
        if(intent!=null && ACTION_UPDATES.equals(intent.getAction())){
            activity.setIntent(new Intent(activity,MainActivity.class));
            UpdateRelease remembered=UpdateChecker.cached(activity);
            if(remembered!=null) prompt(remembered);
            else check(true);
        }
    }

    void openMenu(){
        if(Build.VERSION.SDK_INT>=33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    !=PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(activity).setTitle("Actualizaciones de FlashLight")
                    .setItems(new String[]{"Buscar actualizaciones ahora",
                            "Activar avisos de nuevas versiones"},(dialog,choice)->{
                        if(choice==0)check(true);
                        else activity.requestPermissions(
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_PERMISSION_REQUEST);
                    }).setNegativeButton("Cancelar",null).show();
        }else check(true);
    }

    private void check(boolean manual){
        if(!checking.compareAndSet(false,true)){
            if(manual)message("Ya se está comprobando si hay una versión nueva.");
            return;
        }
        new Thread(()->{
            UpdateChecker.Result result;
            try{result=UpdateChecker.check(activity.getApplicationContext(),manual);}
            catch(Exception failure){result=new UpdateChecker.Result(null,
                    "No se pudieron comprobar las actualizaciones.");}
            checking.set(false);
            UpdateChecker.Result completed=result;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(completed.update!=null){
                    if(manual||UpdateChecker.shouldPrompt(activity,completed.update))
                        prompt(completed.update);
                }else if(manual)message(completed.message);
            });
        },"flashlight-check-updates").start();
    }

    private void prompt(UpdateRelease release){
        if(activity.isFinishing()||activity.isDestroyed())return;
        if(lightOrTransmissionActive.getAsBoolean()){
            deferred=release;
            return;
        }
        if(!ReleaseVersions.newer(release.tag,UpdateChecker.installedName(activity),
                UpdateChecker.installedCode(activity)))return;
        UpdateChecker.markPrompted(activity,release);
        String details="FlashLight "+release.name+" está disponible.\n\n"+
                (release.notes.isEmpty()?"Mejoras y correcciones disponibles.":release.notes)+
                "\n\nLa descarga es voluntaria. Se comprobarán la integridad, el paquete y la firma "+
                "antes de abrir el instalador oficial de Android.";
        new AlertDialog.Builder(activity).setTitle("Nueva versión de FlashLight")
                .setMessage(details)
                .setNegativeButton("Más tarde",null)
                .setPositiveButton("Actualizar",(dialog,which)->installer.start(release))
                .show();
    }

    private void message(String description){
        if(activity.isFinishing()||activity.isDestroyed())return;
        new AlertDialog.Builder(activity).setTitle("Actualizaciones de FlashLight")
                .setMessage(description).setPositiveButton("Aceptar",null).show();
    }
}
