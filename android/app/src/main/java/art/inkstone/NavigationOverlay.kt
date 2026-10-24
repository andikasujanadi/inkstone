package art.inkstone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

internal class NavigationOverlay(context:Context,message:String):FrameLayout(context){
    init{
        val d=resources.displayMetrics.density
        setBackgroundColor(0x77000000);isClickable=true;isFocusable=true;isFocusableInTouchMode=true
        contentDescription=message;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        val panel=LinearLayout(context).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding((24*d).toInt(),(20*d).toInt(),(24*d).toInt(),(20*d).toInt())
            background=GradientDrawable().apply{setColor(StudioColors.surface);cornerRadius=StudioShape.CORNER_DP*d}
        }
        panel.addView(ProgressBar(context).apply{isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(StudioColors.accent)},LinearLayout.LayoutParams((32*d).toInt(),(32*d).toInt()))
        panel.addView(TextView(context).apply{text=message;textSize=16f;setTextColor(Color.WHITE);maxLines=3;setPadding((18*d).toInt(),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
        addView(panel,LayoutParams(minOf((360*d).toInt(),resources.displayMetrics.widthPixels-(40*d).toInt()),-2,Gravity.CENTER))
    }
}
