package com.laila.terastv.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.laila.terastv.R
import com.laila.terastv.ui.theme.TerasTVTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    durationMillis: Int = 2200,
    enableTimer: Boolean = true,          // <- supaya preview tidak delay
    onFinished: () -> Unit = {}
) {
    if (enableTimer) {
        LaunchedEffect(Unit) {
            delay(durationMillis.toLong())
            onFinished()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background full screen
        Image(
            painter = painterResource(id = R.drawable.bg_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Logo di tengah
        Image(
            painter = painterResource(id = R.drawable.logo_splash2),
            contentDescription = "Logo TerasTV",
            modifier = Modifier.width(280.dp)
        )
    }
}

/* ================= Preview ================= */
@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
private fun SplashPreview() {
    TerasTVTheme {
        SplashScreen(enableTimer = false)
    }
}

