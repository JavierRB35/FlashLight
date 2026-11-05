package com.javierrb35.flashlight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-initiated, verified download. Android always owns the installation dialog. */
final class UpdateInstaller {
    private final Activity activity;
    private final AtomicBoolean downloading=new AtomicBoolean(false);
    private UpdateRelease awaitingPermission;

    UpdateInstaller(Activity activity){this.activity=activity;}

    void start(UpdateRelease update){
        if(update==null || !UpdateAssetRules.valid(update.tag,update.id,update.size,
                update.assetName,update.digest,update.url) ||
                !ReleaseVersions.newer(update.tag,UpdateChecker.installedName(activity),
                        UpdateChecker.installedCode(activity))){
            message("Esta versión no se puede instalar o ya está actualizada.");
            return;
        }
        if(!downloading.compareAndSet(false,true)) return;
        AlertDialog progress=new AlertDialog.Builder(activity)
                .setTitle("Actualizando FlashLight")
                .setMessage("Descargando y verificando la APK oficial…")
                .setCancelable(false).create();
        progress.show();
        new Thread(() -> {
            File partial=partialFile();
            try{
                fetchAndVerify(update,partial);
                File target=apkFile();
                if(target.exists()&&!target.delete())
                    throw new IllegalStateException("No se pudo reemplazar la descarga anterior");
                if(!partial.renameTo(target))
                    throw new IllegalStateException("No se pudo guardar la APK verificada");
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if(!activity.isFinishing()&&!activity.isDestroyed())
                        installVerified(update);
                });
            }catch(Exception error){
                if(partial.exists())partial.delete();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    message("No se pudo verificar la actualización. La instalación actual no se ha modificado. "+
                            error.getMessage());
                });
            }finally{downloading.set(false);}
        },"flashlight-apk-download").start();
    }

    private File directory(){return new File(activity.getCacheDir(),"updates");}
    private File partialFile(){return new File(directory(),"flashlight-update.partial.apk");}
    private File apkFile(){return new File(directory(),UpdateApkProvider.FILE_NAME);}

    private void fetchAndVerify(UpdateRelease update,File partial) throws Exception{
        if(!directory().exists()&&!directory().mkdirs())
            throw new IllegalStateException("No se pudo preparar la descarga");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(update.url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent","FlashLight-Android-Updates");
            if(connection.getResponseCode()!=200||!"https".equals(connection.getURL().getProtocol())||
                    !UpdateAssetRules.trustedRedirectHost(connection.getURL().getHost()))
                throw new IllegalStateException("Servidor de descarga no autorizado");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long total=0;
            try(InputStream in=connection.getInputStream();
                    FileOutputStream out=new FileOutputStream(partial)){
                byte[] buffer=new byte[32768];
                int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Descarga interrumpida");
                    total+=n;
                    if(total>update.size||total>UpdateAssetRules.MAX_BYTES)
                        throw new IllegalStateException("Tamaño de APK no válido");
                    digest.update(buffer,0,n);
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
            }
            if(total!=update.size || !hex(digest.digest()).equals(UpdateAssetRules.digestHex(update.digest)))
                throw new IllegalStateException("El tamaño o SHA-256 de la APK no coincide");
            validatePackage(partial,update);
        }finally{if(connection!=null)connection.disconnect();}
    }

    @SuppressWarnings("deprecation")
    private void validatePackage(File file,UpdateRelease update) throws Exception{
        PackageManager pm=activity.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?
                PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),flags);
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||
                !(update.name.equals(candidate.versionName)) ||
                (Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode)!=update.code ||
                !sameSigner(installed,candidate))
            throw new IllegalStateException("El paquete, la versión o la firma no coincide con la aplicación instalada");
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signers(PackageInfo packageInfo){
        if(packageInfo==null)return null;
        if(Build.VERSION.SDK_INT>=28)
            return packageInfo.signingInfo==null?null:packageInfo.signingInfo.getApkContentsSigners();
        return packageInfo.signatures;
    }

    private static boolean sameSigner(PackageInfo first,PackageInfo second) throws Exception{
        Signature[] installed=signers(first),candidate=signers(second);
        if(installed==null||candidate==null||installed.length!=1||candidate.length!=1)return false;
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        return MessageDigest.isEqual(sha.digest(installed[0].toByteArray()),
                sha.digest(candidate[0].toByteArray()));
    }

    private static String hex(byte[] bytes){
        StringBuilder result=new StringBuilder(bytes.length*2);
        for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }

    private void installVerified(UpdateRelease update){
        if(Build.VERSION.SDK_INT>=26 &&
                !activity.getPackageManager().canRequestPackageInstalls()){
            awaitingPermission=update;
            new AlertDialog.Builder(activity)
                    .setTitle("Permitir instalación")
                    .setMessage("Android debe autorizar a FlashLight a instalar actualizaciones descargadas. "+
                            "No se instalará nada sin tu confirmación.")
                    .setNegativeButton("Ahora no", (dialog,which)->awaitingPermission=null)
                    .setPositiveButton("Abrir ajustes",(dialog,which)->{
                        try{
                            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:"+activity.getPackageName()));
                            activity.startActivity(settings);
                        }catch(Exception problem){awaitingPermission=null;
                            message("No se pudieron abrir los ajustes de instalación.");}
                    }).show();
            return;
        }
        awaitingPermission=null;
        Uri uri=UpdateApkProvider.fileUri(activity);
        Intent intent=new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("FlashLight",uri));
        try{activity.startActivity(intent);}
        catch(Exception problem){message("Android no ha podido abrir el instalador de APK.");}
    }

    void onResume(){
        if(awaitingPermission!=null && Build.VERSION.SDK_INT>=26 &&
                activity.getPackageManager().canRequestPackageInstalls())
            installVerified(awaitingPermission);
    }

    private void message(String text){
        if(activity.isFinishing()||activity.isDestroyed())return;
        new AlertDialog.Builder(activity).setTitle("Actualizaciones de FlashLight")
                .setMessage(text).setPositiveButton("Entendido",null).show();
    }
}
