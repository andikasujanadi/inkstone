package art.inkstone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView

internal data class StudioTheme(val light:Boolean){
    val text=if(light)0xff202832.toInt() else Color.WHITE
    val secondary=if(light)0xff52606e.toInt() else 0xffb9c3cd.toInt()
    val toolbar=if(light)0xffeef1f5.toInt() else StudioColors.toolbar
    val workspace=if(light)0xffc9cfd6.toInt() else StudioColors.workspace
    val surface=if(light)0xfff2f4f7.toInt() else StudioColors.surface
    val rail=if(light)0xffe3e8ee.toInt() else StudioColors.rail
    val track=if(light)0xffd8dfe7.toInt() else StudioColors.track
    val border=if(light)0xffaab5c2.toInt() else 0xff59636e.toInt()
    val glass=if(light)0xedf2f4f7.toInt() else StudioColors.glass
    fun label(color:Int):Int{
        if(!light || color==text)return color
        return if(color!=Color.WHITE && color!=Color.BLACK && neutral(color))secondary else color
    }
    fun background(color:Int):Int{
        if(!light || color==Color.TRANSPARENT || !neutral(color) || Color.red(color)>160)return color
        return if(Color.alpha(color)<255)Color.argb(maxOf(Color.alpha(color),128),203,213,224) else if(Color.red(color)>=70)0xfff8fafc.toInt() else track
    }
    companion object{
        const val PRESERVE_COLOR="artwork-color"
        fun of(context:Context)=StudioTheme(context.getSharedPreferences("studio",Context.MODE_PRIVATE).getBoolean("light",false))
        private fun neutral(color:Int)=maxOf(Color.red(color),Color.green(color),Color.blue(color))-minOf(Color.red(color),Color.green(color),Color.blue(color))<32
        fun apply(root:View){
            val theme=of(root.context)
            if(root.tag==PRESERVE_COLOR)return
            if(root is ToolIcon)root.light=theme.light
            val background=root.background as? GradientDrawable
            val fill=background?.color?.defaultColor
            if(fill!=null)background.setColor(theme.background(fill))
            if(root is TextView){
                val color=root.currentTextColor
                val parentFill=((root.parent as? View)?.background as? GradientDrawable)?.color?.defaultColor
                val selected=fill==StudioColors.accent || fill==StudioColors.accentSoft || parentFill==StudioColors.accent || parentFill==StudioColors.accentSoft
                if(selected)root.setTextColor(Color.WHITE)
                else if(neutral(color))root.setTextColor(if(color==Color.WHITE || color==Color.BLACK || color==theme.text)theme.text else theme.secondary)
            }
            if(root is CheckBox)root.buttonTintList=android.content.res.ColorStateList.valueOf(theme.secondary)
            if(root is ViewGroup)for(i in 0 until root.childCount)apply(root.getChildAt(i))
            root.invalidate()
        }
    }
}
