package art.inkstone

import android.graphics.*
import android.os.Build
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

internal data class TransformPlane(val image:Bitmap?,val rect:RectF)
internal class TransformSnapshot(val info:JSONObject,val active:Int,val bounds:RectF,val planes:List<TransformPlane>){
    fun recycle(){planes.forEach{it.image?.recycle()}}
    companion object{
        fun decode(info:JSONObject,data:IntArray):TransformSnapshot{
            require(data.size>=6);val count=data[0];require(count in 1..64 && data[1] in 0 until count)
            val planes=mutableListOf<TransformPlane>();var offset=6
            try{repeat(count+1){
                require(offset+6<=data.size);val rect=RectF(data[offset].toFloat(),data[offset+1].toFloat(),data[offset+2].toFloat(),data[offset+3].toFloat())
                val w=data[offset+4];val h=data[offset+5];offset+=6;require(w>=0 && h>=0 && w.toLong()*h<=6_000_000 && offset.toLong()+w.toLong()*h<=data.size)
                val image=if(w>0 && h>0)Bitmap.createBitmap(data,offset,w,w,h,Bitmap.Config.ARGB_8888)else null;offset+=w*h;planes.add(TransformPlane(image,rect))
            };require(offset==data.size)
                return TransformSnapshot(info,data[1],RectF(data[2].toFloat(),data[3].toFloat(),data[4].toFloat(),data[5].toFloat()),planes)
            }catch(e:Exception){planes.forEach{it.image?.recycle()};throw e}
        }
    }
}

internal class RasterTransform(private val canvas:PaintCanvas,private val session:DrawingSession){
    private var snapshot:TransformSnapshot?=null
    private var generation=0
    private var loading=false
    private var committing=false
    private var awaitingFrame=false
    private val matrix=Matrix()
    private var initial=Matrix()
    private var gestureBefore=Matrix()
    private var gesture=""
    private var pointerA=-1;private var pointerB=-1
    private var pen=false
    private var origin=PointF();private var localOrigin=PointF();private var anchor=PointF();private var corner=PointF()
    private var handle=-1
    private var pairCenter=PointF();private var pairDistance=0f;private var pairAngle=0f;private var accumulatedAngle=0f
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ready get()=snapshot!=null && !committing
    val displaying get()=snapshot!=null
    val bounds get()=snapshot?.bounds?.let{r->RectF().also{matrix.mapRect(it,r)}}
    internal fun currentMatrix()=Matrix(matrix)
    fun ensure(){
        if(session.tool!="Transform" || loading || snapshot!=null || committing)return
        if(session.info.getJSONArray("layers").getJSONObject(session.info.getInt("active")).optBoolean("locked")){canvas.onGesture("This layer is locked");return}
        loading=true;session.transformEditing=true;val token=++generation
        session.transformSnapshot{value->
            if(token!=generation || session.tool!="Transform"){value?.recycle();return@transformSnapshot}
            loading=false
            if(value==null || value.bounds.isEmpty){session.transformEditing=false;value?.recycle();canvas.onGesture("The active layer has no selected pixels to move");return@transformSnapshot}
            snapshot=value;matrix.reset();canvas.invalidate()
        }
    }
    fun finish(cancel:Boolean=false){
        if(committing)return
        val s=snapshot
        if(loading){generation++;loading=false}
        session.transformInteracting=false;session.transformEditing=false;gesture="";pointerA=-1;pointerB=-1
        if(s==null)return
        if(cancel || matrix.isIdentity){dispose();canvas.invalidate();return}
        val values=FloatArray(9);matrix.getValues(values)
        committing=true
        session.command("transformMatrix",JSONObject().put("matrix",JSONArray(listOf(values[0],values[3],values[1],values[4],values[2],values[5]).map{it.toDouble()}))){result->
            if(result.has("error")){dispose();canvas.invalidate()}else awaitingFrame=true
        }
    }
    fun onFrame(){if(awaitingFrame){dispose();canvas.deselect();canvas.invalidate();if(session.tool=="Transform")ensure()}}
    fun dispose(){session.transformEditing=false;session.transformInteracting=false;generation++;snapshot?.recycle();snapshot=null;loading=false;committing=false;awaitingFrame=false;matrix.reset()}
    private fun layerSource(c:Canvas,index:Int,s:TransformSnapshot){
        val plane=s.planes[index];plane.image?.let{c.drawBitmap(it,null,plane.rect,p)}
        if(index==s.active){val moving=s.planes.last();moving.image?.let{val save=c.save();c.concat(matrix);c.drawBitmap(it,null,moving.rect,p);c.restoreToCount(save)}}
    }
    private fun blend(name:String,opacity:Float):Paint=Paint().apply{
        alpha=(opacity*255).roundToInt().coerceIn(0,255)
        if(Build.VERSION.SDK_INT>=29)blendMode=when(name){
            "Multiply"->BlendMode.MULTIPLY;"Screen"->BlendMode.SCREEN;"Overlay"->BlendMode.OVERLAY;"Hard Light"->BlendMode.HARD_LIGHT
            "Darken"->BlendMode.DARKEN;"Lighten"->BlendMode.LIGHTEN;"Difference"->BlendMode.DIFFERENCE;"Exclusion"->BlendMode.EXCLUSION
            "Colour Burn"->BlendMode.COLOR_BURN;"Colour Dodge"->BlendMode.COLOR_DODGE;"Add"->BlendMode.PLUS;else->BlendMode.SRC_OVER
        }else xfermode=PorterDuffXfermode(when(name){"Multiply"->PorterDuff.Mode.MULTIPLY;"Screen"->PorterDuff.Mode.SCREEN;"Overlay"->PorterDuff.Mode.OVERLAY;"Darken"->PorterDuff.Mode.DARKEN;"Lighten"->PorterDuff.Mode.LIGHTEN;"Add"->PorterDuff.Mode.ADD;else->PorterDuff.Mode.SRC_OVER})
    }
    fun draw(c:Canvas):Boolean{
        val s=snapshot?:return false;val page=RectF(0f,0f,session.canvasWidth.toFloat(),session.canvasHeight.toFloat())
        if(s.info.optBoolean("backgroundVisible",true)){p.color=s.info.getLong("background").toInt();c.drawRect(page,p)}
        val clipped=c.save();c.clipRect(page);val layers=s.info.getJSONArray("layers");var base=-1
        for(i in 0 until layers.length()){
            val layer=layers.getJSONObject(i);if(!layer.optBoolean("clipping"))base=i
            if(!layer.getBoolean("visible"))continue
            val save=c.saveLayer(page,blend(layer.getString("blend"),layer.getDouble("opacity").toFloat()))
            layerSource(c,i,s)
            if(layer.optBoolean("clipping")){
                val mask=c.saveLayer(page,Paint().apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)})
                if(base>=0 && layers.getJSONObject(base).getBoolean("visible"))layerSource(c,base,s)
                c.restoreToCount(mask)
            }
            c.restoreToCount(save)
        };c.restoreToCount(clipped);return true
    }
    private fun points():List<PointF>{val r=snapshot?.bounds?:return emptyList();return listOf(PointF(r.left,r.top),PointF(r.right,r.top),PointF(r.right,r.bottom),PointF(r.left,r.bottom),PointF(r.centerX(),r.top),PointF(r.right,r.centerY()),PointF(r.centerX(),r.bottom),PointF(r.left,r.centerY()))}
    private fun mapped():List<PointF> = points().map{v->val a=floatArrayOf(v.x,v.y);matrix.mapPoints(a);PointF(a[0],a[1])}
    private fun rotationHandle():PointF{
        val r=snapshot!!.bounds;val q=floatArrayOf(r.centerX(),r.centerY(),r.centerX(),r.top);matrix.mapPoints(q)
        val length=hypot(q[2]-q[0],q[3]-q[1]).coerceAtLeast(.001f);val extra=30*canvas.resources.displayMetrics.density/canvas.transformZoom
        return PointF(q[2]+(q[2]-q[0])/length*extra,q[3]+(q[3]-q[1])/length*extra)
    }
    fun drawHandles(c:Canvas){
        if(snapshot==null)return;val q=mapped();val d=canvas.resources.displayMetrics.density/canvas.transformZoom
        p.color=StudioColors.accent;p.style=Paint.Style.STROKE;p.strokeWidth=1.3f*d;p.pathEffect=DashPathEffect(floatArrayOf(6*d,4*d),0f)
        c.drawPath(Path().apply{moveTo(q[0].x,q[0].y);for(i in 1..3)lineTo(q[i].x,q[i].y);close()},p);p.pathEffect=null
        val rotate=rotationHandle();c.drawLine(q[4].x,q[4].y,rotate.x,rotate.y,p)
        for(v in q+rotate){p.style=Paint.Style.FILL;p.color=Color.WHITE;c.drawCircle(v.x,v.y,5*d,p);p.style=Paint.Style.STROKE;p.color=StudioColors.accent;c.drawCircle(v.x,v.y,5*d,p)}
        p.style=Paint.Style.FILL
    }
    private fun valid(candidate:Matrix):Boolean{val m=FloatArray(9);candidate.getValues(m);return m.all{it.isFinite() && abs(it)<=1_000_000} && abs(m[0]*m[4]-m[1]*m[3])>1e-6}
    private fun replace(candidate:Matrix){if(valid(candidate)){matrix.set(candidate);canvas.invalidate()}}
    fun scale(factor:Float){ensure();val s=snapshot?:return;val r=RectF();matrix.mapRect(r,s.bounds);replace(Matrix(matrix).apply{postScale(factor,factor,r.centerX(),r.centerY())});canvas.onChanged()}
    fun operation(key:String,value:Float){ensure();val s=snapshot?:return;val r=RectF();matrix.mapRect(r,s.bounds)
        replace(Matrix(matrix).apply{when(key){"sx"->postScale(value,1f,r.centerX(),r.centerY());"sy"->postScale(1f,value,r.centerX(),r.centerY());"angle"->postRotate(value*180f/Math.PI.toFloat(),r.centerX(),r.centerY())}});canvas.onChanged()
    }
    private fun startPair(e:MotionEvent,exclude:Int=-1){
        val ids=(0 until e.pointerCount).filter{it!=exclude};if(ids.size<2)return
        pointerA=e.getPointerId(ids[0]);pointerB=e.getPointerId(ids[1]);val a=canvas.transformDocumentPoint(e.getX(ids[0]),e.getY(ids[0]));val b=canvas.transformDocumentPoint(e.getX(ids[1]),e.getY(ids[1]))
        pairCenter=PointF((a.x+b.x)/2,(a.y+b.y)/2);pairDistance=hypot(b.x-a.x,b.y-a.y);pairAngle=atan2(b.y-a.y,b.x-a.x);accumulatedAngle=0f;initial=Matrix(matrix);gesture="pair"
    }
    fun touch(e:MotionEvent):Boolean{
        if(!ready){ensure();if(e.actionMasked==MotionEvent.ACTION_DOWN)canvas.onGesture(if(committing)"Applying transform…"else"Preparing transform…");return true}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{canvas.onStrokeStart();session.transformInteracting=true;pen=e.getToolType(0) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER);pointerA=e.getPointerId(0);pointerB=-1;origin=canvas.transformDocumentPoint(e.x,e.y);initial=Matrix(matrix);gestureBefore=Matrix(matrix)
                val radius=22*canvas.resources.displayMetrics.density/canvas.transformZoom;val q=mapped();handle=q.indices.minByOrNull{hypot(q[it].x-origin.x,q[it].y-origin.y)}?.takeIf{hypot(q[it].x-origin.x,q[it].y-origin.y)<=radius}?:-1
                val rotate=rotationHandle();gesture=if(handle>=0)"resize"else if(hypot(rotate.x-origin.x,rotate.y-origin.y)<=radius)"rotate"else"move"
                val r=snapshot!!.bounds;anchor=PointF(r.centerX(),r.centerY())
                if(handle>=0){val ps=points();corner=ps[handle];anchor=ps[if(handle<4)(handle+2)%4 else 4+(handle-4+2)%4];val inverse=Matrix();initial.invert(inverse);val xy=floatArrayOf(origin.x,origin.y);inverse.mapPoints(xy);localOrigin=PointF(xy[0],xy[1])}
                else if(gesture=="rotate"){val xy=floatArrayOf(anchor.x,anchor.y);initial.mapPoints(xy);anchor=PointF(xy[0],xy[1])}
            }
            MotionEvent.ACTION_POINTER_DOWN->if(!pen && pointerB<0)startPair(e)
            MotionEvent.ACTION_MOVE->{
                val a=e.findPointerIndex(pointerA);if(a<0)return true;val at=canvas.transformDocumentPoint(e.getX(a),e.getY(a))
                when(gesture){
                    "pair"->{val b=e.findPointerIndex(pointerB);if(b<0)return true;val bt=canvas.transformDocumentPoint(e.getX(b),e.getY(b));val center=PointF((at.x+bt.x)/2,(at.y+bt.y)/2);val distance=hypot(bt.x-at.x,bt.y-at.y);val angle=atan2(bt.y-at.y,bt.x-at.x)
                        var delta=angle-pairAngle;while(delta>Math.PI)delta-=(2*Math.PI).toFloat();while(delta< -Math.PI)delta+=(2*Math.PI).toFloat();accumulatedAngle+=delta;pairAngle=angle
                        val scale=if(pairDistance>.001f)(distance/pairDistance).coerceIn(.02f,50f)else 1f
                        val change=Matrix().apply{setScale(scale,scale,pairCenter.x,pairCenter.y);postRotate(accumulatedAngle*180f/Math.PI.toFloat(),pairCenter.x,pairCenter.y);postTranslate(center.x-pairCenter.x,center.y-pairCenter.y)}
                        replace(Matrix(initial).apply{postConcat(change)})
                    }
                    "move"->replace(Matrix(initial).apply{postTranslate(at.x-origin.x,at.y-origin.y)})
                    "rotate"->{val angle=(atan2(at.y-anchor.y,at.x-anchor.x)-atan2(origin.y-anchor.y,origin.x-anchor.x))*180f/Math.PI.toFloat();replace(Matrix(initial).apply{postRotate(angle,anchor.x,anchor.y)})}
                    "resize"->{val inverse=Matrix();initial.invert(inverse);val xy=floatArrayOf(at.x,at.y);inverse.mapPoints(xy);val x=corner.x+xy[0]-localOrigin.x;val y=corner.y+xy[1]-localOrigin.y
                        var sx=1f;var sy=1f
                        if(handle<4){val dx=corner.x-anchor.x;val dy=corner.y-anchor.y;val v=(((x-anchor.x)*dx+(y-anchor.y)*dy)/(dx*dx+dy*dy).coerceAtLeast(.001f)).coerceIn(.02f,50f);sx=v;sy=v}
                        else if(handle==5 || handle==7)sx=((x-anchor.x)/(corner.x-anchor.x)).coerceIn(.02f,50f)
                        else sy=((y-anchor.y)/(corner.y-anchor.y)).coerceIn(.02f,50f)
                        replace(Matrix(initial).apply{preScale(sx,sy,anchor.x,anchor.y)})
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP->{if(pen && e.getPointerId(e.actionIndex)!=pointerA)return true
                val last=MotionEvent.obtain(e);last.action=MotionEvent.ACTION_MOVE;touch(last);last.recycle()
                val remaining=(0 until e.pointerCount).filter{it!=e.actionIndex}
                if(remaining.size>=2)startPair(e,e.actionIndex)else if(remaining.size==1){val i=remaining[0];pointerA=e.getPointerId(i);pointerB=-1;origin=canvas.transformDocumentPoint(e.getX(i),e.getY(i));initial=Matrix(matrix);gesture="move"}
            }
            MotionEvent.ACTION_UP->{val last=MotionEvent.obtain(e);last.action=MotionEvent.ACTION_MOVE;touch(last);last.recycle();session.transformInteracting=false;gesture="";pointerA=-1;pointerB=-1;canvas.onChanged();canvas.performClick()}
            MotionEvent.ACTION_CANCEL->{replace(gestureBefore);session.transformInteracting=false;gesture="";pointerA=-1;pointerB=-1;canvas.onChanged()}
        };return true
    }
}
