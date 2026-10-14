package ir.hesab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Text actions and focus use ink/blue; lime is reserved for filled, dark-text actions. */
fun Modifier.khsFocusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val focus=LocalKhsSemantics.current.focus
    onFocusChanged { focused=it.hasFocus }.then(if(focused) Modifier.border(3.dp,focus,shape) else Modifier)
}
@Composable fun KhsButton(onClick: () -> Unit,modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable RowScope.() -> Unit) {
    Button(onClick=onClick,modifier=modifier.heightIn(min=48.dp).khsFocusRing(Khs.Pill),enabled=enabled,shape=Khs.Pill,content=content)
}
@Composable fun KhsTextButton(onClick: () -> Unit,modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable RowScope.() -> Unit) {
    TextButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp).khsFocusRing(Khs.Pill),enabled=enabled,shape=Khs.Pill,
        colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable fun KhsTextField(value: String,onValueChange: (String) -> Unit,modifier: Modifier=Modifier,label: (@Composable () -> Unit)?=null,singleLine: Boolean=false,visualTransformation: VisualTransformation=VisualTransformation.None,textStyle: TextStyle=MaterialTheme.typography.bodyLarge) {
    val focus=LocalKhsSemantics.current.focus
    OutlinedTextField(value,onValueChange,modifier=modifier.fillMaxWidth(),label=label,singleLine=singleLine,
        visualTransformation=visualTransformation,shape=Khs.Field,textStyle=textStyle,
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=focus,focusedLabelColor=focus,cursorColor=focus,
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface))
}
@Composable fun KhsNavigationIcon(route: String,selected: Boolean) {
    val color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val stroke=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun p(x: Float,y: Float)=Offset(size.width*x/24,size.height*y/24)
        fun line(x1: Float,y1: Float,x2: Float,y2: Float)=drawLine(color,p(x1,y1),p(x2,y2),strokeWidth=stroke.width,cap=StrokeCap.Round)
        when(route) {
            "home" -> { val path=Path().apply { moveTo(p(3f,10f).x,p(3f,10f).y); lineTo(p(12f,3f).x,p(12f,3f).y); lineTo(p(21f,10f).x,p(21f,10f).y); lineTo(p(21f,21f).x,p(21f,21f).y); lineTo(p(15f,21f).x,p(15f,21f).y); lineTo(p(15f,14f).x,p(15f,14f).y); lineTo(p(9f,14f).x,p(9f,14f).y); lineTo(p(9f,21f).x,p(9f,21f).y); lineTo(p(3f,21f).x,p(3f,21f).y); close() }; drawPath(path,color,style=stroke) }
            "transactions" -> { line(4f,7f,20f,7f); line(16f,3f,20f,7f); line(16f,11f,20f,7f); line(4f,17f,20f,17f); line(4f,17f,8f,13f); line(4f,17f,8f,21f) }
            "analytics" -> { line(4f,21f,20f,21f); line(6f,17f,6f,11f); line(12f,17f,12f,4f); line(18f,17f,18f,8f) }
            "budgets" -> { drawCircle(color,size.width*.38f,style=stroke); drawCircle(color,size.width*.2f,style=stroke); line(12f,2f,12f,5f); line(12f,19f,12f,22f) }
            else -> { listOf(6f to 8f,12f to 16f,18f to 10f).forEach { (x,y) -> line(x,3f,x,y-3f); line(x,y+3f,x,21f); drawCircle(color,size.width*2.5f/24,p(x,y),style=stroke) } }
        }
    }
}
