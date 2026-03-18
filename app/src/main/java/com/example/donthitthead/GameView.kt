package com.example.donthitthead

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.activity.ComponentActivity

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

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Color,
    val size: Float
)

data class FloatingText(
    val text: String,
    var x: Float,
    var y: Float,
    var life: Float,
    val color: Color
)

val AchievementsList = listOf(
    Achievement("survive_1m", "Survivor", "Survive for 1 minute"),
    Achievement("survive_5m", "Veteran", "Survive for 5 minutes"),
    Achievement("score_1000", "Pro Player", "Get a score of 1,000"),
    Achievement("score_5000", "Elite", "Get a score of 5,000"),
    Achievement("score_10000", "Legend", "Reach a score of 10,000"),
    Achievement("combo_10", "Hot Streak", "Reach a 10x Combo"),
    Achievement("combo_50", "Unstoppable", "Reach a 50x Combo"),
    Achievement("combo_100", "Godlike", "Reach a 100x Combo"),
    Achievement("nohit_1m", "Ghost", "Survive 1 min without being hit"),
    Achievement("nohit_3m", "Untouchable", "Survive 3 min without being hit"),
    Achievement("near_miss_100", "Daredevil", "Get 100 near-miss points in a run"),
    Achievement("near_miss_500", "Ad-Whiz", "Get 500 near-miss points in a run"),
    Achievement("shopaholic", "Shopaholic", "Buy 5 items in one run"),
    Achievement("collector", "Hoarder", "Collect 100 coins in a run"),
    Achievement("rich", "Wealthy", "Have 1000 current points to spend"),
    Achievement("speed_demon", "Speed Demon", "Survive when ads are super fast")
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
                }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Start Game")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { 
                    gameState = "ACHIEVEMENTS" 
                }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Achievements")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { 
                    (context as? ComponentActivity)?.finish()
                }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Exit Game")
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
        "ACHIEVEMENTS" -> {
            AchievementsScreen(
                unlockedIds = unlockedSet.toSet(),
                onBack = { gameState = "START" }
            )
        }
    }
}

@Composable
fun AchievementsScreen(unlockedIds: Set<String>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Achievements", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Button(onClick = onBack) {
                Text("Back")
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AchievementsList) { achievement ->
                val isUnlocked = unlockedIds.contains(achievement.id)
                AchievementItem(achievement, isUnlocked)
            }
        }
    }
}

@Composable
fun AchievementItem(achievement: Achievement, isUnlocked: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isUnlocked) Color(0xFF1E1E1E) else Color(0xFF121212))
            .border(1.dp, if (isUnlocked) Color.Yellow.copy(alpha = 0.5f) else Color.DarkGray, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = achievement.title,
                fontSize = 18.sp,
                color = if (isUnlocked) Color.Yellow else Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = achievement.description,
                fontSize = 14.sp,
                color = if (isUnlocked) Color.LightGray else Color.DarkGray
            )
            if (isUnlocked) {
                Text(
                    text = "UNLOCKED",
                    fontSize = 10.sp,
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
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
    var playerColor by remember { mutableStateOf(Color.Cyan) }
    
    var health by remember { mutableFloatStateOf(1.0f) }
    val ads = remember { mutableStateListOf<AdObstacle>() }
    val coins = remember { mutableStateListOf<PointCoin>() }
    val particles = remember { mutableStateListOf<Particle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }
    
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
    var coinsCollected by remember { mutableIntStateOf(0) }
    var notificationText by remember { mutableStateOf<Achievement?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "hit")
    val hitAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(100), RepeatMode.Reverse), label = "alpha"
    )

    val coinPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse"
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
            
            // Particles Update
            val pIter = particles.iterator()
            while(pIter.hasNext()) {
                val p = pIter.next()
                p.x += p.vx
                p.y += p.vy
                p.life -= 0.02f
                if (p.life <= 0) pIter.remove()
            }

            // Floating Text Update
            val ftIter = floatingTexts.iterator()
            while(ftIter.hasNext()) {
                val ft = ftIter.next()
                ft.y -= 2f
                ft.life -= 0.02f
                if (ft.life <= 0) ftIter.remove()
            }

            // Achievement Logic
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
            if (score >= 10000) tryUnlock("score_10000")
            if (maxComboRun >= 10) tryUnlock("combo_10")
            if (maxComboRun >= 50) tryUnlock("combo_50")
            if (maxComboRun >= 100) tryUnlock("combo_100")
            
            val timeSinceHit = frame - lastHitFrame
            if (lastHitFrame != 0L && timeSinceHit >= 3600) tryUnlock("nohit_1m")
            if (lastHitFrame != 0L && timeSinceHit >= 10800) tryUnlock("nohit_3m")
            
            if (nearMissPoints >= 100) tryUnlock("near_miss_100")
            if (nearMissPoints >= 500) tryUnlock("near_miss_500")
            if (itemsBought >= 5) tryUnlock("shopaholic")
            if (coinsCollected >= 100) tryUnlock("collector")
            if (score >= 1000) tryUnlock("rich")
            
            val currentAdSpeed = 12f + (score / 250f)
            if (currentAdSpeed >= 25f) tryUnlock("speed_demon")

            // Move ads
            val adIterator = ads.iterator()
            while (adIterator.hasNext()) {
                val ad = adIterator.next()
                ad.y += ad.speed
                
                val distToCenter = sqrt(Math.pow((playerX - (ad.x + ad.width/2)).toDouble(), 2.0) + 
                                       Math.pow((playerY - (ad.y + ad.height/2)).toDouble(), 2.0)).toFloat()
                if (distToCenter < 120f && !isInvulnerable) {
                    score += 1 
                    nearMissPoints += 1
                }

                val hit = playerX + playerRadius > ad.x && playerX - playerRadius < ad.x + ad.width &&
                         playerY + playerRadius > ad.y && playerY - playerRadius < ad.y + ad.height
                
                if (hit) {
                    if (!isInvulnerable) {
                        shakeAmount = 25f
                        // Emit Hit Particles
                        repeat(15) {
                            particles.add(Particle(playerX, playerY, Random.nextFloat()*20f-10f, Random.nextFloat()*20f-10f, 1f, Color.Red, 10f))
                        }
                        if (hasShield) {
                            hasShield = false
                        } else {
                            health -= 0.25f
                            if (health <= 0f) onGameOver(score)
                        }
                        lastHitFrame = frame
                        adIterator.remove()
                    }
                } else if (ad.y > 2100) { // Remove before shop
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
                    if (dist < 600f) {
                        coin.x += (dx / dist) * 25f
                        coin.y += (dy / dist) * 25f
                    }
                }
                val dist = sqrt(Math.pow((playerX - coin.x).toDouble(), 2.0) + 
                                Math.pow((playerY - coin.y).toDouble(), 2.0))
                if (dist < (playerRadius + coin.radius)) {
                    val add = 10 * combo
                    score += add
                    coinsCollected++
                    combo++
                    if (combo > maxComboRun) maxComboRun = combo
                    comboTimer = 120 
                    
                    // FX
                    floatingTexts.add(FloatingText("+$add", coin.x, coin.y, 1f, Color.Yellow))
                    if (combo % 5 == 0) floatingTexts.add(FloatingText("COMBO x$combo!", playerX, playerY - 50, 1.2f, Color.Magenta))
                    repeat(10) {
                        particles.add(Particle(coin.x, coin.y, Random.nextFloat()*10f-5f, Random.nextFloat()*10f-5f, 0.8f, Color.Yellow, 8f))
                    }
                    
                    coinIterator.remove()
                }
            }
            
            // Spawning
            if (frame % (maxOf(15, 50 - score/400)).toLong() == 0L) {
                ads.add(AdObstacle(Random.nextInt(), Random.nextFloat() * 900f, -300f, 
                    250f + Random.nextFloat() * 150f, 150f + Random.nextFloat() * 100f, 12f + (score / 250f)))
            }
            if (frame % 70 == 0L) {
                coins.add(PointCoin(Random.nextInt(), 100f + Random.nextFloat() * 800f, 100f + Random.nextFloat() * 1800f))
                if (coins.size > 15) coins.removeAt(0)
            }
            
            delay(16)
        }
    }

    val shakeOffset = Offset(
        if (shakeAmount > 0) Random.nextFloat() * shakeAmount - shakeAmount/2 else 0f,
        if (shakeAmount > 0) Random.nextFloat() * shakeAmount - shakeAmount/2 else 0f
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
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

            // Background Grid
            val gridStep = 150f
            for (x in 0..(size.width / gridStep).toInt()) {
                drawLine(Color.DarkGray.copy(alpha = 0.2f), Offset(x * gridStep, 0f), Offset(x * gridStep, size.height))
            }
            for (y in 0..(size.height / gridStep).toInt()) {
                drawLine(Color.DarkGray.copy(alpha = 0.2f), Offset(0f, y * gridStep), Offset(size.width, y * gridStep))
            }

            // Particles
            particles.forEach { p ->
                drawRect(p.color.copy(alpha = p.life), topLeft = Offset(p.x, p.y), size = Size(p.size, p.size))
            }

            // Coins
            coins.forEach { coin ->
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.Yellow.copy(alpha = 0.4f), Color.Transparent), center = Offset(coin.x + drawX, coin.y + drawY), radius = coin.radius * 3f),
                    radius = coin.radius * 3f,
                    center = Offset(coin.x + drawX, coin.y + drawY)
                )
                drawCircle(color = Color(0xFFFFD700), radius = coin.radius * coinPulse, center = Offset(coin.x + drawX, coin.y + drawY))
                drawCircle(color = Color.White, radius = coin.radius * coinPulse, center = Offset(coin.x + drawX, coin.y + drawY), style = Stroke(2f))
            }

            // Player Glow
            drawCircle(
                brush = Brush.radialGradient(listOf(playerColor.copy(alpha = 0.5f), Color.Transparent), center = Offset(playerX + drawX, playerY + drawY), radius = playerRadius * 2.5f),
                radius = playerRadius * 2.5f,
                center = Offset(playerX + drawX, playerY + drawY)
            )

            if (hasShield) {
                drawCircle(color = Color.Cyan.copy(alpha = 0.4f), radius = playerRadius + 20f, center = Offset(playerX + drawX, playerY + drawY), style = Stroke(4f))
            }

            drawCircle(
                color = if (isInvulnerable) playerColor.copy(alpha = hitAlpha) else playerColor,
                radius = playerRadius,
                center = Offset(playerX + drawX, playerY + drawY)
            )

            // Retro Ads
            ads.forEach { ad ->
                drawRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(ad.x + drawX + 10f, ad.y + drawY + 10f), size = Size(ad.width, ad.height))
                drawRect(Color.White, topLeft = Offset(ad.x + drawX, ad.y + drawY), size = Size(ad.width, ad.height))
                drawRect(Color.Red, topLeft = Offset(ad.x + drawX, ad.y + drawY), size = Size(ad.width, 40f))
                drawLine(Color.White, Offset(ad.x + drawX + ad.width - 30f, ad.y + drawY + 10f), Offset(ad.x + drawX + ad.width - 10f, ad.y + drawY + 30f), 3f)
                drawLine(Color.White, Offset(ad.x + drawX + ad.width - 10f, ad.y + drawY + 10f), Offset(ad.x + drawX + ad.width - 30f, ad.y + drawY + 30f), 3f)
                drawRect(Color.Black, topLeft = Offset(ad.x + drawX, ad.y + drawY), size = Size(ad.width, ad.height), style = Stroke(3f))
            }
        }
        
        // Floating Text Layer
        Box(Modifier.fillMaxSize()) {
            floatingTexts.forEach { ft ->
                Text(
                    text = ft.text,
                    color = ft.color.copy(alpha = ft.life),
                    fontSize = (20 + (10 * ft.life)).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(ft.x.dp / 2f, ft.y.dp / 2f) // Rough scaling
                )
            }
        }
        
        // Vertical Health Bar at Top Left
        Box(modifier = Modifier.padding(top = 100.dp, start = 16.dp)) {
            HealthBar(health)
        }

        // Notification
        AnimatedVisibility(
            visible = notificationText != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            notificationText?.let {
                Box(modifier = Modifier.background(Color.Yellow, RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ACHIEVEMENT!", fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        Text(it.title, color = Color.Black, fontSize = 20.sp)
                    }
                }
            }
        }

        // HUD
        Column(modifier = Modifier.padding(24.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(text = "Score: $score", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
                if (combo > 1) {
                    Text(text = "COMBO x$combo", fontSize = 24.sp, color = Color(0xFFFF00FF), fontWeight = FontWeight.ExtraBold)
                    Box(modifier = Modifier.width((comboTimer * 2).dp).height(6.dp).background(Color(0xFFFF00FF)))
                }
            }

            // Shop Taskbar area background
            Box(modifier = Modifier
                .fillMaxWidth()
                .offset(y = 40.dp)
                .height(120.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            )

            // Shop
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShopItem("HEAL", 50, health < 1.0f && score >= 50) { score -= 50; health = (health + 0.25f).coerceAtMost(1.0f); itemsBought++ }
                ShopItem("SHIELD", 75, !hasShield && score >= 75) { score -= 75; hasShield = true; itemsBought++ }
                ShopItem("SHRINK", 150, playerRadius > 25f && score >= 150) { score -= 150; playerRadius = 25f; itemsBought++ }
                ShopItem("MAGNET", 200, !hasMagnet && score >= 200) { score -= 200; hasMagnet = true; itemsBought++ }
                ShopItem("SKIN", 20, score >= 20) { score -= 20; playerColor = Color(Random.nextInt()); itemsBought++ }
            }
        }
    }
}

@Composable
fun HealthBar(health: Float) {
    Box(modifier = Modifier.width(20.dp).height(200.dp).clip(RoundedCornerShape(10.dp)).background(Color.DarkGray)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = health.coerceIn(0f, 1f))
            .align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Green, Color.Yellow, Color.Red))))
    }
}

@Composable
fun ShopItem(name: String, cost: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Color.White else Color.Gray)
        .clickable(enabled = enabled) { onClick() }.padding(4.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            Text("$$cost", fontSize = 11.sp, color = Color.Blue)
        }
    }
}

@Composable
fun AdOverlay(score: Int, highScore: Int, reviveAvailable: Boolean, onRevive: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(32.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GAME OVER", fontSize = 36.sp, color = Color.Red, fontWeight = FontWeight.ExtraBold)
            if (score >= highScore && score > 0) Text("NEW RECORD!", color = Color(0xFF00FF00), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Score: $score", fontSize = 24.sp, color = Color.Black)
            Text("Best: $highScore", fontSize = 18.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("BUY AD-BLOCKER\nTO SURVIVE!", color = Color.DarkGray, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (reviveAvailable) {
                Button(onClick = onRevive, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("REVIVE (100 PTS)") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("RETRY") }
        }
    }
}
