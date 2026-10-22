package art.inkstone

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

class PaintCanvas(context: Context, val session: DrawingSession) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val transform = Matrix()
    private val inverse = Matrix()
    private var zoom = 1f
    private var rotation = 0f
    private var panX = 0f; private var panY = 0f
    private var fitted = false
    private var activePointer = -1
    private var drawing = false
    private var pen = false
    private var multi = false
    private var maxPointers = 0
    private var downTime = 0L
    private var downX = 0f; private var downY = 0f
    private var moved = false
    private var gestureX = 0f; private var gestureY = 0f
    private var gestureDistance = 0f; private var gestureAngle = 0f
    private var gestureA = -1; private var gestureB = -1
    private var hover: PointF? = null
    private val selectionPoints = mutableListOf<PointF>()
    private var selectionPath: Path? = null
    private var transformStart: PointF? = null
    private var transformDelta = PointF()
    private var picking = false
    private var pickPoint: PointF? = null
    var selectionShape = "Freehand"
    var onDismissPanel: () -> Boolean = { false }
    var onChanged: () -> Unit = {}
    var onStrokeStart: () -> Unit = {}
    var onColor: (Int) -> Unit = {}
    var onToggleInterface: () -> Unit = {}
    var onGesture: (String) -> Unit = {}
    var onInputDevice: (MotionEvent) -> Unit = {}
    private val shapePoints=mutableListOf<PointF>()
    private var snapped=false
    private var holdX=0f;private var holdY=0f
    private val shapeHold=Runnable {
        if(drawing && !multi && !snapped && session.quickShape && session.tool=="Paint"){
            QuickShape.fit(shapePoints.filterIndexed{i,_->i%max(1,shapePoints.size/256)==0})?.let{fit->
                session.stroke(3,floatArrayOf())
                val old=session.stabilization;session.stabilization=0f
                session.stroke(0,fit.samples.copyOfRange(0,4))
                session.stroke(1,fit.samples.copyOfRange(4,fit.samples.size))
                session.stabilization=old;snapped=true;onGesture(fit.name+" created")
            }
        }
    }
    private fun recordShape(samples:FloatArray,x:Float,y:Float){
        if(!session.quickShape||session.tool!="Paint"||snapped)return
        for(i in samples.indices step 4){val p=PointF(samples[i],samples[i+1]);if(shapePoints.isEmpty()||hypot(p.x-shapePoints.last().x,p.y-shapePoints.last().y)>1f)shapePoints.add(p)}
        if(shapePoints.size>4096){val reduced=shapePoints.filterIndexed{i,_->i%2==0};shapePoints.clear();shapePoints.addAll(reduced)}
        if(hypot(x-holdX,y-holdY)>3*dp){holdX=x;holdY=y;removeCallbacks(shapeHold);postDelayed(shapeHold,650)}
    }
    private val longPress = Runnable {
        if (session.holdEyedropper && !multi && !pen && !moved && activePointer >= 0 && session.tool == "Paint") {
            session.stroke(3, floatArrayOf()); drawing = false; picking = true
            pick(downX, downY)
        }
    }
    private val dp get() = resources.displayMetrics.density

    init {
        isFocusable = true; contentDescription = "Drawing canvas. Two fingers pan, pinch and rotate. Two-finger tap undo; three-finger tap redo."
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun fit() {
        if (width == 0 || height == 0) return
        zoom = min((width - 100 * dp) / session.canvasWidth, (height - 70 * dp) / session.canvasHeight).coerceAtLeast(.02f)
        rotation = 0f; panX = 0f; panY = 0f; fitted = true; invalidate()
    }
    fun deselect() { selectionPath = null; selectionPoints.clear(); invalidate() }
    fun zoomBy(factor: Float) { zoom = (zoom * factor).coerceIn(.02f, 30f); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { if (!fitted || oldw != w || oldh != h) fit() }
    private fun updateMatrix() {
        session.viewZoom=zoom
        transform.reset(); transform.postTranslate(-session.canvasWidth / 2f, -session.canvasHeight / 2f)
        transform.postScale(zoom, zoom); transform.postRotate(rotation)
        transform.postTranslate(width / 2f + panX, height / 2f + panY)
        transform.invert(inverse)
    }
    private fun documentPoint(x: Float, y: Float): PointF {
        updateMatrix(); val p = floatArrayOf(x, y); inverse.mapPoints(p); return PointF(p[0], p[1])
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (session.lightInterface) 0xffb5b5b5.toInt() else 0xff292929.toInt())
        paint.style=Paint.Style.STROKE;paint.strokeWidth=dp*.65f
        paint.color=if(session.lightInterface)0x14000000 else 0x10ffffff
        val spacing=16*dp
        var gx=0f;while(gx<width){canvas.drawLine(gx,0f,gx,height.toFloat(),paint);gx+=spacing}
        var gy=0f;while(gy<height){canvas.drawLine(0f,gy,width.toFloat(),gy,paint);gy+=spacing}
        updateMatrix(); canvas.save(); canvas.concat(transform)
        paint.color = 0xffe0e0e0.toInt(); paint.style = Paint.Style.FILL; paint.shader = null
        val w = session.canvasWidth.toFloat(); val h = session.canvasHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, paint)
        if (!session.info.optBoolean("backgroundVisible", true)) {
            val tile = 24f
            paint.color = 0xffb5b5b5.toInt()
            var y = 0f
            while (y < h) { var x = 0f; while (x < w) {
                if (((x / tile).toInt() + (y / tile).toInt()) % 2 == 0) canvas.drawRect(x, y, min(x + tile, w), min(y + tile, h), paint)
                x += tile
            }; y += tile }
        }
        canvas.drawBitmap(session.bitmap, 0f, 0f, paint)
        if (session.guide) {
            paint.color = 0x554a92cc; paint.strokeWidth = 1f / zoom
            var x = 0f; while (x <= w) { canvas.drawLine(x, 0f, x, h, paint); x += 80f }
            var y = 0f; while (y <= h) { canvas.drawLine(0f, y, w, y, paint); y += 80f }
        }
        if (selectionPath != null) {
            paint.color=Color.argb((session.selectionVisibility*255).toInt(),70,90,100)
            paint.style=Paint.Style.FILL;canvas.drawPath(selectionPath!!,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * dp / zoom
            paint.pathEffect = DashPathEffect(floatArrayOf(5 * dp / zoom, 5 * dp / zoom), 0f)
            paint.color = Color.WHITE; canvas.drawPath(selectionPath!!, paint)
            paint.color = Color.BLACK; paint.pathEffect = DashPathEffect(floatArrayOf(5 * dp / zoom, 5 * dp / zoom), 5 * dp / zoom)
            canvas.drawPath(selectionPath!!, paint); paint.pathEffect = null; paint.style = Paint.Style.FILL
        }
        if (session.tool == "Transform") {
            paint.style = Paint.Style.STROKE; paint.color = 0xff0a84ff.toInt(); paint.strokeWidth = dp / zoom
            paint.pathEffect = DashPathEffect(floatArrayOf(6 * dp / zoom, 4 * dp / zoom), 0f)
            val rect = RectF(transformDelta.x, transformDelta.y, w + transformDelta.x, h + transformDelta.y)
            canvas.drawRect(rect, paint); paint.pathEffect = null; paint.style = Paint.Style.FILL
            for (x in listOf(rect.left, rect.centerX(), rect.right)) for (y in listOf(rect.top, rect.bottom)) {
                canvas.drawCircle(x, y, 4 * dp / zoom, paint)
            }
        }
        canvas.restore()
        if (hover != null && session.cursor) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp
            paint.color = if(session.cursorStyle=="Dark")0xff333333.toInt() else 0xffeeeeee.toInt();paint.alpha=(session.cursorOpacity*255).toInt(); canvas.drawCircle(hover!!.x, hover!!.y, session.size * (if(session.dynamicBrushScaling)zoom else 1f) * .5f + dp, paint)
            paint.color = if(session.cursorStyle=="Light")0xffeeeeee.toInt() else 0xff333333.toInt();paint.alpha=(session.cursorOpacity*255).toInt(); canvas.drawCircle(hover!!.x, hover!!.y, session.size * (if(session.dynamicBrushScaling)zoom else 1f) * .5f, paint)
            paint.alpha=255;paint.style = Paint.Style.FILL
        }
        pickPoint?.let {
            paint.color = session.color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 10 * dp
            canvas.drawCircle(it.x, it.y - 36 * dp, 28 * dp, paint)
            paint.color = Color.WHITE; paint.strokeWidth = 2 * dp
            canvas.drawCircle(it.x, it.y - 36 * dp, 33 * dp, paint); paint.style = Paint.Style.FILL
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        hover = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else PointF(event.x, event.y)
        onInputDevice(event); invalidate(); return true
    }
    private fun pick(x: Float, y: Float) {
        val p = documentPoint(x, y); pickPoint = PointF(x, y)
        session.command("pick", JSONObject().put("x", p.x).put("y", p.y)) {
            if (it.has("color")) { session.color = it.getLong("color").toInt(); onColor(session.color); invalidate() }
        }
    }
    private fun strokeSamples(event: MotionEvent, index: Int, includeHistory: Boolean): FloatArray {
        val samples = mutableListOf<Float>()
        val maxPressure = event.device?.getMotionRange(MotionEvent.AXIS_PRESSURE, event.source)?.max?.coerceAtLeast(1f) ?: 1f
        fun add(x: Float, y: Float, pressure: Float, tilt: Float) {
            val p = documentPoint(x, y)
            samples.add(p.x); samples.add(p.y); samples.add(if (pen && session.pressureEnabled) (pressure / maxPressure).coerceIn(0f, 1f) else 1f); samples.add(tilt)
        }
        if (includeHistory) for (i in 0 until event.historySize) {
            add(event.getHistoricalX(index, i), event.getHistoricalY(index, i), event.getHistoricalPressure(index, i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, i))
        }
        add(event.getX(index), event.getY(index), event.getPressure(index), event.getAxisValue(MotionEvent.AXIS_TILT, index))
        return samples.toFloatArray()
    }
    private fun startGesture(event: MotionEvent, exclude: Int = -1) {
        val indexes = (0 until event.pointerCount).filter { it != exclude }
        if (indexes.size < 2) { gestureA = -1; gestureB = -1; return }
        val a = indexes[0]; val b = indexes[1]; gestureA = event.getPointerId(a); gestureB = event.getPointerId(b)
        gestureX = (event.getX(a) + event.getX(b)) * .5f; gestureY = (event.getY(a) + event.getY(b)) * .5f
        val dx = event.getX(b) - event.getX(a); val dy = event.getY(b) - event.getY(a)
        gestureDistance = hypot(dx, dy); gestureAngle = atan2(dy, dx)
    }
    private fun moveGesture(event: MotionEvent) {
        if(session.canvasLocked){moved=true;return}
        val a = event.findPointerIndex(gestureA); val b = event.findPointerIndex(gestureB)
        if (a < 0 || b < 0) { startGesture(event); return }
        val x = (event.getX(a) + event.getX(b)) * .5f; val y = (event.getY(a) + event.getY(b)) * .5f
        val dx = event.getX(b) - event.getX(a); val dy = event.getY(b) - event.getY(a)
        val distance = hypot(dx, dy); val angle = atan2(dy, dx)
        if (hypot(x - gestureX, y - gestureY) > 2 * dp || abs(distance - gestureDistance) > 2 * dp || abs(angle - gestureAngle) > .015f) moved = true
        val anchor = documentPoint(gestureX, gestureY)
        if (gestureDistance > 10 * dp) zoom = (zoom * distance / gestureDistance).coerceIn(.02f, 30f)
        var delta = angle - gestureAngle
        if (delta > Math.PI) delta -= (2 * Math.PI).toFloat(); if (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
        rotation += delta * 180f / Math.PI.toFloat()
        updateMatrix(); val mapped = floatArrayOf(anchor.x, anchor.y); transform.mapPoints(mapped)
        panX += x - mapped[0]; panY += y - mapped[1]
        gestureX = x; gestureY = y; gestureDistance = distance; gestureAngle = angle; invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(shapeHold);shapePoints.clear();snapped=false;holdX=event.x;holdY=event.y
                requestFocus()
                if (onDismissPanel()) return true
                downTime = SystemClock.uptimeMillis(); downX = event.x; downY = event.y; moved = false; multi = false; maxPointers = 1
                activePointer = event.getPointerId(0); pen = event.getToolType(0) in listOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
                onInputDevice(event)
                if (!pen && !session.fingerDrawing && session.tool in listOf("Paint", "Erase", "Smudge")) return true
                if(session.info.getJSONArray("layers").getJSONObject(session.info.getInt("active")).optBoolean("locked") && session.tool in listOf("Paint","Smudge","Erase","Fill","Transform")){onGesture("Layer is locked");return true}
                when (session.tool) {
                    "Select" -> { selectionPoints.clear(); selectionPoints.add(documentPoint(event.x, event.y)); updateSelectionPath() }
                    "Transform" -> { transformStart = documentPoint(event.x, event.y); transformDelta = PointF() }
                    "Eyedropper" -> { picking = true; pick(event.x, event.y) }
                    "Fill" -> { val p = documentPoint(event.x, event.y); session.command("flood", JSONObject().put("x", p.x).put("y", p.y).put("color", session.color.toLong() and 0xffffffffL)); onChanged() }
                    else -> {
                        drawing = true
                        onStrokeStart()
                        session.stroke(0, strokeSamples(event, 0, false), event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || (session.penButtonErase && event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)))
                        recordShape(strokeSamples(event,0,false),event.x,event.y)
                        postDelayed(longPress, 550)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(shapeHold)
                if (pen && drawing) return true
                removeCallbacks(longPress); if (drawing) { session.stroke(3, floatArrayOf()); drawing = false }
                multi = true; picking = false; pickPoint = null; maxPointers = max(maxPointers, event.pointerCount)
                startGesture(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (multi) { moveGesture(event); return true }
                val index = event.findPointerIndex(activePointer); if (index < 0) return true
                val x = event.getX(index); val y = event.getY(index)
                if (hypot(x - downX, y - downY) > 5 * dp) { moved = true; removeCallbacks(longPress) }
                when {
                    picking -> pick(x, y)
                    session.tool == "Select" -> { selectionPoints.add(documentPoint(x, y)); updateSelectionPath() }
                    session.tool == "Transform" -> transformStart?.let { val p = documentPoint(x, y); transformDelta = PointF(p.x - it.x, p.y - it.y); invalidate() }
                    drawing && !snapped -> {val samples=strokeSamples(event,index,true);session.stroke(1,samples,event.getToolType(index)==MotionEvent.TOOL_TYPE_ERASER || (session.penButtonErase && event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)));recordShape(samples,x,y)}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                removeCallbacks(shapeHold)
                if (pen && drawing && event.getPointerId(event.actionIndex) == activePointer) {
                    session.stroke(2, if(snapped)floatArrayOf() else strokeSamples(event, event.actionIndex, false), event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_ERASER)
                    drawing = false; onChanged()
                } else if (multi) startGesture(event, event.actionIndex)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(shapeHold)
                removeCallbacks(longPress)
                if (multi) {
                    if (!moved && SystemClock.uptimeMillis() - downTime < 330) {
                        when (maxPointers) { 2 -> { if(session.twoFingerUndo){session.command("undo"); onGesture("Undo")} }; 3 -> { if(session.threeFingerRedo){session.command("redo"); onGesture("Redo")} }; 4 -> if(session.fourFingerFullScreen)onToggleInterface() }
                        onChanged()
                    }
                } else if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    if (drawing) session.stroke(3, floatArrayOf()); drawing = false
                } else when {
                    picking -> { picking = false; pickPoint = null; if (session.tool == "Eyedropper") session.tool = "Paint"; onChanged() }
                    session.tool == "Select" && selectionPoints.size > 1 -> {
                        val points = JSONArray(); selectionPoints.forEach { points.put(JSONArray().put(it.x).put(it.y)) }
                        session.command("select", JSONObject().put("shape", selectionShape).put("points", points)); onChanged()
                    }
                    session.tool == "Transform" -> {
                        session.command("transform", JSONObject().put("dx", transformDelta.x).put("dy", transformDelta.y))
                        transformStart = null; transformDelta = PointF(); deselect(); onChanged()
                    }
                    drawing -> { session.stroke(2, if(snapped)floatArrayOf() else strokeSamples(event, 0, false), event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || (session.penButtonErase && event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY))); onChanged() }
                }
                drawing = false; activePointer = -1; multi = false; pickPoint = null; invalidate(); performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(shapeHold)
                removeCallbacks(longPress); if (drawing) { session.stroke(3, floatArrayOf()); onChanged() }
                drawing = false; activePointer = -1; multi = false; picking = false; pickPoint = null; invalidate()
            }
        }
        return true
    }
    private fun updateSelectionPath() {
        if (selectionPoints.isEmpty()) return
        val first = selectionPoints.first(); val last = selectionPoints.last()
        selectionPath = Path().apply {
            when (selectionShape) {
                "Rectangle" -> addRect(min(first.x, last.x), min(first.y, last.y), max(first.x, last.x), max(first.y, last.y), Path.Direction.CW)
                "Ellipse" -> addOval(RectF(min(first.x, last.x), min(first.y, last.y), max(first.x, last.x), max(first.y, last.y)), Path.Direction.CW)
                else -> { moveTo(first.x, first.y); selectionPoints.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            }
        }; invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
