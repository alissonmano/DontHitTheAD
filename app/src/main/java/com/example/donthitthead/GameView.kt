package com.example.donthitthead

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random

data class AdObstacle(
    val id: Int,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    val speed: Float
)

data class PointCoin(
    val id: Int,
    var x: Float,
    var y: Float,
    val radius: Float = 20f
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String
)

val AchievementsList = listOf(
    Achievement("survive_1m", "Survivor", "Survive for 1 minute"),
    Achievement("survive_5m", "Veteran", "Survive for 5 minutes"),
    Achievement("score_1000", "Pro Player", "Get a score of 1,000"),
    Achievement("score_5000", "Elite", "Get a score of 5,000"),
    Achievement("combo_10", "Hot Streak", "Reach a 10x Combo"),
    Achievement("combo_50", "Unstoppable", "Reach a 50x Combo"),
    Achievement("nohit_1m", "Ghost", "Survive 1 min without being hit"),
    Achievement("nohit_3m", "Untouchable", "Survive 3 min without being hit"),
    Achievement("near_miss_100", "Daredevil", "Get 100 near-miss points in a run"),
    Achievement("shopaholic", "Shopaholic", "Buy 5 items in one run")
)

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE) }
    
    var gameState by remember { mutableStateOf("START") } 
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
    var reviveUsed by remember { mutableStateOf(false) }
    
    val unlockedSet = remember { 
        val initialSet = mutableStateListOf<String>()
        val savedSet = prefs.getStringSet("unlocked_achievements", emptySet()) ?: emptySet()
        initialSet.addAll(savedSet)
        initialSet
    }

    fun unlockAchievement(id: String) {
        if (!unlockedSet.contains(id)) {
            unlockedSet.add(id)
            prefs.edit().putStringSet("unlocked_achievements", unlockedSet.toSet()).apply()
        }
    }
    
    when (gameState) {
        "START" -> {
            reviveUsed = false
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Don't Hit The AD", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("BEST: $highScore", fontSize = 20.sp, color = Color.Yellow)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Achievements: ${unlockedSet.size} / ${AchievementsList.size}", color = Color.Gray)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { 
                    score = 0
                    gameState = "PLAYING" 
                }) {
                    Text("Start Game")
                }
            }
        }
        "PLAYING" -> {
            GameLoop(
                initialScore = score,
                unlockedAchievements = unlockedSet.toSet(),
                onUnlock = { unlockAchievement(it) },
                onGameOver = { finalScore ->
                    score = finalScore
                    if (finalScore > highScore) {
                        highScore = finalScore
                        prefs.edit().putInt("high_score", finalScore).apply()
                    }
                    gameState = "GAME_OVER"
                }
            )
        }
        "GAME_OVER" -> {
            AdOverlay(
                score = score,
                highScore = highScore,
                reviveAvailable = !reviveUsed && score >= 100,
                onRevive = {
                    score -= 100
                    reviveUsed = true
                    gameState = "PLAYING"
                },
                onClose = { gameState = "START" }
            )
        }
    }
}

@Composable
fun GameLoop(
    initialScore: Int, 
    unlockedAchievements: Set<String>,
    onUnlock: (String) -> Unit,
    onGameOver: (Int) -> Unit
) {
    var playerX by remember { mutableFloatStateOf(500f) }
    var playerY by remember { mutableFloatStateOf(1500f) }
    var playerRadius by remember { mutableFloatStateOf(40f) }
    var playerColor by remember { mutableStateOf(Color.Blue) }
    
    var health by remember { mutableFloatStateOf(1.0f) }
    val ads = remember { mutableStateListOf<AdObstacle>() }
    val coins = remember { mutableStateListOf<PointCoin>() }
    var score by remember { mutableIntStateOf(initialScore) }
    var frame by remember { mutableLongStateOf(0L) }
    
    var combo by remember { mutableIntStateOf(1) }
    var comboTimer by remember { mutableIntStateOf(0) }
    var maxComboRun by remember { mutableIntStateOf(1) }
    
    var hasShield by remember { mutableStateOf(false) }
    var hasMagnet by remember { mutableStateOf(false) }
    var shakeAmount by remember { mutableFloatStateOf(0f) }

    // Achievement Tracking
    var lastHitFrame by remember { mutableLongStateOf(0L) }
    var nearMissPoints by remember { mutableIntStateOf(0) }
    var itemsBought by remember { mutableIntStateOf(0) }
    var notificationText by remember { mutableStateOf<Achievement?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "hit")
    val hitAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(100), RepeatMode.Reverse), label = "alpha"
    )
    
    val invulnFrames = 120L
    val isInvulnerable = frame - lastHitFrame < invulnFrames && lastHitFrame != 0L

    LaunchedEffect(notificationText) {
        if (notificationText != null) {
            delay(3000)
            notificationText = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            frame++
            if (shakeAmount > 0) shakeAmount -= 1f
            if (comboTimer > 0) comboTimer-- else combo = 1
            
            val framesSinceHit = frame - lastHitFrame
            
            // Check Achievements
            fun tryUnlock(id: String) {
                if (!unlockedAchievements.contains(id)) {
                    onUnlock(id)
                    notificationText = AchievementsList.find { it.id == id }
                }
            }

            if (frame >= 3600) tryUnlock("survive_1m")
            if (frame >= 18000) tryUnlock("survive_5m")
            if (score >= 1000) tryUnlock("score_1000")
            if (score >= 5000) tryUnlock("score_5000")
            if (maxComboRun >= 10) tryUnlock("combo_10")
            if (maxComboRun >= 50) tryUnlock("combo_50")
            if (framesSinceHit >= 3600 && lastHitFrame != 0L) tryUnlock("nohit_1m")
            if (framesSinceHit >= 10800 && lastHitFrame != 0L) tryUnlock("nohit_3m")
            if (nearMissPoints >= 100) tryUnlock("near_miss_100")
            if (itemsBought >= 5) tryUnlock("shopaholic")

            // Move ads
            val adIterator = ads.iterator()
            while (adIterator.hasNext()) {
                val ad = adIterator.next()
                ad.y += ad.speed
                
                val distToCenter = sqrt(Math.pow((playerX - (ad.x + ad.width/2)).toDouble(), 2.0) + 
                                       Math.pow((playerY - (ad.y + ad.height/2)).toDouble(), 2.0)).toFloat()
                if (distToCenter < 100f && !isInvulnerable) {
                    score += 1 
                    nearMissPoints += 1
                }

                val hit = playerX + playerRadius > ad.x && playerX - playerRadius < ad.x + ad.width &&
                         playerY + playerRadius > ad.y && playerY - playerRadius < ad.y + ad.height
                
                if (hit) {
                    if (!isInvulnerable) {
                        shakeAmount = 20f
                        if (hasShield) {
                            hasShield = false
                        } else {
                            health -= 0.25f
                            if (health <= 0f) onGameOver(score)
                        }
                        lastHitFrame = frame
                        adIterator.remove()
                    }
                } else if (ad.y > 2500) {
                    adIterator.remove()
                }
            }

            // Coins & Magnet
            val coinIterator = coins.iterator()
            while (coinIterator.hasNext()) {
                val coin = coinIterator.next()
                if (hasMagnet) {
                    val dx = playerX - coin.x
                    val dy = playerY - coin.y
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist < 500f) {
                        coin.x += (dx / dist) * 20f
                        coin.y += (dy / dist) * 20f
                    }
                }
                val dist = sqrt(Math.pow((playerX - coin.x).toDouble(), 2.0) + 
                                Math.pow((playerY - coin.y).toDouble(), 2.0))
                if (dist < (playerRadius + coin.radius)) {
                    score += (10 * combo)
                    combo++
                    if (combo > maxComboRun) maxComboRun = combo
                    comboTimer = 120 
                    coinIterator.remove()
                }
            }
            
            // Spawning
            if (frame % (maxOf(20, 60 - score/500)).toLong() == 0L) {
                ads.add(AdObstacle(Random.nextInt(), Random.nextFloat() * 1000f, -200f, 
                    200f + Random.nextFloat() * 200f, 100f + Random.nextFloat() * 100f, 10f + (score / 300f)))
            }
            if (frame % 80 == 0L) {
                coins.add(PointCoin(Random.nextInt(), 100f + Random.nextFloat() * 800f, 100f + Random.nextFloat() * 1800f))
                if (coins.size > 10) coins.removeAt(0)
            }
            
            delay(16)
        }
    }

    val shakeOffset = Offset(
        if (shakeAmount > 0) Random.nextFloat() * shakeAmount - shakeAmount/2 else 0f,
        if (shakeAmount > 0) Random.nextFloat() * shakeAmount - shakeAmount/2 else 0f
    )

    Box(
        modifier = Modifier.fillMaxSize().background(if (score > 1000) Color(0xFF1A1A1A) else Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    playerX += dragAmount.x
                    playerY += dragAmount.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawX = shakeOffset.x
            val drawY = shakeOffset.y
            coins.forEach { coin ->
                drawCircle(color = Color(0xFFFFD700), radius = coin.radius, center = Offset(coin.x + drawX, coin.y + drawY))
            }
            if (hasShield) {
                drawCircle(color = Color.Cyan.copy(alpha = 0.3f), radius = playerRadius + 15f, center = Offset(playerX + drawX, playerY + drawY))
            }
            drawCircle(
                color = if (isInvulnerable) playerColor.copy(alpha = hitAlpha) else playerColor,
                radius = playerRadius,
                center = Offset(playerX + drawX, playerY + drawY)
            )
            ads.forEach { ad ->
                drawRect(color = Color.Red, topLeft = Offset(ad.x + drawX, ad.y + drawY), size = Size(ad.width, ad.height))
            }
        }
        
        // Notification
        AnimatedVisibility(
            visible = notificationText != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp)
        ) {
            notificationText?.let {
                Box(modifier = Modifier.background(Color.Yellow, RoundedCornerShape(8.dp)).padding(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Achievement Unlocked!", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(it.title, color = Color.Black)
                        Text(it.description, fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            }
        }

        // HUD
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Score: $score", fontSize = 28.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                if (combo > 1) {
                    Text(text = "COMBO x$combo", fontSize = 20.sp, color = Color.Magenta, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.width((comboTimer * 1.5).dp).height(4.dp).background(Color.Magenta))
                }
                Spacer(modifier = Modifier.height(8.dp))
                HealthBar(health)
            }

            // Shop
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopItem("Heal", 50, health < 1.0f && score >= 50) { score -= 50; health = (health + 0.25f).coerceAtMost(1.0f); itemsBought++ }
                ShopItem("Shield", 75, !hasShield && score >= 75) { score -= 75; hasShield = true; itemsBought++ }
                ShopItem("Shrink", 150, playerRadius > 25f && score >= 150) { score -= 150; playerRadius = 25f; itemsBought++ }
                ShopItem("Magnet", 200, !hasMagnet && score >= 200) { score -= 200; hasMagnet = true; itemsBought++ }
                ShopItem("Skin", 20, score >= 20) { score -= 20; playerColor = Color(Random.nextInt()); itemsBought++ }
            }
        }
    }
}

@Composable
fun HealthBar(health: Float) {
    Box(modifier = Modifier.width(200.dp).height(20.dp).background(Color.LightGray)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction = health.coerceIn(0f, 1f))
            .background(if (health > 0.5f) Color.Green else if (health > 0.25f) Color.Yellow else Color.Red))
    }
}

@Composable
fun ShopItem(name: String, cost: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(65.dp).clip(CircleShape).background(if (enabled) Color.Black else Color.LightGray)
        .clickable(enabled = enabled) { onClick() }.padding(4.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 10.sp, color = Color.White)
            Text("$cost", fontSize = 10.sp, color = Color.Yellow)
        }
    }
}

@Composable
fun AdOverlay(score: Int, highScore: Int, reviveAvailable: Boolean, onRevive: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(32.dp).background(Color.White, MaterialTheme.shapes.medium).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GAME OVER", fontSize = 32.sp, color = Color.Red, fontWeight = FontWeight.Bold)
            if (score >= highScore && score > 0) Text("NEW BEST!", color = Color.Magenta, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Score: $score", fontSize = 20.sp)
            Text("Best: $highScore", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.LightGray), contentAlignment = Alignment.Center) {
                Text("TIRED OF DYING?\nBUY AD-BLOCKER PRO!", color = Color.DarkGray, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (reviveAvailable) {
                Button(onClick = onRevive, modifier = Modifier.fillMaxWidth()) { Text("Revive (100 pts)") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
        }
    }
}
