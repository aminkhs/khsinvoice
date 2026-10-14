package ir.hesab.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hesab.app.R
import ir.hesab.app.data.UserPreferences

/** Native mapping of KHS-Design-System v1.1; source tokens are in docs/design. */
object Khs {
    val Lime=Color(0xFFD4FA56); val Ink=Color(0xFF161715); val Canvas=Color(0xFFF5F5F2)
    val Stone=Color(0xFFE8E9E4); val Border=Color(0xFFD9DCD5); val Muted=Color(0xFF555A51)
    val Mint=Color(0xFFC9EDCF); val Butter=Color(0xFFF8E58D); val Sky=Color(0xFFB9DDF5); val Lilac=Color(0xFFDED3F7)
    val Success=Color(0xFF216B43); val Warning=Color(0xFF885300); val Error=Color(0xFFB42318); val Focus=Color(0xFF2A5AC8)
    val Control=RoundedCornerShape(12.dp); val Field=RoundedCornerShape(20.dp)
    val Card=RoundedCornerShape(28.dp); val Feature=RoundedCornerShape(40.dp); val Pill=RoundedCornerShape(999.dp)
    val ChartColors=listOf(Success,Focus,Warning,Color(0xFF7757AD),Color(0xFF347485),Color(0xFF9E4D68),Color(0xFF65723C),Ink)
}
data class KhsSemantics(val success: Color,val warning: Color,val focus: Color)
val LocalKhsSemantics=staticCompositionLocalOf { KhsSemantics(Khs.Success,Khs.Warning,Khs.Focus) }
private val light=lightColorScheme(
    primary=Khs.Lime,onPrimary=Khs.Ink,primaryContainer=Khs.Lime,onPrimaryContainer=Khs.Ink,
    secondary=Khs.Success,onSecondary=Color.White,secondaryContainer=Khs.Mint,onSecondaryContainer=Color(0xFF163C26),
    tertiary=Khs.Focus,onTertiary=Color.White,tertiaryContainer=Khs.Sky,onTertiaryContainer=Color(0xFF173C57),
    background=Khs.Canvas,onBackground=Khs.Ink,surface=Color.White,onSurface=Khs.Ink,
    surfaceVariant=Khs.Stone,onSurfaceVariant=Khs.Muted,outline=Khs.Muted,outlineVariant=Khs.Border,
    surfaceContainerLowest=Color.White,surfaceContainerLow=Khs.Canvas,surfaceContainer=Khs.Canvas,
    surfaceContainerHigh=Khs.Stone,surfaceContainerHighest=Khs.Stone,surfaceBright=Color.White,surfaceDim=Khs.Stone,
    error=Khs.Error,onError=Color.White,errorContainer=Color(0xFFFCE3DF),onErrorContainer=Color(0xFF60130D),
    inverseSurface=Khs.Ink,inverseOnSurface=Color.White,inversePrimary=Khs.Lime,surfaceTint=Color.Transparent
)
private val dark=darkColorScheme(
    primary=Khs.Lime,onPrimary=Khs.Ink,primaryContainer=Khs.Lime,onPrimaryContainer=Khs.Ink,
    secondary=Khs.Mint,onSecondary=Color(0xFF163C26),secondaryContainer=Color(0xFF263E2C),onSecondaryContainer=Khs.Mint,
    tertiary=Khs.Sky,onTertiary=Color(0xFF173C57),tertiaryContainer=Color(0xFF233C4C),onTertiaryContainer=Khs.Sky,
    background=Color(0xFF10110F),onBackground=Color(0xFFF7F8F5),surface=Color(0xFF1C1E1A),onSurface=Color(0xFFF7F8F5),
    surfaceVariant=Color(0xFF2A2D27),onSurfaceVariant=Color(0xFFBAC0B5),outline=Color(0xFFBAC0B5),outlineVariant=Color(0xFF3F453A),
    surfaceContainerLowest=Color(0xFF10110F),surfaceContainerLow=Color(0xFF1C1E1A),surfaceContainer=Color(0xFF1C1E1A),
    surfaceContainerHigh=Color(0xFF2A2D27),surfaceContainerHighest=Color(0xFF33372F),surfaceBright=Color(0xFF33372F),surfaceDim=Color(0xFF10110F),
    error=Color(0xFFFFB4A9),onError=Color(0xFF60130D),errorContainer=Color(0xFF61231C),onErrorContainer=Color(0xFFFFDAD4),
    inverseSurface=Khs.Canvas,inverseOnSurface=Khs.Ink,inversePrimary=Khs.Success,surfaceTint=Color.Transparent
)
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resource: Int) = FontFamily((100..900 step 100).map { weight ->
    Font(resource,weight=FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight)))
})
val Estedad=variableFamily(R.font.estedad)
val Inter=variableFamily(R.font.inter)
private fun text(size: Int,line: Int,weight: Int=400) = TextStyle(fontFamily=Estedad,fontSize=size.sp,lineHeight=line.sp,fontWeight=FontWeight(weight),letterSpacing=0.sp,fontFeatureSettings="tnum")
private val typography=Typography(
    displayLarge=text(52,64,800),displayMedium=text(46,58,800),displaySmall=text(42,52,750),
    headlineLarge=text(32,44,750),headlineMedium=text(28,40,700),headlineSmall=text(24,34,700),
    titleLarge=text(22,32,700),titleMedium=text(16,28,650),titleSmall=text(14,24,600),
    bodyLarge=text(16,28),bodyMedium=text(14,24),bodySmall=text(12,20),
    labelLarge=text(15,22,650),labelMedium=text(12,20,600),labelSmall=text(12,20,500)
)
@Composable fun HesabTheme(prefs: UserPreferences,content: @Composable () -> Unit) {
    val isDark=when(prefs.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scheme=if(prefs.dynamicColor && Build.VERSION.SDK_INT>=31) { if(isDark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) } else if(isDark) dark else light
    val semantics=if(isDark) KhsSemantics(Khs.Mint,Khs.Butter,Khs.Sky) else KhsSemantics(Khs.Success,Khs.Warning,Khs.Focus)
    CompositionLocalProvider(LocalKhsSemantics provides semantics) {
        MaterialTheme(colorScheme=scheme,typography=typography,shapes=Shapes(extraSmall=Khs.Field,small=Khs.Control,medium=Khs.Field,large=Khs.Card,extraLarge=Khs.Feature),content=content)
    }
}
