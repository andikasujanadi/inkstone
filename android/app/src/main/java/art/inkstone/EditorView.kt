package art.inkstone

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONObject
import kotlin.math.*

class EditorView(val activity: MainActivity, val session: DrawingSession) : FrameLayout(activity) {
    val canvas = PaintCanvas(activity, session)
    private var colorDrop:ColorDropController?=null
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private val narrow get() = resources.displayMetrics.widthPixels / density < 620
    private val barHeight get() = if (narrow) 100 else 44
    private val blue = 0xff0a84ff.toInt()
    private val icons = mutableMapOf<String, ToolIcon>()
    private var panel: View? = null
    private var panelKey = ""
    private var departingPanel: View? = null
    private var brushGroup = "Inking"
    private var colorTab = "Disc"
    private var currentActionTab="Add"
    private val brushHud=BrushHud(activity)
    private var hudSlider:VerticalSlider?=null
    private val hideHud=Runnable{StudioMotion.chrome(brushHud,false)}
    private lateinit var toolbar: LinearLayout
    private lateinit var sidebar: LinearLayout
    private lateinit var bottom: LinearLayout
    private val notice = TextView(activity)
    private var interfaceHidden = false
    private lateinit var sizeSlider: VerticalSlider
    private lateinit var opacitySlider: VerticalSlider
    private val palette = mutableListOf(0xff202020.toInt(),0xffffffff.toInt(),0xffcf4c4c.toInt(),0xffdf8c53.toInt(),
        0xffe4c778.toInt(),0xffa6b78c.toInt(),0xff518476.toInt(),0xff315e70.toInt(),0xff8ca5b8.toInt(),
        0xff766284.toInt(),0xffb7869d.toInt(),0xffe1c6bc.toInt())

    init {
        setBackgroundColor(if(session.lightInterface)0xffb5b5b5.toInt() else 0xff292929.toInt())
        addView(canvas, LayoutParams(-1,-1).apply { topMargin=dp(barHeight) })
        buildToolbar(); buildSidebar()
        bottom=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(dp(8),dp(4),dp(8),dp(4));background=rounded(0xff252525.toInt(),StudioShape.CORNER_DP) }
        addView(bottom,LayoutParams(-2,dp(52),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(16);leftMargin=dp(52);rightMargin=dp(12)})
        notice.apply {setTextColor(Color.WHITE);textSize=13f;gravity=Gravity.CENTER;setPadding(dp(16),dp(7),dp(16),dp(7));background=rounded(0xee171717.toInt(),StudioShape.CORNER_DP);visibility=GONE}
        addView(notice,LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(barHeight+12)})
        brushHud.visibility=INVISIBLE
        addView(brushHud,LayoutParams(dp(160),dp(160),Gravity.TOP or Gravity.LEFT))
        colorDrop=ColorDropController(this,canvas,session,{activity.deferAutosave()},{activity.scheduleSave();refreshTools()},::showNotice)
        icons["Color"]?.let{colorDrop?.bind(it){colors()}}
        canvas.onDismissPanel=::dismissPanel
        canvas.onStrokeStart={activity.deferAutosave()}
        canvas.onChanged={ activity.scheduleSave();refreshTools() }
        canvas.onColor={ refreshTools();activity.savePreferences() }
        canvas.onGesture=::showNotice
        canvas.onToggleInterface={
            interfaceHidden=!interfaceHidden
            dismissPanel()
            StudioMotion.chrome(toolbar,!interfaceHidden,y=-dp(barHeight).toFloat())
            StudioMotion.chrome(sidebar,!interfaceHidden,x=dp(if(session.rightSidebar)44 else -44).toFloat())
            updateBottom()
        }
        canvas.onInputDevice=::inspectDevice
        session.onFrame={canvas.onRenderedFrame()}
        session.onInfo={refreshTools()}
        session.onError={showNotice(it);activity.message(it)}
        refreshTools()
    }
    private fun rounded(color:Int,radius:Int=StudioShape.CORNER_DP):GradientDrawable=GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun text(value:String,size:Float=15f,color:Int=Color.WHITE):TextView=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),0,dp(14),0)
    }
    private fun line():View=View(activity).apply {setBackgroundColor(0xff414141.toInt());layoutParams=LinearLayout.LayoutParams(-1,dp(1))}
    private fun row(label:String,detail:String="",action:()->Unit):LinearLayout=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48);isClickable=true;isFocusable=true;contentDescription=label
        background=selectable();addView(text(label),LinearLayout.LayoutParams(0,-1,1f))
        if(detail.isNotEmpty())addView(text(detail,13f,0xffb3b3b3.toInt()),LinearLayout.LayoutParams(-2,-1))
        setOnClickListener {StudioMotion.bounce(this);action()}
    }
    private fun selectable():android.graphics.drawable.Drawable? {
        val a=activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground));val d=a.getDrawable(0);a.recycle();return d
    }
    private fun icon(symbol:String,action:()->Unit):ToolIcon=ToolIcon(activity,symbol,action).apply{
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(if(narrow)48 else 44));light=session.lightInterface
        if(symbol in listOf("Undo","Redo"))repeatDelay={(session.rapidUndoDelay*1000).toLong()}
    }
    private fun buildToolbar(){
        toolbar=LinearLayout(activity).apply{orientation=if(narrow)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL;setBackgroundColor(if(session.lightInterface)0xffededed.toInt() else 0xff1c1c1c.toInt())}
        val left=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val right=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL}
        val gallery=text("Gallery",17f,if(session.lightInterface)Color.BLACK else Color.WHITE).apply{isClickable=true;isFocusable=true;background=selectable();setOnClickListener {activity.gallery()}}
        left.addView(gallery,LinearLayout.LayoutParams(dp(if(narrow)92 else 84),dp(if(narrow)50 else 44)))
        for(symbol in listOf("Actions","Adjustments","Select","Transform")){
            val control=icon(symbol){
                when(symbol){"Actions"->actions();"Adjustments"->adjustments();"Select","Transform"->{dismissPanel();session.tool=if(session.tool==symbol)"Paint" else symbol;refreshTools();canvas.invalidate()}}
            };icons[symbol]=control;left.addView(control)
        }
        for(symbol in listOf("Paint","Smudge","Erase","Layers","Color")){
            val control=icon(symbol){
                when(symbol){
                    "Layers"->{if(panelKey=="Layers")dismissPanel() else session.thumbnails{layers()}};"Color"->colors()
                    else->{if(session.tool==symbol)brushes() else {dismissPanel();session.tool=symbol;refreshTools();canvas.invalidate()}}
                }
            };icons[symbol]=control;right.addView(control,LinearLayout.LayoutParams(dp(if(narrow)48 else 64),dp(if(narrow)48 else 44)))
        }
        colorDrop?.let{drop->icons["Color"]?.let{drop.bind(it){colors()}}}
        if(narrow){toolbar.addView(left,LinearLayout.LayoutParams(-1,dp(50)));toolbar.addView(right,LinearLayout.LayoutParams(-1,dp(50)))}
        else {toolbar.addView(left);toolbar.addView(View(activity),LinearLayout.LayoutParams(0,1,1f));toolbar.addView(right)}
        addView(toolbar,LayoutParams(-1,dp(barHeight),Gravity.TOP))
    }
    private fun buildSidebar(){
        sidebar=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(0,dp(7),0,dp(7));background=rounded(0xee1d2326.toInt(),StudioShape.CORNER_DP)}
        sizeSlider=VerticalSlider(activity,"Brush size",sqrt(session.size/600f)){session.size=(it*it*600f).coerceAtLeast(.5f);updateHud(sizeSlider,false)}
        opacitySlider=VerticalSlider(activity,"Brush opacity",session.opacity){session.opacity=it;updateHud(opacitySlider,true)}
        sidebar.addView(sizeSlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sidebar.addView(icon("Eyedropper"){dismissPanel();session.tool=if(session.tool=="Eyedropper")"Paint" else "Eyedropper";refreshTools();showNotice("Touch the canvas to pick a color")},LinearLayout.LayoutParams(dp(32),dp(44)))
        sidebar.addView(opacitySlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sidebar.addView(icon("Undo"){session.command("undo");activity.scheduleSave();showNotice("Undo")},LinearLayout.LayoutParams(dp(32),dp(42)))
        sidebar.addView(icon("Redo"){session.command("redo");activity.scheduleSave();showNotice("Redo")},LinearLayout.LayoutParams(dp(32),dp(42)))
        sizeSlider.tracking={show->trackHud(sizeSlider,false,show)}
        opacitySlider.tracking={show->trackHud(opacitySlider,true,show)}
        val available=(resources.displayMetrics.heightPixels/density-barHeight-24).toInt()
        addView(sidebar,LayoutParams(dp(32),dp(min(510,available)),Gravity.CENTER_VERTICAL or if(session.rightSidebar)Gravity.RIGHT else Gravity.LEFT))
    }
    private fun trackHud(slider:VerticalSlider,opacity:Boolean,show:Boolean){
        brushHud.removeCallbacks(hideHud)
        if(show){dismissPanel();hudSlider=slider;updateHud(slider,opacity);brushHud.bringToFront();StudioMotion.reveal(brushHud)}
        else brushHud.postDelayed(hideHud,650)
    }
    private fun updateHud(slider:VerticalSlider,opacity:Boolean){
        brushHud.value=slider.value;brushHud.opacityMode=opacity;brushHud.soft=session.preset.kind in listOf(2,4)
        brushHud.mark={slider.bookmark=slider.value;slider.invalidate();showNotice("Slider position saved")}
        val params=brushHud.layoutParams as? LayoutParams ?: return
        params.leftMargin=if(session.rightSidebar)width-dp(198) else dp(38)
        params.topMargin=(sidebar.top+slider.top+slider.thumbY-dp(102)).roundToInt().coerceIn(dp(barHeight+8),max(dp(barHeight+8),height-dp(168)))
        brushHud.layoutParams=params;brushHud.invalidate()
    }
    fun refreshTools(){
        for((symbol,view)in icons){view.activeTool=when(symbol){"Color"->panelKey=="Colors";"Layers"->panelKey=="Layers";"Actions"->panelKey=="Actions";"Adjustments"->panelKey=="Adjustments";else->session.tool==symbol};view.swatch=session.color;view.invalidate()}
        if(::sizeSlider.isInitialized)sizeSlider.value=sqrt(session.size/600f)
        if(::opacitySlider.isInitialized)opacitySlider.value=session.opacity
        if(::bottom.isInitialized)updateBottom()
        canvas.invalidate()
    }
    private fun updateBottom(){
        bottom.removeAllViews()
        if(interfaceHidden || session.tool !in listOf("Select","Transform","Fill")){bottom.visibility=GONE;return};bottom.visibility=VISIBLE
        fun button(label:String,selected:Boolean=false,action:()->Unit){val v=text(label,13f,if(selected)blue else Color.WHITE).apply{isClickable=true;isFocusable=true;setPadding(dp(10),0,dp(10),0);background=selectable();setOnClickListener{action()}};bottom.addView(v,LinearLayout.LayoutParams(-2,-1))}
        when(session.tool){
            "Select"->{for(shape in listOf("Freehand","Rectangle","Ellipse"))button(shape,canvas.selectionShape==shape){canvas.selectionShape=shape;updateBottom()}
                button("Invert"){session.command("invertSelection");showNotice("Selection inverted")}
                button("Clear"){session.command("deselect");canvas.deselect()}}
            "Transform"->{button("Flip H"){transform("sx",-1f)};button("Flip V"){transform("sy",-1f)};button("Rotate 45°"){transform("angle",Math.PI.toFloat()/4)};button("Done"){session.tool="Paint";refreshTools()}}
            "Fill"->{button("ColorDrop"){showNotice("Tap an enclosed area to fill it")};button("Fill layer"){session.command("fill",JSONObject().put("color",colorLong()));activity.scheduleSave()};button("Done"){session.tool="Paint";refreshTools()}}
        }
    }
    private fun transform(key:String,value:Float){session.command("transform",JSONObject().put(key,value));canvas.deselect();activity.scheduleSave()}
    fun showNotice(message:String){notice.text=message;if(notice.visibility!=VISIBLE)StudioMotion.reveal(notice);notice.removeCallbacks(hideNotice);notice.postDelayed(hideNotice,1800)}
    private val hideNotice=Runnable{StudioMotion.chrome(notice,false,y=-dp(8).toFloat())}
    fun dismissPanel():Boolean {
        departingPanel?.let{it.animate().cancel();removeView(it)};departingPanel=null
        val old=panel ?: return false
        panel=null;panelKey="";departingPanel=old
        StudioMotion.dismiss(old){removeView(old);if(departingPanel===old)departingPanel=null}
        refreshTools();return true
    }
    private var replacingPanel=false
    private fun panel(key:String,title:String,height:Int=480,left:Boolean=false,extra:(LinearLayout)->Unit={},content:(LinearLayout)->Unit){
        val previous=if(replacingPanel)panel as? LinearLayout else null
        if(panelKey==key && !replacingPanel){dismissPanel();return};if(previous==null)dismissPanel();departingPanel?.let{it.animate().cancel();removeView(it)};departingPanel=null;panelKey=key
        val outer=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(12));elevation=dp(14).toFloat();isClickable=true}
        val heading=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),0)}
        heading.addView(text(title,25f).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},LinearLayout.LayoutParams(0,dp(58),1f));extra(heading)
        outer.addView(heading)
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(6),0,dp(10))}
        content(body)
        if(key=="Brushes")outer.addView(body,LinearLayout.LayoutParams(-1,0,1f)) else {
            val scroll=ScrollView(activity).apply{isFillViewport=false;addView(body);isVerticalScrollBarEnabled=false}
            outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        }
        val screenW=resources.displayMetrics.widthPixels
        val w=min(dp(if(key=="Brushes")450 else if(key=="Actions")390 else 350),screenW-dp(24))
        val h=min(dp(height),resources.displayMetrics.heightPixels-dp(barHeight+18))
        val margin=if(left)dp(64) else dp(12)
        val anchor=if(left)dp(44).toFloat() else {
            val control=icons[if(key=="Brushes")session.tool else if(key=="Colors")"Color" else key]
            if(control!=null && control.width>0){val at=IntArray(2);val own=IntArray(2);control.getLocationInWindow(at);getLocationInWindow(own);at[0]-own[0]+control.width/2f-(screenW-w-margin)}else w*.5f
        }
        if(previous!=null){
            previous.animate().cancel();previous.removeAllViews()
            while(outer.childCount>0){val child=outer.getChildAt(0);outer.removeView(child);previous.addView(child)}
            panelKey=key;refreshTools();StudioMotion.content(previous);return
        }
        outer.background=GlassSurface(canvas,if(left)min(margin,screenW-w-dp(12)).toFloat() else (screenW-w-margin).toFloat(),dp(barHeight).toFloat(),dp(StudioShape.CORNER_DP).toFloat())
        addView(outer,LayoutParams(w,h,Gravity.TOP or if(left)Gravity.LEFT else Gravity.RIGHT).apply{topMargin=dp(barHeight);if(left)leftMargin=min(margin,screenW-w-dp(12)) else rightMargin=margin})
        outer.tag="tool-panel";panel=outer;refreshTools();StudioMotion.reveal(outer,if(left)0f else 1f)
    }
    private fun rebuildPanel(action:()->Unit){replacingPanel=true;try{action()}finally{replacingPanel=false}}
    private fun colorLong()=session.color.toLong() and 0xffffffffL
    private fun subheading(body:LinearLayout,value:String){body.addView(text(value,13f,0xffa8a8a8.toInt()),LinearLayout.LayoutParams(-1,dp(36)))}
    private fun toggle(body:LinearLayout,label:String,value:Boolean,changed:(Boolean)->Unit){
        val line=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(16),0)}
        line.addView(text(label,16f),LinearLayout.LayoutParams(0,dp(45),1f))
        val control=StudioSwitch(activity,label,value,changed)
        line.contentDescription=label;line.isClickable=true;line.setOnClickListener{if(control.isEnabled){StudioMotion.bounce(line);control.performClick()}}
        line.addView(control,LinearLayout.LayoutParams(dp(54),dp(40)))
        body.addView(line)
    }
    private fun slider(body:LinearLayout,label:String,value:Float,changed:(Float)->Unit){
        val title=text("$label ${(value*100).roundToInt()}%",14f);body.addView(title,LinearLayout.LayoutParams(-1,dp(46)).apply{setMargins(dp(2),dp(4),dp(6),dp(4))})
        val seek=SeekBar(activity).apply{max=100;progress=(value*100).roundToInt();setPadding(dp(14),0,dp(14),0);contentDescription=label}
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){changed(s.progress/100f)};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){title.text="$label $p%"}})
        body.addView(seek,LinearLayout.LayoutParams(-1,dp(40)))
    }

    private fun preferenceSlider(body:LinearLayout,label:String,value:Float,format:(Float)->String,changed:(Float)->Unit){
        val row=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(12),0)}
        row.addView(text(label,15f),LinearLayout.LayoutParams(0,dp(30),1f))
        val detail=text(format(value),13f,0xffaab8bf.toInt());row.addView(detail);body.addView(row)
        val seek=SeekBar(activity).apply{max=100;progress=(value*100).roundToInt();contentDescription=label;setPadding(dp(22),0,dp(22),0);progressTintList=android.content.res.ColorStateList.valueOf(0xff43b8f3.toInt());thumbTintList=progressTintList}
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user){detail.text=format(p/100f);changed(p/100f)}}})
        body.addView(seek,LinearLayout.LayoutParams(-1,dp(24)))
    }
    private fun brushes(){
        panel("Brushes","Brush Library",820,extra={heading->heading.addView(icon("Add"){brushStudio()})}){body->
            val columns=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            val categories=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
            for(group in brushCategories){
                val item=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(if(group==brushGroup)blue else 0x226e7175,StudioShape.CORNER_DP);setPadding(dp(10),0,0,0);isClickable=true;isFocusable=true;contentDescription=group;setOnClickListener{brushGroup=group;rebuildPanel(::brushes)}}
                item.addView(CategoryIcon(activity,group,false),LinearLayout.LayoutParams(dp(25),dp(32)))
                item.addView(text(group,12f,if(group==brushGroup)Color.WHITE else 0xffbdc6ca.toInt()).apply{setPadding(dp(8),0,0,0)},LinearLayout.LayoutParams(-1,-1))
                categories.addView(item,LinearLayout.LayoutParams(-1,dp(46)).apply{setMargins(dp(2),dp(4),dp(6),dp(4))})
            }
            columns.addView(ScrollView(activity).apply{isVerticalScrollBarEnabled=false;addView(categories)},LinearLayout.LayoutParams(dp(145),-1))
            val list=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(3),dp(2),dp(4),0)}
            val presets=if(brushGroup=="Recent")listOf(session.preset) else brushPresets.filter{it.group==brushGroup}.sortedBy{if(brushGroup=="Airbrushing")listOf("Soft Brush","Medium Brush","Medium Hard Brush","Hard Brush","Soft Blend","Medium Blend","Medium Hard Blend","Hard Blend","Soft Airbrush","Medium Airbrush","Fine Airbrush").indexOf(it.name) else 0}
            val previews=mutableListOf<BrushPreview>()
            for(preset in presets){
                val preview=BrushPreview(activity,preset,session.preset==preset).apply{isClickable=true;isFocusable=true;setOnClickListener{StudioMotion.bounce(this);session.choose(preset);refreshTools();previews.forEach{it.chosen=it.preset==preset;it.invalidate()}};setOnLongClickListener{session.choose(preset);brushStudio();true}}
                previews.add(preview);list.addView(preview,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(3)})
            }
            if(presets.isEmpty())list.addView(text("No imported brushes",13f,0xffb4bec2.toInt()),LinearLayout.LayoutParams(-1,dp(64)))
            columns.addView(ScrollView(activity).apply{isVerticalScrollBarEnabled=false;addView(list)},LinearLayout.LayoutParams(0,-1,1f))
            body.addView(columns,LinearLayout.LayoutParams(-1,-1))
        }
    }
    private fun brushStudio(){
        dismissPanel();panel("Studio","Brush Studio",540){body->
            body.addView(text(session.preset.name,17f),LinearLayout.LayoutParams(-1,dp(42)))
            body.addView(BrushPreview(activity,session.preset),LinearLayout.LayoutParams(-1,dp(90)))
            slider(body,"Flow",session.flow){session.flow=it.coerceAtLeast(.01f)}
            slider(body,"Stabilization",session.stabilization){session.stabilization=it*.95f}
            slider(body,"Pressure → Size",session.pressureSize){session.pressureSize=it}
            slider(body,"Pressure → Opacity",session.pressureOpacity){session.pressureOpacity=it}
            body.addView(row("Reset brush"){session.choose(session.preset);rebuildPanel(::brushStudio)})
            body.addView(row("Done"){dismissPanel()})
        }
    }
    private fun colors(){
        panel("Colors","Colors",590,extra={h->h.addView(View(activity).apply{setBackgroundColor(session.color)},LinearLayout.LayoutParams(dp(42),dp(28)).apply{rightMargin=dp(18)})}){body->
            if(colorTab in listOf("Disc","Classic","Harmony")){
                body.addView(ColorDisc(activity,session.color,colorTab=="Classic"){session.color=it;refreshTools();activity.savePreferences()},LinearLayout.LayoutParams(-1,dp(282)))
                if(colorTab=="Harmony"){
                    val hsv=FloatArray(3);Color.colorToHSV(session.color,hsv)
                    val row=LinearLayout(activity)
                    for(shift in listOf(0f,120f,180f,240f)){val color=Color.HSVToColor(floatArrayOf((hsv[0]+shift)%360,hsv[1],hsv[2]));row.addView(View(activity).apply{setBackgroundColor(color);contentDescription="Harmony color";isFocusable=true;setOnClickListener{session.color=color;refreshTools();rebuildPanel(::colors)}},LinearLayout.LayoutParams(0,dp(34),1f))};body.addView(row)
                }
            } else if(colorTab=="Value"){
                for((label,shift)in listOf("Red" to 16,"Green" to 8,"Blue" to 0))slider(body,label,((session.color shr shift)and 255)/255f){value->
                    session.color=(session.color and (255 shl shift).inv())or((value*255).roundToInt() shl shift);refreshTools()
                }
                body.addView(row("Hexadecimal",String.format("#%06X",session.color and 0xffffff)){
                    activity.textDialog("Hexadecimal color",String.format("#%06X",session.color and 0xffffff)){input->runCatching{Color.parseColor(if(input.startsWith("#"))input else "#$input")}.onSuccess{session.color=it;refreshTools();rebuildPanel(::colors)}.onFailure{showNotice("Use a color such as #347A68")}}
                })
            }
            subheading(body,if(colorTab=="Palettes")"Studio palette · long press to replace" else "Palette")
            val grid=GridLayout(activity).apply{columnCount=6;setPadding(dp(12),0,dp(12),0)}
            for((index,color)in palette.withIndex()){
                val swatch=View(activity).apply{setBackgroundColor(color);contentDescription=String.format("Color #%06X",color and 0xffffff);isFocusable=true;isClickable=true;setOnClickListener{session.color=color;refreshTools()};setOnLongClickListener{palette[index]=session.color;rebuildPanel(::colors);true}}
                grid.addView(swatch,GridLayout.LayoutParams().apply{width=0;height=dp(32);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(0,0,0,0)})
            };body.addView(grid)
            body.addView(row("ColorDrop","Fill an enclosed area"){dismissPanel();session.tool="Fill";refreshTools();showNotice("Tap an enclosed area to fill it")})
            val tabs=LinearLayout(activity).apply{gravity=Gravity.CENTER}
            for(tab in listOf("Disc","Classic","Harmony","Value","Palettes"))tabs.addView(text(tab,11f,if(tab==colorTab)blue else 0xffbbbbbb.toInt()).apply{setPadding(dp(5),0,dp(5),0);gravity=Gravity.CENTER;isFocusable=true;setOnClickListener{colorTab=tab;rebuildPanel(::colors)}},LinearLayout.LayoutParams(0,dp(48),1f))
            body.addView(tabs)
        }
    }

    private fun layers(){
        panel("Layers","Layers",710,extra={h->h.addView(icon("Add"){session.command("add"){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}})}){body->
            val layers=session.info.getJSONArray("layers");val active=session.info.getInt("active")
            val expanded=mutableListOf<View>()
            for(i in layers.length()-1 downTo 0){
                val layer=layers.getJSONObject(i);val name=layer.getString("name")
                val layerRow=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(5),dp(5),dp(8),dp(5));background=rounded(if(i==active)blue else 0x443d3d3d,StudioShape.CORNER_DP);isClickable=true;isFocusable=true}
                val thumbnail=FrameLayout(activity).apply{background=rounded(0x336e777c,StudioShape.CORNER_DP);clipToOutline=true}
                thumbnail.addView(ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(session.layerThumbnails.getOrNull(i))},LayoutParams(-1,-1));layerRow.addView(thumbnail,LinearLayout.LayoutParams(dp(56),dp(56)))
                layerRow.addView(text(name+if(layer.optBoolean("locked"))"  · Locked" else "",16f),LinearLayout.LayoutParams(0,-1,1f))
                val dropdown=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;visibility=GONE;setPadding(dp(8),dp(10),dp(8),dp(12));background=rounded(0x33373737,StudioShape.CORNER_DP)}
                expanded.add(dropdown)
                val blendButton=text(if(layer.getString("blend")=="Normal")"N" else layer.getString("blend").take(1),14f).apply{gravity=Gravity.CENTER;setPadding(0,0,0,0);contentDescription="Blend options for $name";isClickable=true;isFocusable=true;setOnClickListener{
                    android.transition.TransitionManager.beginDelayedTransition(body,android.transition.AutoTransition().apply{duration=180})
                    val open=dropdown.visibility!=VISIBLE;expanded.forEach{it.visibility=GONE};dropdown.visibility=if(open)VISIBLE else GONE
                }}
                layerRow.addView(blendButton,LinearLayout.LayoutParams(dp(32),dp(48)))
                layerRow.addView(CheckBox(activity).apply{isChecked=layer.getBoolean("visible");contentDescription="$name visible";buttonTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);setOnCheckedChangeListener{_,b->session.command("layer",JSONObject().put("index",i).put("visible",b));activity.scheduleSave()}},LinearLayout.LayoutParams(dp(36),dp(48)))
                layerRow.setOnClickListener{if(i==active)layerOptions(i)else session.command("active",JSONObject().put("index",i)){session.thumbnails{rebuildPanel(::layers)}}}
                layerRow.setOnLongClickListener{layerOptions(i);true}
                val actions=LinearLayout(activity)
                fun swipeAction(label:String,color:Int=0xff303030.toInt(),run:()->Unit){actions.addView(text(label,12f).apply{gravity=Gravity.CENTER;setPadding(dp(2),0,dp(2),0);background=rounded(color,StudioShape.CORNER_DP);isClickable=true;isFocusable=true;contentDescription="$label $name";setOnClickListener{isEnabled=false;run()}},LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=dp(2)})}
                swipeAction(if(layer.optBoolean("locked"))"Unlock" else "Lock"){session.command("layer",JSONObject().put("index",i).put("locked",!layer.optBoolean("locked"))){rebuildPanel(::layers);activity.scheduleSave()}}
                fun action(op:String){session.command("active",JSONObject().put("index",i)){session.command(op){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}}}
                swipeAction("Duplicate"){action("duplicate")};swipeAction("Delete",0xffff4c45.toInt()){action("delete")}
                body.addView(SwipeLayerRow(activity,layerRow,actions,dp(216)).apply{contentDescription="Layer row $name"},LinearLayout.LayoutParams(-1,dp(69)).apply{setMargins(dp(2),dp(1),dp(2),dp(1))})
                val opacityRow=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL};opacityRow.addView(text("Opacity",15f),LinearLayout.LayoutParams(0,dp(32),1f));val opacityLabel=text(if(layer.getDouble("opacity")==1.0)"Max" else "${(layer.getDouble("opacity")*100).roundToInt()}%",14f);opacityRow.addView(opacityLabel);dropdown.addView(opacityRow)
                dropdown.addView(SeekBar(activity).apply{max=100;progress=(layer.getDouble("opacity")*100).roundToInt();contentDescription="Opacity for $name";progressTintList=android.content.res.ColorStateList.valueOf(blue);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){opacityLabel.text=if(p==100)"Max" else "$p%"};override fun onStopTrackingTouch(s:SeekBar){session.command("layer",JSONObject().put("index",i).put("opacity",s.progress/100f));activity.scheduleSave()}})},LinearLayout.LayoutParams(-1,dp(28)))
                val modes=listOf("Darken","Multiply","Colour Burn","Linear Burn","Darker Colour","Normal","Lighten","Screen","Colour Dodge","Add","Overlay","Hard Light","Difference","Exclusion","Subtract")
                val modeRows=mutableMapOf<String,View>()
                for(mode in modes){val v=row(mode,if(mode=="Normal")"N" else mode.take(1)){
                    session.command("layer",JSONObject().put("index",i).put("blend",mode))
                    modeRows.forEach{(m,v)->v.background=rounded(if(m==mode)blue else Color.TRANSPARENT,StudioShape.CORNER_DP)};blendButton.text=if(mode=="Normal")"N" else mode.take(1);activity.scheduleSave()
                };v.contentDescription="Blend $mode for $name";v.background=rounded(if(mode==layer.getString("blend"))blue else Color.TRANSPARENT,StudioShape.CORNER_DP);modeRows[mode]=v;dropdown.addView(v,LinearLayout.LayoutParams(-1,dp(40)))}
                body.addView(dropdown,LinearLayout.LayoutParams(-1,-2))
            }
            body.addView(row("Background colour","●"){
                val colors=intArrayOf(Color.WHITE,0xfffaf9f6.toInt(),0xff181818.toInt(),session.color)
                AlertDialog.Builder(activity).setTitle("Background colour").setItems(arrayOf("White","Warm paper","Black","Current colour","Toggle transparency")){_,which->
                    if(which<4)session.command("background",JSONObject().put("color",colors[which].toLong() and 0xffffffffL))else session.command("background",JSONObject().put("visible",!session.info.getBoolean("backgroundVisible")))
                    activity.scheduleSave()
                }.show()
            })
        }
    }
    private fun layerOptions(index:Int){
        session.command("active",JSONObject().put("index",index)){
            val l=session.info.getJSONArray("layers").getJSONObject(index)
            dismissPanel();panel("Layer Options",l.getString("name"),620){body->
                body.addView(row("Rename"){activity.textDialog("Layer name",l.getString("name")){name->session.command("layer",JSONObject().put("name",name)){rebuildPanel(::layers);activity.scheduleSave()}}})
                slider(body,"Opacity",l.getDouble("opacity").toFloat()){value->session.command("layer",JSONObject().put("opacity",value));activity.scheduleSave()}
                body.addView(row("Blend Mode",l.getString("blend")){
                    val modes=arrayOf("Normal","Multiply","Screen","Overlay","Add","Darken","Lighten","Difference")
                    AlertDialog.Builder(activity).setTitle("Blend Mode").setSingleChoiceItems(modes,modes.indexOf(l.getString("blend"))){dialog,which->session.command("layer",JSONObject().put("blend",modes[which])){rebuildPanel(::layers);activity.scheduleSave()};dialog.dismiss()}.setNegativeButton("Cancel",null).show()
                })
                toggle(body,"Alpha Lock",l.getBoolean("alphaLock")){v->session.command("layer",JSONObject().put("alphaLock",v));activity.scheduleSave()}
                if(index>0)toggle(body,"Clipping Mask",l.getBoolean("clipping")){v->session.command("layer",JSONObject().put("clipping",v));activity.scheduleSave()}
                for((label,op)in listOf("Duplicate" to "duplicate","Fill Layer" to "fill","Clear" to "clear","Merge Down" to "merge","Delete" to "delete")){
                    body.addView(row(label){session.command(op,JSONObject().put("color",colorLong())){rebuildPanel(::layers);activity.scheduleSave()}})
                }
                body.addView(row("Move Up"){session.command("reorder",JSONObject().put("index",index).put("to",min(index+1,session.info.getJSONArray("layers").length()-1))){rebuildPanel(::layers);activity.scheduleSave()}})
                body.addView(row("Move Down"){session.command("reorder",JSONObject().put("index",index).put("to",max(index-1,0))){rebuildPanel(::layers);activity.scheduleSave()}})
            }
        }
    }
    private fun actions(){if(panelKey=="Actions"){dismissPanel();return};actionTab(currentActionTab)}
    private fun actionTab(tab:String){if(panel!=null && !replacingPanel){rebuildPanel{actionTab(tab)};return};currentActionTab=tab;panel("Actions","Actions",750,left=true){body->
        val tabs=LinearLayout(activity).apply{setPadding(dp(8),0,dp(8),dp(14))}
        for(t in listOf("Add","Canvas","Share","Video","Prefs","Help")){
            val column=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=t;setOnClickListener{actionTab(t)}}
            column.addView(ToolIcon(activity,t){actionTab(t)}.apply{activeTool=t==tab},LinearLayout.LayoutParams(dp(48),dp(40)))
            column.addView(text(t,10f,if(t==tab)blue else 0xffbfc8cb.toInt()).apply{gravity=Gravity.CENTER;setPadding(0,0,0,0)},LinearLayout.LayoutParams(-1,dp(19)))
            tabs.addView(column,LinearLayout.LayoutParams(0,-2,1f))
        }
        body.addView(tabs);actionContent(body,tab)
    }}
    private fun actionContent(body:LinearLayout,tab:String){
        when(tab){
            "Add"->{body.addView(row("Insert a photo"){dismissPanel();activity.importImage()});body.addView(row("Import Inkstone document"){dismissPanel();activity.importDocument()});body.addView(row("Rename artwork",activity.documentName){activity.renameCurrent()});body.addView(row("Save artwork"){activity.saveCurrent{if(it)showNotice("Artwork saved")}})}
            "Canvas"->{
                body.addView(row("Canvas information","${session.canvasWidth} × ${session.canvasHeight}"){AlertDialog.Builder(activity).setTitle(activity.documentName).setMessage("${session.canvasWidth} × ${session.canvasHeight} pixels\n${session.info.getJSONArray("layers").length()} layers\nRGBA · 8 bits per channel\n${session.info.getInt("undo")} undo actions available\nStored locally as an Inkstone document").setPositiveButton("Done",null).show()})
                toggle(body,"Lock canvas position",session.canvasLocked){session.canvasLocked=it;activity.savePreferences()}
                toggle(body,"Drawing Guide",session.guide){session.guide=it;canvas.invalidate()}
                body.addView(row("Fit canvas"){canvas.fit();dismissPanel()});body.addView(row("Flip canvas horizontally"){session.command("flipCanvas",JSONObject().put("horizontal",true));canvas.deselect();activity.scheduleSave()});body.addView(row("Flip canvas vertically"){session.command("flipCanvas",JSONObject().put("horizontal",false));canvas.deselect();activity.scheduleSave()})
            }
            "Share"->{for(format in listOf("Inkstone","PNG","JPEG"))body.addView(row(format,if(format=="Inkstone")"Editable layers" else "Flattened image"){dismissPanel();activity.export(format)})}
            "Video"->{body.addView(text("Time-lapse recording is not available in this preview.",15f,0xffbfc8cb.toInt()),LinearLayout.LayoutParams(-1,dp(90)))}
            "Help"->{body.addView(row("About Inkstone","0.1.5"){activity.message("Independent Android painting app. See README and the parity tracker for supported features.")})}
            "Prefs"->{
                val basic=preferenceGroup(body)
                toggle(basic,"Pen pressure",session.pressureEnabled){session.pressureEnabled=it;activity.savePreferences()}
                toggle(basic,"Hold to perfect shape",session.quickShape){session.quickShape=it;activity.savePreferences()}
                toggle(basic,"Light interface",session.lightInterface){session.lightInterface=it;activity.savePreferences();setBackgroundColor(if(it)0xffb5b5b5.toInt() else 0xff292929.toInt());removeView(toolbar);buildToolbar();refreshTools();canvas.invalidate()}
                toggle(basic,"Right-hand interface",session.rightSidebar){session.rightSidebar=it;val p=sidebar.layoutParams as LayoutParams;p.gravity=Gravity.CENTER_VERTICAL or if(it)Gravity.RIGHT else Gravity.LEFT;sidebar.layoutParams=p;activity.savePreferences()}
                toggle(basic,"Dynamic brush scaling",session.dynamicBrushScaling){session.dynamicBrushScaling=it;activity.savePreferences();canvas.invalidate()}
                toggle(basic,"Project canvas",false){}
                (basic.getChildAt(basic.childCount-1) as LinearLayout).apply{alpha=.5f;getChildAt(1).isEnabled=false;contentDescription="Project canvas unavailable";isClickable=true;setOnClickListener{activity.message("External display projection is not available yet.")}}
                toggle(basic,"Brush cursor",session.cursor){session.cursor=it;activity.savePreferences();canvas.invalidate()}
                basic.addView(row("Advanced cursor settings"){cursorSettings()})
                val cloud=preferenceGroup(body)
                toggle(cloud,"Store brushes in iCloud",false){}
                (cloud.getChildAt(0) as LinearLayout).apply{alpha=.5f;getChildAt(1).isEnabled=false;isClickable=true;setOnClickListener{activity.message("iCloud brush sync is unavailable on Android. Brushes stay on this device.")}}
                val input=preferenceGroup(body)
                input.addView(row("Pressure and Smoothing"){brushStudio()})
                input.addView(row("Gesture controls"){gestureSettings()})
                val sliders=preferenceGroup(body)
                preferenceSlider(sliders,"Rapid undo delay",session.rapidUndoDelay,{"%.2fs".format(it)}){session.rapidUndoDelay=it.coerceAtLeast(.05f);activity.savePreferences()}
                preferenceSlider(sliders,"Selection mask visibility",session.selectionVisibility,{"${(it*100).roundToInt()}%"}){session.selectionVisibility=it;activity.savePreferences();canvas.invalidate()}
                val display=preferenceGroup(body)
                toggle(display,"Size & Opacity sidebar",sidebar.visibility==VISIBLE){StudioMotion.chrome(sidebar,it,x=dp(if(session.rightSidebar)38 else -38).toFloat())}

            }
        }
    }
    private fun preferenceGroup(body:LinearLayout):LinearLayout=LinearLayout(activity).apply{
        orientation=LinearLayout.VERTICAL;background=rounded(0x5c080808,StudioShape.CORNER_DP);setPadding(0,dp(3),0,dp(3));clipToOutline=true
        body.addView(this,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(2),dp(5),dp(2),dp(13))})
    }
    private fun cursorSettings(){
        if(!replacingPanel)dismissPanel();panel("Cursor","Advanced cursor settings",410,left=true){body->
            toggle(body,"Brush cursor",session.cursor){session.cursor=it;activity.savePreferences();canvas.invalidate()}
            for(style in listOf("Contrast","Light","Dark"))body.addView(row(style,if(session.cursorStyle==style)"✓" else ""){session.cursorStyle=style;activity.savePreferences();canvas.invalidate();rebuildPanel(::cursorSettings)})
            preferenceSlider(body,"Cursor opacity",session.cursorOpacity,{"${(it*100).roundToInt()}%"}){session.cursorOpacity=it;activity.savePreferences();canvas.invalidate()}
            body.addView(row("Stylus diagnostics"){AlertDialog.Builder(activity).setTitle("Stylus input").setMessage(activity.lastInputDescription).setPositiveButton("Done",null).show()})
        }
    }
    private fun gestureSettings(){
        dismissPanel();panel("Gestures","Gesture controls",470,left=true){body->
            toggle(body,"Paint with finger",session.fingerDrawing){session.fingerDrawing=it;activity.savePreferences()}
            toggle(body,"Two-finger tap Undo",session.twoFingerUndo){session.twoFingerUndo=it;activity.savePreferences()}
            toggle(body,"Three-finger tap Redo",session.threeFingerRedo){session.threeFingerRedo=it;activity.savePreferences()}
            toggle(body,"Four-finger full screen",session.fourFingerFullScreen){session.fourFingerFullScreen=it;activity.savePreferences()}
            toggle(body,"Hold for Eyedropper",session.holdEyedropper){session.holdEyedropper=it;activity.savePreferences()}
            toggle(body,"Pen button eraser",session.penButtonErase){session.penButtonErase=it;activity.savePreferences()}
        }
    }
    private fun adjustments(){
        panel("Adjustments","Adjustments",440,left=true){body->
            for(kind in listOf("Brightness","Saturation","Blur","Grayscale","Invert"))body.addView(row(kind){
                if(kind in listOf("Grayscale","Invert")){session.command("adjust",JSONObject().put("kind",kind));dismissPanel();activity.scheduleSave()}
                else adjustmentSlider(kind)
            })
        }
    }
    private fun adjustmentSlider(kind:String){
        dismissPanel();panel("Adjustment",kind,250,left=true){body->
            var amount=if(kind=="Blur").25f else .5f
            slider(body,kind,amount){amount=it}
            body.addView(row("Apply"){session.command("adjust",JSONObject().put("kind",kind).put("amount",if(kind=="Blur")amount else (amount-.5f)*2));dismissPanel();activity.scheduleSave()})
            body.addView(row("Cancel"){dismissPanel()})
        }
    }
    private fun inspectDevice(event:MotionEvent){
        val d=event.device?:return
        val pressure=d.getMotionRange(MotionEvent.AXIS_PRESSURE,event.source)
        val tilt=d.getMotionRange(MotionEvent.AXIS_TILT,event.source)
        activity.lastInputDescription="${d.name}\nTool: ${when(event.getToolType(0)){MotionEvent.TOOL_TYPE_STYLUS->"Stylus";MotionEvent.TOOL_TYPE_ERASER->"Eraser";else->"Touch"}}\n"+
            "Pressure range: ${pressure?.let{"${it.min} – ${it.max}"}?:"Not reported"}\nTilt: ${if(tilt!=null)"Reported" else "Not reported"}\n"+
            "Buttons: ${event.buttonState}\n\nFeatures depend on what your device exposes through Android. Xiaomi-specific shortcuts have not been verified."
    }
    override fun onDetachedFromWindow(){colorDrop?.cancel();canvas.cancelFillAnimation();super.onDetachedFromWindow()}

}
