package art.inkstone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class Artwork(val id: String, val name: String, val width: Int, val height: Int, val modified: Long)

class DocumentStore(context: Context) {
    private val root = File(context.filesDir, "artworks").apply { mkdirs() }
    fun newId() = UUID.randomUUID().toString()
    private fun file(id: String, suffix: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        return File(root, "$id.$suffix")
    }
    fun list(): List<Artwork> = root.listFiles()?.filter { it.extension == "json" }?.mapNotNull {
        runCatching {
            val j = JSONObject(it.readText())
            Artwork(it.nameWithoutExtension, j.getString("name"), j.getInt("width"), j.getInt("height"), j.getLong("modified"))
        }.getOrNull()
    }?.sortedByDescending { it.modified } ?: emptyList()
    fun save(id: String, name: String, bytes: ByteArray, image: Bitmap, documentWidth:Int=image.width, documentHeight:Int=image.height) {
        atomic(file(id, "inkstone")) { output -> GZIPOutputStream(output).use { it.write(bytes) } }
        val scale=minOf(1f,480f/maxOf(image.width,image.height))
        val thumbnail=if(scale<1f)Bitmap.createScaledBitmap(image,(image.width*scale).toInt().coerceAtLeast(1),(image.height*scale).toInt().coerceAtLeast(1),true) else image
        try { atomic(file(id, "png")) { check(thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { if(thumbnail!==image)thumbnail.recycle() }
        val metadata = JSONObject().put("name", name).put("width", documentWidth).put("height", documentHeight)
            .put("modified", System.currentTimeMillis()).toString().toByteArray()
        atomic(file(id, "json")) { it.write(metadata) }
    }
    private fun atomic(file: File, write: (java.io.OutputStream) -> Unit) {
        val target = AtomicFile(file); val stream = target.startWrite()
        try {
            // Keep AtomicFile's stream open until its finishWrite fsync.
            val shield = object : java.io.FilterOutputStream(stream) {
                override fun write(bytes:ByteArray,offset:Int,length:Int){out.write(bytes,offset,length)}
                override fun close() { flush() }
            }
            write(shield); target.finishWrite(stream)
        } catch (e: Exception) { target.failWrite(stream); throw e }
    }
    fun read(id: String): ByteArray = file(id, "inkstone").inputStream().use(::decode)
    fun thumbnail(id: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(id, "png").path, bounds)
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 480).coerceAtLeast(1)
        return BitmapFactory.decodeFile(file(id, "png").path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun delete(id: String) { listOf("inkstone", "png", "json").forEach { file(id, it).delete() } }
    fun rename(id: String, name: String) {
        val f = file(id, "json"); val j = JSONObject(f.readText()).put("name", name.take(100))
        atomic(f) { it.write(j.toString().toByteArray()) }
    }
    fun duplicate(id: String) {
        val next = newId()
        file(id, "inkstone").copyTo(file(next, "inkstone"))
        file(id, "png").copyTo(file(next, "png"))
        val metadata = JSONObject(file(id, "json").readText())
            .put("name", JSONObject(file(id, "json").readText()).getString("name") + " copy")
            .put("modified", System.currentTimeMillis()).toString().toByteArray()
        atomic(file(next, "json")) { it.write(metadata) }
    }
    companion object {
        fun decode(input: InputStream): ByteArray = GZIPInputStream(input).use { gzip ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(32768); var total = 0
            while (true) {
                val n = gzip.read(chunk); if (n < 0) break
                total += n; require(total <= 128 * 1024 * 1024) { "Document exceeds the 128 MB import limit" }
                out.write(chunk, 0, n)
            }
            out.toByteArray()
        }
    }
}
