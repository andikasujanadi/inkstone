package art.inkstone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StudioTest {
    private fun until(test:()->Boolean) {
        val end=System.nanoTime()+20_000_000_000
        while(System.nanoTime()<end) {shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)}
        error("Timed out waiting for drawing worker")
    }
    private fun shutdown(controller:org.robolectric.android.controller.ActivityController<MainActivity>){
        val session=controller.get().session
        controller.pause().stop().destroy()
        until{session==null || session.fullyClosed}
    }
    private fun click(root:View,description:String) {
        fun find(v:View):View? {
            if(v.contentDescription?.toString()==description || v is TextView && v.text.toString()==description)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it}
            return null
        }
        val v=checkNotNull(find(root)){"No control named $description"};assertTrue(v.performClick());shadowOf(Looper.getMainLooper()).idle()
    }
    private fun render(view:View,name:String,w:Int=1280,h:Int=800) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        view.layout(0,0,w,h)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File(System.getProperty("inkstone.artifacts"),"$name.png");file.parentFile?.mkdirs()
        file.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        assertTrue(file.length()>2000);bitmap.recycle()
    }
    private fun landscape(s:DrawingSession) {
        fun polygon(color:Long,vararg xy:Int){
            val points=JSONArray();for(i in xy.indices step 2)points.put(JSONArray().put(xy[i]).put(xy[i+1]))
            s.command("select",JSONObject().put("shape","Freehand").put("points",points))
            s.command("fill",JSONObject().put("color",color));s.command("deselect")
        }
        s.command("background",JSONObject().put("color",0xffe8d9c2L))
        s.command("layer",JSONObject().put("name","Distant ridges"))
        polygon(0xffa5b4acL,0,820,240,470,400,640,640,310,850,640,1060,430,1600,870,1600,1200,0,1200)
        s.command("add");s.command("layer",JSONObject().put("name","Evening hills"))
        polygon(0xff507a75L,0,940,250,760,510,900,820,650,1080,850,1400,690,1600,760,1600,1200,0,1200)
        s.command("add");s.command("layer",JSONObject().put("name","Foreground"))
        polygon(0xff244d4cL,0,1100,300,980,600,1090,1000,960,1290,1010,1600,910,1600,1200,0,1200)
        s.color=0xffe8c384.toInt();s.size=125f;s.pressureSize=0f
        s.stroke(0,floatArrayOf(1140f,270f,1f,0f));s.stroke(2,floatArrayOf())
        var done=false;s.command("info"){done=true};until{done};until{s.bitmap.getPixel(100,100)==0xffe8d9c2.toInt() && s.bitmap.getPixel(1140,270)==0xffe8c384.toInt()}
    }
    @Test fun nativeJniDocumentAndPixelRoundtrip() {
        val handle=NativeBridge.create(64,64);assertNotEquals(0L,handle)
        NativeBridge.stroke(handle,0,floatArrayOf(32f,32f,1f,0f),0,0xffff0000.toInt(),12f,1f,1f,0f,1f,0f)
        NativeBridge.stroke(handle,2,floatArrayOf(),0,0,12f,1f,1f,0f,1f,0f)
        val before=checkNotNull(NativeBridge.save(handle))
        assertEquals(1,JSONObject(NativeBridge.command(handle,"{\"op\":\"info\"}")).getInt("undo"))
        val restored=NativeBridge.create(32,32);assertFalse(JSONObject(NativeBridge.load(restored,before)).has("error"))
        assertArrayEquals(before,NativeBridge.save(restored))
        assertEquals(64*64,NativeBridge.thumbnails(restored,64)?.size)
        NativeBridge.destroy(handle);NativeBridge.destroy(restored)
    }
    @Test fun editorPanelsAndGalleryRenderAndPersist() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(1600,1200)
        val s=checkNotNull(activity.session);val editor=checkNotNull(activity.editor)
        landscape(s);render(editor,"editor-tablet-framework")
        click(editor,"Paint");render(editor,"brush-library")
        click(editor,"Airbrushing");click(editor,"Soft Brush, Airbrushing");render(editor,"brush-library-airbrushing")
        editor.dismissPanel();click(editor,"Color");render(editor,"colors")
        editor.dismissPanel();click(editor,"Layers");until{s.layerThumbnails.size==3};shadowOf(Looper.getMainLooper()).idle();render(editor,"layers")
        editor.dismissPanel();click(editor,"Actions");render(editor,"actions")
        click(editor,"Prefs");render(editor,"preferences")
        editor.dismissPanel();click(editor,"Select");render(editor,"selection")
        var saved=false;activity.saveCurrent{assertTrue(it);saved=true};until{saved}
        assertTrue(activity.store.list().any{it.id==activity.documentId})
        val bytes=activity.store.read(activity.documentId);assertTrue(bytes.size>100)
        activity.showGallery()
        val gallery=activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        fun hasThumbnail(v:View):Boolean = when(v) {
            is android.widget.ImageView -> v.drawable != null
            is ViewGroup -> (0 until v.childCount).any{hasThumbnail(v.getChildAt(it))}
            else -> false
        }
        until{hasThumbnail(gallery)}
        render(gallery,"gallery")
        shutdown(controller)
    }
    private fun named(root:View,description:String):View? {
        if(root.contentDescription?.toString()==description || root is TextView && root.text.toString()==description)return root
        if(root is ViewGroup)for(i in 0 until root.childCount)named(root.getChildAt(i),description)?.let{return it}
        return null
    }
    @Test fun airbrushAndSmudgeEngineRender(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        a.newCanvas(1000,600);val e=checkNotNull(a.editor);val session=checkNotNull(a.session)
        session.stabilization=0f;session.pressureEnabled=false;session.color=0xff174f90.toInt()
        for((row,name) in listOf("Soft Brush","Medium Brush","Medium Hard Brush","Hard Brush").withIndex()){
            session.choose(brushPresets.first{it.name==name});session.size=80f
            session.stroke(0,floatArrayOf(100f,90f+row*130,1f,0f))
            session.stroke(1,floatArrayOf(400f,90f+row*130,1f,0f));session.stroke(2,floatArrayOf())
        }
        session.command("select",JSONObject().put("shape","Rectangle").put("points",JSONArray().put(JSONArray().put(570).put(80)).put(JSONArray().put(720).put(520))))
        session.command("fill",JSONObject().put("color",0xffe04b42L));session.command("deselect")
        session.command("select",JSONObject().put("shape","Rectangle").put("points",JSONArray().put(JSONArray().put(720).put(80)).put(JSONArray().put(870).put(520))))
        session.command("fill",JSONObject().put("color",0xff2466c2L));session.command("deselect")
        until{session.info.optInt("undo")==6};render(e,"brush-engine-before-smudge")
        session.tool="Smudge";session.choose(brushPresets.first{it.name=="Soft Brush"});session.size=100f;session.flow=1f
        session.stroke(0,floatArrayOf(680f,300f,1f,0f));session.stroke(1,floatArrayOf(790f,300f,1f,0f));session.stroke(2,floatArrayOf())
        until{session.info.optInt("undo")==7};render(e,"brush-engine-after-smudge")
        shutdown(controller)
    }
    @Test fun dragColorDropPreviewsCommitsAndCancels(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        a.newCanvas(320,240);val e=checkNotNull(a.editor);val session=checkNotNull(a.session)
        session.command("fill",JSONObject().put("color",0xffffffffL));until{session.info.optInt("undo")==1}
        render(e,"colordrop-before");session.color=0xffed5c40.toInt()
        val color=checkNotNull(named(e,"Color"));val iconAt=IntArray(2);val own=IntArray(2)
        color.getLocationInWindow(iconAt);e.getLocationInWindow(own)
        fun touch(action:Int,x:Float,y:Float){val ev=MotionEvent.obtain(10,20,action,x-iconAt[0]+own[0],y-iconAt[1]+own[1],0);color.dispatchTouchEvent(ev);ev.recycle()}
        fun down(){touch(MotionEvent.ACTION_DOWN,iconAt[0]-own[0]+color.width/2f,iconAt[1]-own[1]+color.height/2f)}
        down();touch(MotionEvent.ACTION_MOVE,640f,400f)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(230))
        until{session.bitmap.getPixel(160,120)==0xffed5c40.toInt()}
        assertTrue(session.isStrokeActive);assertEquals(1,session.info.optInt("undo"))
        render(e,"colordrop-threshold")
        touch(MotionEvent.ACTION_MOVE,740f,400f);touch(MotionEvent.ACTION_UP,740f,400f)
        until{session.info.optInt("undo")==2 && !session.isStrokeActive}
        session.color=0xff2040ef.toInt();down();touch(MotionEvent.ACTION_MOVE,640f,400f)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(230))
        until{session.bitmap.getPixel(160,120)==0xff2040ef.toInt()}
        touch(MotionEvent.ACTION_CANCEL,640f,400f)
        until{!session.isStrokeActive && session.bitmap.getPixel(160,120)==0xffed5c40.toInt()}
        assertEquals(2,session.info.optInt("undo"));session.command("undo")
        until{session.info.optInt("undo")==1 && session.bitmap.getPixel(160,120)==android.graphics.Color.WHITE}
        render(e,"colordrop-undone");shutdown(controller)
    }
    @Test fun heldShapeIsOneUndoStepAndCanvasResizeStaysStable(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val e=checkNotNull(a.editor);val session=checkNotNull(a.session)
        render(e,"quick-shape-before")
        fun findCanvas(v:View):PaintCanvas?{if(v is PaintCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findCanvas(v.getChildAt(i))?.let{return it};return null}
        val canvas=checkNotNull(findCanvas(e));session.stabilization=0f
        fun touch(action:Int,x:Float,y:Float){val event=MotionEvent.obtain(0,10,action,x,y,0);canvas.dispatchTouchEvent(event);event.recycle()}
        for(i in 0..80){val t=i*2*Math.PI/80;touch(if(i==0)MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,500+90*kotlin.math.cos(t).toFloat(),350+90*kotlin.math.sin(t).toFloat())}
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700))
        touch(MotionEvent.ACTION_UP,590f,350f);until{session.info.optInt("undo")==1}
        render(e,"quick-shape-circle");session.command("undo");until{session.info.optInt("undo")==0}
        a.customCanvas();val setup=checkNotNull(a.canvasSetup)
        render(setup,"custom-canvas-wide",1280,800);click(setup,"Colour profile");click(setup,"Dimensions")
        render(setup,"custom-canvas-narrow",800,1280);click(setup,"Canvas properties");click(setup,"Dimensions")
        render(setup,"custom-canvas-wide-again",1280,800)
        assertTrue(checkNotNull(named(setup,"Create custom canvas")).right<=1280)
        shutdown(controller)
    }
    @Test fun shapeRecognitionAndPreferences(){
        fun oval(rx:Float,ry:Float)= (0..100).map{val t=it*2*Math.PI/100;android.graphics.PointF(200+rx*kotlin.math.cos(t).toFloat(),200+ry*kotlin.math.sin(t).toFloat())}
        assertEquals("Circle",QuickShape.fit(oval(90f,90f))?.name)
        assertEquals("Ellipse",QuickShape.fit(oval(100f,55f))?.name)
        val square=mutableListOf<android.graphics.PointF>()
        for(edge in 0..3)for(i in 0..25){val t=i/25f;square.add(when(edge){0->android.graphics.PointF(100+100*t,100f);1->android.graphics.PointF(200f,100+100*t);2->android.graphics.PointF(200-100*t,200f);else->android.graphics.PointF(100f,200-100*t)})}
        assertEquals("Square",QuickShape.fit(square)?.name)
        assertEquals("Line",QuickShape.fit((0..20).map{android.graphics.PointF(it*10f,it*3f)})?.name)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val e=checkNotNull(a.editor)
        render(e,"shape-options-before");click(e,"Actions");click(e,"Prefs");click(e,"Pen pressure")
        assertFalse(checkNotNull(a.session).pressureEnabled)
        click(e,"Canvas");click(e,"Lock canvas position");assertTrue(checkNotNull(a.session).canvasLocked)
        assertFalse(a.getSharedPreferences("studio",0).getBoolean("pressureEnabled",true))
        render(e,"canvas-lock-settings");shutdown(controller)
    }
    @Test fun panelSectionsKeepTheirContainer(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val e=checkNotNull(a.editor)
        render(e,"panel-before");click(e,"Paint");render(e,"brush-before-switch")
        val panel=e.findViewWithTag<View>("tool-panel")
        click(e,"Airbrushing");assertSame(panel,e.findViewWithTag<View>("tool-panel"));render(e,"brush-after-switch")
        e.dismissPanel();click(e,"Color");click(e,"Classic");render(e,"classic-square-palette")
        shutdown(controller)
    }
    @Test fun canvasMenuAndCustomDimensionsCreateCorrectDocument() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.showGallery();val gallery=activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        render(gallery,"gallery-empty");activity.createDialog()
        val menu=checkNotNull(activity.canvasPopup).contentView
        render(menu,"new-canvas-menu",400,650)
        click(menu,"Custom Canvas")
        val setup=checkNotNull(activity.canvasSetup);render(setup,"custom-canvas",1280,800)
        click(setup,"Canvas DPI");for(key in listOf("1","5","0"))click(setup,"Key $key")
        click(setup,"Millimetres");click(setup,"Canvas Width");click(setup,"Key 5");click(setup,"Key 0")
        click(setup,"Canvas Height");click(setup,"Key 7");click(setup,"Key 0")
        render(setup,"custom-canvas-units",1280,800)
        click(setup,"Canvas properties");render(setup,"custom-canvas-properties",1280,800)
        click(setup,"Create custom canvas")
        val session=checkNotNull(activity.session);until{session.info.optInt("dpi")==150}
        assertEquals(295,session.canvasWidth);assertEquals(413,session.canvasHeight)
        assertTrue(CanvasPresets.custom(activity).any{it.width==295&&it.height==413&&it.dpi==150})
        var saved=false;activity.saveCurrent{assertTrue(it);saved=true};until{saved}
        val doc=JSONObject(String(activity.store.read(activity.documentId)));assertEquals(150,doc.getInt("dpi"))
        shutdown(controller)
    }
    @Test fun layerBlendExpandsInlineAndSwipeActionsWork() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(1600,1200);val session=checkNotNull(activity.session);val editor=checkNotNull(activity.editor)
        landscape(session);render(editor,"workspace-grid");click(editor,"Layers");until{session.layerThumbnails.size==3}
        render(editor,"layers");click(editor,"Blend options for Foreground");render(editor,"layer-blend-dropdown")
        click(editor,"Blend Multiply for Foreground");until{session.info.getJSONArray("layers").getJSONObject(2).getString("blend")=="Multiply"}
        click(editor,"Blend options for Foreground");render(editor,"layers-closed-dropdown")
        fun swipe(name:String){
            val row=checkNotNull(named(editor,"Layer row $name")) as SwipeLayerRow
            fun touch(action:Int,x:Float){val e=MotionEvent.obtain(10,20,action,x,30f,0);row.dispatchTouchEvent(e);e.recycle()}
            touch(MotionEvent.ACTION_DOWN,row.width-10f);touch(MotionEvent.ACTION_MOVE,row.width-210f);touch(MotionEvent.ACTION_UP,row.width-210f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));assertTrue(row.revealed)
        }
        swipe("Foreground");render(editor,"layer-swipe-actions")
        click(editor,"Lock Foreground");until{session.info.getJSONArray("layers").getJSONObject(2).optBoolean("locked")}
        until{named(editor,"Unlock Foreground")!=null};render(editor,"layer-locked")
        swipe("Foreground");click(editor,"Unlock Foreground");until{!session.info.getJSONArray("layers").getJSONObject(2).optBoolean("locked")}
        until{named(editor,"Lock Foreground")!=null};render(editor,"layers-unlocked")
        swipe("Foreground");click(editor,"Duplicate Foreground");until{session.info.getJSONArray("layers").length()==4&&named(editor,"Layer row Foreground copy")!=null}
        render(editor,"layers-duplicated");swipe("Foreground copy");click(editor,"Delete Foreground copy")
        until{session.info.getJSONArray("layers").length()==3}
        shutdown(controller)
    }
    @Test fun groupedPreferencesAndCursorGesturesPersist() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val editor=checkNotNull(activity.editor);val session=checkNotNull(activity.session)
        landscape(session);render(editor,"workspace-before-prefs");click(editor,"Actions");click(editor,"Prefs");render(editor,"preferences")
        click(editor,"Advanced cursor settings");render(editor,"advanced-cursor-settings");click(editor,"Light")
        assertEquals("Light",session.cursorStyle)
        editor.dismissPanel();click(editor,"Actions");click(editor,"Gesture controls");render(editor,"gesture-settings")
        click(editor,"Two-finger tap Undo");assertFalse(session.twoFingerUndo)
        assertFalse(activity.getSharedPreferences("studio",0).getBoolean("twoFingerUndo",true))
        shutdown(controller)
    }

    @Test fun repeatedStrokesAndMidStrokeSaveKeepEveryEndpoint() {
        val session=DrawingSession(1600,1200)
        val store=DocumentStore(RuntimeEnvironment.getApplication())
        session.color=0xffd02020.toInt();session.size=8f;session.pressureSize=0f;session.stabilization=0f
        var frames=0;session.onFrame={frames++}
        session.requestRender();until{frames>0}
        val started=System.nanoTime();var saved=false
        val id=store.newId()
        for(line in 0 until 60){
            val y=20f+line*18f
            session.stroke(0,floatArrayOf(20f,y,1f,0f))
            for(point in 1..64){
                session.stroke(1,floatArrayOf(20f+point*22f,y,1f,0f))
                if(line==30&&point==32)session.persist(store,id,"Mid-stroke"){assertNull(it);saved=true}
            }
            session.stroke(2,floatArrayOf())
        }
        until{session.info.getInt("undo")==60 && session.bitmap.getPixel(1420,1082)==0xffd02020.toInt() && saved}
        for(line in 0 until 60)assertEquals("Lost endpoint for stroke $line",0xffd02020.toInt(),session.bitmap.getPixel(1420,20+line*18))
        assertEquals(1600,store.list().first{it.id==id}.width)
        assertEquals(1200,store.list().first{it.id==id}.height)
        println("60 strokes / 3840 move samples, including mid-stroke disk save: ${(System.nanoTime()-started)/1_000_000} ms (desktop host)")
        session.close();until{session.fullyClosed}
    }

    @Test fun longStrokeDefersIdleAutosaveUntilPenIsLifted() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(320,240);val editor=checkNotNull(activity.editor);val session=checkNotNull(activity.session)
        render(editor,"autosave-long-stroke")
        val view=editor.canvas
        fun touch(action:Int,x:Float){val e=MotionEvent.obtain(100,110,action,x,350f,0);view.onTouchEvent(e);e.recycle()}
        touch(MotionEvent.ACTION_DOWN,500f);touch(MotionEvent.ACTION_MOVE,550f)
        assertTrue(session.isStrokeActive)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2500))
        assertFalse(activity.store.list().any{it.id==activity.documentId})
        touch(MotionEvent.ACTION_MOVE,700f);touch(MotionEvent.ACTION_UP,750f)
        until{session.info.getInt("undo")==1}
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        until{activity.store.list().any{it.id==activity.documentId}}
        assertFalse(session.isStrokeActive)
        shutdown(controller)
    }

    @Test fun sliderShowsLivePreviewAndCancels() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val editor=checkNotNull(controller.get().editor);controller.get().session?.choose(brushPresets.first{it.name=="Soft Brush"});render(editor,"slider-before")
        fun find(v:View):View? {if(v.contentDescription=="Brush size")return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        val slider=checkNotNull(find(editor))
        val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,12f,slider.height*.7f,0);slider.dispatchTouchEvent(down);down.recycle()
        render(editor,"brush-size-preview")
        assertTrue((0 until editor.childCount).map{editor.getChildAt(it)}.any{it is BrushHud && it.visibility==View.VISIBLE})
        val cancel=MotionEvent.obtain(0,10,MotionEvent.ACTION_CANCEL,12f,slider.height*.7f,0);slider.dispatchTouchEvent(cancel);cancel.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1000))
        assertTrue((0 until editor.childCount).map{editor.getChildAt(it)}.filterIsInstance<BrushHud>().all{it.visibility!=View.VISIBLE})
        shutdown(controller)
    }
    @Test fun rapidPanelSwitchingCleansUpOutgoingViews() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val editor=checkNotNull(controller.get().editor)
        click(editor,"Color");editor.dismissPanel();click(editor,"Actions");click(editor,"Color")
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        val panels=(0 until editor.childCount).map{editor.getChildAt(it)}.filter{it.tag=="tool-panel"}
        assertEquals(1,panels.size);assertTrue(panels.single().isEnabled)
        assertEquals(1f,panels.single().alpha,.01f)
        editor.dismissPanel()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        assertFalse((0 until editor.childCount).any{editor.getChildAt(it).tag=="tool-panel"})
        shutdown(controller)
    }
    @Test fun fingerStrokeAndCancelledPalmAreUndoSafe() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(320,240);val editor=checkNotNull(activity.editor);val s=checkNotNull(activity.session)
        render(editor,"input-test")
        val view=editor.canvas
        fun touch(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(100,110,action,x,y,0);view.onTouchEvent(e);e.recycle()}
        touch(MotionEvent.ACTION_DOWN,600f,350f);touch(MotionEvent.ACTION_MOVE,640f,350f);touch(MotionEvent.ACTION_UP,660f,350f)
        until{s.info.getInt("undo")==1}
        touch(MotionEvent.ACTION_DOWN,500f,300f);touch(MotionEvent.ACTION_MOVE,520f,300f);touch(MotionEvent.ACTION_CANCEL,520f,300f)
        var done=false;s.command("info"){done=true};until{done};assertEquals(1,s.info.getInt("undo"))
        shutdown(controller)
    }
    @Test fun phoneLayoutAndImportRejectInvalidDocument() {
        RuntimeEnvironment.setQualifiers("w400dp-h820dp-mdpi")
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(400,400);val s=checkNotNull(activity.session)
        until{s.bitmap.getPixel(0,0) ushr 24==255}
        render(checkNotNull(activity.editor),"editor-phone",400,820)
        click(checkNotNull(activity.editor),"Color");render(checkNotNull(activity.editor),"colors-phone",400,820)
        activity.editor?.dismissPanel();click(checkNotNull(activity.editor),"Select")
        render(checkNotNull(activity.editor),"selection-phone",400,820)
        val handle=NativeBridge.create(32,32);assertTrue(JSONObject(NativeBridge.load(handle,byteArrayOf(1,2,3))).has("error"));NativeBridge.destroy(handle)
        shutdown(controller)
    }
    @Test fun rendererPreferenceAndDocumentRoundTrip(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        activity.newCanvas(96,64);val session=checkNotNull(activity.session);val editor=checkNotNull(activity.editor)
        var configured=false;session.setRenderer("cpu"){configured=true};until{configured}
        assertEquals("cpu",session.info.getString("renderer"));activity.savePreferences()
        assertEquals("cpu",activity.getSharedPreferences("studio",0).getString("rendererMode",null))
        session.stroke(0,floatArrayOf(10f,30f,1f,0f));session.stroke(2,floatArrayOf(80f,30f,1f,0f));until{session.info.getInt("undo")==1}
        var saved:ByteArray?=null;session.snapshot{bytes,image->saved=bytes;image?.recycle()};until{saved!=null}
        var loaded=false;session.load(checkNotNull(saved)){loaded=it};until{loaded}
        assertEquals("cpu",session.info.getString("renderer"));assertEquals(96,session.canvasWidth)
        render(editor,"renderer-preferences");click(editor,"Actions");click(editor,"Prefs");click(editor,"Drawing renderer")
        val dialog=checkNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(dialog.isShowing);dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shutdown(controller)
    }

}
