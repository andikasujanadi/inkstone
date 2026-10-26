package art.inkstone

import android.graphics.PointF
import kotlin.math.*

// Anchor picking adapted from VectorCraft direct.rs at 125df6d8 (MIT).
// Copyright (c) 2026 ArtCraft Team and the VectorCraft contributors.
// License and adaptation notes: third_party/vectorcraft/.
internal object VectorGeometry {
    data class Hit(val objectId:Long,val index:Int)
    fun anchors(obj:VectorObject):List<Pair<Int,PointF>> = when(obj.kind){
        "Bezier"->obj.points.mapIndexedNotNull{i,p->if(i%3==0)i to p else null}
        "Pen"->buildList {
            add(0 to obj.points.first())
            for(i in 1 until obj.points.size){val a=obj.points[i-1];val b=obj.points[i];add(i*3 to PointF((a.x+b.x)/2,(a.y+b.y)/2))}
            add(obj.points.size*3 to obj.points.last())
        }
        else->obj.points.mapIndexed{i,p->i to p}
    }
    fun mapped(obj:VectorObject,p:PointF):PointF{val xy=floatArrayOf(p.x,p.y);obj.matrix.mapPoints(xy);return PointF(xy[0],xy[1])}
    fun nearestAnchor(obj:VectorObject,p:PointF,tolerance:Float):Int? = anchors(obj)
        .map{(i,a)->i to mapped(obj,a)}.filter{(_,a)->hypot(a.x-p.x,a.y-p.y)<=tolerance}
        .minByOrNull{(_,a)->hypot(a.x-p.x,a.y-p.y)}?.first
    fun hitAnchor(doc:VectorDocument,selectedId:Long?,p:PointF,tolerance:Float):Hit?{
        val owners=doc.layers.asReversed().filter{it.effectiveVisible && it.opacity>0}.flatMap{it.objects.asReversed()}
            .sortedBy{it.id!=selectedId}
        return owners.firstNotNullOfOrNull{obj->nearestAnchor(obj,p,tolerance)?.let{Hit(obj.id,it)}}
    }
    private fun lerp(a:PointF,b:PointF,t:Float)=PointF(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
    fun cubic(a:PointF,b:PointF,c:PointF,d:PointF,t:Float):PointF = lerp(lerp(lerp(a,b,t),lerp(b,c,t),t),lerp(lerp(b,c,t),lerp(c,d,t),t),t)
    fun insertAnchor(obj:VectorObject,page:PointF,tolerance:Float):Int {
        obj.toBezier();require(obj.kind=="Bezier"){"Choose a line or pen path"}
        require(obj.points.size+3<=12289){"This path has reached its point limit"}
        var segment=-1;var parameter=0f;var distance=Float.POSITIVE_INFINITY
        for(i in 0 until obj.points.lastIndex step 3){
            val a=obj.points[i];val b=obj.points[i+1];val c=obj.points[i+2];val d=obj.points[i+3]
            fun score(t:Float):Float{val p=mapped(obj,cubic(a,b,c,d,t));return (p.x-page.x).pow(2)+(p.y-page.y).pow(2)}
            var sample=0;var best=Float.POSITIVE_INFINITY
            for(j in 0..64){val score=score(j/64f);if(score<best){best=score;sample=j}}
            var lo=max(0f,(sample-1)/64f);var hi=min(1f,(sample+1)/64f)
            repeat(18){val l=(lo*2+hi)/3;val r=(lo+hi*2)/3;if(score(l)<score(r))hi=r else lo=l}
            val t=(lo+hi)/2;val score=score(t)
            if(score<distance){distance=score;segment=i;parameter=t}
        }
        require(segment>=0 && distance<=tolerance*tolerance){"Tap closer to the selected path"}
        require(parameter in .005f.. .995f){"Tap between existing points"}
        val i=segment;val t=parameter;val a=obj.points[i];val b=obj.points[i+1];val c=obj.points[i+2];val d=obj.points[i+3]
        // De Casteljau subdivision preserves the curve while introducing one editable anchor.
        val ab=lerp(a,b,t);val bc=lerp(b,c,t);val cd=lerp(c,d,t);val left=lerp(ab,bc,t);val right=lerp(bc,cd,t);val anchor=lerp(left,right,t)
        repeat(3){obj.points.removeAt(i+1)}
        obj.points.addAll(i+1,listOf(ab,left,anchor,right,cd,d));obj.geometryChanged();return i+3
    }
    fun simplify(points:List<PointF>,tolerance:Float):MutableList<PointF>{
        if(points.size<3)return points.map{PointF(it.x,it.y)}.toMutableList()
        val keep=BooleanArray(points.size);keep[0]=true;keep[points.lastIndex]=true
        val pending=java.util.ArrayDeque<Pair<Int,Int>>();pending.add(0 to points.lastIndex)
        while(pending.isNotEmpty()){
            val (a,b)=pending.removeLast();val p=points[a];val q=points[b];val dx=q.x-p.x;val dy=q.y-p.y;val norm=dx*dx+dy*dy
            var far=tolerance*tolerance;var index=-1
            for(i in a+1 until b){val r=points[i];val t=if(norm>0)((r.x-p.x)*dx+(r.y-p.y)*dy)/norm else 0f;val u=t.coerceIn(0f,1f);val d=(r.x-p.x-u*dx).pow(2)+(r.y-p.y-u*dy).pow(2)
                if(d>far){far=d;index=i}}
            if(index>=0){keep[index]=true;pending.add(a to index);pending.add(index to b)}
        }
        return points.filterIndexed{i,_->keep[i]}.map{PointF(it.x,it.y)}.toMutableList()
    }
}
