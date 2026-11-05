package com.javierrb35.flashlight;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only URI for the one verified APK, with no external provider access. */
public final class UpdateApkProvider extends ContentProvider {
    static final String FILE_NAME="flashlight-update.apk";
    static Uri fileUri(android.content.Context c) {
        return Uri.parse("content://"+c.getPackageName()+".updates/"+FILE_NAME);
    }
    private File approved(Uri uri) throws FileNotFoundException {
        if(getContext()==null||uri==null||!("/"+FILE_NAME).equals(uri.getPath()))
            throw new FileNotFoundException("Ruta no autorizada");
        File file=new File(new File(getContext().getCacheDir(),"updates"),FILE_NAME);
        if(!file.isFile())throw new FileNotFoundException("APK no disponible");
        return file;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        return uri!=null&&("/"+FILE_NAME).equals(uri.getPath())
                ?"application/vnd.android.package-archive":null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Solo lectura");
        return ParcelFileDescriptor.open(approved(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
            String[] args,String sort){
        try{
            File file=approved(uri);
            String[] cols=projection==null?
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(cols);
            Object[] values=new Object[cols.length];
            for(int i=0;i<cols.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))
                    values[i]="FlashLight-update.apk";
                if(OpenableColumns.SIZE.equals(cols[i])) values[i]=file.length();
            }
            cursor.addRow(values);
            return cursor;
        }catch(FileNotFoundException unavailable){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){
        throw new UnsupportedOperationException();
    }
}
