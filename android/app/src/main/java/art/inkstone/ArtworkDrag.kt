package art.inkstone

import android.animation.ValueAnimator
import android.content.ClipData
import android.graphics.PointF
import android.view.DragEvent
import android.view.View

internal object ArtworkDrag {
    data class Token(val owner:Any,val ids:List<String>,val primary:String)
    fun source(view:View,owner:Any,id:String,selected:()->List<String>){
        view.tooltipText="Hold and drag into any stack. Tap ⋯ for artwork options."
        view.setOnLongClickListener{val ids=selected().takeIf{id in it}?:listOf(id);it.startDragAndDrop(ClipData.newPlainText("Inkstone artwork",id),View.DragShadowBuilder(it),Token(owner,ids,id),0)}
    }
    fun target(view:View,owner:Any,drop:(Token,PointF)->Unit){
        var pulse:ValueAnimator?=null;val glow=LayerDrag.DropGlow(view.resources.displayMetrics.density).apply{join=true}
        fun stop(){pulse?.cancel();pulse=null;view.foreground=null}
        view.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){};override fun onViewDetachedFromWindow(v:View){stop()}})
        view.setOnDragListener{_,event->val token=event.localState as? Token;if(token==null || token.owner!==owner)false else{
            when(event.action){
                DragEvent.ACTION_DRAG_ENTERED->{view.foreground=glow;pulse=ValueAnimator.ofFloat(.25f,1f).apply{duration=850;repeatMode=ValueAnimator.REVERSE;repeatCount=ValueAnimator.INFINITE;addUpdateListener{glow.pulse=it.animatedValue as Float;glow.invalidateSelf()};start()}}
                DragEvent.ACTION_DROP->{val at=IntArray(2);view.getLocationOnScreen(at);stop();drop(token,PointF(at[0]+event.x,at[1]+event.y))}
                DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->stop()
            };true
        }}
    }
}
