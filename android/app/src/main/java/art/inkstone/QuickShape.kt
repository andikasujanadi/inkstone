package art.inkstone

import android.graphics.PointF
import kotlin.math.*

internal object QuickShape {
    data class Fit(val name:String,val samples:FloatArray)
    fun fit(points:List<PointF>):Fit? {
        if(points.size<8)return null
        val cx=points.map{it.x.toDouble()}.average().toFloat();val cy=points.map{it.y.toDouble()}.average().toFloat()
        val xx=points.sumOf{((it.x-cx)*(it.x-cx)).toDouble()};val yy=points.sumOf{((it.y-cy)*(it.y-cy)).toDouble()}
        val xy=points.sumOf{((it.x-cx)*(it.y-cy)).toDouble()}
        val angle=(atan2(2*xy,xx-yy)/2).toFloat();val co=cos(angle);val si=sin(angle)
        fun projected(p:PointF)=PointF((p.x-cx)*co+(p.y-cy)*si,-(p.x-cx)*si+(p.y-cy)*co)
        val local=points.map(::projected)
        val left=local.minOf{it.x};val right=local.maxOf{it.x};val top=local.minOf{it.y};val bottom=local.maxOf{it.y}
        val a=(right-left)/2;val b=(bottom-top)/2;val span=max(a,b)*2
        if(span<12)return null
        val first=points.first();val last=points.last();val distance=hypot(last.x-first.x,last.y-first.y)
        fun output(name:String,vertices:List<PointF>):Fit=Fit(name,FloatArray(vertices.size*4).also{out->vertices.forEachIndexed{i,p->out[i*4]=p.x;out[i*4+1]=p.y;out[i*4+2]=1f}})
        if(b/a.coerceAtLeast(1f)<.08f && distance>span*.65f)return output("Line",listOf(first,last))
        if(distance>span*.3f||min(a,b)<5)return null
        val mx=(left+right)/2;val my=(top+bottom)/2
        // Compare ellipse and four-sided fits in a rotated coordinate system.
        val ellipseError=local.map{abs(hypot((it.x-mx)/a,(it.y-my)/b)-1f)}.average()
        val rectError=local.map{min(abs(abs(it.x-mx)/a-1),abs(abs(it.y-my)/b-1))}.average()
        fun world(x:Float,y:Float)=PointF(cx+x*co-y*si,cy+x*si+y*co)
        var bestError=Double.MAX_VALUE;var bestCorners:List<PointF>?=null;var bestSquare=false
        for(step in 0 until 90){
            val t=step*Math.PI/180;val c=cos(t).toFloat();val sn=sin(t).toFloat()
            val projected=points.map{PointF((it.x-cx)*c+(it.y-cy)*sn,-(it.x-cx)*sn+(it.y-cy)*c)}
            val l=projected.minOf{it.x};val r=projected.maxOf{it.x};val u=projected.minOf{it.y};val v=projected.maxOf{it.y}
            val halfW=(r-l)/2;val halfH=(v-u)/2
            if(min(halfW,halfH)<5)continue
            val error=projected.map{min(abs(abs(it.x-(l+r)/2)/halfW-1),abs(abs(it.y-(u+v)/2)/halfH-1))}.average()
            if(error<bestError){bestError=error;bestSquare=abs(halfW-halfH)/max(halfW,halfH)<.12f
                fun corner(x:Float,y:Float)=PointF(cx+x*c-y*sn,cy+x*sn+y*c)
                bestCorners=listOf(corner(l,u),corner(r,u),corner(r,v),corner(l,v),corner(l,u))
            }
        }
        if(bestError<.09 && bestError<ellipseError*.7 && bestCorners!=null)return output(if(bestSquare)"Square" else "Rectangle",bestCorners)
        if(ellipseError>.19)return null
        val circle=abs(a-b)/max(a,b)<.12f;val rx=if(circle)(a+b)/2 else a;val ry=if(circle)rx else b
        return output(if(circle)"Circle" else "Ellipse",(0..160).map{val t=it*2*Math.PI/160;world(mx+rx*cos(t).toFloat(),my+ry*sin(t).toFloat())})
    }
}
