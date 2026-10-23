package art.inkstone

import java.nio.ByteBuffer

object NativeBridge {
    init { System.loadLibrary("inkstone") }
    external fun create(width: Int, height: Int): Long
    external fun destroy(handle: Long)
    external fun command(handle: Long, json: String): String
    external fun stroke(handle: Long, phase: Int, samples: FloatArray, kind: Int, color: Int,
        size: Float, opacity: Float, flow: Float, stabilization: Float, pressureSize: Float, pressureOpacity: Float)
    external fun render(handle: Long, pixels: ByteBuffer, background: Boolean): Boolean
    external fun renderPatch(handle: Long, force: Boolean): IntArray?
    external fun preview(handle: Long): IntArray?
    external fun writePsd(handle: Long, path: String): String
    external fun save(handle: Long): ByteArray?
    external fun load(handle: Long, bytes: ByteArray): String
    external fun importPixels(handle: Long, width: Int, height: Int, pixels: IntArray): String
    external fun transformSnapshot(handle:Long):IntArray?
    external fun thumbnails(handle: Long, size: Int): IntArray?
}
