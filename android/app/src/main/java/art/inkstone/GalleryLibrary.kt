package art.inkstone

internal data class GalleryEntry(val art:Artwork?=null,val stack:ArtworkStack?=null,val members:List<Artwork> = emptyList(),val total:Int=members.size){
    val name get()=art?.name?:stack!!.name
    val modified get()=art?.modified?:members.maxOfOrNull{it.modified}?:stack!!.created
    val created get()=art?.created?:stack!!.created
}
internal object GalleryLibrary {
    val sorts=listOf("Recently updated","Oldest updated","Name A–Z","Name Z–A","Newest created","Oldest created")
    fun entries(artworks:List<Artwork>,stacks:List<ArtworkStack>,inside:String?,tag:String?,sort:Int):List<GalleryEntry>{
        fun matches(a:Artwork)=tag==null || a.tags.any{it.equals(tag,true)}
        val known=stacks.map{it.id}.toSet()
        val entries=if(inside!=null)artworks.filter{it.stackId==inside && matches(it)}.map{GalleryEntry(art=it)}else{
            artworks.filter{(it.stackId==null || it.stackId !in known) && matches(it)}.map{GalleryEntry(art=it)}+
                stacks.mapNotNull{stack->val all=artworks.filter{it.stackId==stack.id};val match=all.filter(::matches);if(tag!=null && match.isEmpty())null else GalleryEntry(stack=stack,members=match,total=all.size)}
        }
        val comparator=when(sort){1->compareBy<GalleryEntry>{it.modified};2->compareBy{it.name.lowercase(java.util.Locale.ROOT)};3->compareByDescending{it.name.lowercase(java.util.Locale.ROOT)};4->compareByDescending{it.created};5->compareBy{it.created};else->compareByDescending{it.modified}}
        return entries.sortedWith(comparator.thenBy{it.art?.id?:it.stack!!.id})
    }
}
