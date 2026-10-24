package art.inkstone

import android.content.ClipData
import android.graphics.*
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import kotlin.math.*

internal data class PaletteColorDrag(val color:Int)

internal fun bindPaletteDrag(source:View,color:()->Int){
    val slop=8*source.resources.displayMetrics.density
    var x=0f;var y=0f;var dragging=false
    source.tooltipText="Drag this color onto your palette"
    source.isClickable=true;source.isFocusable=true
    source.setOnTouchListener{v,e->
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;dragging=false;v.isPressed=true;true}
            MotionEvent.ACTION_MOVE->{
                if(!dragging && hypot(e.x-x,e.y-y)>slop){
                    val ink=color();dragging=v.startDragAndDrop(ClipData.newPlainText("Inkstone color",String.format("#%06X",ink and 0xffffff)),View.DragShadowBuilder(v),PaletteColorDrag(ink),0)
                    if(dragging){v.isPressed=false;v.alpha=.3f}
                };true
            }
            MotionEvent.ACTION_UP->{v.isPressed=false;if(!dragging)v.performClick();true}
            MotionEvent.ACTION_CANCEL->{v.isPressed=false;if(!dragging)v.alpha=1f;true}
            else->true
        }
    }
    source.setOnDragListener{v,e->if(e.action==DragEvent.ACTION_DRAG_ENDED){v.alpha=1f;dragging=false};e.localState is PaletteColorDrag}
}

internal class PaletteGrid(
    context:android.content.Context,colors:List<Int>,pick:(Int)->Unit,replace:(Int)->Unit,drop:(Int)->Unit
):FrameLayout(context){
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var targeted=false
    init{
        tag="palette-drop-target";contentDescription="Palette · drop a color here to add it"
        setWillNotDraw(false);setPadding((12*density).roundToInt(),0,(12*density).roundToInt(),0)
        val grid=GridLayout(context).apply{columnCount=6}
        colors.forEachIndexed{index,color->
            val cell=object:View(context){override fun onMeasure(w:Int,h:Int){super.onMeasure(w,h);setMeasuredDimension(measuredWidth,measuredWidth)}}.apply{
                setBackgroundColor(color);contentDescription=String.format("Color #%06X",color and 0xffffff)
                isClickable=true;isFocusable=true;tooltipText="Tap to use · hold to replace with active color"
                setOnClickListener{pick(color)};setOnLongClickListener{replace(index);true}
            }
            grid.addView(cell,GridLayout.LayoutParams().apply{width=0;height=0;columnSpec=GridLayout.spec(index%6,1f);rowSpec=GridLayout.spec(index/6);setMargins(0,0,0,0)})
        }
        minimumHeight=(48*density).roundToInt();addView(grid,LayoutParams(-1,-2))
        if(colors.isEmpty())addView(android.widget.TextView(context).apply{text="Drag a color here to start your palette";textSize=13f;gravity=android.view.Gravity.CENTER;setTextColor(0xffb9c4cc.toInt())},LayoutParams(-1,(64*density).roundToInt()))
        setOnDragListener{_,e->
            val data=e.localState as? PaletteColorDrag
            if(data==null)false else{
                when(e.action){
                    DragEvent.ACTION_DRAG_ENTERED->{targeted=true;invalidate()}
                    DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->{targeted=false;invalidate()}
                    DragEvent.ACTION_DROP->{targeted=false;invalidate();drop(data.color)}
                };true
            }
        }
    }
    override fun dispatchDraw(canvas:Canvas){
        super.dispatchDraw(canvas)
        if(targeted){paint.color=StudioColors.accent;paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;canvas.drawRect(paddingLeft.toFloat()+density,density,width-paddingRight-density,height-density,paint)}
    }
}
