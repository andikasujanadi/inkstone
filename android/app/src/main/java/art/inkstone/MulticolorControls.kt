package art.inkstone

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import kotlin.math.*

/** Editor-owned controls; changes use the same persistent brush profile as Brush Studio. */
internal class MulticolorControls(private val activity:MainActivity,private val session:DrawingSession,private val changed:()->Unit):LinearLayout(activity){
    private val brush=session.currentBrush
    private val density=resources.displayMetrics.density
    private val tips=linkedMapOf("Round" to 0,"Pencil" to 1,"Airbrush" to 2,"Dry ink" to 3,"Wash" to 4,"Flat ribbon" to 7,"Bristle" to 11,"Marker" to 12,"Chalk" to 13,"Pastel" to 14,"Medium airbrush" to 8,"Firm airbrush" to 9,"Spray" to 10)
    private val colors=(brush.palette.takeIf{it.size>=2}?:PaletteStore(activity).active.colors.take(6)).toMutableList()
    private val preview=FrameLayout(activity)
    private val swatches=LinearLayout(activity)
    private val editor=LinearLayout(activity).apply{orientation=VERTICAL;visibility=GONE}
    private val tipTiles=mutableListOf<BrushPreview>()
    private var sourceRefresh:()->Unit={}
    private var cycleRefresh:()->Unit={}
    private fun dp(n:Int)=(n*density).roundToInt()
    private fun shape(color:Int,stroke:Boolean=false)=GradientDrawable().apply{setColor(color);cornerRadius=dp(StudioShape.CORNER_DP).toFloat();if(stroke)setStroke(dp(1),0xff41464d.toInt())}
    private fun label(value:String,size:Float=14f)=TextView(activity).apply{text=value;textSize=size;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL}
    private fun button(value:String,description:String=value,action:()->Unit)=label(value,13f).apply{gravity=Gravity.CENTER;setPadding(dp(10),0,dp(10),0);minimumHeight=dp(44);contentDescription=description;isClickable=true;isFocusable=true;background=shape(StudioColors.track);setOnClickListener{StudioMotion.bounce(this);action()}}
    private fun card(title:String):LinearLayout=LinearLayout(activity).apply{orientation=VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(12));background=shape(0x7832383e,true);addView(label(title),LayoutParams(-1,dp(30)))}.also{addView(it,LayoutParams(-1,-2).apply{bottomMargin=dp(12)})}
    private fun horizontal(host:LinearLayout,contents:LinearLayout):HorizontalScrollView=HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(contents)}.also{host.addView(it,LayoutParams(-1,-2))}
    private fun save(){brush.palette=colors.toList();changed();refreshPreview()}
    private fun preset()=session.preset.copy(kind=brush.kind,colorMode=brush.colorMode,colorLayout=brush.colorLayout,palette=colors.toList(),colorCycle=brush.colorCycle)
    private fun refreshPreview(){preview.removeAllViews();preview.addView(BrushPreview(activity,preset().copy(name="")),FrameLayout.LayoutParams(-1,-1));tipTiles.forEach{it.chosen=it.preset.kind==brush.kind;it.invalidate()};cycleRefresh()}
    private fun choices(host:LinearLayout,values:List<Pair<String,String>>,selected:()->String,choose:(String)->Unit):()->Unit{
        val row=LinearLayout(activity);val tiles=values.map{(title,key)->button(title){choose(key);save()}.also{row.addView(it,LayoutParams(-2,dp(44)).apply{rightMargin=dp(6)})} }
        horizontal(host,row)
        val refresh={tiles.forEachIndexed{i,v->v.background=shape(if(selected()==values[i].second)StudioColors.accent else StudioColors.track);v.isSelected=selected()==values[i].second}}
        tiles.forEach{v->val old=v.text.toString();v.setOnClickListener{StudioMotion.bounce(v);choose(values.first{it.first==old}.second);refresh();StudioTheme.apply(host);save()}}
        StudioTheme.apply(host);refresh();return refresh
    }
    init{
        orientation=VERTICAL;setPadding(dp(8),0,dp(8),0)
        val combination=card("Color combination")
        sourceRefresh=choices(combination,listOf("Solid" to "Solid","Spectrum" to "Rainbow","Custom" to "Palette"),{brush.colorMode}){brush.colorMode=it}
        swatches.gravity=Gravity.CENTER_VERTICAL;swatches.setPadding(0,dp(12),0,dp(8));horizontal(combination,swatches);combination.addView(editor)
        val palettes=LinearLayout(activity).apply{orientation=VERTICAL;visibility=GONE}
        combination.addView(button("Saved palettes  ›"){palettes.visibility=if(palettes.visibility==GONE)VISIBLE else GONE});combination.addView(palettes)
        PaletteStore(activity).all.forEach{p->palettes.addView(button(p.name,"Use palette ${p.name}"){if(p.colors.size>=2){colors.clear();colors.addAll(p.colors.take(12));brush.colorMode="Palette";sourceRefresh();refreshSwatches();save();palettes.visibility=GONE}else activity.message("This palette needs at least two colors")})}
        refreshSwatches()
        val tipCard=card("Brush tip");val tipRow=LinearLayout(activity)
        tips.forEach{(name,kind)->val tile=BrushPreview(activity,session.preset.copy(name=name,kind=kind,colorMode="Solid",size=36f,fadeLength=0f),kind==brush.kind).apply{background=shape(StudioColors.track,true);contentDescription="Brush tip $name";isClickable=true;isFocusable=true;setOnClickListener{brush.kind=kind;StudioMotion.bounce(this);requestRectangleOnScreen(Rect(0,0,width,height),false);save()}}
            tipTiles.add(tile);tipRow.addView(tile,LayoutParams(dp(108),dp(82)).apply{rightMargin=dp(8)})}
        val tipScroll=horizontal(tipCard,tipRow);tipScroll.post{tipScroll.scrollTo(dp(116)*tips.values.indexOf(brush.kind).coerceAtLeast(0),0)}
        val cycleCard=card("Color cycle");cycleCard.addView(preview,LayoutParams(-1,dp(76)))
        val details=LinearLayout(activity).apply{orientation=VERTICAL;visibility=GONE}
        cycleCard.addView(button("Adjust color cycle  ⌄"){details.visibility=if(details.visibility==GONE)VISIBLE else GONE});cycleCard.addView(details)
        choices(details,listOf("Along stroke" to "Along","Across width" to "Across","Bristle strands" to "Bristles"),{brush.colorLayout}){brush.colorLayout=it}
        val number=EditText(activity).apply{setText(brush.colorCycle.roundToInt().toString());setTextColor(Color.WHITE);textSize=15f;inputType=android.text.InputType.TYPE_CLASS_NUMBER;isSingleLine=true;contentDescription="Color cycle length";background=shape(StudioColors.track);setPadding(dp(12),0,dp(12),0);setSelectAllOnFocus(true)}
        val lengthRow=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(10),0,0);addView(number,LayoutParams(0,dp(44),1f));addView(button("Set px","Set color cycle length"){val n=number.text.toString().toFloatOrNull();if(n==null)activity.message("Enter a number from 20 to 20000")else{brush.colorCycle=n.coerceIn(20f,20000f);number.setText(brush.colorCycle.roundToInt().toString());save()}},LayoutParams(-2,dp(44)))};details.addView(lengthRow)
        val slider=CycleSlider(activity,brush.colorCycle){brush.colorCycle=it;number.setText(it.roundToInt().toString());save()};details.addView(slider,LayoutParams(-1,dp(44)))
        val caption=label("",12f);cycleCard.addView(caption,LayoutParams(-1,dp(28)))
        cycleRefresh={caption.text="${brush.colorCycle.roundToInt()} px · ${when(brush.colorLayout){"Across"->"Across width";"Bristles"->"Bristle strands";else->"Along stroke"}}";slider.value=brush.colorCycle;slider.invalidate()}
        details.addView(label("Shorter cycles change color more often.",12f),LayoutParams(-1,dp(34)))
        refreshPreview()
    }
    private fun refreshSwatches(){swatches.removeAllViews();colors.forEachIndexed{i,color->val chip=button("","Combination color ${i+1}"){editColor(i)}.apply{tag=StudioTheme.PRESERVE_COLOR;background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(color);setStroke(dp(1),0x55ffffff)};minimumHeight=0;setPadding(0,0,0,0)};swatches.addView(chip,LayoutParams(dp(36),dp(36)).apply{rightMargin=dp(8)})};if(colors.size<12)swatches.addView(button("+","Add brush color"){editColor(colors.size)},LayoutParams(dp(40),dp(40)))}
    private fun editColor(index:Int){editor.removeAllViews();editor.visibility=VISIBLE;var chosen=colors.getOrElse(index){session.color}
        val hex=EditText(activity).apply{setText(String.format("#%06X",chosen and 0xffffff));setTextColor(Color.WHITE);textSize=15f;isSingleLine=true;contentDescription="Brush color hex";background=shape(StudioColors.track);setPadding(dp(12),0,dp(12),0)}
        editor.addView(ColorDisc(activity,chosen,false){chosen=it;hex.setText(String.format("#%06X",it and 0xffffff))},LayoutParams(-1,dp(260)));editor.addView(hex,LayoutParams(-1,dp(44)))
        val actions=LinearLayout(activity)
        fun action(title:String,description:String=title,run:()->Unit){actions.addView(button(title,description,run),LayoutParams(-2,dp(44)).apply{rightMargin=dp(4)})}
        action("Apply","Apply brush color"){val parsed=runCatching{Color.parseColor(hex.text.toString())}.getOrNull();if(parsed==null)activity.message("Enter a valid hex color")else{if(index<colors.size)colors[index]=parsed else colors.add(parsed);brush.colorMode="Palette";sourceRefresh();refreshSwatches();editor.visibility=GONE;save()}}
        action("Cancel"){editor.visibility=GONE}
        horizontal(editor,actions)
        if(index<colors.size){val order=LinearLayout(activity);fun move(title:String,to:Int){order.addView(button(title){if(to in colors.indices){colors.add(to,colors.removeAt(index));refreshSwatches();editor.visibility=GONE;save()}},LayoutParams(0,dp(44),1f))};move("←",index-1);move("→",index+1);order.addView(button("Remove"){if(colors.size>2){colors.removeAt(index);refreshSwatches();editor.visibility=GONE;save()}else activity.message("Keep at least two colors")},LayoutParams(0,dp(44),1f));editor.addView(order)};StudioTheme.apply(editor)
    }
}

internal class CycleSlider(context:android.content.Context,var value:Float,private val changed:(Float)->Unit):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init{contentDescription="Color cycle slider";isFocusable=true;isClickable=true}
    override fun onDraw(c:Canvas){val d=resources.displayMetrics.density;val left=12*d;val right=width-12*d;val x=left+(right-left)*(ln(value.coerceIn(20f,20000f)/20f)/ln(1000f));p.strokeWidth=4*d;p.strokeCap=Paint.Cap.ROUND;p.color=0xff4d535a.toInt();c.drawLine(left,height/2f,right,height/2f,p);p.color=StudioColors.accent;c.drawLine(left,height/2f,x,height/2f,p);p.color=StudioTheme.of(context).text;c.drawCircle(x,height/2f,8*d,p)}
    override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_DOWN||e.actionMasked==MotionEvent.ACTION_MOVE||e.actionMasked==MotionEvent.ACTION_UP){parent.requestDisallowInterceptTouchEvent(true);val d=resources.displayMetrics.density;value=20f*1000f.pow(((e.x-12*d)/(width-24*d).coerceAtLeast(1f)).coerceIn(0f,1f));changed(value);invalidate();if(e.actionMasked==MotionEvent.ACTION_UP)performClick();return true};return e.actionMasked==MotionEvent.ACTION_CANCEL}
    override fun performClick():Boolean{super.performClick();return true}
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean{if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){value=(value*if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)1.1f else 1/1.1f).coerceIn(20f,20000f);changed(value);invalidate();return true};return super.onKeyDown(keyCode,event)}
}
