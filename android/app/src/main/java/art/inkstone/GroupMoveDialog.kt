package art.inkstone

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

internal object GroupMoveDialog {
    fun show(context:Context,move:(Int,Int)->Unit){
        val body=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        fun field(label:String)=EditText(context).apply{hint=label;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED;setText("0");body.addView(this)}
        val x=field("Horizontal offset (px)");val y=field("Vertical offset (px)")
        AlertDialog.Builder(context).setTitle("Move group contents").setView(body).setPositiveButton("Move"){_,_->move(x.text.toString().toIntOrNull()?:0,y.text.toString().toIntOrNull()?:0)}.setNegativeButton("Cancel",null).show()
    }
}
