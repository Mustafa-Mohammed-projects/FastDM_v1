package com.fastdm.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.fastdm.app.R

/** Shows res/drawable/presplash.png; MainActivity keeps it on screen for 3 seconds. */
@Composable
fun SplashOverlay() {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val scale by animateFloatAsState(if (go) 1f else 0.9f, tween(700), label = "splashScale")
    val fade by animateFloatAsState(if (go) 1f else 0f, tween(500), label = "splashFade")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F1A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.presplash),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                },
            contentScale = ContentScale.Fit
        )
    }
}
