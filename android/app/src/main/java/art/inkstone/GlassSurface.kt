package art.inkstone

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.*

// Capture only the artwork view when a popover opens; never blur every pen sample.
internal class GlassSurface(source:View,private val panelX:Float,private val panelY:Float,private val radius:Float):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect=RectF()
    private val scale=.16f
    private val image:Bitmap
    init{
        val w=max(1,((source.width+source.left)*scale).roundToInt());val h=max(1,((source.height+source.top)*scale).roundToInt())
        image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(StudioColors.workspace);canvas.scale(scale,scale);canvas.translate(source.left.toFloat(),source.top.toFloat());source.draw(canvas)
        val pixels=IntArray(w*h);val temp=IntArray(w*h);image.getPixels(pixels,0,w,0,0,w,h)
        val r=5
        for(pass in 0..1){
            val major=if(pass==0)h else w;val minor=if(pass==0)w else h
            val input=if(pass==0)pixels else temp;val output=if(pass==0)temp else pixels
            for(a in 0 until major){var red=0;var green=0;var blue=0
                fun index(b:Int)=if(pass==0)a*w+b else b*w+a
                for(b in 0..min(r,minor-1)){val c=input[index(b)];red+=(c ushr 16)and 255;green+=(c ushr 8)and 255;blue+=c and 255}
                for(b in 0 until minor){val n=min(minor,b+r+1)-max(0,b-r);output[index(b)]=Color.rgb(red/n,green/n,blue/n)
                    if(b>=r){val c=input[index(b-r)];red-=(c ushr 16)and 255;green-=(c ushr 8)and 255;blue-=c and 255}
                    if(b+r+1<minor){val c=input[index(b+r+1)];red+=(c ushr 16)and 255;green+=(c ushr 8)and 255;blue+=c and 255}
                }
            }
        }
        image.setPixels(pixels,0,w,0,0,w,h)
    }
    override fun onBoundsChange(bounds:Rect){rect.set(bounds)}
    override fun draw(c:Canvas){
        val save=c.save();val path=Path().apply{addRoundRect(rect,radius,radius,Path.Direction.CW)};c.clipPath(path)
        val imageSave=c.save();c.translate(-panelX,-panelY);c.scale(1/scale,1/scale)
        paint.color=Color.WHITE;paint.alpha=255;c.drawBitmap(image,0f,0f,paint);c.restoreToCount(imageSave)
        paint.color=StudioColors.glass;c.drawRect(rect,paint);c.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int){}
    override fun setColorFilter(filter:ColorFilter?){}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
