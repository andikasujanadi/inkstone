package art.inkstone

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.widget.*
import kotlin.math.roundToInt

internal object GroupAppearance {
    val modes=listOf("Pass Through","Normal","Multiply","Screen","Overlay","Darken","Lighten","Add")
    fun label(mode:String)=if(mode=="Pass Through")"PT"else if(mode=="Normal")"N"else mode.take(1)
    fun paint(mode:String,opacity:Float)=Paint().apply{
        alpha=(opacity.coerceIn(0f,1f)*255).roundToInt()
        if(android.os.Build.VERSION.SDK_INT>=29){blendMode=when(mode){
            "Multiply"->android.graphics.BlendMode.MULTIPLY;"Screen"->android.graphics.BlendMode.SCREEN;"Overlay"->android.graphics.BlendMode.OVERLAY
            "Darken"->android.graphics.BlendMode.DARKEN;"Lighten"->android.graphics.BlendMode.LIGHTEN;"Add"->android.graphics.BlendMode.PLUS
            else->android.graphics.BlendMode.SRC_OVER
        }}else xfermode=PorterDuffXfermode(when(mode){
            "Multiply"->PorterDuff.Mode.MULTIPLY;"Screen"->PorterDuff.Mode.SCREEN;"Overlay"->PorterDuff.Mode.OVERLAY
            "Darken"->PorterDuff.Mode.DARKEN;"Lighten"->PorterDuff.Mode.LIGHTEN;"Add"->PorterDuff.Mode.ADD
            else->PorterDuff.Mode.SRC_OVER
        })
    }
    fun show(context:Context,name:String,mode:String,opacity:Float,apply:(String,Float)->Unit){
        val d=context.resources.displayMetrics.density
        val body=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding((24*d).toInt(),(12*d).toInt(),(24*d).toInt(),0)}
        val value=TextView(context).apply{text="Opacity ${(opacity*100).roundToInt()}%"}
        val slider=SeekBar(context).apply{max=100;progress=(opacity*100).roundToInt();contentDescription="Group opacity";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar,p:Int,user:Boolean){value.text="Opacity $p%"}
            override fun onStartTrackingTouch(v:SeekBar){};override fun onStopTrackingTouch(v:SeekBar){}
        })}
        val picker=Spinner(context).apply{adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,modes);setSelection(modes.indexOf(mode).coerceAtLeast(0));contentDescription="Group blend mode"}
        body.addView(value);body.addView(slider);body.addView(picker)
        body.addView(TextView(context).apply{text="Applies to the combined contents of this folder.";setPadding(0,(12*d).toInt(),0,0)})
        AlertDialog.Builder(context).setTitle(name).setView(body).setPositiveButton("Apply"){_,_->apply(modes[picker.selectedItemPosition],slider.progress/100f)}.setNegativeButton("Cancel",null).show()
    }
}
