package art.inkstone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Editor-owned range control with a stable touch target. */
internal class BrushRange(context:Context, initial:Float, light:Boolean, private val changed:(Float)->Unit):View(context){
    var value=initial.coerceIn(0f,1f);private set
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val theme=StudioTheme(light)
    init{isFocusable=true;isClickable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    override fun onDraw(c:Canvas){
        val left=14*d;val right=width-14*d;val y=height/2f
        paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=4*d;paint.color=theme.track;c.drawLine(left,y,right,y,paint)
        paint.color=StudioColors.accent;c.drawLine(left,y,left+(right-left)*value,y,paint)
        paint.color=theme.text;c.drawCircle(left+(right-left)*value,y,7*d,paint)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{parent?.requestDisallowInterceptTouchEvent(true);isPressed=true}
            MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);isPressed=false;return true}
        }
        if(e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP)){
            value=((e.x-14*d)/(width-28*d).coerceAtLeast(1f)).coerceIn(0f,1f);changed(value);invalidate()
            if(e.actionMasked==MotionEvent.ACTION_UP){isPressed=false;parent?.requestDisallowInterceptTouchEvent(false);performClick()}
        };return true
    }
    override fun onKeyDown(code:Int,event:android.view.KeyEvent):Boolean{
        if(code==android.view.KeyEvent.KEYCODE_DPAD_LEFT || code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){value=(value+if(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT).01f else -.01f).coerceIn(0f,1f);changed(value);invalidate();return true};return super.onKeyDown(code,event)
    }
    override fun performClick():Boolean{super.performClick();return true}
    override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.SeekBar";info.rangeInfo=android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0f,100f,value*100);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)}
    override fun performAccessibilityAction(action:Int,args:android.os.Bundle?):Boolean{
        if(action==android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id){value=((args?.getFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?:0f)/100f).coerceIn(0f,1f);changed(value);invalidate();return true};return super.performAccessibilityAction(action,args)
    }
}
