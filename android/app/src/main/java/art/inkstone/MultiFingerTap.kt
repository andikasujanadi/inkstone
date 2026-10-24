package art.inkstone

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.hypot

internal class MultiFingerTap(private val slop:Float){
    private val starts=mutableMapOf<Int,PointF>()
    private var started=0L
    private var maximum=0
    private var eligible=false
    fun observe(e:MotionEvent):Int{
        if(e.actionMasked==MotionEvent.ACTION_DOWN){starts.clear();started=e.eventTime;maximum=0;eligible=true}
        if(e.actionMasked==MotionEvent.ACTION_CANCEL || e.flags and MotionEvent.FLAG_CANCELED!=0)eligible=false
        for(i in 0 until e.pointerCount){
            if(e.getToolType(i)!=MotionEvent.TOOL_TYPE_FINGER)eligible=false
            val p=starts.getOrPut(e.getPointerId(i)){PointF(e.getX(i),e.getY(i))}
            if(hypot(e.getX(i)-p.x,e.getY(i)-p.y)>slop)eligible=false
        }
        maximum=maxOf(maximum,e.pointerCount)
        if(e.actionMasked!=MotionEvent.ACTION_UP)return 0
        val result=if(eligible && e.eventTime-started<=500 && maximum in 2..4)maximum else 0
        eligible=false;starts.clear();return result
    }
}
