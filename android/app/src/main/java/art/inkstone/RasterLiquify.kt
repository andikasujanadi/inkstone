package art.inkstone

import android.graphics.*
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

internal class RasterLiquify(private val canvas:PaintCanvas,private val session:DrawingSession){
    var mode="Push";var size=160f;var strength=.5f
    var active=false;private set
    private var ready=false;private var pointer=-1;private var gesture=false;private var stylus=false
    private var pending=JSONArray();private var starts=false;private var ends=false
    private var cursor:PointF?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val submit=Runnable{flush()}
    fun begin(){
        if(active)return;active=true;ready=false;session.liquifyEditing=true
        session.command("liquifyBegin"){result->ready=!result.has("error");if(!ready){active=false;session.liquifyEditing=false;session.tool="Paint"}}
    }
    private fun flush(){
        canvas.removeCallbacks(submit)
        if(pending.length()==0)return
        val points=pending;pending=JSONArray();session.previewLiquify(JSONObject().put("mode",mode).put("size",size).put("strength",strength).put("start",starts).put("end",ends).put("points",points));starts=false;ends=false
    }
    fun finish(cancel:Boolean=false){
        if(!active)return;flush();active=false;ready=false;cursor=null;pointer=-1;session.liquifyEditing=false
        session.command(if(cancel)"liquifyCancel"else"liquifyCommit"){canvas.onChanged()};canvas.invalidate()
    }
    fun reset(){finish(true);begin()}
    fun draw(c:Canvas,zoom:Float){cursor?.let{p->paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f*canvas.resources.displayMetrics.density/zoom;paint.color=StudioColors.accent;c.drawCircle(p.x,p.y,size/2,paint)}}
    fun touch(e:MotionEvent):Boolean{
        if(session.tool!="Liquify")return false
        if(e.actionMasked==MotionEvent.ACTION_POINTER_UP && stylus && e.getPointerId(e.actionIndex)!=pointer)return true
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN && stylus && pointer>=0)return true
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN){ends=true;flush();gesture=true;pointer=-1;cursor=null;return false}
        if(gesture){if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL)gesture=false;return false}
        val pen=e.getToolType(0) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER)
        if(session.penOnly && !pen)return false
        if(!active)begin()
        if(!ready)return true
        fun add(x:Float,y:Float,pressure:Float){val p=canvas.transformDocumentPoint(x,y);cursor=p;if(pending.length()<2048)pending.put(JSONArray().put(p.x).put(p.y).put(if(pen && session.pressureEnabled)pressure.coerceIn(0f,1f)else 1f))}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{stylus=pen;if(canvas.onDismissPanel())return true;canvas.onStrokeStart();pointer=e.getPointerId(0);starts=true;add(e.x,e.y,e.pressure);flush()}
            MotionEvent.ACTION_MOVE->{val i=e.findPointerIndex(pointer);if(i>=0){for(h in 0 until e.historySize)add(e.getHistoricalX(i,h),e.getHistoricalY(i,h),e.getHistoricalPressure(i,h));add(e.getX(i),e.getY(i),e.getPressure(i));canvas.removeCallbacks(submit);canvas.postOnAnimation(submit)}}
            MotionEvent.ACTION_UP->{if(pointer>=0){add(e.x,e.y,e.pressure);ends=true;flush()};pointer=-1;cursor=null;canvas.performClick()}
            MotionEvent.ACTION_CANCEL->{ends=true;flush();pointer=-1;cursor=null}
        };canvas.invalidate();return true
    }
}
