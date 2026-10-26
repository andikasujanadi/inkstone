package art.inkstone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

internal class GalleryMotion private constructor(private val images:MutableMap<String,Pair<Bitmap,Rect>>,private val target:String?){
    private val running=mutableListOf<ValueAnimator>()
    private val overlays=mutableListOf<BitmapDrawable>()
    private var finished=false
    private var detach:View.OnAttachStateChangeListener?=null
    fun cancel(root:View){
        if(finished)return;finished=true;running.toList().forEach{it.cancel()};overlays.forEach{root.overlay.remove(it)};images.clear();overlays.clear();running.clear();detach?.let{root.removeOnAttachStateChangeListener(it)};cards(root).values.forEach{it.animate().cancel();it.alpha=1f;it.translationY=0f;it.scaleX=1f;it.scaleY=1f}
    }
    fun play(root:View){
        detach=object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){};override fun onViewDetachedFromWindow(v:View){cancel(v)}}.also{root.addOnAttachStateChangeListener(it)}
        root.viewTreeObserver.addOnPreDrawListener(object:android.view.ViewTreeObserver.OnPreDrawListener{override fun onPreDraw():Boolean{
            if(root.viewTreeObserver.isAlive)root.viewTreeObserver.removeOnPreDrawListener(this)
            if(finished)return true
            val current=cards(root);val destination=target?.let{find(root,"gallery:$it")}?.let{rect(it,root)}
            images.forEach{(key,data)->
                val (bitmap,start)=data;val view=current[key];val end=view?.let{rect(it,root)}?:destination?:Rect(start).apply{offset(0,-(12*root.resources.displayMetrics.density).toInt())}
                view?.alpha=0f
                val overlay=BitmapDrawable(root.resources,bitmap).apply{bounds=start};overlays.add(overlay);root.overlay.add(overlay)
                val animation=ValueAnimator.ofFloat(0f,1f).apply{
                    duration=380;interpolator=DecelerateInterpolator(1.5f)
                    addUpdateListener{value->val t=value.animatedValue as Float;overlay.bounds=Rect(lerp(start.left,end.left,t),lerp(start.top,end.top,t),lerp(start.right,end.right,t),lerp(start.bottom,end.bottom,t));overlay.alpha=if(view==null)((1-t)*255).toInt()else((1-((t-.65f)/.35f).coerceIn(0f,1f))*255).toInt();view?.alpha=((t-.65f)/.35f).coerceIn(0f,1f)}
                    addListener(object:AnimatorListenerAdapter(){override fun onAnimationEnd(a:Animator){root.overlay.remove(overlay);view?.alpha=1f}})
                };running.add(animation);animation.start()
            }
            current.filter{it.key !in images && it.value.getGlobalVisibleRect(Rect())}.values.forEachIndexed{i,view->view.alpha=0f;view.translationY=12*root.resources.displayMetrics.density;view.animate().alpha(1f).translationY(0f).setStartDelay((i.coerceAtMost(6)*18).toLong()).setDuration(280).setInterpolator(DecelerateInterpolator()).withEndAction{view.animate().setStartDelay(0)}.start()}
            root.postDelayed({cancel(root)},480);return true
        }})
    }
    companion object{
        private fun lerp(a:Int,b:Int,t:Float)=(a+(b-a)*t).toInt()
        private fun rect(v:View,root:View):Rect{val at=IntArray(2);val origin=IntArray(2);v.getLocationOnScreen(at);root.getLocationOnScreen(origin);return Rect(at[0]-origin[0],at[1]-origin[1],at[0]-origin[0]+v.width,at[1]-origin[1]+v.height)}
        private fun find(v:View,tag:String):View?{if(v.tag==tag)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),tag)?.let{return it};return null}
        private fun cards(root:View):Map<String,View>{val out=linkedMapOf<String,View>();fun visit(v:View){val tag=v.tag as? String;if(tag?.startsWith("gallery:art:")==true || tag?.startsWith("gallery:stack:")==true)out[tag.removePrefix("gallery:")]=v;if(v is ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))};visit(root);return out}
        fun capture(root:View,target:String?=null,release:Pair<String,PointF>?=null):GalleryMotion{
            val images=linkedMapOf<String,Pair<Bitmap,Rect>>()
            cards(root).forEach{(key,view)->if(view.width>0 && view.height>0 && view.getGlobalVisibleRect(Rect())){
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val r=rect(view,root)
                if(release?.first==key){val origin=IntArray(2);root.getLocationOnScreen(origin);val x=release.second.x-origin[0];val y=release.second.y-origin[1];r.offsetTo((x-r.width()/2f).toInt(),(y-r.height()/2f).toInt())}
                images[key]=bitmap to r
            }};return GalleryMotion(images,target)
        }
    }
}
