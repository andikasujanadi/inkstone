package art.inkstone

import android.content.Context
import org.json.JSONArray

internal class RecentBrushes(context:Context){
    private val prefs=context.getSharedPreferences("recent-brushes",Context.MODE_PRIVATE)
    private fun key(tool:String)=when(tool){"Erase"->"Erase";"Smudge"->"Smudge";else->"Paint"}
    fun list(tool:String):List<BrushPreset>{
        val names=runCatching{JSONArray(prefs.getString(key(tool),"[]"))}.getOrDefault(JSONArray())
        return (0 until names.length()).mapNotNull{i->brushPresets.firstOrNull{it.name==names.optString(i)}}.distinct()
    }
    fun record(tool:String,preset:BrushPreset){
        val recent=(listOf(preset)+list(tool)).distinctBy{it.name}.take(20)
        prefs.edit().putString(key(tool),JSONArray(recent.map{it.name}).toString()).apply()
    }
}
