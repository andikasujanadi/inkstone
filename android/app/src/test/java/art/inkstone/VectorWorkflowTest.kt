package art.inkstone

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class VectorWorkflowTest{
    private fun until(test:()->Boolean){val end=System.nanoTime()+25_000_000_000;while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)};error("Workflow timed out")}
    private fun named(v:View,s:String):View?{if(v.contentDescription==s || v is TextView && v.text.toString()==s)return v;if(v is ViewGroup)for(i in 0 until v.childCount)named(v.getChildAt(i),s)?.let{return it};return null}
    private fun click(v:View,s:String){assertTrue(checkNotNull(named(v,s)){"Missing $s"}.performClick());shadowOf(Looper.getMainLooper()).idle()}
    private fun render(v:View,name:String){shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700));v.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,1280,800);val image=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);v.draw(Canvas(image));File(System.getProperty("inkstone.artifacts"),"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
    private fun input(v:View,action:Int,ids:List<Int>,points:List<PointF>,time:Long,tool:Int=MotionEvent.TOOL_TYPE_FINGER){
        val props=Array(ids.size){i->MotionEvent.PointerProperties().apply{id=ids[i];toolType=tool}}
        val coords=Array(ids.size){i->MotionEvent.PointerCoords().apply{x=points[i].x;y=points[i].y;pressure=1f;size=.05f}}
        val event=MotionEvent.obtain(time-10,time,action,ids.size,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);v.dispatchTouchEvent(event);event.recycle()
    }
    private fun touch(v:View,action:Int,p:PointF)=input(v,action,listOf(0),listOf(p),android.os.SystemClock.uptimeMillis())
    private fun tap(v:View,count:Int,jitter:Float=0f,cancel:Boolean=false){
        val t=android.os.SystemClock.uptimeMillis();val points=(0 until count).map{PointF(350f+it*75,300f+it*8)}
        touch(v,MotionEvent.ACTION_DOWN,points[0]);for(i in 1 until count)input(v,MotionEvent.ACTION_POINTER_DOWN or (i shl 8),(0..i).toList(),points.take(i+1),t+i*55)
        input(v,MotionEvent.ACTION_MOVE,(0 until count).toList(),points.map{PointF(it.x+jitter,it.y)},t+180)
        if(cancel){input(v,MotionEvent.ACTION_CANCEL,(0 until count).toList(),points,t+200);return}
        // Lift the first finger first so pointer indexes shift, as they do on a tablet.
        for(i in 0 until count-1)input(v,MotionEvent.ACTION_POINTER_UP,(i until count).toList(),points.drop(i),t+220+i*30)
        input(v,MotionEvent.ACTION_UP,listOf(count-1),listOf(points.last()),t+340)
    }
    private fun done(c:org.robolectric.android.controller.ActivityController<MainActivity>){val s=c.get().session;c.pause().stop().destroy();until{s==null || s.fullyClosed};until{c.get().io.isTerminated}}
    @Test fun vectorSupportsMoreThanThirtyTwoLayersAndReloadsThem(){
        val doc=VectorDocument(320,240)
        repeat(80){val before=doc.encode();val id=doc.nextId();doc.layers.add(VectorLayer(id,"Layer $id"));doc.active=doc.layers.lastIndex;doc.commit(before)}
        val loaded=VectorDocument.decode(doc.encode());assertEquals(81,loaded.layers.size);assertEquals(80,loaded.active)
        assertTrue(doc.undo());assertEquals(80,doc.layers.size);assertTrue(doc.redo());assertEquals(81,doc.layers.size)
    }
    @Test fun vectorColorDropFillsClosedFreehandAndKeepsUndoAndSvg(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,mode="vector");val e=a.vectorEditor!!;val doc=a.vectorDocument!!;val canvas=e.canvas
        val obj=VectorObject(doc.nextId(),"Pen",mutableListOf(PointF(60f,40f),PointF(230f,40f),PointF(230f,190f),PointF(60f,190f)),Color.BLACK,null,3f)
        doc.edit{doc.layers[0].objects.add(obj)};canvas.changeTool("Nodes");canvas.selectedId=obj.id;canvas.onSelection();render(e,"vector-fill-before")
        click(e,"Close path");assertTrue(obj.closed);canvas.ink=0xffbe4279.toInt();canvas.onSelection()
        val control=checkNotNull(named(e,"Colors"));val loc=IntArray(2);val origin=IntArray(2);control.getLocationInWindow(loc);e.getLocationInWindow(origin)
        val start=PointF(loc[0]-origin[0]+control.width/2f,loc[1]-origin[1]+control.height/2f);val target=canvas.viewPoint(150f,120f).apply{offset(canvas.left.toFloat(),canvas.top.toFloat())}
        touch(e,MotionEvent.ACTION_DOWN,start);touch(e,MotionEvent.ACTION_MOVE,target);assertEquals(0f,control.alpha,0f);render(e,"vector-color-drag")
        touch(e,MotionEvent.ACTION_UP,target);assertEquals(canvas.ink,obj.fill);assertEquals(1f,control.alpha,0f)
        val b=doc.bitmap();assertEquals(canvas.ink,b.getPixel(150,120));b.recycle();assertTrue(doc.svg().contains(" Z\""));assertTrue(VectorDocument.decode(doc.encode()).layers[0].objects[0].closed)
        tap(canvas,2,4f);assertNull(doc.objectById(obj.id)!!.fill);tap(canvas,3,4f);assertEquals(canvas.ink,doc.objectById(obj.id)!!.fill)
        render(e,"vector-filled-freehand");done(c)
    }
    @Test fun joinsTransformedEndpointsAsOneEditablePath(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,mode="vector");val e=a.vectorEditor!!;val doc=a.vectorDocument!!;val canvas=e.canvas
        val first=VectorObject(doc.nextId(),"Line",mutableListOf(PointF(30f,40f),PointF(140f,40f)),Color.BLACK,null,3f)
        val second=VectorObject(doc.nextId(),"Line",mutableListOf(PointF(0f,0f),PointF(0f,100f)),Color.BLUE,null,3f,Matrix().apply{postTranslate(150f,40f)})
        doc.edit{doc.layers[0].objects.addAll(listOf(first,second))};canvas.changeTool("Nodes");canvas.selectedId=first.id;canvas.onSelection();render(e,"vector-join-before")
        click(e,"Join ends");val at=canvas.viewPoint(150f,40f);touch(canvas,MotionEvent.ACTION_DOWN,at);touch(canvas,MotionEvent.ACTION_UP,at)
        assertEquals(1,doc.layers[0].objects.size);assertEquals("Bezier",first.kind);assertEquals(150f,first.points.last().x,.001f);assertEquals(140f,first.points.last().y,.001f);assertEquals(0,(first.points.size-1)%3)
        canvas.undo();assertEquals(2,doc.layers[0].objects.size);canvas.redo();assertEquals(1,doc.layers[0].objects.size);done(c)
    }
    @Test fun vectorDropRejectsOpenAndLockedPathsAndRespectsTransforms(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,mode="vector");val canvas=a.vectorEditor!!.canvas;val doc=a.vectorDocument!!;render(a.vectorEditor!!,"vector-fill-hit-before")
        val obj=VectorObject(doc.nextId(),"Pen",mutableListOf(PointF(0f,0f),PointF(80f,0f),PointF(80f,80f),PointF(0f,80f)),Color.BLACK,null,3f,Matrix().apply{postTranslate(100f,60f)})
        doc.edit{doc.layers[0].objects.add(obj)};val at=canvas.viewPoint(145f,95f);assertFalse(canvas.fillAt(at.x,at.y,Color.RED));assertNull(obj.fill)
        obj.closed=true;obj.geometryChanged();doc.layers[0].locked=true;assertFalse(canvas.fillAt(at.x,at.y,Color.RED));doc.layers[0].locked=false
        assertTrue(canvas.fillAt(at.x,at.y,Color.RED));assertEquals(Color.RED,obj.fill);done(c)
    }
    @Test fun rasterAndVectorRecognizeTwoThreeFingerTapsWithoutTreatingPanAsUndo(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(64,64);val s=a.session!!;val canvas=a.editor!!.canvas;render(a.editor!!,"gesture-raster-before")
        s.command("fill",JSONObject().put("color",0xff2367a9L));until{s.info.optInt("undo")==1}
        for(penOnly in listOf(false,true)){s.penOnly=penOnly;tap(canvas,2,4f);until{s.info.optInt("redo")==1};tap(canvas,3,4f);until{s.info.optInt("redo")==0 && s.info.optInt("undo")==1}}
        tap(canvas,3,cancel=true);var read=false;s.command("info"){read=true};until{read};assertEquals(1,s.info.optInt("undo"))
        tap(canvas,2,45f);read=false;s.command("info"){read=true};until{read};assertEquals(1,s.info.optInt("undo"))
        a.newCanvas(320,240,mode="vector");val doc=a.vectorDocument!!;val vc=a.vectorEditor!!.canvas;render(a.vectorEditor!!,"gesture-vector-before");doc.edit{doc.layers[0].name="Edited"}
        tap(vc,2,4f);assertEquals("Layer 1",doc.layers[0].name);tap(vc,3,4f);assertEquals("Edited",doc.layers[0].name)
        tap(vc,2,45f);assertEquals("Edited",doc.layers[0].name);a.setVectorInputPreference(2,false);tap(vc,2);assertEquals("Edited",doc.layers[0].name);done(c)
    }
    @Test fun vectorHistoryPreservesAtLeastThirtyForwardAndBackward(){
        val doc=VectorDocument(32,32);repeat(70){n->doc.edit{doc.layers[0].name="State $n"}};assertEquals(64,doc.undoCount)
        repeat(30){assertTrue(doc.undo())};assertEquals("State 39",doc.layers[0].name);assertEquals(30,doc.redoCount)
        repeat(30){assertTrue(doc.redo())};assertEquals("State 69",doc.layers[0].name)
        doc.undo();doc.edit{doc.layers[0].name="Branch"};assertEquals(0,doc.redoCount)
    }
}
