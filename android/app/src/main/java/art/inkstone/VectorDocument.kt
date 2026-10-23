package art.inkstone

import android.graphics.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.*

internal class VectorObject(
    val id:Long,var kind:String,val points:MutableList<PointF>,var stroke:Int,var fill:Int?,var width:Float,
    var matrix:Matrix=Matrix()
){
    private var cachedPath:Path?=null
    fun toBezier(){
        if(kind=="Line"){
            val a=points.first();val b=points.last()
            points.clear();points.addAll(listOf(PointF(a.x,a.y),PointF(a.x+(b.x-a.x)/3,a.y+(b.y-a.y)/3),PointF(a.x+(b.x-a.x)*2/3,a.y+(b.y-a.y)*2/3),PointF(b.x,b.y)))
            kind="Bezier";geometryChanged();return
        }
        if(kind!="Pen")return
        val raw=points.map{PointF(it.x,it.y)};val converted=mutableListOf(PointF(raw[0].x,raw[0].y));var start=raw[0]
        fun segment(control:PointF,end:PointF){
            converted.add(PointF(start.x+(control.x-start.x)*2/3,start.y+(control.y-start.y)*2/3))
            converted.add(PointF(end.x+(control.x-end.x)*2/3,end.y+(control.y-end.y)*2/3));converted.add(end);start=end
        }
        for(i in 1 until raw.size){val a=raw[i-1];val b=raw[i];segment(a,PointF((a.x+b.x)/2,(a.y+b.y)/2))}
        val end=if(raw.size==1)PointF(start.x+.001f,start.y)else raw.last();segment(PointF((start.x+end.x)/2,(start.y+end.y)/2),end)
        points.clear();points.addAll(converted);kind="Bezier";geometryChanged()
    }
    fun geometryChanged(){cachedPath=null}
    fun path():Path {
        cachedPath?.let{return it}
        val p=Path();val a=points.first();val b=points.last()
        when(kind){
            "Rectangle"->p.addRect(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y),Path.Direction.CW)
            "Ellipse"->p.addOval(RectF(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y)),Path.Direction.CW)
            "Bezier"->{p.moveTo(a.x,a.y);for(i in 1 until points.size step 3){val c=points[i];val d=points[i+1];val e=points[i+2];p.cubicTo(c.x,c.y,d.x,d.y,e.x,e.y)}}
            "Line"->{p.moveTo(a.x,a.y);p.lineTo(b.x,b.y)}
            else->{p.moveTo(a.x,a.y)
                if(points.size==1)p.lineTo(a.x+.001f,a.y)
                else {for(i in 1 until points.size){val prev=points[i-1];val q=points[i];p.quadTo(prev.x,prev.y,(prev.x+q.x)/2,(prev.y+q.y)/2)};p.lineTo(b.x,b.y)}
            }
        }
        cachedPath=p;return p
    }
    fun bounds():RectF=RectF().also{path().computeBounds(it,true);it.inset(-width/2,-width/2);matrix.mapRect(it)}
    fun hit(point:PointF,tolerance:Float):Boolean {
        val inverse=Matrix();if(!matrix.invert(inverse))return false
        val xy=floatArrayOf(point.x,point.y);inverse.mapPoints(xy)
        val paint=Paint().apply{style=if(fill!=null)Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE;strokeWidth=width+inverse.mapRadius(tolerance)*2;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
        val area=Path();paint.getFillPath(path(),area);val r=RectF();area.computeBounds(r,true)
        return Region().apply{setPath(area,Region(floor(r.left).toInt()-1,floor(r.top).toInt()-1,ceil(r.right).toInt()+1,ceil(r.bottom).toInt()+1))}.contains(xy[0].roundToInt(),xy[1].roundToInt())
    }
    fun json():JSONObject {
        val xy=JSONArray();points.forEach{xy.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble()))}
        val values=FloatArray(9);matrix.getValues(values)
        return JSONObject().put("id",id).put("kind",kind).put("points",xy).put("stroke",stroke.toLong() and 0xffffffffL)
            .put("fill",fill?.let{it.toLong() and 0xffffffffL}?:JSONObject.NULL).put("width",width.toDouble())
            .put("matrix",JSONArray(listOf(values[0],values[3],values[1],values[4],values[2],values[5]).map{it.toDouble()}))
    }
}
internal class VectorLayer(val id:Long,var name:String,var visible:Boolean=true,var locked:Boolean=false,var opacity:Float=1f,
    val objects:MutableList<VectorObject> = mutableListOf())

internal class VectorDocument(val width:Int,val height:Int,val dpi:Int=300,var background:Int=Color.WHITE,var backgroundVisible:Boolean=true){
    init{require(width in 16..8192 && height in 16..8192 && width.toLong()*height<=16_777_216 && dpi in 1..2400){"Invalid Vector canvas"}}
    var layers=mutableListOf(VectorLayer(1,"Layer 1"));private set
    var active=0
    private var nextId=2L
    private val undo=ArrayDeque<ByteArray>();private val redo=ArrayDeque<ByteArray>()
    val undoCount get()=undo.size
    val redoCount get()=redo.size
    fun nextId():Long{check(nextId<Long.MAX_VALUE-10000){"Vector identifier limit reached"};return nextId++}
    fun objectById(id:Long?)=layers.flatMap{it.objects}.firstOrNull{it.id==id}
    fun owner(id:Long?)=layers.firstOrNull{layer->layer.objects.any{it.id==id}}
    fun hit(point:PointF,tolerance:Float):VectorObject? {
        for(layer in layers.asReversed())if(layer.visible && layer.opacity>0)for(obj in layer.objects.asReversed())if(obj.hit(point,tolerance))return obj
        return null
    }
    fun encode():ByteArray {
        val data=JSONObject().put("mode","vector").put("version",2).put("width",width).put("height",height).put("dpi",dpi)
            .put("background",background.toLong() and 0xffffffffL).put("backgroundVisible",backgroundVisible).put("active",active)
        val list=JSONArray();for(layer in layers){val objects=JSONArray();layer.objects.forEach{objects.put(it.json())}
            list.put(JSONObject().put("id",layer.id).put("name",layer.name).put("visible",layer.visible).put("locked",layer.locked).put("opacity",layer.opacity.toDouble()).put("objects",objects))}
        return data.put("layers",list).toString().toByteArray(Charsets.UTF_8)
    }
    fun restore(bytes:ByteArray){
        val other=decode(bytes);require(other.width==width && other.height==height){"Document size changed"}
        layers=other.layers;active=other.active;background=other.background;backgroundVisible=other.backgroundVisible;nextId=other.nextId
    }
    fun edit(change:()->Unit){val before=encode();try{change();commit(before)}catch(e:Exception){restore(before);throw e}}
    fun commit(before:ByteArray){val after=encode();require(after.size<=MAX_BYTES){"Vector project is too large"}
        require(layers.size in 1..32 && active in layers.indices){"Invalid Vector layers"}
        require(layers.sumOf{it.objects.size}<=5000 && layers.sumOf{l->l.objects.sumOf{it.points.size}}<=250000){"Vector object/point limit reached"}
        for(layer in layers){require(layer.opacity.isFinite() && layer.opacity in 0f..1f)
            for(obj in layer.objects){require(obj.width.isFinite() && obj.width in .1f..1000f)
                require(obj.points.all{it.x.isFinite() && it.y.isFinite() && abs(it.x)<=1_000_000 && abs(it.y)<=1_000_000}){"Object is outside the supported coordinate range"}
                val m=FloatArray(9);obj.matrix.getValues(m);require(m.all{it.isFinite() && abs(it)<=1_000_000} && abs(m[0]*m[4]-m[1]*m[3])>=1e-8){"Object transform limit reached"}
            }
        }
        if(before.contentEquals(after))return
        undo.addLast(before);redo.clear();trim()
    }
    private fun trim(){while(undo.size+redo.size>64 || undo.sumOf{it.size.toLong()}+redo.sumOf{it.size.toLong()}>32L*1024*1024){if(undo.size>1)undo.removeFirst() else if(redo.isNotEmpty())redo.removeFirst() else break}}
    fun undo():Boolean{if(undo.isEmpty())return false;redo.addLast(encode());restore(undo.removeLast());trim();return true}
    fun redo():Boolean{if(redo.isEmpty())return false;undo.addLast(encode());restore(redo.removeLast());trim();return true}
    fun draw(canvas:Canvas,includeBackground:Boolean=true,onlyLayer:VectorLayer?=null){
        if(onlyLayer==null && includeBackground && backgroundVisible)canvas.drawColor(background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        for(layer in if(onlyLayer!=null)listOf(onlyLayer) else layers){
            if(onlyLayer==null && (!layer.visible || layer.opacity<=0))continue
            val save=if(onlyLayer==null && layer.opacity<1f)canvas.saveLayerAlpha(RectF(0f,0f,width.toFloat(),height.toFloat()),(layer.opacity*255).roundToInt()) else canvas.save()
            for(obj in layer.objects)drawObject(canvas,obj,paint)
            canvas.restoreToCount(save)
        }
    }
    fun bitmap(maxSide:Int=max(width,height),onlyLayer:VectorLayer?=null):Bitmap {
        val scale=min(1f,maxSide.toFloat()/max(width,height));val w=max(1,(width*scale).roundToInt());val h=max(1,(height*scale).roundToInt())
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{Canvas(it).apply{scale(scale,scale);draw(this,onlyLayer=onlyLayer)}}
    }
    fun svg():String {
        fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
        fun color(c:Int)="#"+Integer.toHexString(c and 0xffffff).padStart(6,'0')
        val out=StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${width}px\" height=\"${height}px\" viewBox=\"0 0 $width $height\" color-interpolation=\"sRGB\">\n")
        out.append("<g id=\"background\"${if(backgroundVisible)"" else " display=\"none\""}><rect width=\"$width\" height=\"$height\" fill=\"${color(background)}\" fill-opacity=\"${Color.alpha(background)/255f}\"/></g>\n")
        for(layer in layers){out.append("<g id=\"layer-${layer.id}\" opacity=\"${layer.opacity}\"${if(layer.visible)"" else " display=\"none\""}><title>${esc(layer.name)}</title>\n")
            for(obj in layer.objects){val m=FloatArray(9);obj.matrix.getValues(m)
                val attrs="id=\"object-${obj.id}\" transform=\"matrix(${m[0]} ${m[3]} ${m[1]} ${m[4]} ${m[2]} ${m[5]})\" stroke=\"${color(obj.stroke)}\" stroke-opacity=\"${Color.alpha(obj.stroke)/255f}\" stroke-width=\"${obj.width}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill=\"${obj.fill?.let{color(it)}?:"none"}\" fill-opacity=\"${obj.fill?.let{Color.alpha(it)/255f}?:1f}\""
                val a=obj.points.first();val b=obj.points.last()
                when(obj.kind){
                    "Rectangle"->out.append("<rect x=\"${min(a.x,b.x)}\" y=\"${min(a.y,b.y)}\" width=\"${abs(b.x-a.x)}\" height=\"${abs(b.y-a.y)}\" $attrs/>\n")
                    "Ellipse"->out.append("<ellipse cx=\"${(a.x+b.x)/2}\" cy=\"${(a.y+b.y)/2}\" rx=\"${abs(b.x-a.x)/2}\" ry=\"${abs(b.y-a.y)/2}\" $attrs/>\n")
                    else->{val d=StringBuilder("M${a.x} ${a.y}")
                        if(obj.kind=="Bezier")for(i in 1 until obj.points.size step 3){val c=obj.points[i];val e=obj.points[i+1];val f=obj.points[i+2];d.append(" C${c.x} ${c.y} ${e.x} ${e.y} ${f.x} ${f.y}")}
                        else if(obj.kind=="Line")d.append(" L${b.x} ${b.y}")
                        else if(obj.points.size==1)d.append(" L${a.x+.001f} ${a.y}")
                        else{for(i in 1 until obj.points.size){val p=obj.points[i-1];val q=obj.points[i];d.append(" Q${p.x} ${p.y} ${(p.x+q.x)/2} ${(p.y+q.y)/2}")};d.append(" L${b.x} ${b.y}")}
                        out.append("<path d=\"$d\" $attrs/>\n")
                    }
                }
            };out.append("</g>\n")
        };return out.append("</svg>\n").toString()
    }
    fun writePsd(file:java.io.File){
        require((layers.size.toLong()+1)*width*height<=67_108_864){"Too many rasterized layer pixels for PSD. Export SVG or reduce canvas size/layers."}
        val handle=NativeBridge.create(width,height);check(handle!=0L){"Could not create PSD renderer"}
        fun command(json:JSONObject){val r=JSONObject(NativeBridge.command(handle,json.toString()));check(!r.has("error")){r.optString("error")}}
        try{command(JSONObject().put("op","setup").put("dpi",dpi).put("color",background.toLong() and 0xffffffffL).put("visible",backgroundVisible))
            for((index,layer) in layers.withIndex()){
                val image=bitmap(onlyLayer=layer);val pixels=IntArray(width*height)
                try{image.getPixels(pixels,0,width,0,0,width,height)}finally{image.recycle()}
                val r=JSONObject(NativeBridge.importPixels(handle,width,height,pixels));check(!r.has("error")){r.optString("error")}
                if(index==0){command(JSONObject().put("op","active").put("index",0));command(JSONObject().put("op","delete"))}
                command(JSONObject().put("op","layer").put("name",layer.name).put("visible",layer.visible).put("locked",layer.locked).put("opacity",layer.opacity.toDouble()))
            }
            val result=JSONObject(NativeBridge.writePsd(handle,file.absolutePath));check(!result.has("error")){result.optString("error")}
        }finally{NativeBridge.destroy(handle)}
    }
    companion object {
        const val MAX_BYTES=16*1024*1024
        fun isVector(bytes:ByteArray)=runCatching{JSONObject(bytes.toString(Charsets.UTF_8)).optString("mode")=="vector"}.getOrDefault(false)
        fun drawObject(canvas:Canvas,obj:VectorObject,paint:Paint=Paint(Paint.ANTI_ALIAS_FLAG)){
            val save=canvas.save();canvas.concat(obj.matrix);paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            obj.fill?.let{paint.style=Paint.Style.FILL;paint.color=it;canvas.drawPath(obj.path(),paint)}
            paint.style=Paint.Style.STROKE;paint.strokeWidth=obj.width;paint.color=obj.stroke;canvas.drawPath(obj.path(),paint);canvas.restoreToCount(save)
        }
        fun decode(bytes:ByteArray):VectorDocument{
            require(bytes.size<=MAX_BYTES){"Vector project exceeds 16 MB"};val json=JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.getString("mode")=="vector" && json.getInt("version")==2){"Unsupported Vector document"}
            val w=json.getInt("width");val h=json.getInt("height");require(w in 16..8192 && h in 16..8192 && w.toLong()*h<=16_777_216){"Invalid canvas size"}
            val dpi=json.getInt("dpi");require(dpi in 1..2400)
            val doc=VectorDocument(w,h,dpi,json.getLong("background").toInt(),json.getBoolean("backgroundVisible"));val list=json.getJSONArray("layers")
            require(list.length() in 1..32);doc.layers.clear();val ids=HashSet<Long>();var points=0;var objects=0
            fun number(v:Double):Float{require(v.isFinite() && abs(v)<=1_000_000){"Invalid Vector coordinates"};return v.toFloat()}
            for(i in 0 until list.length()){val l=list.getJSONObject(i);val id=l.getLong("id");require(id in 1 until Long.MAX_VALUE-10000 && ids.add(id));
                val opacity=number(l.getDouble("opacity"));require(opacity in 0f..1f)
                val layer=VectorLayer(id,l.getString("name").take(100),l.getBoolean("visible"),l.getBoolean("locked"),opacity)
                val os=l.getJSONArray("objects");objects+=os.length();require(objects<=5000)
                for(j in 0 until os.length()){val o=os.getJSONObject(j);val oid=o.getLong("id");require(oid in 1 until Long.MAX_VALUE-10000 && ids.add(oid));val kind=o.getString("kind");require(kind in listOf("Pen","Bezier","Line","Rectangle","Ellipse"))
                    val xy=o.getJSONArray("points");require(xy.length() in 1..12289 && when(kind){"Pen"->xy.length()<=4096;"Bezier"->xy.length()>=4 && xy.length()%3==1;else->xy.length()==2});points+=xy.length();require(points<=250000)
                    val coords=mutableListOf<PointF>();for(k in 0 until xy.length()){val p=xy.getJSONArray(k);require(p.length()==2);coords.add(PointF(number(p.getDouble(0)),number(p.getDouble(1))))}
                    val width=number(o.getDouble("width"));require(width in .1f..1000f)
                    val values=o.getJSONArray("matrix");require(values.length()==6);val m=FloatArray(6){number(values.getDouble(it))};require(abs(m[0]*m[3]-m[1]*m[2])>=1e-8){"Singular object transform"}
                    val matrix=Matrix().apply{setValues(floatArrayOf(m[0],m[2],m[4],m[1],m[3],m[5],0f,0f,1f))}
                    layer.objects.add(VectorObject(oid,kind,coords,o.getLong("stroke").toInt(),if(o.isNull("fill"))null else o.getLong("fill").toInt(),width,matrix))
                };doc.layers.add(layer)
            }
            doc.active=json.getInt("active");require(doc.active in doc.layers.indices);doc.nextId=ids.max()+1;return doc
        }
    }
}
