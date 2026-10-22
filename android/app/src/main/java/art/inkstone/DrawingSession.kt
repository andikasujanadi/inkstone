package art.inkstone

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class BrushPreset(val name: String, val group: String, val kind: Int, val size: Float,
    val flow: Float = 1f, val pressureSize: Float = .8f, val pressureOpacity: Float = 0f)

val brushPresets = listOf(
    BrushPreset("Studio Pen", "Inking", 0, 12f),
    BrushPreset("Technical Pen", "Inking", 0, 4f, pressureSize = .35f),
    BrushPreset("Dry Ink", "Inking", 3, 18f, .9f),
    BrushPreset("Graphite", "Sketching", 1, 9f, .8f, .7f, .7f),
    BrushPreset("Soft Pencil", "Sketching", 1, 22f, .65f, .5f, .8f),
    BrushPreset("Compressed Charcoal", "Charcoals", 3, 48f, .8f, .55f, .5f),
    BrushPreset("Willow", "Charcoals", 3, 14f, .65f, .9f, .65f),
    BrushPreset("Round Paint", "Painting", 0, 42f, .55f, .7f, .2f),
    BrushPreset("Water Wash", "Painting", 4, 90f, .8f, .4f, .6f),
    BrushPreset("Flat Brush", "Painting", 7, 55f, .7f, .6f, .2f),
    BrushPreset("Soft Airbrush", "Airbrushing", 2, 110f, .7f, .25f, .7f),
    BrushPreset("Fine Airbrush", "Airbrushing", 2, 32f, .9f, .4f, .5f),
    BrushPreset("Monoline", "Calligraphy", 0, 16f, pressureSize = 0f),
    BrushPreset("Flat Lettering", "Calligraphy", 7, 26f, pressureSize = .9f)
) + additionalPresets()

val brushCategories=listOf("Recent","Sketching","Inking","Drawing","Painting","Artistic","Calligraphy","Airbrushing","Textures","Abstract","Charcoals","Elements","Spraypaints","Materials","Vintage","Luminance","Industrial","Organic","Water","Imported")
private fun additionalPresets():List<BrushPreset> {
    val air=listOf(
        BrushPreset("Soft Brush","Airbrushing",2,120f,.8f,.15f,.35f),
        BrushPreset("Medium Brush","Airbrushing",8,100f,.8f,.2f,.3f),
        BrushPreset("Medium Hard Brush","Airbrushing",9,90f,.8f,.2f,.25f),
        BrushPreset("Hard Brush","Airbrushing",0,80f,1f,.25f,0f),
        BrushPreset("Soft Blend","Airbrushing",2,140f,.25f,.1f,.15f),
        BrushPreset("Medium Blend","Airbrushing",8,120f,.25f,.1f,.15f),
        BrushPreset("Medium Hard Blend","Airbrushing",9,100f,.25f,.1f,.15f),
        BrushPreset("Hard Blend","Airbrushing",0,80f,.18f,.1f,.1f),
        BrushPreset("Medium Airbrush","Airbrushing",8,75f,.55f,.2f,.5f)
    )
    val originals=listOf(
        BrushPreset("Expressive Line","Drawing",0,14f,1f,.9f),
        BrushPreset("Chalk","Drawing",13,28f,.8f,.55f,.4f),
        BrushPreset("Pastel","Drawing",14,38f,.8f,.35f,.3f),
        BrushPreset("Bristle","Artistic",11,54f,.8f,.5f,.2f),
        BrushPreset("Wide Bristle","Painting",11,90f,.65f,.35f,.2f),
        BrushPreset("Opaque Round","Painting",0,70f,1f,.5f),
        BrushPreset("Soft Glaze","Painting",2,160f,.3f,.15f,.3f),
        BrushPreset("Chisel Marker","Inking",12,28f,.85f,.2f),
        BrushPreset("Fine Liner","Inking",0,3f,1f,.1f),
        BrushPreset("Soft Charcoal","Charcoals",13,65f,.65f,.5f,.5f),
        BrushPreset("Rough Grain","Textures",14,75f,.65f,.4f,.4f),
        BrushPreset("Broken Marks","Abstract",3,48f,.6f,.7f,.3f),
        BrushPreset("Dust","Elements",10,130f,.6f,.1f,.4f),
        BrushPreset("Fine Mist","Spraypaints",10,100f,.75f,.15f,.4f),
        BrushPreset("Wide Spray","Spraypaints",10,220f,.6f,.1f,.3f),
        BrushPreset("Dry Surface","Materials",14,60f,.7f,.4f,.3f),
        BrushPreset("Faded Ink","Vintage",13,20f,.5f,.6f,.5f),
        BrushPreset("Soft Glow","Luminance",2,160f,.45f,.2f,.5f),
        BrushPreset("Hard Edge","Industrial",12,48f,1f,.1f),
        BrushPreset("Soft Grain","Organic",14,65f,.55f,.5f,.3f),
        BrushPreset("Diluted Wash","Water",4,130f,.6f,.3f,.5f),
        BrushPreset("Soft Wash","Water",2,180f,.25f,.15f,.5f),
        BrushPreset("Broad Lettering","Calligraphy",7,65f,.8f,.7f)
    )
    return air+originals
}

class DrawingSession(width: Int, height: Int) : Closeable {
    private val worker = Executors.newSingleThreadExecutor()
    private val storage = Executors.newSingleThreadExecutor()
    @Volatile private var penActive=false
    @Volatile private var dropActive=false
    val isStrokeActive get()=penActive||dropActive
    private val dropGeneration=AtomicLong(0)
    private val inputLock=Any()
    private data class StrokeSettings(val kind:Int,val color:Int,val size:Float,val opacity:Float,val flow:Float,val smooth:Float,val pressureSize:Float,val pressureOpacity:Float)
    private class MoveBatch(val settings:StrokeSettings,val chunks:MutableList<FloatArray>,var size:Int)
    private var pendingMoves:MoveBatch?=null
    private val main = Handler(Looper.getMainLooper())
    private var handle = NativeBridge.create(width, height).also { check(it != 0L) { "Cannot create canvas" } }
    @Volatile private var closed = false
    private var disposed = false
    @Volatile internal var fullyClosed=false; private set
    @Volatile var info = JSONObject(NativeBridge.command(handle, "{\"op\":\"info\"}")); private set
    var bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); private set
    private val revision = AtomicLong(0)
    private val renderPending = AtomicBoolean(false)
    var onFrame: () -> Unit = {}
    var onInfo: () -> Unit = {}
    var onError: (String) -> Unit = {}
    var layerThumbnails: List<Bitmap> = emptyList(); private set
    val canvasWidth get() = info.getInt("width")
    val canvasHeight get() = info.getInt("height")

    var preset = brushPresets[0]
    var size = preset.size
    var opacity = 1f
    var flow = preset.flow
    var stabilization = .12f
    var pressureEnabled=true
    var canvasLocked=false
    var quickShape=true
    var pressureSize = preset.pressureSize
    var pressureOpacity = preset.pressureOpacity
    var color = 0xff202020.toInt()
    var tool = "Paint"
    var fingerDrawing = true
    var guide = false
    var symmetry = false
    var cursor = true
    var cursorStyle="Contrast"
    var cursorOpacity=1f
    var twoFingerUndo=true
    var threeFingerRedo=true
    var fourFingerFullScreen=true
    var holdEyedropper=true
    var penButtonErase=true
    var rightSidebar = false
    var lightInterface = false
    var dynamicBrushScaling=true
    var viewZoom=1f
    var rapidUndoDelay=.47f
    var selectionVisibility=.3f

    fun choose(p: BrushPreset) {
        preset = p; size = p.size; flow = p.flow; pressureSize = p.pressureSize; pressureOpacity = p.pressureOpacity
    }

    fun stroke(phase: Int, samples: FloatArray, eraser: Boolean = false) {
        if (closed) return
        if(phase==0)penActive=true
        if(phase==2||phase==3)penActive=false
        val kind=if(eraser||tool=="Erase")5 else if(tool=="Smudge")128+ preset.kind else preset.kind
        val settings=StrokeSettings(kind,color,if(dynamicBrushScaling)size else size/viewZoom.coerceAtLeast(.02f),opacity,flow,stabilization,if(pressureEnabled)pressureSize else 0f,if(pressureEnabled)pressureOpacity else 0f)
        synchronized(inputLock){
            if(phase==1){
                val pending=pendingMoves
                if(pending!=null && pending.settings==settings && pending.size+samples.size<=65536){pending.chunks.add(samples);pending.size+=samples.size;return}
                val batch=MoveBatch(settings,mutableListOf(samples),samples.size);pendingMoves=batch
                worker.execute{
                    val merged=synchronized(inputLock){
                        if(pendingMoves===batch)pendingMoves=null
                        FloatArray(batch.size).also{out->var offset=0;for(chunk in batch.chunks){chunk.copyInto(out,offset);offset+=chunk.size}}
                    }
                    if(!disposed){sendStroke(1,merged,batch.settings);changed()}
                }
            }else{
                pendingMoves=null
                worker.execute{
                    if(!disposed){sendStroke(phase,samples,settings);if(phase==2||phase==3)readInfo();changed()}
                }
            }
        }
    }
    private fun sendStroke(phase:Int,samples:FloatArray,s:StrokeSettings){
        NativeBridge.stroke(handle,phase,samples,s.kind,s.color,s.size,s.opacity,s.flow,s.smooth,s.pressureSize,s.pressureOpacity)
    }

    private var pendingDrop:JSONObject?=null
    private var dropQueued=false
    fun previewColorDrop(values:JSONObject){
        if(closed)return
        dropActive=true;dropGeneration.incrementAndGet()
        synchronized(inputLock){
            pendingDrop=values
            if(dropQueued)return
            dropQueued=true
            worker.execute{
                val latest=synchronized(inputLock){val value=pendingDrop;pendingDrop=null;dropQueued=false;value}
                if(!disposed && latest!=null){latest.put("op","floodPreview")
                    val result=JSONObject(NativeBridge.command(handle,latest.toString()))
                    if(result.has("error"))main.post{if(!closed)onError(result.getString("error"))}
                    changed()
                }
            }
        }
    }
    fun finishColorDrop(cancel:Boolean,then:()->Unit){
        val generation=dropGeneration.get()
        command(if(cancel)"floodCancel" else "floodCommit"){if(dropGeneration.get()==generation)dropActive=false;then()}
    }

    fun command(op: String, values: JSONObject = JSONObject(), then: (JSONObject) -> Unit = {}) {
        if (closed) return
        values.put("op", op)
        synchronized(inputLock){pendingMoves=null}
        worker.execute {
            if (disposed) return@execute
            val result = JSONObject(NativeBridge.command(handle, values.toString()))
            if (result.has("error")) main.post { if (!closed) onError(result.getString("error")) }
            else if (result.has("layers")) info = result
            changed()
            main.post { if (!closed) { onInfo(); then(result) } }
        }
    }

    private fun readInfo() {
        info = JSONObject(NativeBridge.command(handle, "{\"op\":\"info\"}"))
        main.post { if (!closed) onInfo() }
    }

    private fun changed() { revision.incrementAndGet(); requestRender() }

    fun requestRender() {
        if (closed || !renderPending.compareAndSet(false, true)) return
        worker.execute {
            if (closed) { renderPending.set(false); return@execute }
            val version = revision.get()
            val w = canvasWidth; val h = canvasHeight
            val resize=bitmap.width!=w || bitmap.height!=h
            val patch=NativeBridge.renderPatch(handle,resize)
            main.post {
                if (!closed && patch!=null) {
                    if(resize)bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    bitmap.setPixels(patch,4,patch[2],patch[0],patch[1],patch[2],patch[3])
                    onFrame()
                }
                renderPending.set(false)
                if (!closed && revision.get() != version) requestRender()
            }
        }
    }

    fun snapshot(then: (ByteArray?, Bitmap?) -> Unit) {
        if (closed) return
        worker.execute {
            val bytes = NativeBridge.save(handle)
            val buffer = ByteBuffer.allocateDirect(canvasWidth * canvasHeight * 4)
            NativeBridge.render(handle, buffer, true)
            val image = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            copyRgba(image, buffer)
            main.post { if (!closed) then(bytes, image) }
        }
    }

    fun persist(store: DocumentStore, id: String, name: String, then: (String?) -> Unit = {}) {
        if (closed) return
        synchronized(inputLock){pendingMoves=null}
        worker.execute {
            val snapshot=runCatching{
                val bytes=checkNotNull(NativeBridge.save(handle)){"Could not save drawing"}
                val preview=checkNotNull(NativeBridge.preview(handle))
                val image=Bitmap.createBitmap(preview,2,preview[0],preview[0],preview[1],Bitmap.Config.ARGB_8888)
                Triple(bytes,image,canvasWidth to canvasHeight)
            }
            if(snapshot.isFailure){main.post{then(snapshot.exceptionOrNull()?.message?:"Could not save drawing")};return@execute}
            val (bytes,image,dimensions)=snapshot.getOrThrow()
            storage.execute{
                val failure=runCatching{store.save(id,name,bytes,image,dimensions.first,dimensions.second)}.exceptionOrNull()?.message
                image.recycle();main.post{then(failure)}
            }
        }
    }

    fun load(bytes: ByteArray, then: (Boolean) -> Unit) {
        worker.execute {
            val result = JSONObject(NativeBridge.load(handle, bytes))
            if (result.has("error")) main.post { onError(result.getString("error")); then(false) }
            else { info = result; changed(); main.post { if (!closed) { onInfo(); then(true) } } }
        }
    }

    fun import(image: Bitmap, then: () -> Unit) {
        val data = IntArray(image.width * image.height)
        image.getPixels(data, 0, image.width, 0, 0, image.width, image.height)
        worker.execute {
            val result = JSONObject(NativeBridge.importPixels(handle, image.width, image.height, data))
            if (result.has("error")) main.post { onError(result.getString("error")) }
            else { info = result; changed(); main.post { if (!closed) { onInfo(); then() } } }
        }
    }

    fun thumbnails(then: () -> Unit) {
        if (closed) return
        worker.execute {
            val size = 64
            val data = NativeBridge.thumbnails(handle, size)
            val images = if (data == null) emptyList() else (0 until data.size / (size * size)).map { index ->
                Bitmap.createBitmap(data, index * size * size, size, size, size, Bitmap.Config.ARGB_8888)
            }
            main.post { if (!closed) { layerThumbnails = images; then() } }
        }
    }

    override fun close() {
        if(closed)return
        closed = true
        worker.execute { NativeBridge.destroy(handle); handle = 0; disposed=true; storage.execute{fullyClosed=true}; storage.shutdown() }
        worker.shutdown()
    }
}

// Native Skia on desktop test hosts may use BGRA; Android devices normally use RGBA.
private val bitmapUsesBgra: Boolean by lazy {
    val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    probe.setPixel(0, 0, 0xffff0000.toInt())
    val bytes = ByteBuffer.allocateDirect(4)
    probe.copyPixelsToBuffer(bytes)
    probe.recycle()
    bytes.get(0).toInt() == 0
}
private fun copyRgba(bitmap: Bitmap, buffer: ByteBuffer) {
    if (bitmapUsesBgra) {
        for (i in 0 until bitmap.width * bitmap.height * 4 step 4) {
            val red = buffer.get(i)
            buffer.put(i, buffer.get(i + 2)); buffer.put(i + 2, red)
        }
    }
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)
}
