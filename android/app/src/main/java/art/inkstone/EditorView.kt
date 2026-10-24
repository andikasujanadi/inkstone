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
    private var narrow = resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE || resources.displayMetrics.widthPixels / density < 700
    private val barHeight get() = if (narrow) 88 else 44
    private val blue = StudioColors.accent
    private val icons = mutableMapOf<String, ToolIcon>()
    private var panel: View? = null
    private var panelKey = ""
    private var departingPanel: View? = null
    private var brushGroup = "Inking"
    private var colorHeader:View?=null
    private var colorTab = "Disc"
    private var currentActionTab="Add"
    private val brushHud=BrushHud(activity)
    private var hudSlider:VerticalSlider?=null
    private val hideHud=Runnable{StudioMotion.chrome(brushHud,false)}
    private lateinit var toolbar: FrameLayout
    private lateinit var sidebar: LinearLayout
    private lateinit var bottom: LinearLayout
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
        bottom=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(dp(8),dp(4),dp(8),dp(4));background=rounded(StudioColors.surface,StudioShape.CORNER_DP) }
        addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(this@EditorView.bottom)},LayoutParams(-2,dp(52),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(16);leftMargin=dp(52);rightMargin=dp(12)})
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
        session.onToolChange={canvas.finishQuickShape();canvas.syncTransformTool()}
        session.beforeSnapshot={canvas.finishQuickShape();canvas.rasterTransform.finish()}
        session.beforeCommand={op->if(op !in listOf("info","pick")){canvas.finishQuickShape();if(op!="transformMatrix")canvas.rasterTransform.finish()}}
        session.onFrame={canvas.onRenderedFrame()}
        session.onInfo={refreshTools()}
        session.onError={showNotice(it);activity.message(it)}
        refreshTools()
        post{if(ReferencePanel.wasOpen(activity))showReference()}
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
                when(symbol){"Actions"->actions();"Adjustments"->adjustments();"Select","Transform"->{dismissPanel();session.tool=if(session.tool==symbol)"Paint" else symbol;refreshTools();canvas.invalidate()}}
            };icons[symbol]=control;left.addView(control)
        }
        for(symbol in listOf("Paint","Smudge","Erase","Layers","Color")){
            val control=icon(symbol){
                when(symbol){
                    "Layers"->{if(panelKey=="Layers")dismissPanel() else session.thumbnails{layers()}};"Color"->colors()
                    else->{
                        val switching=session.tool!=symbol
                        if(switching){session.tool=symbol;refreshTools();canvas.invalidate()}
                        if(switching && panelKey=="Brushes")rebuildPanel(::brushes)else brushes()
                    }
                }
            };icons[symbol]=control;right.addView(control,LinearLayout.LayoutParams(dp(48),dp(44)))
        }
        colorDrop?.let{drop->icons["Color"]?.let{drop.bind(it){colors()}}}
        toolbar.addView(left,LayoutParams(-2,dp(44),Gravity.TOP or Gravity.LEFT))
        toolbar.addView(right,LayoutParams(-2,dp(44),Gravity.TOP or Gravity.RIGHT).apply{topMargin=if(narrow)dp(44) else 0})
        historyControls=FrameLayout(activity).apply{setBackgroundColor(StudioColors.toolbar);contentDescription="History and save"}
        val undoRedo=LinearLayout(activity)
        for(symbol in listOf("Undo","Redo"))undoRedo.addView(icon(symbol){session.command(symbol.lowercase());activity.scheduleSave();showNotice(symbol)},LinearLayout.LayoutParams(dp(44),dp(44)))
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
        ((bottom.parent as View).layoutParams as LayoutParams).bottomMargin=dp(if(narrow)60 else 16)
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
        sizeSlider=VerticalSlider(activity,"Brush size",sqrt(session.size/600f)){session.size=(it*it*600f).coerceAtLeast(.5f);updateHud(sizeSlider,false)}
        opacitySlider=VerticalSlider(activity,"Brush opacity",session.opacity){session.opacity=it;updateHud(opacitySlider,true)}
        sidebar.addView(sizeSlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sidebar.addView(icon("Eyedropper"){dismissPanel();session.tool=if(session.tool=="Eyedropper")"Paint" else "Eyedropper";refreshTools();showNotice("Touch the canvas to pick a color")},LinearLayout.LayoutParams(dp(32),dp(44)))
        sidebar.addView(opacitySlider,LinearLayout.LayoutParams(dp(32),0,1f))
        sizeSlider.tracking={show->trackHud(sizeSlider,false,show)}
        opacitySlider.tracking={show->trackHud(opacitySlider,true,show)}
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
        if(::sizeSlider.isInitialized)sizeSlider.value=sqrt(session.size/600f)
        if(::opacitySlider.isInitialized)opacitySlider.value=session.opacity
        if(::bottom.isInitialized)updateBottom()
        canvas.invalidate()
    }
    private fun updateBottom(){
        bottom.removeAllViews()
        if(interfaceHidden || session.tool !in listOf("Select","Transform","Fill") && !session.info.optBoolean("maskEditing") && !canvas.quickShapeEdit.released){bottom.visibility=GONE;return};bottom.visibility=VISIBLE
        fun button(label:String,selected:Boolean=false,action:()->Unit){val v=text(label,13f,if(selected)blue else Color.WHITE).apply{isClickable=true;isFocusable=true;setPadding(dp(10),0,dp(10),0);background=selectable();setOnClickListener{action()}};bottom.addView(v,LinearLayout.LayoutParams(-2,-1))}
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
            "Select"->{for(shape in listOf("Freehand","Rectangle","Ellipse"))button(shape,canvas.selectionShape==shape){canvas.selectionShape=shape;updateBottom()}
                button("Invert"){session.command("invertSelection");showNotice("Selection inverted")}
                button("Clear"){session.command("deselect");canvas.deselect()}}
            "Transform"->{button("Smaller"){canvas.rasterTransform.scale(.9f)};button("Larger"){canvas.rasterTransform.scale(1.1f)};button("Flip H"){transform("sx",-1f)};button("Flip V"){transform("sy",-1f)};button("Rotate 45°"){transform("angle",Math.PI.toFloat()/4)};button("Cancel"){canvas.rasterTransform.finish(cancel=true);session.tool="Paint";refreshTools()};button("Done"){session.tool="Paint";refreshTools()}}
            "Fill"->{button("ColorDrop"){showNotice("Tap an enclosed area to fill it")};button("Fill layer"){session.command("fill",JSONObject().put("color",colorLong()));activity.scheduleSave()};button("Done"){session.tool="Paint";refreshTools()}}
        }
    }
    private fun transform(key:String,value:Float){canvas.rasterTransform.operation(key,value)}
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
    private fun panel(key:String,title:String,height:Int=480,left:Boolean=false,extra:(LinearLayout)->Unit={},footer:(LinearLayout)->Unit={},content:(LinearLayout)->Unit){
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
        footer(outer)
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
    private fun eraserSettings(){
        panel("Eraser","Eraser",330){body->
            body.addView(text("Soft edge ← Hard edge",16f),LinearLayout.LayoutParams(-1,dp(48)))
            slider(body,"Hardness",session.eraserHardness){session.eraserHardness=it;activity.savePreferences()}
            body.addView(row("Soft"){session.eraserHardness=0f;activity.savePreferences();rebuildPanel(::eraserSettings)})
            body.addView(row("Medium"){session.eraserHardness=.5f;activity.savePreferences();rebuildPanel(::eraserSettings)})
            body.addView(row("Hard"){session.eraserHardness=1f;activity.savePreferences();rebuildPanel(::eraserSettings)})
        }
    }
    private fun brushes(){
        canvas.finishQuickShape()
        panel("Brushes",when(session.tool){"Erase"->"Eraser Library";"Smudge"->"Smudge Library";else->"Brush Library"},820,extra={heading->heading.addView(icon("Add"){brushStudio()})}){body->
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
                val preview=BrushPreview(activity,preset,session.preset==preset,session.tool=="Erase").apply{isClickable=true;isFocusable=true;setOnClickListener{StudioMotion.bounce(this);session.choose(preset);refreshTools();previews.forEach{it.chosen=it.preset==preset;it.invalidate()}};setOnLongClickListener{session.choose(preset);brushStudio();true}}
                previews.add(preview);list.addView(preview,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(3)})
            }
            if(presets.isEmpty())list.addView(text("No imported brushes",13f,0xffb4bec2.toInt()),LinearLayout.LayoutParams(-1,dp(64)))
            columns.addView(ScrollView(activity).apply{isVerticalScrollBarEnabled=false;addView(list)},LinearLayout.LayoutParams(0,-1,1f))
            body.addView(columns,LinearLayout.LayoutParams(-1,-1))
        }
    }
    private fun brushStudio(){
        dismissPanel();panel("Studio",if(session.tool=="Erase")"Eraser Studio" else "Brush Studio",540){body->
            body.addView(text(session.preset.name,17f),LinearLayout.LayoutParams(-1,dp(42)))
            body.addView(BrushPreview(activity,session.preset,eraser=session.tool=="Erase"),LinearLayout.LayoutParams(-1,dp(90)))
            if(session.tool=="Erase")body.addView(row("Edge hardness"){eraserSettings()})
            slider(body,"Flow",session.flow){session.flow=it.coerceAtLeast(.01f)}
            slider(body,"Stabilization",session.stabilization/.95f){session.stabilization=it*.95f;activity.savePreferences()}
            slider(body,"Pressure → Size",session.pressureSize){session.pressureSize=it}
            slider(body,"Pressure → Opacity",session.pressureOpacity){session.pressureOpacity=it}
            body.addView(row("Reset brush"){session.choose(session.preset);rebuildPanel(::brushStudio)})
            body.addView(row("Done"){dismissPanel()})
        }
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
        panel("Layers","Layers",710,extra={h->h.addView(icon("Add"){session.command("add"){session.thumbnails{rebuildPanel(::layers)};activity.scheduleSave()}})}){body->
            val layers=session.info.getJSONArray("layers");val active=session.info.getInt("active")
            val expanded=mutableListOf<View>()
            for(i in layers.length()-1 downTo 0){
                val layer=layers.getJSONObject(i);val name=layer.getString("name")
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
    private fun maskAction(action:String,enabled:Boolean?=null){
        val values=JSONObject().put("action",action);if(enabled!=null)values.put("enabled",enabled)
        session.command("mask",values){
            if(action in listOf("add","edit","invert")){if(colorBeforeMask==null)colorBeforeMask=session.color;session.tool="Paint";session.color=Color.BLACK;showNotice("Mask: black hides · white reveals")}
            session.thumbnails{dismissPanel();refreshTools()};activity.scheduleSave()
        }
    }
    private fun layerOptions(index:Int){
        session.command("active",JSONObject().put("index",index)){
            val l=session.info.getJSONArray("layers").getJSONObject(index)
            dismissPanel();panel("Layer Options",l.getString("name"),680){body->
                body.addView(row("Move / resize contents"){startLayerTransform()})
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
            "Add"->{body.addView(row("Insert a photo"){dismissPanel();activity.importImage()});body.addView(row("Import Inkstone / PSD document"){dismissPanel();activity.importDocument()});body.addView(row("Rename artwork",activity.documentName){activity.renameCurrent()});body.addView(row("Save artwork"){activity.saveCurrent{if(it)showNotice("Artwork saved")}})}
            "Canvas"->{
                body.addView(row("Canvas information","${session.canvasWidth} × ${session.canvasHeight}"){AlertDialog.Builder(activity).setTitle(activity.documentName).setMessage("${session.canvasWidth} × ${session.canvasHeight} pixels\n${session.info.getJSONArray("layers").length()} layers\nRGBA · 8 bits per channel\n${session.info.getInt("undo")} undo actions available\nStored locally as an Inkstone document").setPositiveButton("Done",null).show()})
                toggle(body,"Lock canvas position",session.canvasLocked){session.canvasLocked=it;activity.savePreferences()}
                body.addView(row("Symmetry",session.symmetryMode){symmetrySettings()})
                body.addView(row("Reference image"){dismissPanel();showReference()})
                toggle(body,"Drawing Guide",session.guide){session.guide=it;canvas.invalidate()}
                body.addView(row("Fit canvas"){canvas.fit();dismissPanel()});body.addView(row("Flip canvas horizontally"){session.command("flipCanvas",JSONObject().put("horizontal",true));canvas.deselect();activity.scheduleSave()});body.addView(row("Flip canvas vertically"){session.command("flipCanvas",JSONObject().put("horizontal",false));canvas.deselect();activity.scheduleSave()})
            }
            "Share"->{body.addView(text("Save a copy to your device",14f,0xffbcc5cc.toInt()),LinearLayout.LayoutParams(-1,dp(40)));for(format in listOf("Inkstone","PNG","JPEG","PSD"))body.addView(row(format,if(format=="Inkstone" || format=="PSD")"Editable layers" else "Flattened image"){dismissPanel();activity.export(format)})}
            "Video"->{body.addView(text("Time-lapse recording is not available in this preview.",15f,0xffbfc8cb.toInt()),LinearLayout.LayoutParams(-1,dp(90)))}
            "Help"->{body.addView(row("About Inkstone","0.1.7-dev"){activity.message("Independent Android painting app. See README and the parity tracker for supported features.")})}
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
        if(!replacingPanel)dismissPanel();panel("Cursor","Advanced cursor settings",410,left=true){body->
            toggle(body,"Brush cursor",session.cursor){session.cursor=it;activity.savePreferences();canvas.invalidate()}
            for(style in listOf("Contrast","Light","Dark"))body.addView(row(style,if(session.cursorStyle==style)"✓" else ""){session.cursorStyle=style;activity.savePreferences();canvas.invalidate();rebuildPanel(::cursorSettings)})
            preferenceSlider(body,"Cursor opacity",session.cursorOpacity,{"${(it*100).roundToInt()}%"}){session.cursorOpacity=it;activity.savePreferences();canvas.invalidate()}
            body.addView(row("Stylus diagnostics"){AlertDialog.Builder(activity).setTitle("Stylus input").setMessage(activity.lastInputDescription).setPositiveButton("Done",null).show()})
        }
    }
    private fun gestureSettings(){
        dismissPanel();panel("Gestures","Gesture controls",470,left=true){body->
            toggle(body,"Pen-only input",session.penOnly){session.penOnly=it;activity.savePreferences()}
            toggle(body,"Protect controls from palm",session.protectControls){session.protectControls=it;activity.savePreferences()}
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
