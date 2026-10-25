package art.inkstone

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

internal enum class StylusAction(val key:String,val label:String){
    ERASE("erase","Hold to erase"),EYEDROPPER("eyedropper","Hold for Eyedropper"),UNDO("undo","Undo"),REDO("redo","Redo"),TOGGLE("toggle","Switch brush / eraser"),NONE("none","None");
    companion object{fun from(key:String?)=entries.firstOrNull{it.key==key}?:ERASE}
}

internal class StylusButtons(private val pressed:()->Unit){
    private var motion=false
    private val keys=mutableSetOf<Int>()
    val down get()=motion || keys.isNotEmpty()
    fun reset(){motion=false;keys.clear()}
    fun observe(e:MotionEvent){
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){reset();return}
        if((0 until e.pointerCount).none{e.getToolType(it) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER)})return
        val before=down
        val mask=MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY
        motion=e.buttonState and mask!=0
        if(e.actionMasked==MotionEvent.ACTION_BUTTON_PRESS && e.actionButton and mask!=0)motion=true
        if(e.actionMasked==MotionEvent.ACTION_BUTTON_RELEASE && e.buttonState and mask==0)motion=false
        if(!before && down)pressed()
    }
    fun key(e:KeyEvent):Boolean{
        val stylusKey=e.keyCode in listOf(KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY,KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY,KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY,KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL) ||
            (e.keyCode==KeyEvent.KEYCODE_BACK && e.isFromSource(InputDevice.SOURCE_STYLUS))
        if(!stylusKey)return false
        val before=down
        when(e.action){KeyEvent.ACTION_DOWN->keys.add(e.keyCode);KeyEvent.ACTION_UP->{keys.remove(e.keyCode);if(keys.isEmpty())motion=false}}
        if(e.isCanceled){reset();return true}
        if(!before && down && e.repeatCount==0)pressed()
        return true
    }
}
