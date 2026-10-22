package art.inkstone

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

internal object StudioMotion {
    private val ease = PathInterpolator(.18f,.8f,.22f,1f)
    fun reveal(view:View, originX:Float=.5f) {
        view.animate().cancel()
        view.visibility=View.VISIBLE
        if(!ValueAnimator.areAnimatorsEnabled()) {reset(view);return}
        view.pivotX=view.layoutParams.width.coerceAtLeast(0)*originX
        view.pivotY=0f
        view.alpha=0f;view.scaleX=.96f;view.scaleY=.96f;view.translationY=-6*view.resources.displayMetrics.density
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(220).setInterpolator(ease).withLayer().start()
    }
    fun dismiss(view:View,done:()->Unit) {
        view.animate().cancel()
        fun disable(v:View){v.isEnabled=false;v.isClickable=false;if(v is ViewGroup)for(i in 0 until v.childCount)disable(v.getChildAt(i))}
        disable(view)
        if(!ValueAnimator.areAnimatorsEnabled()){done();return}
        view.animate().alpha(0f).scaleX(.97f).scaleY(.97f).translationY(-4*view.resources.displayMetrics.density)
            .setDuration(140).setInterpolator(ease).withLayer().withEndAction(done).start()
    }
    fun chrome(view:View,show:Boolean,x:Float=0f,y:Float=0f) {
        view.animate().cancel()
        if(!ValueAnimator.areAnimatorsEnabled()){reset(view);view.visibility=if(show)View.VISIBLE else View.INVISIBLE;return}
        if(show)view.visibility=View.VISIBLE
        view.animate().alpha(if(show)1f else 0f).translationX(if(show)0f else x).translationY(if(show)0f else y)
            .setDuration(240).setInterpolator(ease).withEndAction{if(!show)view.visibility=View.INVISIBLE}.start()
    }
    fun content(view:View){
        if(!ValueAnimator.areAnimatorsEnabled())return
        if(view is ViewGroup)for(i in 0 until view.childCount){val child=view.getChildAt(i);child.alpha=.35f;child.translationY=4*view.resources.displayMetrics.density
            child.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(ease).start()}
    }
    fun bounce(view:View){
        if(!ValueAnimator.areAnimatorsEnabled())return
        view.animate().cancel();view.scaleX=.94f;view.scaleY=.94f
        view.animate().scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
    }
    private fun reset(v:View){v.alpha=1f;v.scaleX=1f;v.scaleY=1f;v.translationX=0f;v.translationY=0f}
}
