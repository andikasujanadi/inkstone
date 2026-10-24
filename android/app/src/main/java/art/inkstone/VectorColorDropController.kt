package art.inkstone

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.hypot

internal class VectorColorDropController(private val root:FrameLayout,private val canvas:VectorCanvas){
    private val density=root.resources.displayMetrics.density
    private var cursorX=0f;private var cursorY=0f;private var downX=0f;private var downY=0f
    private var dragging=false;private var color=0;private var source:View?=null
    private val preview=object:View(root.context){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){if(dragging){
            paint.style=Paint.Style.FILL;paint.color=0x44000000;c.drawCircle(cursorX,cursorY+2*density,18*density,paint)
            paint.color=color;c.drawCircle(cursorX,cursorY,16*density,paint)
            paint.style=Paint.Style.STROKE;paint.color=Color.WHITE;paint.strokeWidth=2*density;c.drawCircle(cursorX,cursorY,16*density,paint)
        }}
    }.apply{tag="vector-colordrop-preview";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    init{root.addView(preview,FrameLayout.LayoutParams(-1,-1))}
    fun bind(control:View,tap:()->Unit){control.tooltipText="Colors · drag to fill a closed path";control.setOnTouchListener{_,e->
        val from=IntArray(2);val to=IntArray(2);control.getLocationInWindow(from);root.getLocationInWindow(to)
        cursorX=from[0]-to[0]+e.x;cursorY=from[1]-to[1]+e.y
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{cancel();source=control;color=canvas.ink;downX=cursorX;downY=cursorY;control.isPressed=true;control.animate().cancel();control.scaleX=1f;control.scaleY=1f}
            MotionEvent.ACTION_MOVE->{
                if(!dragging && hypot(cursorX-downX,cursorY-downY)>9*density){dragging=true;control.alpha=0f;canvas.cancelGesture();canvas.onDismissPanel();preview.bringToFront();root.parent?.requestDisallowInterceptTouchEvent(true)}
                preview.invalidate()
            }
            MotionEvent.ACTION_UP->{
                if(dragging){if(cursorX>=canvas.left && cursorX<canvas.right && cursorY>=canvas.top && cursorY<canvas.bottom)canvas.fillAt(cursorX-canvas.left,cursorY-canvas.top,color)}else tap()
                cancel()
            }
            MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN->cancel()
        };true
    }}
    fun cancel(){source?.alpha=1f;source?.isPressed=false;source=null;dragging=false;preview.invalidate()}
}
