package art.inkstone

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

internal object ImageExports {
    fun encode(image:Bitmap,kind:String,output:OutputStream){
        require(kind=="PNG" || kind=="JPEG")
        if(kind=="PNG"){check(image.compress(Bitmap.CompressFormat.PNG,100,output)){"PNG encoding failed"};return}
        val opaque=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
        try{Canvas(opaque).apply{drawColor(Color.WHITE);drawBitmap(image,0f,0f,null)}
            check(opaque.compress(Bitmap.CompressFormat.JPEG,95,output)){"JPEG encoding failed"}
        }finally{opaque.recycle()}
    }
    fun filename(name:String,kind:String):String {
        val clean=name.replace(Regex("[\\\\/\\p{Cntrl}]"),"_").take(100).ifBlank{"Artwork"}
        return "$clean-${java.util.UUID.randomUUID().toString().take(8)}.${if(kind=="JPEG")"jpg" else "png"}"
    }
    fun saveToGallery(context:Context,image:Bitmap,kind:String,name:String){
        require(kind=="PNG" || kind=="JPEG")
        val mime=if(kind=="JPEG")"image/jpeg" else "image/png"
        val filename=filename(name,kind)
        if(Build.VERSION.SDK_INT>=29){
            val resolver=context.contentResolver
            val values=ContentValues().apply{
                put(MediaStore.Images.Media.DISPLAY_NAME,filename);put(MediaStore.Images.Media.MIME_TYPE,mime)
                put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/Inkstone")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values)){"Could not create gallery image"}
            try{
                checkNotNull(resolver.openOutputStream(uri,"w")){"Could not open gallery image"}.use{encode(image,kind,it)}
                check(resolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)>0){"Could not publish gallery image"}
            }catch(e:Exception){runCatching{resolver.delete(uri,null,null)};throw e}
        }else{
            @Suppress("DEPRECATION")
            val directory=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Inkstone")
            check(directory.isDirectory || directory.mkdirs()){"Could not create Pictures/Inkstone"}
            val file=File(directory,filename)
            check(file.createNewFile()){"Image name already exists; try saving again"}
            try{file.outputStream().use{encode(image,kind,it)}}catch(e:Exception){file.delete();throw e}
            MediaScannerConnection.scanFile(context,arrayOf(file.absolutePath),arrayOf(mime),null)
        }
    }
}
