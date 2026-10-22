package art.inkstone

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*

class GalleryView(private val activity: MainActivity) : LinearLayout(activity) {
    private val density=resources.displayMetrics.density
    private fun dp(v:Int)=(v*density).roundToInt()
    private var selecting=false
    private val selected=mutableSetOf<String>()
    private val blue=0xff0a84ff.toInt()
    private fun label(text:String,size:Float=15f,color:Int=Color.WHITE)=TextView(activity).apply{
        this.text=text;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(12),0)
    }
    init {orientation=VERTICAL;setBackgroundColor(0xff242424.toInt());rebuild()}
    private fun rebuild(){
        removeAllViews()
        val narrow=resources.displayMetrics.widthPixels/density<600
        val header=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(if(narrow)12 else 36),dp(6),dp(16),0)}
        header.addView(label("Inkstone",if(narrow)27f else 36f).apply{typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)},LayoutParams(0,dp(80),1f))
        fun action(text:String,run:()->Unit){header.addView(label(text,15f).apply{gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setOnClickListener{run()}},LayoutParams(-2,dp(52)))}
        action(if(selecting)"Done" else "Select"){selecting=!selecting;selected.clear();rebuild()}
        if(!narrow){action("Import"){activity.importDocument()};action("Photo"){activity.importImage()}}
        header.addView(ToolIcon(activity,"Add"){activity.createDialog()},LayoutParams(dp(48),dp(52)))
        addView(header)
        if(narrow){val imports=LinearLayout(activity).apply{gravity=Gravity.RIGHT;setPadding(0,0,dp(18),0)}
            for((name,run)in listOf("Import" to {activity.importDocument()},"Photo" to {activity.importImage()}))imports.addView(label(name).apply{isFocusable=true;setOnClickListener{run()}},LayoutParams(-2,dp(42)))
            addView(imports)
        }
        val artworks=activity.store.list()
        val scroll=ScrollView(activity).apply{isFillViewport=artworks.isEmpty();isVerticalScrollBarEnabled=false}
        val content=LinearLayout(activity).apply{orientation=VERTICAL;setPadding(dp(if(narrow)16 else 42),dp(20),dp(if(narrow)16 else 42),dp(40))}
        if(artworks.isEmpty()){
            val empty=LinearLayout(activity).apply{orientation=VERTICAL;gravity=Gravity.CENTER}
            empty.addView(label("Your next canvas.",30f).apply{gravity=Gravity.CENTER},LayoutParams(-1,dp(65)))
            empty.addView(label("Tap + to create an artwork, or import a photo.",15f,0xffb0b0b0.toInt()).apply{gravity=Gravity.CENTER},LayoutParams(-1,dp(64)))
            empty.addView(label("Create canvas",16f,blue).apply{gravity=Gravity.CENTER;isFocusable=true;setOnClickListener{activity.createDialog()}},LayoutParams(-1,dp(52)))
            content.addView(empty,LayoutParams(-1,-1))
        } else {
            val available=resources.displayMetrics.widthPixels/density-(if(narrow)32 else 84)
            val columns=if(available<500)2 else if(available<850)3 else 4
            val cardW=(available/columns).toInt()
            for(chunk in artworks.chunked(columns)){
                val row=LinearLayout(activity)
                for(art in chunk){row.addView(card(art,cardW),LayoutParams(0,-2,1f))}
                repeat(columns-chunk.size){row.addView(View(activity),LayoutParams(0,dp(1),1f))}
                content.addView(row)
            }
        }
        scroll.addView(content);addView(scroll,LayoutParams(-1,0,1f))
        if(selecting){val bottom=LinearLayout(activity).apply{gravity=Gravity.CENTER;setBackgroundColor(0xff242424.toInt())}
            bottom.addView(label("${selected.size} selected",14f,0xffbbbbbb.toInt()),LayoutParams(0,dp(60),1f))
            bottom.addView(label("Duplicate",15f,blue).apply{isFocusable=true;setOnClickListener{
                val ids=selected.toList();activity.io.execute{val result=runCatching{ids.forEach{activity.store.duplicate(it)}};activity.runOnUiThread{result.onFailure{activity.message(it.message?:"Could not duplicate")};selected.clear();rebuild()}}
            }},LayoutParams(-2,dp(60)))
            bottom.addView(label("Delete",15f,0xffff6258.toInt()).apply{isFocusable=true;setOnClickListener{deleteSelected()}},LayoutParams(-2,dp(60)));addView(bottom)
        }
    }
    private fun card(art:Artwork,widthDp:Int):View{
        val card=LinearLayout(activity).apply{orientation=VERTICAL;setPadding(dp(10),0,dp(10),dp(30));isClickable=true;isFocusable=true;contentDescription="${art.name}, ${art.width} by ${art.height} pixels"}
        val frame=FrameLayout(activity).apply{
            background=GradientDrawable().apply{setColor(0xff202020.toInt());cornerRadius=dp(StudioShape.CORNER_DP).toFloat();if(art.id in selected)setStroke(dp(3),blue)}
        }
        val image=ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(dp(7),dp(7),dp(7),dp(7))}
        frame.addView(image,FrameLayout.LayoutParams(-1,-1))
        activity.io.execute{val bitmap=activity.store.thumbnail(art.id);activity.runOnUiThread{if(isAttachedToWindow)image.setImageBitmap(bitmap)}}
        if(art.id in selected)frame.addView(label("✓",22f,blue).apply{gravity=Gravity.RIGHT or Gravity.TOP},FrameLayout.LayoutParams(-1,dp(40)))
        card.addView(frame,LayoutParams(-1,dp((widthDp*.72f).toInt())))
        card.addView(label(art.name,14f).apply{setPadding(dp(2),0,dp(2),0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LayoutParams(-1,dp(32)))
        card.addView(label("${art.width} × ${art.height}px",11f,0xff999999.toInt()).apply{setPadding(dp(2),0,0,0)},LayoutParams(-1,dp(18)))
        card.setOnClickListener{if(selecting){if(!selected.add(art.id))selected.remove(art.id);rebuild()}else activity.openArtwork(art)}
        card.setOnLongClickListener{
            AlertDialog.Builder(activity).setTitle(art.name).setItems(arrayOf("Rename","Duplicate","Delete")){_,which->
                when(which){
                    0->activity.textDialog("Artwork name",art.name){name->activity.store.rename(art.id,name);rebuild()}
                    1->{activity.io.execute{val result=runCatching{activity.store.duplicate(art.id)};activity.runOnUiThread{result.onFailure{activity.message(it.message?:"Could not duplicate")};rebuild()}}}
                    2->{selected.clear();selected.add(art.id);deleteSelected()}
                }
            }.show();true
        };return card
    }
    private fun deleteSelected(){
        if(selected.isEmpty())return
        AlertDialog.Builder(activity).setTitle("Delete ${selected.size} artwork${if(selected.size==1)"" else "s"}?")
            .setMessage("This removes the local artwork and its layers. Export a copy first if you want to keep it.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->selected.forEach{activity.store.delete(it)};selected.clear();rebuild()}.show()
    }
}
