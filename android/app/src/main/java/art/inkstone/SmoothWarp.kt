package art.inkstone

import android.graphics.PointF

/** Interpolates the 4×4 handles; mirrored edge samples keep straight edges straight. */
internal object SmoothWarp {
    const val STEPS = 24
    private fun cubic(a:Float,b:Float,c:Float,d:Float,t:Float):Float =
        b + .5f*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)))

    fun point(handles:List<PointF>,u:Float,v:Float):PointF {
        require(handles.size==16)
        fun coordinate(x:Int,y:Int,axis:Int):Float {
            fun row(y0:Int):Float {
                fun value(x0:Int)=handles[y0*4+x0].let{if(axis==0)it.x else it.y}
                return when {x<0->2*value(0)-value(1);x>3->2*value(3)-value(2);else->value(x)}
            }
            return when {y<0->2*row(0)-row(1);y>3->2*row(3)-row(2);else->row(y)}
        }
        val x=u.coerceIn(0f,3f).toInt().coerceAtMost(2);val y=v.coerceIn(0f,3f).toInt().coerceAtMost(2)
        fun axis(a:Int):Float {
            val rows=FloatArray(4){j->cubic(coordinate(x-1,y+j-1,a),coordinate(x,y+j-1,a),coordinate(x+1,y+j-1,a),coordinate(x+2,y+j-1,a),u-x)}
            return cubic(rows[0],rows[1],rows[2],rows[3],v-y)
        }
        return PointF(axis(0),axis(1))
    }
    fun grid(handles:List<PointF>):List<PointF> = (0..STEPS).flatMap{y->(0..STEPS).map{x->point(handles,x*3f/STEPS,y*3f/STEPS)}}
}
