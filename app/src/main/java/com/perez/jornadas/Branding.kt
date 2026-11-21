package com.perez.jornadas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = "Jornadas", modifier = modifier.size(size))
}

/** Encabezado con degradado y esquinas redondeadas. */
@Composable
fun AppHeader(subtitle: String) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    Box(Modifier.fillMaxWidth().background(BrandBrush, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Logo(46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Jornadas", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                AnimatedContent(targetState = subtitle, label = "subtitle") {
                    Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("v$version", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Pantalla de entrada animada. */
@Composable
fun Splash() {
    val scale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        launch { logoAlpha.animateTo(1f, tween(500)) }
        delay(350)
        textAlpha.animateTo(1f, tween(500))
    }
    Box(
        Modifier.fillMaxSize().background(BrandBrush).pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(120.dp, Modifier.graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; alpha = logoAlpha.value
            })
            Spacer(Modifier.height(20.dp))
            Column(Modifier.graphicsLayer { alpha = textAlpha.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Jornadas", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text("Tus días de trabajo, en orden", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
