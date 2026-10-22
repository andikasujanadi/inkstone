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
    var documentId = ""; private set
    var documentName = "Untitled Artwork"
    val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = object:Runnable {
        override fun run(){if(session?.isStrokeActive==true)handler.postDelayed(this,1000) else saveCurrent()}
    }
    private var pendingExport: ((Uri) -> Unit)? = null
    private var onImported: ((Uri) -> Unit)? = null
    var lastInputDescription = "Draw with your pen to inspect its input capabilities."
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        store = DocumentStore(this)
        if (store.list().isEmpty()) newCanvas(1600, 1200) else showGallery()
    }

    fun newCanvas(width: Int, height: Int, dpi:Int=300, name:String="Untitled Artwork", background:Int=Color.WHITE, visible:Boolean=true) {
        runCatching {
            val next = DrawingSession(width, height)
            session?.close(); session = next; documentId = store.newId(); documentName = name
            next.command("setup",org.json.JSONObject().put("dpi",dpi).put("color",background.toLong() and 0xffffffffL).put("visible",visible))
            configure(next); openEditor(); scheduleSave()
        }.onFailure { message(it.message ?: "Cannot create this canvas") }
    }
    private fun configure(s: DrawingSession) {
        s.setRenderer(prefs.getString("rendererMode","auto")?:"auto")
        s.pressureEnabled=prefs.getBoolean("pressureEnabled",true);s.canvasLocked=prefs.getBoolean("canvasLocked",false);s.quickShape=prefs.getBoolean("quickShape",true)
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
        prefs.edit().putString("rendererMode",s.rendererMode).putBoolean("pressureEnabled",s.pressureEnabled).putBoolean("canvasLocked",s.canvasLocked).putBoolean("quickShape",s.quickShape).putString("cursorStyle",s.cursorStyle).putFloat("cursorOpacity",s.cursorOpacity)
            .putBoolean("twoFingerUndo",s.twoFingerUndo).putBoolean("threeFingerRedo",s.threeFingerRedo)
            .putBoolean("fourFingerFullScreen",s.fourFingerFullScreen).putBoolean("holdEyedropper",s.holdEyedropper).putBoolean("penButtonErase",s.penButtonErase)
            .putBoolean("finger", s.fingerDrawing).putBoolean("right", s.rightSidebar)
            .putBoolean("dynamicBrushScaling",s.dynamicBrushScaling).putFloat("rapidUndoDelay",s.rapidUndoDelay).putFloat("selectionVisibility",s.selectionVisibility)
            .putBoolean("light", s.lightInterface).putBoolean("cursor", s.cursor).putInt("color", s.color).apply()
    }
    private fun openEditor() {
        val s = session ?: return
        editor = EditorView(this, s)
        setContentView(editor); s.requestRender()
    }
    fun deferAutosave(){handler.removeCallbacks(autosave)}
    fun scheduleSave() { handler.removeCallbacks(autosave); handler.postDelayed(autosave, 1800) }
    fun saveCurrent(then: (Boolean) -> Unit = {}) {
        handler.removeCallbacks(autosave)
        val s = session ?: run { then(true); return }
        s.persist(store, documentId, documentName) { error ->
            if (error != null) message("Could not save: $error")
            then(error == null)
        }
        savePreferences()
    }
    fun gallery() {
        editor?.showNotice("Saving artwork…")
        saveCurrent { saved -> if (saved && !isDestroyed) showGallery() }
    }
    fun showGallery() {
        canvasSetup=null;canvasPopup?.dismiss();canvasPopup=null
        session?.close(); session = null
        editor = null
        setContentView(GalleryView(this))
    }
    fun openArtwork(art: Artwork) {
        io.execute {
            val data = runCatching { store.read(art.id) }
            runOnUiThread {
                data.onSuccess { bytes ->
                    val s = DrawingSession(32, 32)
                    s.onError = ::message
                    s.load(bytes) { ok ->
                        if (ok) { session?.close(); session = s; documentId = art.id; documentName = art.name; configure(s); openEditor() }
                        else s.close()
                    }
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
    fun customCanvas(){canvasSetup=CanvasSetupView(this);setContentView(canvasSetup)}
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
    fun export(kind: String) {
        val s = session ?: return
        s.snapshot { bytes, image ->
            if (bytes == null || image == null) { message("Could not export artwork"); return@snapshot }
            val suffix = if (kind == "Inkstone") "inkstone" else if (kind == "JPEG") "jpg" else "png"
            pendingExport = { uri ->
                io.execute {
                    val result = runCatching {
                        contentResolver.openOutputStream(uri, "wt").use { output ->
                            checkNotNull(output) { "No output stream" }
                            if (kind == "Inkstone") GZIPOutputStream(output).use { it.write(bytes) }
                            else check(image.compress(if (kind == "JPEG") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, output))
                        }
                    }
                    image.recycle()
                    runOnUiThread { result.onSuccess { message("Exported ${documentName}.$suffix") }.onFailure { message("Export failed: ${it.message}") } }
                }
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(if (kind == "Inkstone") "application/octet-stream" else if (kind == "JPEG") "image/jpeg" else "image/png")
                .putExtra(Intent.EXTRA_TITLE, "$documentName.$suffix")
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 100)
        }
    }
    fun importImage() {
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
                        session?.import(image) { scheduleSave() }; image.recycle()
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
                        val s = DrawingSession(32, 32); s.onError = ::message
                        s.load(bytes) { ok ->
                            if (ok) { session?.close(); session = s; documentId = store.newId(); documentName = "Imported Artwork"; configure(s); openEditor(); scheduleSave() }
                            else s.close()
                        }
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
        if (resultCode == RESULT_OK) data?.data?.let { if (requestCode == 100) pendingExport?.invoke(it) else if (requestCode == 101) onImported?.invoke(it) }
        pendingExport = null; onImported = null
    }
    fun message(text: String) { if (!isDestroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    override fun onPause() { super.onPause(); saveCurrent() }
    override fun onDestroy() { handler.removeCallbacks(autosave); session?.close(); io.shutdown(); super.onDestroy() }
    @Deprecated("Native back handling")
    override fun onBackPressed() { if(canvasSetup!=null){showGallery();return};if(canvasPopup?.isShowing==true){canvasPopup?.dismiss();return}; if (editor != null) { if (!editor!!.dismissPanel()) gallery() } else super.onBackPressed() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
