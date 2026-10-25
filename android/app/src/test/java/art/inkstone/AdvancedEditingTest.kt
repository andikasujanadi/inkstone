package art.inkstone

import android.app.AlertDialog
import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AdvancedEditingTest{
    private fun until(test:()->Boolean){val end=System.nanoTime()+25_000_000_000;while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)};error("Advanced editing timed out")}
    private fun named(v:View,s:String):View?{if(v.contentDescription==s || v is TextView && v.text.toString()==s)return v;if(v is ViewGroup)for(i in 0 until v.childCount)named(v.getChildAt(i),s)?.let{return it};return null}
    private fun click(v:View,s:String){
        if(named(v,s)==null){
            val group=when(s){"Automatic","Freehand","Rectangle","Ellipse"->"Shape";"Replace","Add","Remove","Invert"->"Combine";"Copy & Paste","Copy","Cut","Paste"->"Clipboard";"Threshold","Feather"->"Refine";"Save selection","Load selection","Delete saved"->"Saved";"Color Fill","Clear","Deselect"->"Selection actions";"Freeform","Uniform","Distort","Warp"->"Mode";"Smaller","Larger","Flip H","Flip V","Rotate 45°","Fit to Canvas"->"Adjust";"Snapping","Bilinear","Nearest"->"Options";else->null}
            if(group!=null){assertTrue(checkNotNull(named(v,group)){"Missing group $group"}.performClick());shadowOf(Looper.getMainLooper()).idle()}
        }
        assertTrue(checkNotNull(named(v,s)){"Missing $s"}.performClick());shadowOf(Looper.getMainLooper()).idle()
    }

    private fun layout(v:View,name:String){shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));v.rootView.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.rootView.layout(0,0,1280,800);v.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,1280,800);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));val b=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File(System.getProperty("inkstone.artifacts"),"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private fun command(s:DrawingSession,op:String,values:JSONObject=JSONObject()){var done=false;s.command(op,values){assertFalse(it.toString(),it.has("error"));done=true};until{done}}
    private fun pick(s:DrawingSession,x:Int,y:Int):Int{var result:JSONObject?=null;s.command("pick",JSONObject().put("x",x).put("y",y)){result=it};until{result!=null};return result!!.getLong("color").toInt()}
    private fun bytes(s:DrawingSession):ByteArray{var result:ByteArray?=null;s.documentSnapshot{result=it};until{result!=null};return result!!}
    private fun point(e:EditorView,x:Float,y:Float)=e.canvas.transformViewPoint(x,y).apply{offset(e.canvas.left.toFloat(),e.canvas.top.toFloat())}
    private fun touch(e:EditorView,action:Int,p:PointF){val time=android.os.SystemClock.uptimeMillis();val event=MotionEvent.obtain(time,time,action,p.x,p.y,0);e.dispatchTouchEvent(event);event.recycle()}
    private fun drag(e:EditorView,from:PointF,to:PointF){touch(e,MotionEvent.ACTION_DOWN,from);touch(e,MotionEvent.ACTION_MOVE,to);touch(e,MotionEvent.ACTION_UP,to)}
    private fun rect(s:DrawingSession,x0:Int,y0:Int,x1:Int,y1:Int,mode:String="Replace")=command(s,"select",JSONObject().put("shape","Rectangle").put("mode",mode).put("points",JSONArray().put(JSONArray().put(x0).put(y0)).put(JSONArray().put(x1).put(y1))))
    private fun seed(s:DrawingSession){rect(s,60,60,180,140);command(s,"selectionFill",JSONObject().put("color",0xff228855L));rect(s,120,60,180,140);command(s,"selectionFill",JSONObject().put("color",0xffdd6633L));command(s,"deselect")}
    private fun done(c:org.robolectric.android.controller.ActivityController<MainActivity>){val s=c.get().session;c.pause().stop().destroy();until{s==null || s.fullyClosed};until{c.get().io.isTerminated}}
    @Test fun shelfShowsSixGroupsExpandsOneAtATimeAndScrollsOnPortrait(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val e=c.get().editor!!;layout(e,"compact-toolbar-before");click(e,"Select");layout(e,"compact-selection-collapsed")
        val shelf=e.findViewWithTag<View>("canvas-tool-shelf");val main=e.findViewWithTag<LinearLayout>("canvas-tool-main-row");assertEquals(6,main.childCount);assertTrue(shelf.width<=536);assertNull(named(e,"Rectangle"))
        click(e,"Shape");layout(e,"compact-selection-expanded");assertNotNull(named(e,"Rectangle"));assertEquals(4,e.findViewWithTag<LinearLayout>("canvas-tool-expanded-row").childCount)
        click(e,"Clipboard");assertNull(named(e,"Rectangle"));assertNotNull(named(e,"Copy & Paste"));click(e,"Clipboard");assertEquals(View.GONE,e.findViewWithTag<View>("canvas-tool-options-scroll").visibility)
        org.robolectric.RuntimeEnvironment.setQualifiers("w400dp-h820dp-mdpi")
        e.rootView.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.EXACTLY));e.rootView.layout(0,0,400,820);e.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.EXACTLY));e.layout(0,0,400,820);val scroll=e.findViewWithTag<HorizontalScrollView>("canvas-tool-groups-scroll");assertTrue(scroll.getChildAt(0).width>scroll.width);scroll.scrollTo(80,0);assertTrue(scroll.scrollX>0);assertTrue(shelf.width<=320);done(c)
    }
    @Test fun rectangleAddRemoveFeatherSavedSelectionAndCopyPasteWorkThroughEditor(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,visible=false);val e=a.editor!!;val s=e.session;layout(e,"advanced-selection-before");click(e,"Select");click(e,"Rectangle")
        drag(e,point(e,40f,40f),point(e,120f,120f));command(s,"info");click(e,"Add");drag(e,point(e,100f,100f),point(e,180f,160f));command(s,"info");click(e,"Remove");drag(e,point(e,60f,60f),point(e,80f,80f));command(s,"info")
        s.color=Color.RED;click(e,"Color Fill");command(s,"info");assertEquals(Color.RED,pick(s,150,130));assertEquals(0,Color.alpha(pick(s,70,70)));assertEquals(0,Color.alpha(pick(s,200,100)))
        command(s,"selectionFeather",JSONObject().put("radius",5));click(e,"Save selection")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();fun edit(v:View):EditText?{if(v is EditText)return v;if(v is ViewGroup)for(i in 0 until v.childCount)edit(v.getChildAt(i))?.let{return it};return null};edit(dialog.window!!.decorView)!!.setText("Subject");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();until{s.info.optJSONArray("savedSelections")?.length()==1}
        click(e,"Deselect");command(s,"info");assertFalse(s.info.getBoolean("selection"));click(e,"Load selection");ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,0,0);command(s,"info");assertTrue(s.info.getBoolean("selection"))
        layout(e,"advanced-feathered-selection");click(e,"Copy & Paste");until{s.info.getJSONArray("layers").length()==2 && s.tool=="Transform"};until{e.canvas.rasterTransform.ready};assertNotNull(named(e,"Mode"));assertTrue(Color.alpha(pick(s,150,130))>0);assertEquals(0,Color.alpha(pick(s,200,100)));
        a.newCanvas(160,120,visible=false);val other=a.editor!!;layout(other,"advanced-paste-new-document");click(other,"Select");click(other,"Paste");until{other.session.info.getJSONArray("layers").length()==2};assertTrue(Color.alpha(pick(other.session,80,60))>0);done(c)
    }
    @Test fun automaticSelectionAndClearOnlyAffectMatchingConnectedRegion(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,visible=false);val e=a.editor!!;val s=e.session;seed(s);layout(e,"advanced-auto-before");click(e,"Select");click(e,"Automatic");drag(e,point(e,80f,90f),point(e,80f,90f));command(s,"info")
        assertTrue(s.info.getBoolean("selection"));click(e,"Clear");command(s,"info");assertEquals(0,Color.alpha(pick(s,80,90)));assertEquals(0xffdd6633.toInt(),pick(s,150,90));command(s,"undo");assertEquals(0xff228855.toInt(),pick(s,80,90));done(c)
    }
    @Test fun freeformUniformDistortResetAndFitHaveLiveHandlesAndOneCommit(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,visible=false);val e=a.editor!!;val s=e.session;seed(s);layout(e,"advanced-transform-before");click(e,"Transform");until{e.canvas.rasterTransform.ready};val t=e.canvas.rasterTransform
        click(e,"Freeform");drag(e,point(e,180f,140f),point(e,220f,145f));val free=t.bounds!!;assertTrue(free.width()/120f>free.height()/80f+.15f)
        click(e,"Reset");click(e,"Uniform");drag(e,point(e,180f,140f),point(e,220f,145f));val uniform=t.bounds!!;assertEquals(uniform.width()/120f,uniform.height()/80f,.01f)
        click(e,"Reset");click(e,"Snapping");drag(e,point(e,120f,100f),point(e,159f,100f));assertEquals(160f,t.bounds!!.centerX(),.1f);click(e,"Reset");click(e,"Bilinear");ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,0,0);assertEquals("Nearest",t.interpolation);click(e,"Distort");drag(e,point(e,180f,60f),point(e,160f,78f));val values=FloatArray(9);t.currentMatrix().getValues(values);assertTrue(kotlin.math.abs(values[6])+kotlin.math.abs(values[7])>.00001f);layout(e,"advanced-distort-live")
        val undo=s.info.getInt("undo");click(e,"Done");until{s.info.getInt("undo")==undo+1 && !t.displaying};assertTrue(Color.alpha(pick(s,100,100))>0);command(s,"undo")
        click(e,"Transform");until{t.ready};click(e,"Fit to Canvas");assertTrue(t.bounds!!.width()>=319f || t.bounds!!.height()>=239f);click(e,"Cancel");done(c)
    }
    @Test fun warpGridCommitCancelAndUndoPreserveSourceArtwork(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(320,240,visible=false);val e=a.editor!!;val s=e.session;seed(s);val original=bytes(s)
        layout(e,"advanced-warp-before");click(e,"Transform");until{e.canvas.rasterTransform.ready};click(e,"Warp");drag(e,point(e,100f,86.6667f),point(e,116f,86.6667f));layout(e,"advanced-warp-live");val preview=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);assertTrue(e.canvas.rasterTransform.draw(Canvas(preview)));val previewColor=preview.getPixel(126,100);preview.recycle();val undo=s.info.getInt("undo");click(e,"Done");until{s.info.getInt("undo")==undo+1 && !e.canvas.rasterTransform.displaying};layout(e,"advanced-warp-committed");assertEquals("Warp preview must match the saved geometry",previewColor,pick(s,126,100));val warped=bytes(s);assertFalse(original.contentEquals(warped));command(s,"undo");assertArrayEquals(original,bytes(s));command(s,"redo");assertArrayEquals(warped,bytes(s))
        click(e,"Transform");until{e.canvas.rasterTransform.ready};click(e,"Warp");drag(e,point(e,100f,86.6667f),point(e,105f,89f));click(e,"Cancel");assertArrayEquals(warped,bytes(s));done(c)
    }
}
