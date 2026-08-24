package com.vakya.tether

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import coil.compose.rememberAsyncImagePainter
import com.vakya.tether.ui.theme.TetherNeon
import com.vakya.tether.ui.theme.TetherNeonContainer
import com.vakya.tether.ui.theme.TetherTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class BlockingActivity : ComponentActivity() {

    private var blockedPackage: String? = null
    private var awaitingScanResult = false

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        awaitingScanResult = false
        when (result.contents) {
            null -> Unit // scan cancelled
            QrCodeActivity.getOrCreateToken(this) -> unlockAndReturn()
            QrCodeActivity.LEGACY_QR_CONTENT ->
                Toast.makeText(this, "That QR code is from an older Tether version. Print a new one from \"Show QR Code\".", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "Incorrect QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME")
        val packageName = blockedPackage ?: "An app"
        val prefs = AppSettings.prefs(this)
        val strictMode = prefs.getBoolean(AppSettings.KEY_STRICT_MODE, false)
        val unlockMinutes = prefs.getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)

        val goHome = {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })

        setContent {
            TetherTheme {
                BlockingScreen(
                    packageName = packageName,
                    strictMode = strictMode,
                    unlockMinutes = unlockMinutes,
                    onGoHomeClick = goHome,
                    onTakeBreakClick = {
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                        }
                        startService(breakIntent)
                        finish()
                    },
                    onScanToUnlockClick = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchScanner()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() { super.onResume(); AppForeground.onBlockingResumed(blockedPackage) }
    override fun onPause() { super.onPause(); AppForeground.onBlockingPaused() }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && !awaitingScanResult) finish()
    }

    private fun launchScanner() {
        awaitingScanResult = true
        qrScanLauncher.launch(ScanOptions().setOrientationLocked(true))
    }

    private fun unlockAndReturn() {
        val pkg = blockedPackage ?: return
        val minutes = AppSettings.prefs(this).getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)
        val unlockIntent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.ACTION_UNLOCK_APP
            putExtra(AppMonitoringService.EXTRA_UNLOCK_PACKAGE, pkg)
        }
        startService(unlockIntent)
        Toast.makeText(this, "Unlocked for $minutes minute${if (minutes != 1) "s" else ""}.", Toast.LENGTH_SHORT).show()
        packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    strictMode: Boolean,
    unlockMinutes: Int,
    onGoHomeClick: () -> Unit,
    onTakeBreakClick: () -> Unit,
    onScanToUnlockClick: () -> Unit
) {
    val context = LocalContext.current

    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var breaksRemaining by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(key1 = Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        breaksRemaining = prefs.getInt("breaks_remaining", 0)

        val pm = context.packageManager
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            appName = packageName
        }
    }

    // Animated ring pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── App Icon with animated neon ring ──────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Outer pulsing glow ring
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .scale(ringScale)
                        .clip(RoundedCornerShape(28.dp))
                        .background(TetherNeon.copy(alpha = 0.07f))
                )
                // Neon border frame
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, TetherNeon.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = appIcon),
                        contentDescription = "$appName icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Lock badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .clip(CircleShape)
                        .background(TetherNeon)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF00150C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── ACCESS DENIED label ───────────────────────────────────────
            Text(
                text = "ACCESS DENIED",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = TetherNeon
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (strictMode) {
                    "Tap your NFC tag or scan your QR code to unlock $appName for $unlockMinutes minute${if (unlockMinutes != 1) "s" else ""}."
                } else {
                    "Tap your NFC tag or scan your QR code to unlock."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Action Buttons ────────────────────────────────────────────
            if (strictMode) {
                // Scan is primary when in strict mode
                Button(
                    onClick = onScanToUnlockClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TetherNeon,
                        contentColor = Color(0xFF00150C)
                    )
                ) {
                    Text("SCAN TO UNLOCK", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onGoHomeClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Go Home", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onGoHomeClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, TetherNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TetherNeon)
                ) {
                    Text("GO HOME", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                // Ghost/muted break button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .background(Color.Transparent)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Take a Break  ·  $breaksRemaining remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            prefs.edit { putInt("breaks_remaining", breaksRemaining - 1) }
                            onTakeBreakClick()
                        }
                    )
                }
            }
        }
    }
}
