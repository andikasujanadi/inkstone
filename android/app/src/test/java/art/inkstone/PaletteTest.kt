package art.inkstone

import android.app.AlertDialog
import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
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
class PaletteTest{
    private fun until(test:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)};error("Palette test timed out")}
    private fun named(v:View,s:String):View?{if(v.contentDescription==s || v is TextView && v.text.toString()==s)return v;if(v is ViewGroup)for(i in 0 until v.childCount)named(v.getChildAt(i),s)?.let{return it};return null}
    private fun click(v:View,s:String){assertTrue(checkNotNull(named(v,s)){"Missing $s"}.performClick());shadowOf(Looper.getMainLooper()).idle()}
    private fun layout(v:View,w:Int=1280,h:Int=800){shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));v.rootView.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.rootView.layout(0,0,w,h);v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)}
    private fun done(c:org.robolectric.android.controller.ActivityController<MainActivity>){val s=c.get().session;c.pause().stop().destroy();until{s==null || s.fullyClosed};until{c.get().io.isTerminated}}
    private fun drag(action:Int,x:Float,y:Float,state:Any):DragEvent{
        val e=DragEvent::class.java.getDeclaredMethod("obtain").apply{isAccessible=true}.invoke(null) as DragEvent
        for((name,value)in listOf("mAction" to action,"mX" to x,"mY" to y,"mLocalState" to state))DragEvent::class.java.getDeclaredField(name).apply{isAccessible=true}.set(e,value)
        return e
    }
    @Test fun customPaletteCreationSelectionAndEditsSurviveReload(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.getSharedPreferences("color-palettes",0).edit().clear().commit()
        val p=PaletteStore(a);val defaults=p.active.colors.toList();assertTrue(p.add(0xff123abc.toInt()));assertTrue(p.isCustom);assertEquals(defaults, p.all.first().colors)
        val copy=p.active.id;p.create("Portrait tones");assertTrue(p.active.colors.isEmpty());p.add(0xffb47250.toInt());p.add(0xffe3ac82.toInt());p.replace(1,0xfff0c7a3.toInt());p.rename("Skin tones")
        val restored=PaletteStore(a);assertEquals("Skin tones",restored.active.name);assertEquals(listOf(0xffb47250.toInt(),0xfff0c7a3.toInt()),restored.active.colors)
        assertFalse(restored.add(0xffb47250.toInt()));restored.select(copy);assertEquals("My palette",restored.active.name);assertTrue(restored.active.colors.contains(0xff123abc.toInt()))
        restored.delete();assertEquals("studio",PaletteStore(a).active.id);assertEquals(defaults,PaletteStore(a).active.colors);done(c)
    }
    @Test fun headerColorDropAddsToCustomPaletteWithoutPaintingAndCellsAreSquare(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val e=c.get().editor!!;val s=e.session
        s.color=0xff123abc.toInt();click(e,"Color");layout(e)
        val header=checkNotNull(named(e,"Active color preview"));assertEquals(s.color,(header.background as android.graphics.drawable.ColorDrawable).color)
        val grid=e.findViewWithTag<View>("palette-drop-target");val pos=IntArray(2);val own=IntArray(2);grid.getLocationInWindow(pos);e.getLocationInWindow(own)
        val x=pos[0]-own[0]+grid.width/2f;val y=pos[1]-own[1]+grid.height/2f
        val payload=PaletteColorDrag(s.color)
        for(action in listOf(DragEvent.ACTION_DRAG_STARTED,DragEvent.ACTION_DRAG_LOCATION,DragEvent.ACTION_DROP,DragEvent.ACTION_DRAG_ENDED))e.dispatchDragEvent(drag(action,x,y,payload))
        layout(e);assertEquals(s.color,e.palettes.active.colors.last());assertEquals("My palette",e.palettes.active.name);assertEquals(0,s.info.optInt("undo"))
        val target=e.findViewWithTag<PaletteGrid>("palette-drop-target");val cells=target.getChildAt(0) as GridLayout
        for(i in 0 until cells.childCount){val cell=cells.getChildAt(i);assertTrue(cell.width>0);assertEquals(cell.width,cell.height);if(i%6!=0)assertEquals(cells.getChildAt(i-1).right,cell.left)}
        val image=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);e.draw(Canvas(image));File(System.getProperty("inkstone.artifacts"),"custom-palette-dropped.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        click(e,String.format("Color #%06X",s.color and 0xffffff));assertEquals(s.color,e.session.color)
        val id=e.palettes.active.id;c.get().newCanvas(256,256);assertEquals(id,c.get().editor!!.palettes.active.id);assertTrue(c.get().editor!!.palettes.active.colors.contains(s.color));done(c)
    }
    @Test fun paletteControlsCreateRenameAndAddColors(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val e=c.get().editor!!;click(e,"Color");click(e,"Palettes");click(e,"New palette")
        fun name(value:String){val dialog=ShadowAlertDialog.getLatestAlertDialog();fun edit(v:View):EditText?{if(v is EditText)return v;if(v is ViewGroup)for(i in 0 until v.childCount)edit(v.getChildAt(i))?.let{return it};return null};checkNotNull(edit(dialog.window!!.decorView)).setText(value);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
        name("My colors");assertEquals("My colors",e.palettes.active.name);assertTrue(e.palettes.active.colors.isEmpty())
        e.session.color=0xff5588aa.toInt();e.refreshTools();click(e,"Add current color");assertEquals(listOf(0xff5588aa.toInt()),e.palettes.active.colors)
        click(e,"Rename palette");name("Ocean");assertEquals("Ocean",PaletteStore(c.get()).active.name)
        click(e,"Palette");ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,0,0);assertEquals("Studio",e.palettes.active.name);done(c)
    }
}
