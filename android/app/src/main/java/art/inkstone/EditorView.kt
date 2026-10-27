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
    val canvas = PaintCanvas(activity, session).apply{onAtlas={activity.showAtlas(true)}}
    private var colorDrop:ColorDropController?=null
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private var narrow = resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE || resources.displayMetrics.widthPixels / density < 700
    private val barHeight get() = if (narrow) 88 else 44
    private val blue = StudioColors.accent
    private val icons = mutableMapOf<String, ToolIcon>()
    private var panel: View? = null
    internal var panelKey = ""; private set
    private var departingPanel: View? = null
    private var paintBrushGroup: String? = null
    private var smudgeBrushGroup: String? = null
    private var eraseBrushGroup: String? = null
    internal var brushGroup: String
        get() = when(session.tool) {
            "Erase" -> eraseBrushGroup ?: session.eraseBrush.preset.group
            "Smudge" -> smudgeBrushGroup ?: session.smudgeBrush.preset.group
            else -> paintBrushGroup ?: session.paintBrush.preset.group
        }
        set(value) {
            when(session.tool) {
                "Erase" -> eraseBrushGroup = value
                "Smudge" -> smudgeBrushGroup = value
                else -> paintBrushGroup = value
            }
        }
    private var colorHeader:View?=null
    private var colorTab = "Disc"
    private var currentActionTab="Add"
    private val brushHud=BrushHud(activity)
    private var hudSlider:VerticalSlider?=null
    private val hideHud=Runnable{StudioMotion.chrome(brushHud,false)}
    private lateinit var toolbar: FrameLayout
    private lateinit var sidebar: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var bottomHost:LinearLayout
    private lateinit var bottomOptions:LinearLayout
    private lateinit var bottomOptionsScroll:HorizontalScrollView
    private var bottomGroup=""
    private var bottomContext=""
    private lateinit var historyControls: FrameLayout
    private val galleryButton by lazy{text("Gallery",17f).apply{isClickable=true;isFocusable=true;background=selectable();setOnClickListener{activity.gallery()}}}
    private val saveButton by lazy{text("Save",13f).apply{gravity=Gravity.CENTER;setPadding(dp(6),0,dp(6),0);isSingleLine=true;isClickable=true;isFocusable=true;contentDescription="Save artwork";translationX=dp(71).toFloat();setOnClickListener{saveOptions()}}}
    private var penTouchActive=false
    private var blockedTouch=false
    private val notice = TextView(activity)
    private var interfaceHidden = false
    private lateinit var sizeSlider: VerticalSlider
    private lateinit var opacitySlider: VerticalSlider
    internal val palettes=PaletteStore(activity)

    init {
        setBackgroundColor(if(session.lightInterface)0xffb5b5b5.toInt() else StudioColors.workspace)
        addView(canvas, LayoutParams(-1,-1).apply { topMargin=dp(barHeight) })
        buildToolbar(); buildSidebar()
        bottom=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;tag="canvas-tool-main-row"}
        bottomOptions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;tag="canvas-tool-expanded-row"}
        bottomHost=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4));background=rounded(StudioColors.surface,StudioShape.CORNER_DP);tag="canvas-tool-shelf"}
        bottomOptionsScroll=HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;isFillViewport=true;addView(bottomOptions);visibility=GONE;tag="canvas-tool-options-scroll"}
        bottomHost.addView(bottomOptionsScroll,LinearLayout.LayoutParams(-1,dp(44)))
        bottomHost.addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;isFillViewport=true;addView(this@EditorView.bottom);tag="canvas-tool-groups-scroll"},LinearLayout.LayoutParams(-1,dp(44)))
        addView(bottomHost,LayoutParams(min(dp(536),resources.displayMetrics.widthPixels-dp(80)),-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(16);leftMargin=dp(52);rightMargin=dp(12)})
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
        canvas.quickShapeEdit.changed={updateBottom();activity.scheduleSave()}
        canvas.onInputDevice=::inspectDevice
        session.onToolChange={if(session.tool!="Liquify")canvas.liquify.finish();if(session.tool!="Guide")canvas.guides.editing=false;canvas.finishQuickShape();canvas.syncTransformTool()}
        session.beforeSnapshot={canvas.liquify.finish();canvas.finishQuickShape();canvas.rasterTransform.finish()}
        session.beforeCommand={op->if(!op.startsWith("liquify") && op !in listOf("info","pick","selectionPreview") && canvas.liquify.active){canvas.liquify.finish();session.tool="Paint"};if(op !in listOf("info","pick","selectionPreview")){canvas.finishQuickShape();if(op !in listOf("transformMatrix","transformProjective","transformMesh"))canvas.rasterTransform.finish()}}
        session.onFrame={canvas.onRenderedFrame()}
        session.onInfo={canvas.syncSelection();refreshTools()}
        session.onHistoryNotice=::showNotice
        session.onError={showNotice(it);activity.message(it)}
        refreshTools()
        post{if(ReferencePanel.wasOpen(activity))showReference()}
    }
    private fun selectionAction(op:String,values:JSONObject=JSONObject(),transform:Boolean=false){session.command(op,values){result->if(!result.has("error")){canvas.syncSelection();activity.scheduleSave();if(transform)session.tool="Transform";refreshTools()}}}
    private fun selectionThreshold(){
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))};val label=text("${(canvas.selectionThreshold*100).roundToInt()}%")
        body.addView(label);body.addView(SeekBar(activity).apply{max=100;progress=(canvas.selectionThreshold*100).roundToInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){canvas.selectionThreshold=s.progress/100f};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){label.text="$p%"}})})
        AlertDialog.Builder(activity).setTitle("Automatic selection threshold").setView(body).setPositiveButton("Done",null).show()
    }
    private fun selectionFeather(){
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))};val label=text("0 px")
        body.addView(label);body.addView(SeekBar(activity).apply{max=100;contentDescription="Selection feather radius";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){selectionAction("selectionFeather",JSONObject().put("radius",s.progress))};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){label.text="$p px"}})})
        AlertDialog.Builder(activity).setTitle("Feather selection").setView(body).setPositiveButton("Done",null).show()
    }
    private fun saveSelection(){activity.textDialog("Selection name",""){selectionAction("selectionSave",JSONObject().put("name",it))}}
    private fun loadSelection(delete:Boolean=false){
        val names=session.info.optJSONArray("savedSelections");if(names==null || names.length()==0){showNotice("No saved selections yet");return}
        AlertDialog.Builder(activity).setTitle(if(delete)"Delete saved selection"else"Load selection").setItems(Array(names.length()){names.getString(it)}){_,index->selectionAction(if(delete)"selectionDelete"else"selectionLoad",JSONObject().put("name",names.getString(index)))}.setNegativeButton("Cancel",null).show()
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
        layoutParams=LinearLayout.LayoutParams(dp(40),dp(44));light=session.lightInterface
        if(symbol in listOf("Undo","Redo"))repeatDelay={(session.rapidUndoDelay*1000).toLong()}
    }
    private fun buildToolbar(){
        toolbar=FrameLayout(activity).apply{setBackgroundColor(if(session.lightInterface)0xffededed.toInt() else StudioColors.toolbar)}
        val left=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val right=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL}
        (galleryButton.parent as? android.view.ViewGroup)?.removeView(galleryButton)
        galleryButton.setTextColor(if(session.lightInterface)Color.BLACK else Color.WHITE)
        left.addView(galleryButton,LinearLayout.LayoutParams(dp(84),dp(44)))
        for(symbol in listOf("Actions","Adjustments","Select","Transform")){
            val control=icon(symbol){
                when(symbol){
                    "Actions"->actions()
                    "Adjustments"->adjustments()
                    "Select","Transform"->{dismissPanel();session.tool=if(session.tool==symbol)"Paint" else symbol;refreshTools();canvas.invalidate()}
                }
            };icons[symbol]=control;left.addView(control)
        }
        for(symbol in listOf("Paint","Smudge","Erase","Layers","Color")){
            val control=icon(symbol){
                when(symbol){
                    "Layers"->{
                        if(panelKey=="Layers")dismissPanel()
                        else if(panel!=null && !panelIsLeft)session.thumbnails{rebuildPanel(::layers)}
                        else session.thumbnails{layers()}
                    }
                    "Color"->{
                        if(panelKey=="Colors")dismissPanel()
                        else if(panel!=null && !panelIsLeft)rebuildPanel(::colors)
                        else colors()
                    }
                    else->{
                        val switching=session.tool!=symbol
                        if(switching){
                            if(session.tool in listOf("Paint","Smudge"))activity.lastDrawingTool=session.tool
                            session.tool=symbol
                            brushGroup=session.preset.group
                            refreshTools()
                            canvas.invalidate()
                        }
                        if(panel!=null && !panelIsLeft){
                            if(switching || panelKey!="Brushes") rebuildPanel(::brushes)
                            else dismissPanel()
                        } else brushes()
                    }
                }
            };icons[symbol]=control;right.addView(control,LinearLayout.LayoutParams(dp(48),dp(44)))
        }
        colorDrop?.let{drop->icons["Color"]?.let{drop.bind(it){colors()}}}
        toolbar.addView(left,LayoutParams(-2,dp(44),Gravity.TOP or Gravity.LEFT))
        toolbar.addView(right,LayoutParams(-2,dp(44),Gravity.TOP or Gravity.RIGHT).apply{topMargin=if(narrow)dp(44) else 0})
        historyControls=FrameLayout(activity).apply{setBackgroundColor(StudioColors.toolbar);contentDescription="History and save"}
        val undoRedo=LinearLayout(activity)
        for(symbol in listOf("Undo","Redo"))undoRedo.addView(icon(symbol){session.command(symbol.lowercase());activity.scheduleSave()},LinearLayout.LayoutParams(dp(44),dp(44)))
        historyControls.addView(undoRedo,LayoutParams(dp(88),dp(44),Gravity.CENTER))
        (saveButton.parent as? android.view.ViewGroup)?.removeView(saveButton)
        saveButton.setTextColor(if(session.lightInterface)Color.BLACK else Color.WHITE)
        historyControls.addView(saveButton,LayoutParams(dp(54),dp(44),Gravity.CENTER))
        if(narrow)addView(historyControls,LayoutParams(-1,dp(44),Gravity.BOTTOM))
        else toolbar.addView(historyControls,LayoutParams(dp(196),dp(44),Gravity.CENTER))
        addView(toolbar,LayoutParams(-1,dp(barHeight),Gravity.TOP))
    }
    override fun onMeasure(widthSpec:Int,heightSpec:Int){
        val w=MeasureSpec.getSize(widthSpec);val h=MeasureSpec.getSize(heightSpec)
        val compact=w<h || w/density<700
        if(compact!=narrow){
            narrow=compact;dismissPanel();removeView(toolbar);(historyControls.parent as? android.view.ViewGroup)?.removeView(historyControls);buildToolbar();refreshTools()
        }
        (canvas.layoutParams as LayoutParams).apply{topMargin=dp(barHeight);bottomMargin=if(narrow)dp(44) else 0}
        (sidebar.layoutParams as LayoutParams).apply{topMargin=dp(barHeight);bottomMargin=if(narrow)dp(44) else 0}
        (notice.layoutParams as LayoutParams).topMargin=dp(barHeight+12)
        (bottomHost.layoutParams as LayoutParams).apply{bottomMargin=dp(if(narrow)60 else 16);width=min(dp(536),max(dp(128),w-dp(80)))}
        super.onMeasure(widthSpec,heightSpec)
    }
    fun startLayerTransform(){
        dismissPanel();session.command("deselect");canvas.deselect();session.tool="Transform";refreshTools();showNotice("Drag corners to resize. Two fingers move, pinch and rotate. Done applies; Cancel restores.")
    }
    private fun saveOptions(){
        AlertDialog.Builder(activity).setTitle("Save artwork")
            .setItems(arrayOf("Save project to Inkstone","Save PNG to photo gallery","Save JPG to photo gallery","Export PNG to folder","Export JPG to folder","Export PSD with layers","Export editable Inkstone")){_,which->
                when(which){0->activity.saveCurrent{if(it)showNotice("Artwork saved in gallery")};1->activity.saveImageToGallery("PNG");2->activity.saveImageToGallery("JPEG");3->activity.export("PNG");4->activity.export("JPEG");5->activity.export("PSD");6->activity.export("Inkstone")}
            }.setNegativeButton("Cancel",null).show()
    }
    private fun buildSidebar(){
        sidebar=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(0,dp(10),0,dp(10));setBackgroundColor(StudioColors.rail);contentDescription="Docked brush controls"}
        sizeSlider=VerticalSlider(activity,"Brush size",sqrt(session.size/600f)){if(session.tool=="Liquify")canvas.liquify.size=(it*it*600f).coerceAtLeast(8f)else session.size=(it*it*600f).coerceAtLeast(.5f);updateHud(sizeSlider,false)}
        opacitySlider=VerticalSlider(activity,"Brush opacity",session.opacity){if(session.tool=="Liquify")canvas.liquify.strength=it.coerceAtLeast(.01f)else session.opacity=it;updateHud(opacitySlider,true)}
        sidebar.addView(sizeSlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sidebar.addView(icon("Eyedropper"){dismissPanel();session.tool=if(session.tool=="Eyedropper")"Paint" else "Eyedropper";refreshTools();showNotice("Touch the canvas to pick a color")},LinearLayout.LayoutParams(dp(32),dp(44)))
        sidebar.addView(opacitySlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sizeSlider.tracking={show->trackHud(sizeSlider,false,show);if(!show)activity.savePreferences()}
        opacitySlider.tracking={show->trackHud(opacitySlider,true,show);if(!show)activity.savePreferences()}
        addView(sidebar,LayoutParams(dp(44),-1,Gravity.TOP or if(session.rightSidebar)Gravity.RIGHT else Gravity.LEFT).apply{topMargin=dp(barHeight)})
        layoutDock()
    }
    private fun layoutDock(){
        val dock=sidebar.layoutParams as LayoutParams
        dock.gravity=Gravity.TOP or if(session.rightSidebar)Gravity.RIGHT else Gravity.LEFT
        sidebar.layoutParams=dock
        val drawing=canvas.layoutParams as LayoutParams
        drawing.leftMargin=if(session.rightSidebar)0 else dp(44)
        drawing.rightMargin=if(session.rightSidebar)dp(44) else 0
        canvas.layoutParams=drawing
    }
    private fun penEvent(event:MotionEvent)= (0 until event.pointerCount).any{event.getToolType(it)==MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it)==MotionEvent.TOOL_TYPE_ERASER}
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        val pen=penEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_CANCEL)penTouchActive=false
        if(pen)when(event.actionMasked){
            MotionEvent.ACTION_DOWN->penTouchActive=true
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->penTouchActive=false
            MotionEvent.ACTION_POINTER_UP->if(event.getToolType(event.actionIndex) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER))penTouchActive=false
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN && !pen){
            fun hits(view:View)=view.visibility==VISIBLE && event.x>=view.left && event.x<view.right && event.y>=view.top && event.y<view.bottom
            blockedTouch=session.protectControls && penTouchActive && (hits(sidebar)||event.y<dp(barHeight))
        }
        if(blockedTouch && !pen){if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL)blockedTouch=false;return true}
        return super.dispatchTouchEvent(event)
    }
    private fun trackHud(slider:VerticalSlider,opacity:Boolean,show:Boolean){
        brushHud.removeCallbacks(hideHud)
        if(show){dismissPanel();hudSlider=slider;updateHud(slider,opacity);brushHud.bringToFront();StudioMotion.reveal(brushHud)}
        else brushHud.postDelayed(hideHud,650)
    }
    private fun updateHud(slider:VerticalSlider,opacity:Boolean){
        brushHud.labelOverride=if(session.tool=="Liquify" && opacity)"Strength"else null
        brushHud.value=slider.value;brushHud.opacityMode=opacity;brushHud.soft=(session.tool=="Erase" && session.eraserHardness<.7f) || session.preset.kind in listOf(2,4,8)
        brushHud.mark={slider.bookmark=slider.value;slider.invalidate();showNotice("Slider position saved")}
        val params=brushHud.layoutParams as? LayoutParams ?: return
        params.leftMargin=if(session.rightSidebar)width-dp(198) else dp(44)
        params.topMargin=(sidebar.top+slider.top+slider.thumbY-dp(102)).roundToInt().coerceIn(dp(barHeight+8),max(dp(barHeight+8),height-dp(168)))
        brushHud.layoutParams=params;brushHud.invalidate()
    }
    fun refreshTools(){
        if(!session.info.optBoolean("maskEditing")){colorBeforeMask?.let{session.color=it};colorBeforeMask=null}
        canvas.syncTransformTool()
        colorHeader?.setBackgroundColor(session.color)
        for((symbol,view)in icons){view.activeTool=when(symbol){"Color"->panelKey=="Colors";"Layers"->panelKey=="Layers";"Actions"->panelKey=="Actions";"Adjustments"->panelKey=="Adjustments";else->session.tool==symbol};view.swatch=session.color;view.invalidate()}
        if(::sizeSlider.isInitialized){sizeSlider.value=sqrt((if(session.tool=="Liquify")canvas.liquify.size else session.size)/600f);sizeSlider.contentDescription=if(session.tool=="Liquify")"Liquify size"else"Brush size"}
        if(::opacitySlider.isInitialized){opacitySlider.value=if(session.tool=="Liquify")canvas.liquify.strength else session.opacity;opacitySlider.contentDescription=if(session.tool=="Liquify")"Liquify strength"else"Brush opacity"}
        if(::bottom.isInitialized)updateBottom()
        canvas.invalidate()
    }
    private fun updateBottom(){
        bottom.removeAllViews();bottomOptions.removeAllViews();bottomOptionsScroll.visibility=GONE
        val context=when{canvas.quickShapeEdit.released->"Shape edit";session.info.optBoolean("maskEditing")->"Mask edit";else->session.tool}
        if(context!=bottomContext){bottomContext=context;bottomGroup=""}
        if(interfaceHidden || session.tool !in listOf("Select","Transform","Fill","Guide","Liquify") && !session.info.optBoolean("maskEditing") && !canvas.quickShapeEdit.released){bottomHost.visibility=GONE;return};bottomHost.visibility=VISIBLE
        var row=bottom
        fun button(label:String,selected:Boolean=false,action:()->Unit){val v=text(label,13f,if(selected)blue else Color.WHITE).apply{isClickable=true;isFocusable=true;setPadding(dp(10),0,dp(10),0);background=selectable();setOnClickListener{action()}};row.addView(v,LinearLayout.LayoutParams(-2,dp(44)))}
        fun group(label:String,detail:String="",items:()->Unit){
            val selected=bottomGroup==label
            val control=text(label+if(selected)" ▴"else" ▾",if(narrow)11f else 13f,if(selected)blue else Color.WHITE).apply{
                contentDescription=if(label=="Actions" && session.tool=="Select")"Selection actions" else label;tooltipText=if(detail.isEmpty())"$label options" else "$label: $detail";gravity=Gravity.CENTER;setPadding(dp(3),0,dp(3),0);isSingleLine=true;isClickable=true;isFocusable=true;background=selectable()
                setOnClickListener{bottomGroup=if(selected)""else label;updateBottom();StudioMotion.content(bottomHost);bottomOptionsScroll.scrollTo(0,0)}
            }
            bottom.addView(control,LinearLayout.LayoutParams(dp(if(narrow)64 else 86),dp(44)))
            if(selected){bottomOptionsScroll.visibility=VISIBLE;row=bottomOptions;items();row=bottom}
        }
        if(canvas.quickShapeEdit.released){
            button("Edit ${canvas.quickShapeEdit.name}"){showNotice("Drag a corner to resize; drag the handle above to rotate")}
            if(canvas.quickShapeEdit.name in listOf("Circle","Ellipse"))button("Circle"){canvas.quickShapeEdit.constrain()}
            if(canvas.quickShapeEdit.name in listOf("Rectangle","Square"))button("Square"){canvas.quickShapeEdit.constrain()}
            button("Cancel shape"){canvas.finishQuickShape(true);refreshTools()};button("Done"){canvas.finishQuickShape();refreshTools()};return
        }
        if(session.info.optBoolean("maskEditing")){
            button("Mask · Black hides"){session.color=Color.BLACK;refreshTools()};button("White reveals"){session.color=Color.WHITE;refreshTools()};button("Layer pixels"){maskAction("pixels")};return
        }
        when(session.tool){
            "Liquify"->{
                val l=canvas.liquify
                group("Mode",l.mode){for(mode in listOf("Push","Pinch","Expand","Twirl right","Twirl left","Restore"))button(mode,l.mode==mode){l.mode=mode;updateBottom()}}
                group("Brush"){button("Size: ${l.size.toInt()}"){liquifySlider(true)};button("Strength: ${(l.strength*100).toInt()}%"){liquifySlider(false)}}
                button("Reset"){l.reset()};button("Cancel"){l.finish(true);session.tool="Paint";refreshTools()};button("Done"){l.finish();session.tool="Paint";refreshTools()}
            }
            "Guide"->{button("Drag vanishing points"){};button("Done"){canvas.guides.editing=false;canvas.guides.save();session.tool="Paint";refreshTools();canvas.invalidate()}}
            "Select"->{
                group("Shape",canvas.selectionShape){for(shape in listOf("Automatic","Freehand","Rectangle","Ellipse"))button(shape,canvas.selectionShape==shape){canvas.selectionShape=shape;updateBottom()}}
                group("Combine",canvas.selectionMode){for(mode in listOf("Replace","Add","Remove"))button(mode,canvas.selectionMode==mode){canvas.selectionMode=mode;updateBottom()};button("Invert"){selectionAction("invertSelection")}}
                group("Clipboard"){button("Copy & Paste"){selectionAction("selectionCopyPaste",transform=true)};button("Copy"){selectionAction("selectionCopy")};button("Cut"){selectionAction("selectionCut")};button("Paste"){selectionAction("selectionPaste",transform=true)}}
                group("Refine"){button("Threshold"){selectionThreshold()};button("Feather"){selectionFeather()}}
                group("Saved"){
                    button("Save selection"){saveSelection()};button("Load selection"){loadSelection()};button("Delete saved"){loadSelection(delete=true)}
                }
                group("Actions"){button("Color Fill"){selectionAction("selectionFill",JSONObject().put("color",colorLong()))};button("Clear"){selectionAction("selectionClear")};button("Deselect"){selectionAction("deselect");canvas.deselect()};button("Transform"){session.tool="Transform";refreshTools()}}
            }
            "Transform"->{
                val t=canvas.rasterTransform
                group("Mode",t.mode){for(mode in listOf("Freeform","Uniform","Distort","Warp"))button(mode,t.mode==mode){t.mode=mode;canvas.invalidate();updateBottom()}}
                group("Adjust"){button("Smaller"){t.scale(.9f)};button("Larger"){t.scale(1.1f)};button("Flip H"){transform("sx",-1f)};button("Flip V"){transform("sy",-1f)};button("Rotate 45°"){transform("angle",Math.PI.toFloat()/4)};button("Fit to Canvas"){t.fitToCanvas()}}
                group("Options",t.interpolation){button("Snapping",t.snapping){t.snapping=!t.snapping;updateBottom()};button(t.interpolation){AlertDialog.Builder(activity).setTitle("Transform interpolation").setItems(arrayOf("Nearest","Bilinear")){_,index->t.interpolation=if(index==0)"Nearest"else"Bilinear";updateBottom()}.show()}}
                button("Reset"){t.reset()};button("Cancel"){t.finish(cancel=true);session.tool="Paint";refreshTools()};button("Done"){session.tool="Paint";refreshTools()}
            }
            "Fill"->{button("ColorDrop"){showNotice("Tap an enclosed area to fill it")};button("Fill layer"){session.command("fill",JSONObject().put("color",colorLong()));activity.scheduleSave()};button("Done"){session.tool="Paint";refreshTools()}}
        }
    }
    private fun transform(key:String,value:Float){canvas.rasterTransform.operation(key,value)}
    fun showNotice(message:String){notice.text=message;if(notice.visibility!=VISIBLE)StudioMotion.reveal(notice);notice.removeCallbacks(hideNotice);notice.postDelayed(hideNotice,1800)}
    private val hideNotice=Runnable{StudioMotion.chrome(notice,false,y=-dp(8).toFloat())}
    fun dismissPanel():Boolean {
        activity.cancelRecordingShortcut()
        departingPanel?.let{it.animate().cancel();removeView(it)};departingPanel=null
        val old=panel ?: return false
        panel=null;panelKey="";departingPanel=old
        StudioMotion.dismiss(old){removeView(old);if(departingPanel===old)departingPanel=null}
        refreshTools();return true
    }
    private var replacingPanel=false
    private var panelIsLeft=false
    private fun panel(key:String,title:String,height:Int=480,left:Boolean=false,extra:(LinearLayout)->Unit={},footer:(LinearLayout)->Unit={},content:(LinearLayout)->Unit){
        val previous=if(replacingPanel && panelIsLeft==left)panel as? LinearLayout else null
        if(panelKey==key && !replacingPanel){dismissPanel();return};if(previous==null)dismissPanel();departingPanel?.let{it.animate().cancel();removeView(it)};departingPanel=null;panelKey=key;panelIsLeft=left
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
        footer(outer)
        val screenW=resources.displayMetrics.widthPixels
        val w=min(dp(if(key=="Brushes")450 else if(key=="Actions" || key=="Multicolor")390 else 350),screenW-dp(24))
        val h=min(dp(height),resources.displayMetrics.heightPixels-dp(barHeight+18))
        val margin=if(left)dp(64) else dp(12)
        val anchor=if(left)dp(44).toFloat() else {
            val control=icons[if(key=="Brushes")session.tool else if(key=="Colors")"Color" else key]
            if(control!=null && control.width>0){val at=IntArray(2);val own=IntArray(2);control.getLocationInWindow(at);getLocationInWindow(own);at[0]-own[0]+control.width/2f-(screenW-w-margin)}else w*.5f
        }
        if(previous!=null){
            previous.animate().cancel()
            previous.removeAllViews()
            while(outer.childCount>0){val child=outer.getChildAt(0);outer.removeView(child);previous.addView(child)}
            val lp=previous.layoutParams as? LayoutParams
            val geometryChanged=lp?.width!=w || lp.height!=h
            previous.layoutParams=LayoutParams(w,h,Gravity.TOP or if(left)Gravity.LEFT else Gravity.RIGHT).apply{topMargin=dp(barHeight);if(left)leftMargin=min(margin,screenW-w-dp(12))else rightMargin=margin}
            if(geometryChanged)previous.background=GlassSurface(canvas,if(left)min(margin,screenW-w-dp(12)).toFloat()else(screenW-w-margin).toFloat(),dp(barHeight).toFloat(),dp(StudioShape.CORNER_DP).toFloat())
            panelKey=key
            refreshTools()
            return
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
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){changed(s.progress/100f)};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){title.text="$label $p%";if(user)changed(p/100f)}})
        body.addView(seek,LinearLayout.LayoutParams(-1,dp(40)))
    }

    private fun preferenceSlider(body:LinearLayout,label:String,value:Float,format:(Float)->String,changed:(Float)->Unit){
        val row=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(12),0)}
        row.addView(text(label,15f),LinearLayout.LayoutParams(0,dp(30),1f))
        val detail=text(format(value),13f,0xffaab8bf.toInt());row.addView(detail);body.addView(row)
        val seek=SeekBar(activity).apply{max=100;progress=(value*100).roundToInt();contentDescription=label;setPadding(dp(22),0,dp(22),0);progressTintList=android.content.res.ColorStateList.valueOf(StudioColors.accentSoft);thumbTintList=progressTintList}
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user){detail.text=format(p/100f);changed(p/100f)}}})
        body.addView(seek,LinearLayout.LayoutParams(-1,dp(24)))
    }
    private fun eraserSettings(left:Boolean=panelIsLeft){
        rebuildPanel{
            panel("Eraser","Eraser",350,left=left){body->
                body.addView(row("← Back to Studio"){brushStudio(left)})
                body.addView(text("Soft edge ← Hard edge",16f),LinearLayout.LayoutParams(-1,dp(48)))
                slider(body,"Hardness",session.eraserHardness){session.eraserHardness=it;activity.savePreferences()}
                body.addView(row("Soft"){session.eraserHardness=0f;activity.savePreferences();rebuildPanel{eraserSettings(left)}})
                body.addView(row("Medium"){session.eraserHardness=.5f;activity.savePreferences();rebuildPanel{eraserSettings(left)}})
                body.addView(row("Hard"){session.eraserHardness=1f;activity.savePreferences();rebuildPanel{eraserSettings(left)}})
            }
        }
    }
    private fun brushes(){
        canvas.finishQuickShape()
        if(panelKey != "Brushes") {
            brushGroup = session.preset.group
        }
        panel("Brushes",when(session.tool){"Erase"->"Eraser Library";"Smudge"->"Smudge Library";else->"Brush Library"},820,extra={heading->heading.addView(icon("Add"){addLibraryItem()})}){body->
            val columns=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            val categories=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
            val list=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(3),dp(2),dp(4),0)}
            val listScroll=ScrollView(activity).apply{isVerticalScrollBarEnabled=false;addView(list);tag="brush-presets-scroll"}
            val categoryViews=mutableMapOf<String,LinearLayout>()
            fun populate(){
                list.removeAllViews();listScroll.scrollTo(0,0)
                categoryViews.forEach{(group,item)->item.background=rounded(if(group==brushGroup)blue else 0x226e7175,StudioShape.CORNER_DP);(item.getChildAt(1) as TextView).setTextColor(if(group==brushGroup)Color.WHITE else 0xffbdc6ca.toInt())}
                val presets=if(brushGroup=="Recent")(RecentBrushes(activity).list(session.tool)+session.preset).distinct() else BrushLibrary(activity).all().filter{it.group==brushGroup}.sortedBy{if(brushGroup=="Airbrushing")listOf("Soft Brush","Medium Brush","Medium Hard Brush","Hard Brush","Soft Blend","Medium Blend","Medium Hard Blend","Hard Blend","Soft Airbrush","Medium Airbrush","Fine Airbrush").indexOf(it.name) else 0}
                val previews=mutableListOf<BrushPreview>()
                for(preset in presets){
                    val preview=BrushPreview(activity,preset,session.preset==preset,session.tool=="Erase").apply{
                        isClickable=true;isFocusable=true
                        setOnClickListener{StudioMotion.bounce(this);session.choose(preset);activity.savePreferences();refreshTools();previews.forEach{it.chosen=it.preset==preset;it.invalidate()}}
                        setOnLongClickListener{session.choose(preset);activity.savePreferences();brushOptions(preset);true}
                    }
                    previews.add(preview);list.addView(preview,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(3)})
                }
                if(presets.isEmpty())list.addView(text("No brushes yet. Use + to create one.",13f,0xffb4bec2.toInt()),LinearLayout.LayoutParams(-1,dp(64)))
            }
            for(group in BrushLibrary(activity).categories()){
                val item=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,0,0);isClickable=true;isFocusable=true;contentDescription=group;setOnClickListener{brushGroup=group;populate()}}
                item.addView(CategoryIcon(activity,group,false),LinearLayout.LayoutParams(dp(25),dp(32)))
                item.addView(text(group,12f).apply{setPadding(dp(8),0,0,0)},LinearLayout.LayoutParams(-1,-1))
                item.setOnLongClickListener{categoryOptions(group);true}
                categoryViews[group]=item
                categories.addView(item,LinearLayout.LayoutParams(-1,dp(46)).apply{setMargins(dp(2),dp(4),dp(6),dp(4))})
            }
            val categoryScroll=ScrollView(activity).apply{isVerticalScrollBarEnabled=false;addView(categories);tag="brush-categories-scroll"}
            categoryViews[brushGroup]?.let{v->categoryScroll.post{categoryScroll.scrollTo(0,(v.top-dp(30)).coerceAtLeast(0))}}
            columns.addView(categoryScroll,LinearLayout.LayoutParams(dp(145),-1))
            columns.addView(listScroll,LinearLayout.LayoutParams(0,-1,1f))
            populate()
            body.addView(columns,LinearLayout.LayoutParams(-1,-1))
        }
    }
    fun onToolSwitched() {
        brushGroup = session.preset.group
        refreshTools()
        if (panelKey == "Brushes") rebuildPanel(::brushes)
    }
    internal fun multicolorSettings(left:Boolean=false){
        rebuildPanel{
            panel("Multicolor","Multicolor",700,left=left,extra={heading->
                heading.addView(text("×",25f).apply{contentDescription="Close multicolor";isClickable=true;isFocusable=true;setOnClickListener{dismissPanel()}},LinearLayout.LayoutParams(dp(44),dp(48)))
            },footer={footer->footer.addView(row("Done"){brushStudio(left)})}){body->
                body.addView(MulticolorControls(activity,session){activity.savePreferences();canvas.invalidate()})
            }
        }
    }
    private fun brushStudio(left:Boolean=panelIsLeft){
        rebuildPanel{
            panel("Studio",if(session.tool=="Erase")"Eraser Studio" else "Brush Studio",560,left=left){body->
                if(left)body.addView(row("← Back to Preferences"){actionTab("Prefs")})
                else body.addView(row("← Back to Library"){rebuildPanel(::brushes)})
                body.addView(text(session.preset.name,17f),LinearLayout.LayoutParams(-1,dp(42)))
                body.addView(BrushPreview(activity,currentPreset(),eraser=session.tool=="Erase"),LinearLayout.LayoutParams(-1,dp(90)))
                if(session.tool=="Erase")body.addView(row("Edge hardness"){eraserSettings(left)})
                slider(body,"Flow",session.flow){session.flow=it.coerceAtLeast(.01f);activity.savePreferences()}
                slider(body,"Stabilization",session.stabilization/.95f){session.stabilization=it*.95f;activity.savePreferences()}
                preferenceSlider(body,"Fade tail length",session.fadeLength/2000f,{if(it==0f)"Off" else "${(it*2000).roundToInt()} px"}){session.fadeLength=it*2000f;activity.savePreferences()}
                slider(body,"Pressure → Size",session.pressureSize){session.pressureSize=it;activity.savePreferences()}
                slider(body,"Pressure → Opacity",session.pressureOpacity){session.pressureOpacity=it;activity.savePreferences()}
                if(session.tool=="Paint"){
                    body.addView(row("Multicolor settings",session.currentBrush.colorMode){multicolorSettings(left)})
                    slider(body,"Wet mix · experimental",session.currentBrush.wetMix){session.currentBrush.wetMix=it;activity.savePreferences()}
                }
                body.addView(row(if(session.preset.customId.isEmpty())"Save as new brush"else"Save brush changes"){saveLibraryBrush()})
                body.addView(row("Library options"){brushOptions(session.preset)})
                body.addView(row("Reset brush"){session.choose(session.preset);activity.savePreferences();rebuildPanel{brushStudio(left)}})
                body.addView(row("Done"){if(left)actionTab("Prefs") else rebuildPanel(::brushes)})
            }
        }
    }
    private fun currentPreset()=session.preset.copy(kind=session.currentBrush.kind,colorLayout=session.currentBrush.colorLayout,size=session.size,flow=session.flow,pressureSize=session.pressureSize,pressureOpacity=session.pressureOpacity,fadeLength=session.fadeLength,opacity=session.opacity,stabilization=session.stabilization,hardness=session.eraserHardness,colorMode=session.currentBrush.colorMode,colorCycle=session.currentBrush.colorCycle,wetMix=session.currentBrush.wetMix,palette=session.currentBrush.palette)
    private fun saveLibraryBrush(){
        val library=BrushLibrary(activity);val p=currentPreset()
        if(p.customId.isEmpty())activity.textDialog("New brush name",p.name+" copy"){name->runCatching{library.copy(p,name)}.onSuccess{session.choose(it);activity.savePreferences();brushStudio()}.onFailure{showNotice(it.message?:"Cannot save brush")}}
        else runCatching{library.save(p)}.onSuccess{session.choose(it);activity.savePreferences();showNotice("Brush saved");brushStudio()}.onFailure{showNotice(it.message?:"Cannot save brush")}
    }
    private fun chooseBrushPalette(left:Boolean){val all=PaletteStore(activity).all
        AlertDialog.Builder(activity).setTitle("Brush palette").setItems(all.map{it.name}.toTypedArray()){_,i->if(all[i].colors.size<2){showNotice("Add at least two colors to this palette first")}else{session.currentBrush.palette=all[i].colors.take(12);activity.savePreferences();brushStudio(left)}}.show()
    }
    private fun addLibraryItem(){AlertDialog.Builder(activity).setTitle("Brush library").setItems(arrayOf("Brush Studio","New brush","Duplicate current brush","New category")){_,i->when(i){
        0->brushStudio()
        1,2->activity.textDialog("Brush name",if(i==1)"My Brush"else session.preset.name+" copy"){name->runCatching{BrushLibrary(activity).copy(if(i==1)BrushPreset("My Brush","My Brushes",0,16f)else currentPreset(),name,if(brushGroup in listOf("Recent","Imported"))"My Brushes"else brushGroup)}.onSuccess{session.choose(it);brushGroup=it.group;activity.savePreferences();brushStudio()}.onFailure{showNotice(it.message?:"Cannot create brush")}}
        3->activity.textDialog("Category name",""){name->runCatching{BrushLibrary(activity).createCategory(name)}.onSuccess{brushGroup=name.trim().take(60);rebuildPanel(::brushes)}.onFailure{showNotice(it.message?:"Cannot create category")}}
    }}.show()}
    private fun categoryOptions(group:String){val lib=BrushLibrary(activity);if(group !in lib.customCategories())return
        AlertDialog.Builder(activity).setTitle(group).setItems(arrayOf("Rename category","Delete category")){_,i->if(i==0)activity.textDialog("Category name",group){name->runCatching{lib.renameCategory(group,name)}.onSuccess{brushGroup=name.trim().take(60);rebuildPanel(::brushes)}.onFailure{showNotice(it.message?:"Cannot rename category")}}
            else AlertDialog.Builder(activity).setMessage("Move its brushes to My Brushes and delete this category?").setPositiveButton("Delete"){_,_->lib.deleteCategory(group);brushGroup="My Brushes";rebuildPanel(::brushes)}.setNegativeButton("Cancel",null).show()}.show()
    }
    private fun brushOptions(p:BrushPreset){val lib=BrushLibrary(activity);val custom=p.customId.isNotEmpty();val options=if(custom)arrayOf("Edit brush","Duplicate","Rename","Move to category","Delete")else arrayOf("Edit brush","Duplicate")
        AlertDialog.Builder(activity).setTitle(p.name).setItems(options){_,i->when(i){
            0->brushStudio()
            1->activity.textDialog("Brush name",p.name+" copy"){name->runCatching{lib.copy(currentPreset(),name)}.onSuccess{session.choose(it);activity.savePreferences();brushStudio()}.onFailure{showNotice(it.message?:"Cannot duplicate brush")}}
            2->activity.textDialog("Brush name",p.name){name->session.choose(lib.save(currentPreset().copy(name=name)));activity.savePreferences();rebuildPanel(::brushes)}
            3->{val groups=lib.categories().filter{it!="Recent"};AlertDialog.Builder(activity).setTitle("Move brush").setItems(groups.toTypedArray()){_,n->session.choose(lib.save(currentPreset().copy(group=groups[n])));brushGroup=groups[n];activity.savePreferences();rebuildPanel(::brushes)}.show()}
            4->AlertDialog.Builder(activity).setMessage("Delete ${p.name}?").setPositiveButton("Delete"){_,_->lib.delete(p.customId);session.choose(brushPresets[0]);activity.savePreferences();rebuildPanel(::brushes)}.setNegativeButton("Cancel",null).show()
        }}.show()
    }
    private fun colors(){
        canvas.finishQuickShape()
        panel("Colors","Colors",710,extra={h->h.addView(View(activity).apply{setBackgroundColor(session.color);contentDescription="Active color preview";colorHeader=this;bindPaletteDrag(this){session.color};setOnClickListener{showNotice("Drag this color onto the palette below") }},LinearLayout.LayoutParams(dp(42),dp(28)).apply{rightMargin=dp(18)})},footer={outer->
            val tabs=LinearLayout(activity).apply{gravity=Gravity.CENTER}
            for(tab in listOf("Disc","Classic","Harmony","Value","Palettes"))tabs.addView(text(tab,11f,if(tab==colorTab)blue else 0xffbbbbbb.toInt()).apply{setPadding(dp(5),0,dp(5),0);gravity=Gravity.CENTER;isFocusable=true;setOnClickListener{colorTab=tab;rebuildPanel(::colors)}},LinearLayout.LayoutParams(0,dp(48),1f))
            outer.addView(tabs)
        }){body->
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
            body.addView(row("Palette",palettes.active.name){choosePalette()})
            body.addView(PaletteGrid(activity,palettes.active.colors,
                pick={session.color=it;refreshTools();activity.savePreferences()},
                replace={palettes.replace(it,session.color);rebuildPanel(::colors)},
                drop={addPaletteColor(it)}))
            body.addView(row("Add current color","+"){addPaletteColor(session.color)})
            if(colorTab=="Palettes"){
                body.addView(row("New palette","+"){createPalette()})
                if(palettes.isCustom){
                    body.addView(row("Rename palette"){activity.textDialog("Rename palette",palettes.active.name){name->runCatching{palettes.rename(name)}.onSuccess{rebuildPanel(::colors)}.onFailure{showNotice(it.message?:"Enter a name")}}})
                    body.addView(row("Delete palette"){AlertDialog.Builder(activity).setTitle("Delete ${palettes.active.name}?").setMessage("This removes the palette and its saved colors.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->palettes.delete();rebuildPanel(::colors)}.show()})
                }
            }
            body.addView(row("Eyedropper","Pick from canvas"){dismissPanel();session.tool="Eyedropper";refreshTools();showNotice("Touch the canvas to pick a color")})
            body.addView(row("ColorDrop","Fill an enclosed area"){dismissPanel();session.tool="Fill";refreshTools();showNotice("Tap an enclosed area to fill it")})

        }
    }

    private fun addPaletteColor(color:Int){
        val added=palettes.add(color)
        rebuildPanel(::colors)
        showNotice(if(added)"Color added to ${palettes.active.name}" else "Color is already in this palette")
    }
    private fun createPalette(){activity.textDialog("New palette",""){name->runCatching{palettes.create(name)}.onSuccess{rebuildPanel(::colors)}.onFailure{showNotice(it.message?:"Enter a name")}}}
    private fun choosePalette(){
        val options=palettes.all
        AlertDialog.Builder(activity).setTitle("Palettes").setItems((options.map{it.name}+"+ New palette").toTypedArray()){_,index->
            if(index==options.size)createPalette()else{palettes.select(options[index].id);rebuildPanel(::colors)}
        }.show()
    }

    private fun layers(){
        panel("Layers","Layers",710,extra={h->h.addView(text("Group",14f).apply{setPadding(dp(10),0,dp(10),0);isClickable=true;contentDescription="Create layer group";setOnClickListener{activity.textDialog("Group name","New group"){name->groupAction("create",null,JSONObject().put("name",name))}}});h.addView(icon("Add"){session.command("add"){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}})}){body->
            body.tag="layer-list"
            val layers=session.info.getJSONArray("layers");val active=session.info.getInt("active")
            val expanded=mutableListOf<View>()
            val groups=session.info.optJSONArray("groups")?:org.json.JSONArray()
            val seenGroups=mutableSetOf<Long>()
            fun header(group:JSONObject){
                val id=group.getLong("id");val name=group.getString("name")
                val line=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(0x554c535a,StudioShape.CORNER_DP)}
                line.addView(text((if(group.optBoolean("collapsed"))"▸ "else"▾ ")+name+(if(group.optDouble("opacity",1.0)<1.0)" · ${(group.optDouble("opacity",1.0)*100).roundToInt()}%"else""),15f).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,0,0);isClickable=true;contentDescription="Toggle group $name";setOnClickListener{groupAction("update",id,JSONObject().put("collapsed",!group.optBoolean("collapsed")))}},LinearLayout.LayoutParams(0,dp(48),1f))
                line.addView(text(GroupAppearance.label(group.optString("blend","Pass Through")),13f).apply{gravity=Gravity.CENTER;isClickable=true;contentDescription="Options for group $name";setOnClickListener{groupOptions(group)}},LinearLayout.LayoutParams(dp(44),dp(48)))
                line.addView(CheckBox(activity).apply{isChecked=group.getBoolean("visible");contentDescription="$name group visible";setOnCheckedChangeListener{_,checked->groupAction("update",id,JSONObject().put("visible",checked))}},LinearLayout.LayoutParams(dp(44),dp(48)))
                line.tag="group-$id"
                LayerDrag.target(line,session,folder=true,open=!group.optBoolean("collapsed"),canJoin={source->(0 until layers.length()).map{layers.getJSONObject(it)}.firstOrNull{it.getLong("id")==source}?.optLong("groupId",-1)!=id}){layer,above,join->
                    val members=(layers.length()-1 downTo 0).map{layers.getJSONObject(it)}.filter{it.optLong("groupId",-1)==id};val own=members.any{it.getLong("id")==layer}
                    moveLayer(layer,if(join)null else members.firstOrNull()?.getLong("id"),if(join || (own && !above))id else null,if(own && !above)true else above)
                }
                body.addView(line,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(5)})
            }
            for(i in layers.length()-1 downTo 0){
                val layer=layers.getJSONObject(i);val name=layer.getString("name")
                val group=if(layer.isNull("groupId"))null else (0 until groups.length()).map{groups.getJSONObject(it)}.firstOrNull{it.getLong("id")==layer.getLong("groupId")}
                if(group!=null){if(seenGroups.add(group.getLong("id")))header(group);if(group.optBoolean("collapsed"))continue}
                val layerRow=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(5),dp(5),dp(8),dp(5));background=rounded(if(i==active)blue else 0x443d3d3d,StudioShape.CORNER_DP);isClickable=true;isFocusable=true}
                val thumbnail=FrameLayout(activity).apply{background=rounded(0x336e777c,StudioShape.CORNER_DP);clipToOutline=true}
                thumbnail.addView(ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(session.layerThumbnails.getOrNull(i))},LayoutParams(-1,-1));layerRow.addView(thumbnail,LinearLayout.LayoutParams(dp(56),dp(56)))
                layerRow.addView(text(name+(if(layer.optBoolean("locked"))" · Locked" else "")+(if(layer.optBoolean("hasMask"))" · Mask" else ""),16f),LinearLayout.LayoutParams(0,-1,1f))
                if(layer.optBoolean("hasMask"))layerRow.addView(text(if(i==active && session.info.optBoolean("maskEditing"))"[M]"else"M",14f).apply{gravity=Gravity.CENTER;isClickable=true;contentDescription="Edit mask for $name";tooltipText="Edit layer mask";setOnClickListener{session.command("active",JSONObject().put("index",i)){maskAction("edit")}}},LinearLayout.LayoutParams(dp(40),dp(48)))
                val dropdown=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;visibility=GONE;setPadding(dp(8),dp(10),dp(8),dp(12));background=rounded(0x33373737,StudioShape.CORNER_DP)}
                expanded.add(dropdown)
                val blendButton=text(if(layer.getString("blend")=="Normal")"N" else layer.getString("blend").take(1),14f).apply{gravity=Gravity.CENTER;setPadding(0,0,0,0);contentDescription="Blend options for $name";isClickable=true;isFocusable=true;setOnClickListener{
                    android.transition.TransitionManager.beginDelayedTransition(body,android.transition.AutoTransition().apply{duration=180})
                    val open=dropdown.visibility!=VISIBLE;expanded.forEach{it.visibility=GONE};dropdown.visibility=if(open)VISIBLE else GONE
                }}
                layerRow.addView(blendButton,LinearLayout.LayoutParams(dp(32),dp(48)))
                layerRow.addView(CheckBox(activity).apply{isChecked=layer.optBoolean("ownVisible",layer.getBoolean("visible"));contentDescription="$name visible";buttonTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);setOnCheckedChangeListener{_,b->session.command("layer",JSONObject().put("index",i).put("visible",b));activity.scheduleSave()}},LinearLayout.LayoutParams(dp(36),dp(48)))
                layerRow.setOnClickListener{if(i==active)layerOptions(i)else session.command("active",JSONObject().put("index",i)){rebuildPanel(::layers)}}
                LayerDrag.source(layerRow,session,layer.getLong("id"),name)
                LayerDrag.target(layerRow,session){source,above,_->
                    val id=layer.optLong("groupId",-1);val bottom=id>=0 && (i==0 || layers.getJSONObject(i-1).optLong("groupId",-1)!=id)
                    moveLayer(source,layer.getLong("id"),if(id<0 || (!above && bottom))null else id,above)
                }
                val actions=LinearLayout(activity)
                fun swipeAction(label:String,color:Int=0xff303030.toInt(),run:()->Unit){actions.addView(text(label,12f).apply{gravity=Gravity.CENTER;setPadding(dp(2),0,dp(2),0);background=rounded(color,StudioShape.CORNER_DP);isClickable=true;isFocusable=true;contentDescription="$label $name";setOnClickListener{isEnabled=false;run()}},LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=dp(2)})}
                swipeAction(if(layer.optBoolean("locked"))"Unlock" else "Lock"){session.command("layer",JSONObject().put("index",i).put("locked",!layer.optBoolean("locked"))){rebuildPanel(::layers);activity.scheduleSave()}}
                fun action(op:String){session.command("active",JSONObject().put("index",i)){session.command(op){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}}}
                swipeAction("Duplicate"){action("duplicate")};swipeAction("Delete",0xffff4c45.toInt()){action("delete")}
                body.addView(SwipeLayerRow(activity,layerRow,actions,dp(216)).apply{contentDescription="Layer row $name";tag="layer-${layer.getLong("id")}"},LinearLayout.LayoutParams(-1,dp(69)).apply{setMargins(dp(if(group!=null)22 else 2),dp(1),dp(2),dp(1))})
                val opacityRow=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL};opacityRow.addView(text("Opacity",15f),LinearLayout.LayoutParams(0,dp(32),1f));val opacityLabel=text(if(layer.getDouble("opacity")==1.0)"Max" else "${(layer.getDouble("opacity")*100).roundToInt()}%",14f);opacityRow.addView(opacityLabel);dropdown.addView(opacityRow)
                dropdown.addView(SeekBar(activity).apply{max=100;progress=(layer.getDouble("opacity")*100).roundToInt();contentDescription="Opacity for $name";progressTintList=android.content.res.ColorStateList.valueOf(blue);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){opacityLabel.text=if(p==100)"Max" else "$p%"};override fun onStopTrackingTouch(s:SeekBar){session.command("layer",JSONObject().put("index",i).put("opacity",s.progress/100f));activity.scheduleSave()}})},LinearLayout.LayoutParams(-1,dp(28)))
                val modes=listOf("Darken","Multiply","Colour Burn","Linear Burn","Darker Colour","Normal","Lighten","Screen","Colour Dodge","Add","Overlay","Hard Light","Difference","Exclusion","Subtract")
                val modeRows=mutableMapOf<String,View>()
                for(mode in modes){val v=row(mode,if(mode=="Normal")"N" else mode.take(1)){
                    session.command("layer",JSONObject().put("index",i).put("blend",mode))
                    modeRows.forEach{(m,v)->v.background=rounded(if(m==mode)blue else Color.TRANSPARENT,StudioShape.CORNER_DP)};blendButton.text=if(mode=="Normal")"N" else mode.take(1);activity.scheduleSave()
                };v.contentDescription="Blend $mode for $name";v.background=rounded(if(mode==layer.getString("blend"))blue else Color.TRANSPARENT,StudioShape.CORNER_DP);modeRows[mode]=v;dropdown.addView(v,LinearLayout.LayoutParams(-1,dp(40)))}
                body.addView(dropdown,LinearLayout.LayoutParams(-1,-2).apply{leftMargin=if(group!=null)dp(20)else 0})
            }
            for(j in 0 until groups.length()){val group=groups.getJSONObject(j);if(seenGroups.add(group.getLong("id")))header(group)}
            body.addView(row("Background colour","●"){
                val colors=intArrayOf(Color.WHITE,0xfffaf9f6.toInt(),0xff181818.toInt(),session.color)
                AlertDialog.Builder(activity).setTitle("Background colour").setItems(arrayOf("White","Warm paper","Black","Current colour","Toggle transparency")){_,which->
                    if(which<4)session.command("background",JSONObject().put("color",colors[which].toLong() and 0xffffffffL))else session.command("background",JSONObject().put("visible",!session.info.getBoolean("backgroundVisible")))
                    activity.scheduleSave()
                }.show()
            })
        }
    }
    private fun moveLayer(id:Long,target:Long?,group:Long?,above:Boolean){
        val motion=LayerDrag.capture(this,session)
        session.command("moveLayer",JSONObject().put("id",id).put("targetId",target?:JSONObject.NULL).put("groupId",group?:JSONObject.NULL).put("above",above)){session.thumbnails{rebuildPanel(::layers);motion.settle(this)};activity.scheduleSave()}
    }
    private fun groupAction(action:String,id:Long?,values:JSONObject=JSONObject()){
        values.put("action",action);if(id!=null)values.put("id",id)
        session.command("group",values){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}
    }
    private fun groupOptions(group:JSONObject){
        val id=group.getLong("id")
        AlertDialog.Builder(activity).setTitle(group.getString("name")).setItems(arrayOf("Rename",if(group.optBoolean("locked"))"Unlock group"else"Lock group","Move contents","Ungroup","Blend & opacity")){_,which->when(which){
            0->activity.textDialog("Group name",group.getString("name")){groupAction("update",id,JSONObject().put("name",it))}
            1->groupAction("update",id,JSONObject().put("locked",!group.optBoolean("locked")))
            2->GroupMoveDialog.show(activity){x,y->groupAction("translate",id,JSONObject().put("x",x).put("y",y))}
            3->groupAction("ungroup",id)
            4->GroupAppearance.show(activity,group.getString("name"),group.optString("blend","Pass Through"),group.optDouble("opacity",1.0).toFloat()){mode,opacity->groupAction("update",id,JSONObject().put("blend",mode).put("opacity",opacity.toDouble()))}
        }}.show()
    }
    private fun chooseLayerGroup(index:Int){
        val groups=session.info.optJSONArray("groups")?:org.json.JSONArray()
        val options=listOf("New group","Remove from group")+(0 until groups.length()).map{groups.getJSONObject(it).getString("name")}
        AlertDialog.Builder(activity).setTitle("Layer group").setItems(options.toTypedArray()){_,which->
            val layer=session.info.getJSONArray("layers").getJSONObject(index)
            when(which){
                0->activity.textDialog("Group name","New group"){groupAction("create",null,JSONObject().put("name",it).put("index",index))}
                1->if(!layer.isNull("groupId"))groupAction("removeMember",layer.getLong("groupId"),JSONObject().put("index",index))
                else->groupAction("member",groups.getJSONObject(which-2).getLong("id"),JSONObject().put("index",index))
            }
        }.show()
    }
    private fun maskAction(action:String,enabled:Boolean?=null){
        val values=JSONObject().put("action",action);if(enabled!=null)values.put("enabled",enabled)
        session.command("mask",values){
            if(action in listOf("add","edit","invert")){if(colorBeforeMask==null)colorBeforeMask=session.color;session.tool="Paint";session.color=Color.BLACK;showNotice("Mask: black hides · white reveals")}
            session.thumbnails{dismissPanel();refreshTools()};activity.scheduleSave()
        }
    }
    private fun layerOptions(index:Int){
        if(session.info.getInt("active")!=index){session.command("active",JSONObject().put("index",index)){layerOptions(index)};return}
        val l=session.info.getJSONArray("layers").getJSONObject(index)
        rebuildPanel{
            panel("Layer Options",l.getString("name"),680){body->
                body.addView(row("← Back to Layers"){rebuildPanel(::layers)})
                    body.addView(row("Move / resize contents"){startLayerTransform()})
                body.addView(row("Layer group"){chooseLayerGroup(index)})
                body.addView(row("Rename"){activity.textDialog("Layer name",l.getString("name")){name->session.command("layer",JSONObject().put("name",name)){rebuildPanel(::layers);activity.scheduleSave()}}})
                slider(body,"Opacity",l.getDouble("opacity").toFloat()){value->session.command("layer",JSONObject().put("opacity",value));activity.scheduleSave()}
                body.addView(row("Blend Mode",l.getString("blend")){
                    val modes=arrayOf("Normal","Multiply","Screen","Overlay","Add","Darken","Lighten","Difference")
                    AlertDialog.Builder(activity).setTitle("Blend Mode").setSingleChoiceItems(modes,modes.indexOf(l.getString("blend"))){dialog,which->session.command("layer",JSONObject().put("blend",modes[which])){rebuildPanel(::layers);activity.scheduleSave()};dialog.dismiss()}.setNegativeButton("Cancel",null).show()
                })
                toggle(body,"Alpha Lock",l.getBoolean("alphaLock")){v->session.command("layer",JSONObject().put("alphaLock",v));activity.scheduleSave()}
                if(index>0)toggle(body,"Clipping Mask",l.getBoolean("clipping")){v->session.command("layer",JSONObject().put("clipping",v));activity.scheduleSave()}
                if(!l.optBoolean("hasMask"))body.addView(row("Add layer mask"){maskAction("add")})
                else{
                    body.addView(row("Edit layer mask"){maskAction("edit")});body.addView(row("Select layer pixels"){maskAction("pixels")})
                    toggle(body,"Enable layer mask",l.optBoolean("maskEnabled",true)){v->maskAction("enable",v)}
                    body.addView(row("Invert layer mask"){maskAction("invert")})
                    body.addView(row("Apply layer mask"){maskAction("apply")});body.addView(row("Delete layer mask"){maskAction("delete")})
                }
                for((label,op)in listOf("Duplicate" to "duplicate","Fill Layer" to "fill","Clear" to "clear","Merge Down" to "merge","Delete" to "delete")){
                    body.addView(row(label){session.command(op,JSONObject().put("color",colorLong())){rebuildPanel(::layers);activity.scheduleSave()}})
                }
                body.addView(row("Move Up"){session.command("reorder",JSONObject().put("index",index).put("to",min(index+1,session.info.getJSONArray("layers").length()-1))){rebuildPanel(::layers);activity.scheduleSave()}})
                body.addView(row("Move Down"){session.command("reorder",JSONObject().put("index",index).put("to",max(index-1,0))){rebuildPanel(::layers);activity.scheduleSave()}})
            }
        }
    }
    private fun symmetrySettings(){
        val modes=arrayOf("Off","Vertical","Horizontal","Quadrant","Radial 4")
        AlertDialog.Builder(activity).setTitle("Drawing symmetry").setSingleChoiceItems(modes,modes.indexOf(session.symmetryMode)){dialog,which->session.setSymmetry(modes[which]);activity.savePreferences();canvas.invalidate();dialog.dismiss();dismissPanel();showNotice("Symmetry: ${modes[which]} · Paint and Erase")}.setNegativeButton("Cancel",null).show()
    }
    internal var colorBeforeMask:Int?=null;private set
    private var reference:ReferencePanel?=null
    internal fun showReference(){
        reference?.let{it.bringToFront();return}
        reference=ReferencePanel(activity,{color->session.color=color;refreshTools()},{reference=null}).also{it.attach(this)}
    }
    private fun actions(){
        if(panelKey=="Actions"){dismissPanel();return}
        if(panel!=null && panelIsLeft && !replacingPanel){rebuildPanel{actionTab(currentActionTab)};return}
        actionTab(currentActionTab)
    }
    private fun actionTab(tab:String){
        if(panel!=null && panelIsLeft && !replacingPanel){rebuildPanel{actionTab(tab)};return}
        if(panelKey!="Actions")dismissPanel()
        currentActionTab=tab
        panel("Actions","Actions",750,left=true){body->
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
            "Add"->{body.addView(row("Insert a photo"){dismissPanel();activity.importImage()});body.addView(row("Import Inkstone / PSD document"){dismissPanel();activity.importDocument()});body.addView(row("Rename artwork",activity.documentName){activity.renameCurrent()});body.addView(row("Save artwork"){activity.saveCurrent{if(it)showNotice("Artwork saved")}})}
            "Canvas"->{
                body.addView(row("Canvas information","${session.canvasWidth} × ${session.canvasHeight}"){AlertDialog.Builder(activity).setTitle(activity.documentName).setMessage("${session.canvasWidth} × ${session.canvasHeight} pixels\n${session.info.getJSONArray("layers").length()} layers\nRGBA · 8 bits per channel\n${session.info.getInt("undo")} undo actions available\nStored locally as an Inkstone document").setPositiveButton("Done",null).show()})
                toggle(body,"Lock canvas position",session.canvasLocked){session.canvasLocked=it;activity.savePreferences()}
                toggle(body,"Pinch to fit canvas",session.pinchToFit){session.pinchToFit=it;activity.savePreferences();showNotice(if(it)"Quick zoom out snaps canvas into view" else "Pinch to fit disabled")}
                body.addView(row("Symmetry",session.symmetryMode){symmetrySettings()})
                body.addView(row("Reference image"){dismissPanel();showReference()})
                toggle(body,"Drawing Guide",canvas.guides.enabled){session.guide=it;canvas.guides.enabled=it;canvas.guides.save();canvas.invalidate()}
                body.addView(row("Drawing guide settings",canvas.guides.mode){canvas.guides.settings(activity,{canvas.invalidate()},{dismissPanel();session.tool="Guide";refreshTools()})})
                body.addView(row("Canvas Atlas"){dismissPanel();activity.showAtlas()})
                body.addView(row("Fit canvas"){canvas.animateFit();dismissPanel()})
                body.addView(row("Reset rotation"){canvas.resetRotation();dismissPanel();showNotice("Rotation reset to 0°")})
                body.addView(row("Flip canvas horizontally"){session.command("flipCanvas",JSONObject().put("horizontal",true));canvas.deselect();activity.scheduleSave()})
                body.addView(row("Flip canvas vertically"){session.command("flipCanvas",JSONObject().put("horizontal",false));canvas.deselect();activity.scheduleSave()})
            }
            "Share"->{body.addView(text("Save a copy to your device",14f,0xffbcc5cc.toInt()),LinearLayout.LayoutParams(-1,dp(40)));for(format in listOf("Inkstone","PNG","JPEG","PSD"))body.addView(row(format,if(format=="Inkstone" || format=="PSD")"Editable layers" else "Flattened image"){dismissPanel();activity.export(format)})}
            "Video"->{body.addView(text("Time-lapse recording is not available in this preview.",15f,0xffbfc8cb.toInt()),LinearLayout.LayoutParams(-1,dp(90)))}
            "Help"->{body.addView(row("About Inkstone",activity.packageManager.getPackageInfo(activity.packageName,0).versionName?:"Preview"){activity.message("Independent Android painting app. See README and the parity tracker for supported features.")})}
            "Prefs"->{
                val rendering=preferenceGroup(body)
                rendering.addView(row("Drawing renderer",if(session.rendererMode=="auto")"GPU · Auto" else "CPU"){rendererSettings()})
                val basic=preferenceGroup(body)
                toggle(basic,"Pen-only input",session.penOnly){session.penOnly=it;activity.savePreferences();showNotice(if(it)"Only pen draws; touch gestures and controls stay enabled" else "Touch input restored")}
                toggle(basic,"Protect controls from palm",session.protectControls){session.protectControls=it;activity.savePreferences()}
                toggle(basic,"Pen pressure",session.pressureEnabled){session.pressureEnabled=it;activity.savePreferences()}
                toggle(basic,"Hold to perfect shape",session.quickShape){session.quickShape=it;activity.savePreferences()}
                toggle(basic,"Light interface",session.lightInterface){session.lightInterface=it;activity.savePreferences();setBackgroundColor(if(it)0xffb5b5b5.toInt() else StudioColors.workspace);removeView(toolbar);buildToolbar();refreshTools();canvas.invalidate()}
                toggle(basic,"Right-hand interface",session.rightSidebar){session.rightSidebar=it;layoutDock();activity.savePreferences()}
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
                input.addView(row("Shortcut keys"){shortcutSettings()})
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
    private fun rendererSettings(){
        val details=session.info
        val active=if(details.optString("renderer")=="gpu")"GPU · ${details.optString("rendererDevice")}" else "CPU"
        val reason=details.optString("rendererError").takeUnless{it.isBlank()||it=="null"}
        AlertDialog.Builder(activity).setTitle("Drawing renderer")
            .setMessage("Active: $active"+(reason?.let{"\nGPU fallback: $it"}?:"")+"\n\nAuto uses Vulkan for larger brushes, smudge and layer compositing when supported. Small work stays on CPU to avoid GPU overhead. CPU keeps the software renderer. Changes apply after the current stroke.")
            .setPositiveButton("GPU · Auto"){_,_->session.setRenderer("auto"){activity.savePreferences();showNotice(if(session.info.optString("renderer")=="gpu")"GPU enabled" else "CPU fallback active")}}
            .setNeutralButton("CPU"){_,_->session.setRenderer("cpu"){activity.savePreferences();showNotice("CPU renderer enabled")}}
            .setNegativeButton("Close",null).show()
    }
    private fun cursorSettings(){
        rebuildPanel{
            panel("Cursor","Advanced cursor settings",430,left=true){body->
                body.addView(row("← Back to Preferences"){actionTab("Prefs")})
                toggle(body,"Brush cursor",session.cursor){session.cursor=it;activity.savePreferences();canvas.invalidate()}
                for(style in listOf("Contrast","Light","Dark"))body.addView(row(style,if(session.cursorStyle==style)"✓" else ""){session.cursorStyle=style;activity.savePreferences();canvas.invalidate();rebuildPanel(::cursorSettings)})
                preferenceSlider(body,"Cursor opacity",session.cursorOpacity,{"${(it*100).roundToInt()}%"}){session.cursorOpacity=it;activity.savePreferences();canvas.invalidate()}
                body.addView(row("Stylus diagnostics"){AlertDialog.Builder(activity).setTitle("Stylus input").setMessage(activity.lastInputDescription).setPositiveButton("Done",null).show()})
            }
        }
    }
    private fun gestureSettings(){
        rebuildPanel{
            panel("Gestures","Gesture controls",530,left=true){body->
                body.addView(row("← Back to Preferences"){actionTab("Prefs")})
                toggle(body,"Pen-only input",session.penOnly){session.penOnly=it;activity.savePreferences()}
                toggle(body,"Protect controls from palm",session.protectControls){session.protectControls=it;activity.savePreferences()}
                toggle(body,"Pinch to fit canvas",session.pinchToFit){session.pinchToFit=it;activity.savePreferences()}
                toggle(body,"Two-finger tap Undo",session.twoFingerUndo){session.twoFingerUndo=it;activity.savePreferences()}
                toggle(body,"Three-finger tap Redo",session.threeFingerRedo){session.threeFingerRedo=it;activity.savePreferences()}
                toggle(body,"Four-finger full screen",session.fourFingerFullScreen){session.fourFingerFullScreen=it;activity.savePreferences()}
                toggle(body,"Hold for Eyedropper",session.holdEyedropper){session.holdEyedropper=it;activity.savePreferences()}
            body.addView(row("Stylus button",session.stylusButtonAction.label){
                AlertDialog.Builder(activity).setTitle("Stylus button function").setSingleChoiceItems(StylusAction.entries.map{it.label}.toTypedArray(),session.stylusButtonAction.ordinal){dialog,index->
                    session.stylusButtonAction=StylusAction.entries[index];canvas.stylusButtons.reset();activity.savePreferences();dialog.dismiss();rebuildPanel(::gestureSettings)
                }.setNegativeButton("Cancel",null).show()
            })
            }
        }
    }
    private fun shortcutSettings(){
        rebuildPanel{
            panel("Shortcuts","Shortcut keys",460,left=true){body->
                body.addView(row("← Back to Preferences"){actionTab("Prefs")})
                body.addView(text("Configure keyboard shortcuts. Tap 'Set' then press your desired key combination.",12f,0xffaab8bf.toInt()).apply{setPadding(dp(8),0,dp(8),dp(12))})
                val group=preferenceGroup(body)
                val updaters=mutableListOf<()->Unit>()
                for(action in listOf("undo" to "Undo","redo" to "Redo","toggle_eraser" to "Eraser / Previous")){
                    val actionKey=action.first
                    val actionName=action.second

                    val row=LinearLayout(activity).apply{
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        minimumHeight=dp(52)
                        setPadding(dp(12),dp(4),dp(12),dp(4))
                    }
                    row.addView(text(actionName,15f),LinearLayout.LayoutParams(0,-2,1f))

                    val keyBadge=text("",13f).apply{
                        setPadding(dp(6),dp(4),dp(10),dp(4))
                    }
                    row.addView(keyBadge)

                    val setBtn=text("",13f,Color.WHITE).apply{
                        gravity=Gravity.CENTER
                        setPadding(dp(14),dp(6),dp(14),dp(6))
                        isClickable=true
                        isFocusable=true
                    }
                    row.addView(setBtn,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(8)})

                    val resetBtn=text("Reset",13f,0xffcccccc.toInt()).apply{
                        gravity=Gravity.CENTER
                        background=rounded(0x33ffffff,StudioShape.CORNER_DP)
                        setPadding(dp(12),dp(6),dp(12),dp(6))
                        isClickable=true
                        isFocusable=true
                    }
                    row.addView(resetBtn,LinearLayout.LayoutParams(-2,-2))

                    fun updateRow(){
                        val current=activity.getShortcut(actionKey)
                        val isSetting=activity.recordingShortcutAction==actionKey
                        keyBadge.text=if(isSetting)"Press a new key" else current.display()
                        keyBadge.setTextColor(if(isSetting)StudioColors.accentSoft else 0xffaab8bf.toInt())
                        setBtn.text=if(isSetting)"Cancel" else "Set"
                        setBtn.background=rounded(if(isSetting)0xff702020.toInt() else StudioColors.accent,StudioShape.CORNER_DP)
                    }

                    setBtn.setOnClickListener{
                        if(activity.recordingShortcutAction==actionKey){
                            activity.cancelRecordingShortcut()
                        }else{
                            activity.startRecordingShortcut(actionKey){_,_->
                                post{updaters.forEach{it()}}
                            }
                        }
                        updaters.forEach{it()}
                    }

                    resetBtn.setOnClickListener{
                        activity.resetShortcut(actionKey)
                        if(activity.recordingShortcutAction==actionKey)activity.cancelRecordingShortcut()
                        updaters.forEach{it()}
                    }

                    updateRow()
                    updaters.add(::updateRow)
                    group.addView(row)
                }
            }
        }
    }
    private fun adjustments(){
        if(panelKey=="Adjustments"){dismissPanel();return}
        if(panel!=null && panelIsLeft && !replacingPanel){rebuildPanel(::adjustments);return}
        panel("Adjustments","Adjustments",440,left=true){body->
            body.addView(row("Liquify"){dismissPanel();session.tool="Liquify";canvas.liquify.begin();refreshTools();activity.deferAutosave();showNotice("Liquify · drag to deform, Done to apply")})
            for(kind in listOf("Brightness","Saturation","Blur","Grayscale","Invert"))body.addView(row(kind){
                if(kind in listOf("Grayscale","Invert")){session.command("adjust",JSONObject().put("kind",kind));dismissPanel();activity.scheduleSave()}
                else adjustmentSlider(kind)
            })
        }
    }
    private fun liquifySlider(size:Boolean){
        val l=canvas.liquify;val seek=SeekBar(activity).apply{max=if(size)592 else 99;progress=if(size)(l.size-8).toInt()else(l.strength*100).toInt()-1;contentDescription=if(size)"Liquify size"else"Liquify strength"}
        AlertDialog.Builder(activity).setTitle(if(size)"Liquify size"else"Liquify strength").setView(seek).setPositiveButton("Apply"){_,_->if(size)l.size=seek.progress+8f else l.strength=(seek.progress+1)/100f;updateBottom()}.setNegativeButton("Cancel",null).show()
    }
    private fun adjustmentSlider(kind:String){
        rebuildPanel{
            panel("Adjustment",kind,250,left=true){body->
                body.addView(row("← Back to Adjustments"){rebuildPanel(::adjustments)})
                var amount=if(kind=="Blur").25f else .5f
                slider(body,kind,amount){amount=it}
                body.addView(row("Apply"){session.command("adjust",JSONObject().put("kind",kind).put("amount",if(kind=="Blur")amount else (amount-.5f)*2));dismissPanel();activity.scheduleSave()})
                body.addView(row("Cancel"){rebuildPanel(::adjustments)})
            }
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
