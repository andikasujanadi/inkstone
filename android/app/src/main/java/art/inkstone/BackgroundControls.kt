package art.inkstone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import kotlin.math.roundToInt

internal class BackgroundControls(context:Context,initial:Int,initialVisible:Boolean,private val activeColor:()->Int,private val change:(Int?,Boolean)->Unit):LinearLayout(context){
    private val theme get()=StudioTheme.of(context)
    private var color=initial
    private var visible=initialVisible
    private val density=resources.displayMetrics.density
    private fun dp(n:Int)=(n*density).roundToInt()
    private fun backgroundColor(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(StudioShape.CORNER_DP).toFloat()}
    private fun button(title:String,run:()->Unit)=TextView(context).apply{text=title;textSize=14f;setTextColor(theme.text);gravity=Gravity.CENTER;setPadding(dp(10),0,dp(10),0);background=backgroundColor(theme.track);isClickable=true;isFocusable=true;contentDescription=title;setOnClickListener{StudioMotion.bounce(this);run()}}
    private val swatch=android.view.View(context).apply{tag=StudioTheme.PRESERVE_COLOR;contentDescription="Background color preview"}
    private val hex=EditText(context).apply{isSingleLine=true;setTextColor(theme.text);textSize=16f;background=backgroundColor(theme.track);setPadding(dp(12),0,dp(12),0);contentDescription="Background hex color"}
    private lateinit var visibilityButton:TextView
    private lateinit var picker:ColorDisc
    private fun refresh(){swatch.background=backgroundColor(color);hex.setText(String.format("#%06X",color and 0xffffff));visibilityButton.text=if(visible)"Background visible · tap for transparency" else "Transparent · tap to show background";visibilityButton.isSelected=visible;picker.setColor(color)}
    private fun choose(value:Int){color=value or 0xff000000.toInt();visible=true;refresh();change(color,true)}
    init{
        orientation=VERTICAL;setPadding(dp(8),0,dp(8),dp(8))
        addView(swatch,LayoutParams(-1,dp(44)).apply{bottomMargin=dp(12)})
        val presets=LinearLayout(context)
        for((name,value) in listOf("White" to Color.WHITE,"Warm paper" to 0xfffaf9f6.toInt(),"Black" to Color.BLACK))presets.addView(button(name){choose(value)},LayoutParams(-2,dp(44)).apply{rightMargin=dp(6)})
        presets.addView(button("Current colour"){choose(activeColor())},LayoutParams(-2,dp(44)))
        addView(HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;addView(presets)},LayoutParams(-1,dp(48)))
        visibilityButton=button("Background transparency"){visible=!visible;refresh();change(null,visible)}
        addView(visibilityButton,LayoutParams(-1,dp(44)).apply{topMargin=dp(8)})
        picker=ColorDisc(context,color,false){draft->color=draft;swatch.background=backgroundColor(color);hex.setText(String.format("#%06X",color and 0xffffff))}
        addView(picker,LayoutParams(-1,dp(270)))
        val row=LinearLayout(context);row.addView(hex,LayoutParams(0,dp(44),1f));row.addView(button("Apply color"){runCatching{Color.parseColor(hex.text.toString().let{if(it.startsWith("#"))it else "#$it"})}.onSuccess(::choose).onFailure{hex.error="Use a hex color such as #347A68"}},LayoutParams(dp(100),dp(44)));addView(row)
        addView(TextView(context).apply{text="Pick a color, then Apply color. Your brush color stays unchanged.";textSize=12f;setTextColor(theme.secondary);setPadding(0,dp(12),0,0)},LayoutParams(-1,-2))
        refresh()
    }
}
