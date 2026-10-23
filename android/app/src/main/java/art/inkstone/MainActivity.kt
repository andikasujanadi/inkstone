package art.inkstone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

class MainActivity : Activity() {
    lateinit var store: DocumentStore
    var session: DrawingSession? = null; private set
    var editor: EditorView? = null; private set
    internal var vectorDocument:VectorDocument?=null; private set
    internal var vectorEditor:VectorEditorView?=null; private set
    internal var vectorStabilization:Float
        get()=prefs.getFloat("vectorStabilization",.25f).coerceIn(0f,1f)
        set(value){prefs.edit().putFloat("vectorStabilization",value.coerceIn(0f,1f)).apply()}
    internal val penOnlyInput get()=prefs.getBoolean("penOnly",false)
    internal val protectVectorControls get()=prefs.getBoolean("protectControls",true)
    internal fun setVectorInputPreference(index:Int,enabled:Boolean){prefs.edit().putBoolean(if(index==0)"penOnly" else "protectControls",enabled).apply()}
    var documentId = ""; private set
    var documentName = "Untitled Artwork"
    val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = object:Runnable {
        override fun run(){if(session?.isStrokeActive==true || vectorEditor?.busy==true)handler.postDelayed(this,1000) else saveCurrent()}
    }
    private var pendingExport: ((Uri) -> Unit)? = null
    private var onImported: ((Uri) -> Unit)? = null
    var lastInputDescription = "Draw with your pen to inspect its input capabilities."
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        store = DocumentStore(this)
        if (store.list().isEmpty()) newCanvas(1600, 1200) else showGallery()
    }

    fun newCanvas(width: Int, height: Int, dpi:Int=300, name:String="Untitled Artwork", background:Int=Color.WHITE, visible:Boolean=true, mode:String="raster") {
        if(mode=="vector"){
            runCatching{require(width in 16..8192 && height in 16..8192 && width.toLong()*height<=16_777_216);val doc=VectorDocument(width,height,dpi,background,visible);openVector(doc,store.newId(),name);scheduleSave()}.onFailure{message(it.message?:"Cannot create Vector canvas")};return
        }
        runCatching {
            val next = DrawingSession(width, height)
            vectorDocument=null;vectorEditor=null
            session?.close(); session = next; documentId = store.newId(); documentName = name
            next.command("setup",org.json.JSONObject().put("dpi",dpi).put("color",background.toLong() and 0xffffffffL).put("visible",visible))
            configure(next); openEditor(); scheduleSave()
        }.onFailure { message(it.message ?: "Cannot create this canvas") }
    }
    private fun openVector(doc:VectorDocument,id:String,name:String){
        session?.close();session=null;editor=null;canvasSetup=null;canvasPopup?.dismiss();canvasPopup=null
        vectorDocument=doc;documentId=id;documentName=name
        vectorEditor=VectorEditorView(this,doc);setContentView(vectorEditor);vectorEditor?.let{StudioMotion.canvasEnter(it)}
    }
    private fun loadedDocument(bytes:ByteArray,id:String,name:String){
        io.execute{
            val parsed=runCatching{if(VectorDocument.isVector(bytes))VectorDocument.decode(bytes)else null}
            runOnUiThread{
                if(isDestroyed)return@runOnUiThread
                parsed.onSuccess{vector->
                    if(vector!=null)openVector(vector,id,name)
                    else{val s=DrawingSession(32,32);s.onError=::message
                        s.load(bytes){ok->if(ok){session?.close();session=s;vectorDocument=null;vectorEditor=null;documentId=id;documentName=name;configure(s);openEditor()}else s.close()}}
                }.onFailure{message("Could not open project: ${it.message}")}
            }
        }
    }
    private fun configure(s: DrawingSession) {
        s.setRenderer(prefs.getString("rendererMode","auto")?:"auto")
        s.eraserHardness=prefs.getFloat("eraserHardness",1f)
        s.pressureEnabled=prefs.getBoolean("pressureEnabled",true);s.canvasLocked=prefs.getBoolean("canvasLocked",false);s.quickShape=prefs.getBoolean("quickShape",true)
        s.penOnly=prefs.getBoolean("penOnly",false)
        s.protectControls=prefs.getBoolean("protectControls",true)
        s.stabilization=prefs.getFloat("stabilization",.12f).coerceIn(0f,.95f)
        s.fingerDrawing = prefs.getBoolean("finger", true)
        s.rightSidebar = prefs.getBoolean("right", false)
        s.lightInterface = prefs.getBoolean("light", false)
        s.cursor = prefs.getBoolean("cursor", true)
        s.dynamicBrushScaling=prefs.getBoolean("dynamicBrushScaling",true)
        s.rapidUndoDelay=prefs.getFloat("rapidUndoDelay",.47f)
        s.selectionVisibility=prefs.getFloat("selectionVisibility",.3f)
        s.cursorStyle=prefs.getString("cursorStyle","Contrast")?:"Contrast"
        s.cursorOpacity=prefs.getFloat("cursorOpacity",1f)
        s.twoFingerUndo=prefs.getBoolean("twoFingerUndo",true);s.threeFingerRedo=prefs.getBoolean("threeFingerRedo",true)
        s.fourFingerFullScreen=prefs.getBoolean("fourFingerFullScreen",true);s.holdEyedropper=prefs.getBoolean("holdEyedropper",true);s.penButtonErase=prefs.getBoolean("penButtonErase",true)
        s.color = prefs.getInt("color", 0xff202020.toInt())
    }
    fun savePreferences() {
        val s = session ?: return
        prefs.edit().putFloat("eraserHardness",s.eraserHardness).putBoolean("penOnly",s.penOnly).putBoolean("protectControls",s.protectControls).putFloat("stabilization",s.stabilization).putString("rendererMode",s.rendererMode).putBoolean("pressureEnabled",s.pressureEnabled).putBoolean("canvasLocked",s.canvasLocked).putBoolean("quickShape",s.quickShape).putString("cursorStyle",s.cursorStyle).putFloat("cursorOpacity",s.cursorOpacity)
            .putBoolean("twoFingerUndo",s.twoFingerUndo).putBoolean("threeFingerRedo",s.threeFingerRedo)
            .putBoolean("fourFingerFullScreen",s.fourFingerFullScreen).putBoolean("holdEyedropper",s.holdEyedropper).putBoolean("penButtonErase",s.penButtonErase)
            .putBoolean("finger", s.fingerDrawing).putBoolean("right", s.rightSidebar)
            .putBoolean("dynamicBrushScaling",s.dynamicBrushScaling).putFloat("rapidUndoDelay",s.rapidUndoDelay).putFloat("selectionVisibility",s.selectionVisibility)
            .putBoolean("light", s.lightInterface).putBoolean("cursor", s.cursor).putInt("color", s.color).apply()
    }
    private fun openEditor() {
        val s = session ?: return
        editor = EditorView(this, s)
        setContentView(editor);editor?.let{StudioMotion.canvasEnter(it)}; s.requestRender()
    }
    fun deferAutosave(){handler.removeCallbacks(autosave)}
    fun scheduleSave() { handler.removeCallbacks(autosave); handler.postDelayed(autosave, 1800) }
    fun saveCurrent(then: (Boolean) -> Unit = {}) {
        handler.removeCallbacks(autosave)
        vectorDocument?.let{doc->
            val bytes=vectorEditor?.snapshotBytes()?:doc.encode();val id=documentId;val name=documentName
            io.execute{val result=runCatching{val copy=VectorDocument.decode(bytes);val image=copy.bitmap(480);try{store.save(id,name,bytes,image,doc.width,doc.height,"vector")}finally{image.recycle()}}
                runOnUiThread{result.onFailure{message("Could not save Vector project: ${it.message}")};then(result.isSuccess)}};return
        }
        val s = session ?: run { then(true); return }
        s.persist(store, documentId, documentName) { error ->
            if (error != null) message("Could not save: $error")
            then(error == null)
        }
        savePreferences()
    }
    fun gallery() {
        editor?.showNotice("Saving artwork…")
        saveCurrent { saved -> if (saved && !isDestroyed){val view=editor?:vectorEditor;if(view!=null)StudioMotion.canvasExit(view){if(!isDestroyed)showGallery()} else showGallery()} }
    }
    fun showGallery() {
        canvasSetup=null;canvasPopup?.dismiss();canvasPopup=null
        session?.close(); session = null
        editor = null;vectorDocument=null;vectorEditor=null
        setContentView(GalleryView(this).also{StudioMotion.canvasEnter(it)})
    }
    fun openArtwork(art: Artwork) {
        io.execute {
            val data = runCatching { store.read(art.id) }
            runOnUiThread {
                data.onSuccess { bytes -> loadedDocument(bytes,art.id,art.name)
                }.onFailure { message("Could not open artwork: ${it.message}") }
            }
        }
    }
    internal var canvasPopup:PopupWindow?=null
    internal var canvasSetup:CanvasSetupView?=null
    fun createDialog(anchor:View?=null) {
        canvasPopup?.dismiss()
        val d=resources.displayMetrics.density
        val content=NewCanvasMenu(this){canvasPopup?.dismiss();canvasPopup=null}
        val popup=PopupWindow(content,minOf((400*d).toInt(),resources.displayMetrics.widthPixels-(24*d).toInt()),minOf((730*d).toInt(),resources.displayMetrics.heightPixels-(98*d).toInt()),true)
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.isOutsideTouchable=true;popup.elevation=12*d
        canvasPopup=popup
        popup.showAtLocation(window.decorView,Gravity.TOP or Gravity.RIGHT,(18*d).toInt(),(82*d).toInt())
        StudioMotion.reveal(content,1f)
    }
    fun customCanvas(mode:String="raster"){canvasSetup=CanvasSetupView(this,mode);setContentView(canvasSetup)}
    fun renameCurrent() {
        textDialog("Artwork name", documentName) { name -> documentName = name; scheduleSave() }
    }
    fun textDialog(title: String, initial: String, then: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); selectAll(); isSingleLine = true }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply { setPadding(pad, 12, pad, 0); addView(input) }
        AlertDialog.Builder(this).setTitle(title).setView(container).setPositiveButton("Done") { _, _ ->
            input.text.toString().trim().take(100).takeIf { it.isNotEmpty() }?.let(then)
        }.setNegativeButton("Cancel", null).show()
    }
    private var pendingExportCleanup:(()->Unit)?=null
    private var pendingGalleryKind:String?=null
    private fun chooseExport(suffix:String,mime:String,cleanup:()->Unit={},write:(java.io.OutputStream)->Unit){
        if(isDestroyed){cleanup();return}
        pendingExportCleanup?.invoke();pendingExportCleanup=cleanup
        val name=documentName
        pendingExport={uri->io.execute{
            val result=runCatching{contentResolver.openOutputStream(uri,"wt").use{write(checkNotNull(it){"No output stream"})}}
            cleanup()
            runOnUiThread{result.onSuccess{message("Exported $name.$suffix")}.onFailure{message("Export failed: ${it.message}")}}
        }}
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,"$name.$suffix")
        try{
            @Suppress("DEPRECATION")
            startActivityForResult(intent,100)
        }catch(e:Exception){pendingExport=null;pendingExportCleanup=null;cleanup();message("Could not open file picker: ${e.message}")}
    }
    fun export(kind:String){
        if(vectorDocument!=null){exportVector(kind);return}
        val s=session?:return
        when(kind){
            "PSD"->{
                val file=runCatching{java.io.File.createTempFile("inkstone-export-",".psd",cacheDir)}.getOrElse{message("Export failed: ${it.message}");return}
                editor?.showNotice("Preparing layered PSD…")
                s.writePsd(file){error->
                    if(error!=null)message("PSD export failed: $error")
                    else chooseExport("psd","image/vnd.adobe.photoshop",{file.delete()}){output->file.inputStream().use{it.copyTo(output)}}
                }
            }
            "Inkstone"->s.documentSnapshot{bytes->
                if(bytes==null)message("Could not export artwork")
                else chooseExport("inkstone","application/octet-stream"){output->GZIPOutputStream(output).use{it.write(bytes)}}
            }
            "PNG","JPEG"->s.imageSnapshot{image->
                if(image==null)message("Could not export artwork")
                else chooseExport(if(kind=="JPEG")"jpg" else "png",if(kind=="JPEG")"image/jpeg" else "image/png",{image.recycle()}){output->ImageExports.encode(image,kind,output)}
            }
            else->message("Unsupported export format")
        }
    }
    private fun vectorImage(then:(Bitmap?)->Unit){
        val bytes=vectorEditor?.snapshotBytes()?:vectorDocument?.encode()?:return
        io.execute{val result=runCatching{VectorDocument.decode(bytes).bitmap()};runOnUiThread{if(isDestroyed)result.getOrNull()?.recycle() else then(result.getOrNull())}}
    }
    private fun exportVector(kind:String){
        val bytes=vectorEditor?.snapshotBytes()?:vectorDocument?.encode()?:return
        when(kind){
            "Inkstone"->chooseExport("inkstone","application/octet-stream"){output->GZIPOutputStream(output).use{it.write(bytes)}}
            "SVG"->io.execute{val result=runCatching{VectorDocument.decode(bytes).svg().toByteArray(Charsets.UTF_8)};runOnUiThread{result.onSuccess{svg->chooseExport("svg","image/svg+xml"){it.write(svg)}}.onFailure{message("SVG export failed: ${it.message}")}}}
            "PNG","JPEG"->vectorImage{image->if(image==null)message("Could not render Vector artwork")else chooseExport(if(kind=="JPEG")"jpg" else "png",if(kind=="JPEG")"image/jpeg" else "image/png",{image.recycle()}){ImageExports.encode(image,kind,it)}}
            "PSD"->{val file=runCatching{java.io.File.createTempFile("inkstone-export-",".psd",cacheDir)}.getOrElse{message(it.message?:"Could not create PSD");return}
                message("Preparing rasterized Vector layers…")
                io.execute{val result=runCatching{VectorDocument.decode(bytes).writePsd(file)};runOnUiThread{result.onSuccess{chooseExport("psd","image/vnd.adobe.photoshop",{file.delete()}){output->file.inputStream().use{it.copyTo(output)}}}.onFailure{file.delete();message("PSD export failed: ${it.message}")}}}
            }
        }
    }
    fun saveImageToGallery(kind:String){
        if(kind !in listOf("PNG","JPEG"))return
        if(android.os.Build.VERSION.SDK_INT<29 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            pendingGalleryKind=kind;requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),102);return
        }
        val name=documentName
        val save:(Bitmap?)->Unit={image->
            if(image==null)message("Could not render artwork")
            else io.execute{val result=runCatching{ImageExports.saveToGallery(this,image,kind,name)};image.recycle();runOnUiThread{result.onSuccess{message("Saved to Pictures/Inkstone")}.onFailure{message("Gallery save failed: ${it.message}")}}}
        }
        if(vectorDocument!=null){vectorImage(save);return}
        session?.imageSnapshot{image->
            if(image==null){message("Could not render artwork");return@imageSnapshot}
            io.execute{
                val result=runCatching{ImageExports.saveToGallery(this,image,kind,name)}
                image.recycle();runOnUiThread{result.onSuccess{message("Saved to Pictures/Inkstone")}.onFailure{message("Gallery save failed: ${it.message}")}}
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==102){val kind=pendingGalleryKind;pendingGalleryKind=null
            if(kind!=null && grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)saveImageToGallery(kind)
            else message("Gallery access denied. You can still export PNG or JPG to a folder.")
        }
    }
    fun importImage() {
        if(vectorDocument!=null){message("Photo insertion belongs to Raster canvases. Vector mode stores paths and shapes.");return}
        onImported = { uri ->
            io.execute {
                val result = runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image format" }
                    var sample = 1
                    while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 8_388_608) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    contentResolver.openInputStream(uri).use { checkNotNull(BitmapFactory.decodeStream(it, null, opts)) }
                }
                runOnUiThread {
                    result.onSuccess { image ->
                        if (editor == null) newCanvas(image.width.coerceAtLeast(16), image.height.coerceAtLeast(16))
                        session?.import(image) { editor?.startLayerTransform();scheduleSave() }; image.recycle()
                    }.onFailure { message("Import failed: ${it.message}") }
                }
            }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 101)
    }
    fun importDocument() {
        onImported = { uri ->
            io.execute {
                val result = runCatching { contentResolver.openInputStream(uri).use { DocumentStore.decode(checkNotNull(it)) } }
                runOnUiThread {
                    result.onSuccess { bytes ->
                        loadedDocument(bytes,store.newId(),"Imported Artwork")
                    }.onFailure { message("Import failed: ${it.message}") }
                }
            }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 101)
    }
    @Deprecated("Activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==100){
            val action=pendingExport;val cleanup=pendingExportCleanup;pendingExport=null;pendingExportCleanup=null
            val uri=data?.data
            if(resultCode==RESULT_OK && uri!=null)action?.invoke(uri) else cleanup?.invoke()
        }else if(requestCode==101){if(resultCode==RESULT_OK)data?.data?.let{onImported?.invoke(it)};onImported=null}
    }
    fun message(text: String) { if (!isDestroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    override fun onPause() { super.onPause(); saveCurrent() }
    override fun onDestroy() { pendingExportCleanup?.invoke();pendingExportCleanup=null;pendingExport=null;handler.removeCallbacks(autosave); session?.close(); io.shutdown(); super.onDestroy() }
    @Deprecated("Native back handling")
    override fun onBackPressed() { if(canvasSetup!=null){showGallery();return};if(canvasPopup?.isShowing==true){canvasPopup?.dismiss();return}; if(vectorEditor!=null){if(vectorEditor?.dismissPanel()!=true)gallery();return};if (editor != null) { if (!editor!!.dismissPanel()) gallery() } else super.onBackPressed() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if(vectorEditor?.key(keyCode,event)==true)return true
        val s = session
        if (s != null && editor != null) {
            if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Z) { s.command(if (event.isShiftPressed) "redo" else "undo"); scheduleSave(); return true }
            if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_S) { saveCurrent { if (it) message("Artwork saved") }; return true }
            if (keyCode == KeyEvent.KEYCODE_B) { s.tool = "Paint"; editor?.refreshTools(); return true }
            if (keyCode == KeyEvent.KEYCODE_E) { s.tool = "Erase"; editor?.refreshTools(); return true }
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) { editor?.dismissPanel(); s.command("deselect"); editor?.canvas?.deselect(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
