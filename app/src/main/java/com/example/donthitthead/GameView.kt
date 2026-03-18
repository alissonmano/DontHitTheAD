package com.example.donthitthead

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

data class AdObstacle(
    val id: Int,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    val speed: Float
)

@Composable
fun GameScreen() {
    var gameState by remember { mutableStateOf("START") } // START, PLAYING, GAME_OVER
    var score by remember { mutableIntStateOf(0) }
    
    when (gameState) {
        "START" -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Don't Hit The AD", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { gameState = "PLAYING" }) {
                    Text("Start Game")
                }
            }
        }
        "PLAYING" -> {
            GameLoop(
                onGameOver = { finalScore ->
                    score = finalScore
                    gameState = "GAME_OVER"
                }
            )
        }
        "GAME_OVER" -> {
            AdOverlay(
                score = score,
                onClose = { gameState = "START" }
            )
        }
    }
}

@Composable
fun GameLoop(onGameOver: (Int) -> Unit) {
    var playerX by remember { mutableFloatStateOf(500f) }
    var playerY by remember { mutableFloatStateOf(1500f) }
    val playerRadius = 40f
    
    var health by remember { mutableFloatStateOf(1.0f) }
    val ads = remember { mutableStateListOf<AdObstacle>() }
    var score by remember { mutableIntStateOf(0) }
    var frame by remember { mutableLongStateOf(0L) }
    
    // Animation for hit feedback
    val infiniteTransition = rememberInfiniteTransition(label = "hit")
    val hitAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    var lastHitFrame by remember { mutableLongStateOf(-200L) }
    val isInvulnerable = frame - lastHitFrame < 120 // ~2.0s invulnerability (60fps * 2)

    LaunchedEffect(Unit) {
        while (true) {
            frame++
            // Move ads
            val iterator = ads.iterator()
            while (iterator.hasNext()) {
                val ad = iterator.next()
                ad.y += ad.speed
                
                // Collision check
                val hit = playerX + playerRadius > ad.x && playerX - playerRadius < ad.x + ad.width &&
                         playerY + playerRadius > ad.y && playerY - playerRadius < ad.y + ad.height
                
                if (hit) {
                    if (!isInvulnerable) {
                        health -= 0.25f // 4 hits to die
                        lastHitFrame = frame
                        iterator.remove()
                        if (health <= 0f) {
                            onGameOver(score)
                        }
                    }
                } else if (ad.y > 2500) {
                    iterator.remove()
                    score++
                }
            }
            
            // Spawn new ads
            if (frame % 60 == 0L) {
                ads.add(
                    AdObstacle(
                        id = Random.nextInt(),
                        x = Random.nextFloat() * 1000f,
                        y = -200f,
                        width = 200f + Random.nextFloat() * 200f,
                        height = 100f + Random.nextFloat() * 100f,
                        speed = 10f + (score / 5f)
                    )
                )
            }
            
            delay(16) // ~60fps
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    playerX += dragAmount.x
                    playerY += dragAmount.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw Player
            drawCircle(
                color = if (isInvulnerable) Color.Blue.copy(alpha = hitAlpha) else Color.Blue,
                radius = playerRadius,
                center = Offset(playerX, playerY)
            )
            
            // Draw Ads
            ads.forEach { ad ->
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(ad.x, ad.y),
                    size = Size(ad.width, ad.height)
                )
            }
        }
        
        // HUD
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Score: $score",
                fontSize = 20.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Health Bar
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(20.dp)
                    .background(Color.LightGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = health.coerceIn(0f, 1f))
                        .background(if (health > 0.5f) Color.Green else if (health > 0.25f) Color.Yellow else Color.Red)
                )
            }
        }
    }
}

@Composable
fun AdOverlay(score: Int, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(Color.White, shape = MaterialTheme.shapes.medium)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GAME OVER", fontSize = 24.sp, color = Color.Red)
            Text("Score: $score", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            // The "AD"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text("HOT ADS IN YOUR AREA!\nBuy Nothing Now!", color = Color.DarkGray)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onClose) {
                Text("Close Ad & Restart")
            }
        }
    }
}
