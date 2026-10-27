package art.inkstone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

internal class NavigationOverlay(context:Context,message:String,compact:Boolean=false):FrameLayout(context){
    init{
        val d=resources.displayMetrics.density
        setBackgroundColor(if(compact)Color.TRANSPARENT else 0x77000000);isClickable=true;isFocusable=true;isFocusableInTouchMode=true
        contentDescription=message;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        val panel=LinearLayout(context).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(((if(compact)14 else 24)*d).toInt(),((if(compact)10 else 20)*d).toInt(),((if(compact)14 else 24)*d).toInt(),((if(compact)10 else 20)*d).toInt())
            background=GradientDrawable().apply{setColor(StudioColors.surface);cornerRadius=StudioShape.CORNER_DP*d}
        }
        panel.addView(ProgressBar(context).apply{isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(StudioColors.accent)},LinearLayout.LayoutParams(((if(compact)20 else 32)*d).toInt(),((if(compact)20 else 32)*d).toInt()))
        panel.addView(TextView(context).apply{text=message;textSize=if(compact)13f else 16f;setTextColor(Color.WHITE);maxLines=3;setPadding((18*d).toInt(),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
        addView(panel,LayoutParams(minOf(((if(compact)250 else 360)*d).toInt(),resources.displayMetrics.widthPixels-(40*d).toInt()),-2,if(compact)Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER).apply{if(compact)bottomMargin=(24*d).toInt()})
    }
}
