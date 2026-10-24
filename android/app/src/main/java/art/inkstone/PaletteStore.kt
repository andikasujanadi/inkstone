package art.inkstone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class PaletteStore(context:Context){
    data class Palette(val id:String,var name:String,val colors:MutableList<Int>)
    private val preferences=context.getSharedPreferences("color-palettes",Context.MODE_PRIVATE)
    private val custom=mutableListOf<Palette>()
    private val studio=Palette("studio","Studio",mutableListOf(0xff202020.toInt(),0xffffffff.toInt(),0xffcf4c4c.toInt(),0xffdf8c53.toInt(),0xffe4c778.toInt(),0xffa6b78c.toInt(),0xff518476.toInt(),0xff315e70.toInt(),0xff8ca5b8.toInt(),0xff766284.toInt(),0xffb7869d.toInt(),0xffe1c6bc.toInt()))
    private var selected=preferences.getString("selected","studio")?:"studio"
    init{
        runCatching{
            val list=JSONArray(preferences.getString("palettes","[]"))
            for(i in 0 until list.length()){
                val p=list.getJSONObject(i);val id=p.getString("id")
                require(id!="studio" && custom.none{it.id==id})
                val colors=p.getJSONArray("colors")
                custom.add(Palette(id,p.getString("name").take(60),MutableList(colors.length()){colors.getLong(it).toInt() or 0xff000000.toInt()}))
            }
        }.onFailure{custom.clear()}
        if(all.none{it.id==selected})selected="studio"
    }
    val all:List<Palette> get()=listOf(studio)+custom
    val active:Palette get()=all.first{it.id==selected}
    val isCustom:Boolean get()=selected!="studio"
    fun select(id:String){require(all.any{it.id==id});selected=id;save()}
    fun create(name:String){val title=name.trim().take(60);require(title.isNotEmpty()){"Give your palette a name"};val p=Palette(UUID.randomUUID().toString(),title,mutableListOf());custom.add(p);selected=p.id;save()}
    private fun editable():Palette{
        if(!isCustom){val p=Palette(UUID.randomUUID().toString(),"My palette",studio.colors.toMutableList());custom.add(p);selected=p.id}
        return active
    }
    fun add(color:Int):Boolean{
        val ink=color or 0xff000000.toInt()
        if(active.colors.contains(ink))return false
        editable().colors.add(ink);save();return true
    }
    fun replace(index:Int,color:Int){require(index in active.colors.indices);editable().colors[index]=color or 0xff000000.toInt();save()}
    fun rename(name:String){require(isCustom);val title=name.trim().take(60);require(title.isNotEmpty()){"Give your palette a name"};active.name=title;save()}
    fun delete(){require(isCustom);custom.removeAll{it.id==selected};selected="studio";save()}
    private fun save(){
        val list=JSONArray();for(p in custom){val colors=JSONArray();p.colors.forEach{colors.put(it.toLong() and 0xffffffffL)};list.put(JSONObject().put("id",p.id).put("name",p.name).put("colors",colors))}
        preferences.edit().putString("palettes",list.toString()).putString("selected",selected).apply()
    }
}
