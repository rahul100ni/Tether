package com.rahul100ni.tether

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rahul100ni.tether.ui.theme.TetherNeon
import com.rahul100ni.tether.ui.theme.TetherNeonContainer
import com.rahul100ni.tether.ui.theme.TetherTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TetherTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }
    var blockedAppAttempts by remember { mutableStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val appPrefs = remember { AppSettings.prefs(context) }
    var overrideEnabled by remember { mutableStateOf(appPrefs.getBoolean(AppSettings.KEY_OVERRIDE_ENABLED, true)) }
    var overrideSeconds by remember { mutableStateOf(appPrefs.getInt(AppSettings.KEY_OVERRIDE_SECONDS, AppSettings.DEFAULT_OVERRIDE_SECONDS)) }
    var holdProgress by remember { mutableStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val qrCodeScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == QrCodeActivity.getOrCreateToken(context)) {
            if (isServiceRunning) {
                context.stopService(Intent(context, AppMonitoringService::class.java))
                Toast.makeText(context, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                isServiceRunning = false
            } else {
                startMonitoringService(context)
                Toast.makeText(context, "Monitoring started.", Toast.LENGTH_SHORT).show()
                isServiceRunning = true
            }
        } else if (result.contents == QrCodeActivity.LEGACY_QR_CONTENT) {
            Toast.makeText(context, "That QR code is from an older Tether version. Print a new one from \"Show QR Code\".", Toast.LENGTH_LONG).show()
        } else if (result.contents != null) {
            Toast.makeText(context, "Incorrect QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsageStatsPermission(context)
                canDrawOverlays = Settings.canDrawOverlays(context)
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (!isServiceRunning && prefs.getBoolean("monitoring_active", false) && hasUsagePermission && canDrawOverlays) {
                    startMonitoringService(context)
                    isServiceRunning = true
                }
                blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
                overrideEnabled = prefs.getBoolean(AppSettings.KEY_OVERRIDE_ENABLED, true)
                overrideSeconds = prefs.getInt(AppSettings.KEY_OVERRIDE_SECONDS, AppSettings.DEFAULT_OVERRIDE_SECONDS)
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val duration = overrideSeconds * 1000L
            while (isHolding && System.currentTimeMillis() - startTime < duration) {
                holdProgress = (System.currentTimeMillis() - startTime) / duration.toFloat()
                delay(50)
            }
            if (isHolding) {
                holdProgress = 1f
                context.stopService(Intent(context, AppMonitoringService::class.java))
                isServiceRunning = false
            }
        } else {
            holdProgress = 0f
        }
    }

    val allPermissionsGranted = hasUsagePermission && canDrawOverlays

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Wordmark Header ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TETHER",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            letterSpacing = 8.sp,
                            color = TetherNeon
                        )
                    )
                    Text(
                        text = "stay tethered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Status Panel ────────────────────────────────────────────
                StatusPanel(
                    isActive = isServiceRunning,
                    blockedAppAttempts = blockedAppAttempts
                )

                // ── Start Button ────────────────────────────────────────────
                if (!isServiceRunning) {
                    Button(
                        onClick = {
                            startMonitoringService(context)
                            isServiceRunning = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TetherNeon,
                            contentColor = Color(0xFF00150C)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "INITIATE SESSION",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // ── Action Panel ────────────────────────────────────────────
                ActionPanel(
                    isServiceRunning = isServiceRunning,
                    hasCameraPermission = hasCameraPermission,
                    onManageApps = { context.startActivity(Intent(context, AppSelectionActivity::class.java)) },
                    onWriteNfc = { context.startActivity(Intent(context, NfcWriteActivity::class.java)) },
                    onShowQr = { context.startActivity(Intent(context, QrCodeActivity::class.java)) },
                    onScanQr = {
                        if (hasCameraPermission) {
                            qrCodeScannerLauncher.launch(ScanOptions().setOrientationLocked(true))
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                )

                // ── Emergency Override ──────────────────────────────────────
                if (isServiceRunning && overrideEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EMERGENCY OVERRIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    })
                                }
                        ) {
                            LinearProgressIndicator(
                                progress = { holdProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer
                            )
                            val overrideLabel = if (overrideSeconds % 60 == 0) "${overrideSeconds / 60}m" else "${overrideSeconds}s"
                            Text(
                                text = "HOLD $overrideLabel TO FORCE TERMINATE",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (holdProgress > 0.5f) MaterialTheme.colorScheme.onError
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            // ── Permissions Screen ──────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Neon lock icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TetherNeonContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⬡",
                        color = TetherNeon,
                        fontSize = 36.sp
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "PERMISSIONS REQUIRED",
                    style = MaterialTheme.typography.labelLarge,
                    color = TetherNeon,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tether needs a few permissions to monitor and block apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(36.dp))
                if (!hasUsagePermission) {
                    Button(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TetherNeon,
                            contentColor = Color(0xFF00150C)
                        ),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    ) {
                        Text("GRANT USAGE ACCESS", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TetherNeon),
                        colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = TetherNeon),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
                    ) {
                        Text("GRANT OVERLAY PERMISSION", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ── Animated Status Panel ─────────────────────────────────────────────────────
@Composable
private fun StatusPanel(isActive: Boolean, blockedAppAttempts: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isActive) TetherNeon.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Animated pulse dot
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                if (isActive) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(TetherNeon.copy(alpha = 0.12f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) TetherNeon
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isActive) "● ACTIVE" else "○ INACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) TetherNeon else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        isActive && blockedAppAttempts > 0 ->
                            "$blockedAppAttempts blocked attempt${if (blockedAppAttempts != 1) "s" else ""} this session"
                        isActive -> "Session in progress"
                        else -> "No active session"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Action Panel ───────────────────────────────────────────────────────────────
@Composable
private fun ActionPanel(
    isServiceRunning: Boolean,
    hasCameraPermission: Boolean,
    onManageApps: () -> Unit,
    onWriteNfc: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        ActionRow(icon = Icons.Default.Apps,           label = "Manage Blocked Apps", enabled = !isServiceRunning, onClick = onManageApps)
        ActionRowDivider()
        ActionRow(icon = Icons.Default.Nfc,            label = "Write NFC Tag",        enabled = !isServiceRunning, onClick = onWriteNfc)
        ActionRowDivider()
        ActionRow(icon = Icons.Default.QrCode2,        label = "Show QR Code",         enabled = !isServiceRunning, onClick = onShowQr)
        ActionRowDivider()
        ActionRow(icon = Icons.Default.QrCodeScanner,  label = "Scan QR Code",         onClick = onScanQr)
        ActionRowDivider()
        ActionRow(icon = Icons.Default.Tune,           label = "Settings",             onClick = onSettings)
    }
}

@Composable
private fun ActionRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.35f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (enabled) TetherNeonContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) TetherNeon
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )

        // Right indicator dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) TetherNeon.copy(alpha = 0.5f)
                    else Color.Transparent
                )
        )
    }
}
