package art.inkstone

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

internal class SwipeLayerRow(context:Context,val foreground:View,private val actions:View,private val actionWidth:Int):FrameLayout(context){
    private var startX=0f;private var startY=0f;private var origin=0f;private var dragging=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    var revealed=false;private set
    init{clipChildren=true;addView(actions,LayoutParams(actionWidth,-1,android.view.Gravity.RIGHT));addView(foreground,LayoutParams(-1,-1))}
    private fun updateActions(){
        val exposed=(-foreground.translationX).toInt().coerceIn(0,actionWidth)
        actions.clipBounds=android.graphics.Rect(actionWidth-exposed,0,actionWidth,height)
        actions.invalidate();invalidate()
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);updateActions()}
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{startX=event.x;startY=event.y;origin=foreground.translationX;dragging=false}
            MotionEvent.ACTION_MOVE->{val dx=event.x-startX;val dy=event.y-startY;if(abs(dx)>slop&&abs(dx)>abs(dy)*1.2f){dragging=true;foreground.animate().cancel();foreground.translationX=(origin+dx).coerceIn(-actionWidth.toFloat(),0f);updateActions();parent.requestDisallowInterceptTouchEvent(true);return true}}
        };return false
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{startX=event.x;startY=event.y;origin=foreground.translationX;return true}
            MotionEvent.ACTION_MOVE->{foreground.translationX=(origin+event.x-startX).coerceIn(-actionWidth.toFloat(),0f);updateActions();return true}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{reveal(foreground.translationX < -actionWidth*.35f);dragging=false;return true}
        };return dragging||super.onTouchEvent(event)
    }
    fun reveal(open:Boolean){revealed=open;foreground.animate().translationX(if(open)-actionWidth.toFloat() else 0f).setDuration(240).setUpdateListener{updateActions()}.withEndAction{updateActions()}.start()}
    override fun onDetachedFromWindow(){foreground.animate().cancel();super.onDetachedFromWindow()}
}
