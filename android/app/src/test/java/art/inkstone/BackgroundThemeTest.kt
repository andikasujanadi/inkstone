package art.inkstone

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
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
class BackgroundThemeTest{
    private fun until(test:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(10)};error("Timed out")}
    private fun layout(v:View,w:Int=1280,h:Int=800){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))}
    private fun find(v:View,label:String):View?{if(v.contentDescription==label || v is TextView && v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),label)?.let{return it};return null}
    private fun render(v:View,name:String){layout(v);val b=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File(System.getProperty("inkstone.artifacts"),"theme-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun rasterBackgroundPresetsRestoreVisibilityAndRapidChoicesUseTheLastColor(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(120,100);val s=a.session!!;val root=a.editor!!;until{s.frameSerial>0};s.color=0xffb43377.toInt()
        var hidden=false;s.command("background",JSONObject().put("visible",false)){hidden=true};until{hidden};root.backgroundSettings();layout(root)
        find(root,"White")!!.performClick();find(root,"Black")!!.performClick();find(root,"Warm paper")!!.performClick();until{s.info.optLong("background")==0xfffaf9f6L && s.info.optBoolean("backgroundVisible")}
        var checked=false;s.imageSnapshot{image->assertNotNull(image);assertEquals(0xfffaf9f6.toInt(),image!!.getPixel(20,20));image.recycle();checked=true};until{checked};assertEquals(0xffb43377.toInt(),s.color)
        find(root,"Background transparency")!!.performClick();until{!s.info.optBoolean("backgroundVisible")};find(root,"Current colour")!!.performClick();until{s.info.optLong("background")==0xffb43377L && s.info.optBoolean("backgroundVisible")}
        (find(root,"Background hex color") as EditText).setText("#347A68");find(root,"Apply color")!!.performClick();until{s.info.optLong("background")==0xff347a68L};assertEquals(0xffb43377.toInt(),s.color)
        var undone=false;s.command("undo"){undone=true};until{undone};assertEquals(0xffb43377L,s.info.getLong("background"));render(root,"background-dark")
        c.pause().stop().destroy();until{a.io.isTerminated}
    }
    @Test fun vectorBackgroundUsesTheSamePickerAndSupportsUndo(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(120,100,visible=false,mode="vector");val root=a.vectorEditor!!;root.canvas.ink=0xff458ab4.toInt();root.backgroundSettings();layout(root)
        find(root,"Current colour")!!.performClick();assertEquals(0xff458ab4.toInt(),root.document.background);assertTrue(root.document.backgroundVisible)
        find(root,"Black")!!.performClick();assertEquals(Color.BLACK,root.document.background);assertTrue(root.document.undo());assertEquals(0xff458ab4.toInt(),root.document.background)
        assertNotNull(find(root,"Background hex color"));c.pause().stop().destroy();until{a.io.isTerminated}
    }
    @Test fun lightModeUpdatesHistoryPanelsAndSurvivesRotationWithoutChangingArtwork(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.newCanvas(120,100,background=0xffe3b0a4.toInt());val root=a.editor!!;until{a.session!!.frameSerial>0};layout(root);root.setLightInterface(true);layout(root)
        val theme=StudioTheme(true);val history=find(root,"History and save")!!;assertEquals(theme.toolbar,(history.background as android.graphics.drawable.ColorDrawable).color)
        root.backgroundSettings();layout(root);assertEquals(theme.text,(find(root,"White") as TextView).currentTextColor);render(root,"background-light")
        root.dismissPanel();find(root,"Paint")!!.performClick();layout(root);render(root,"brushes-light")
        root.multicolorSettings();layout(root);render(root,"multicolor-light")
        layout(root,800,1280);assertEquals(theme.toolbar,(find(root,"History and save")!!.background as android.graphics.drawable.ColorDrawable).color)
        assertEquals(0xffe3b0a4L,a.session!!.info.getLong("background"));root.setLightInterface(false);layout(root);assertFalse(a.session!!.lightInterface)
        c.pause().stop().destroy();until{a.io.isTerminated}
    }
    @Test fun galleryAndCanvasSetupUseLightSurfacesWithoutTintingColorSwatches(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.getSharedPreferences("studio",0).edit().putBoolean("light",true).apply()
        val gallery=GalleryView(a);layout(gallery);assertEquals(StudioTheme(true).surface,(gallery.background as android.graphics.drawable.ColorDrawable).color);render(gallery,"gallery-light")
        val setup=CanvasSetupView(a);layout(setup);assertEquals(StudioTheme(true).surface,(setup.background as android.graphics.drawable.ColorDrawable).color);assertTrue((find(setup,"Cancel") as ToolIcon).light);render(setup,"canvas-setup-light")
        find(setup,"Canvas properties")!!.performClick();layout(setup);val black=find(setup,"Background #ff000000")!!;assertEquals(Color.BLACK,(black.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor)
        c.pause().stop().destroy();until{a.io.isTerminated}
    }
    @Test fun vectorInheritsTheSavedLightTheme(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();a.getSharedPreferences("studio",0).edit().putBoolean("light",true).apply();a.newCanvas(120,100,mode="vector");val root=a.vectorEditor!!;layout(root);root.backgroundSettings();layout(root)
        assertEquals(StudioTheme(true).text,(find(root,"White") as TextView).currentTextColor);assertTrue((find(root,"Close") as ToolIcon).light);render(root,"vector-background-light")
        c.pause().stop().destroy();until{a.io.isTerminated}
    }
}
