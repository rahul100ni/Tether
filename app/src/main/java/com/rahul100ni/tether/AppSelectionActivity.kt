package com.rahul100ni.tether

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.rahul100ni.tether.database.BlockedApp
import com.rahul100ni.tether.database.BlockedAppDao
import com.rahul100ni.tether.ui.theme.TetherNeon
import com.rahul100ni.tether.ui.theme.TetherNeonContainer
import com.rahul100ni.tether.ui.theme.TetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false
)

class AppSelectionViewModel(private val blockedAppDao: BlockedAppDao, private val application: Application) : ViewModel() {

    companion object {
        private val EXCLUDED_PACKAGES = setOf(
            "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
            "com.sonyericsson.android.socialphonebook", "com.android.systemui",
            "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher",
            "com.android.settings", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            // Camera apps across major OEMs — the website promises the camera can never be blocked
            "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
            "com.sec.android.app.camera", "com.oneplus.camera", "com.oplus.camera",
            "com.xiaomi.camera", "com.huawei.camera", "com.motorola.camera3",
            "org.codeaurora.snapcam", "com.sonymobile.photopro", "com.asus.camera"
        )
    }

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    init { loadInstalledApps() }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val allApps = pm.queryIntentActivities(intent, 0)

            val baseAppList = allApps.mapNotNull { app ->
                val packageName = app.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == application.packageName || EXCLUDED_PACKAGES.contains(packageName)) return@mapNotNull null
                try {
                    AppInfo(appName = app.loadLabel(pm).toString(), packageName = packageName)
                } catch (e: Exception) {
                    android.util.Log.w("AppSelectionViewModel", "Skipping $packageName: ${e.message}")
                    null
                }
            }
                // queryIntentActivities returns one entry per launcher activity, so packages
                // with multiple launcher entries would duplicate the LazyColumn key and crash
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }

            blockedAppDao.getAllBlockedApps().collect { blockedApps ->
                val blockedAppPackages = blockedApps.map { it.packageName }.toSet()
                _apps.value = baseAppList.map { it.copy(isSelected = blockedAppPackages.contains(it.packageName)) }
            }
        }
    }

    fun onAppSelectionChanged(app: AppInfo, isSelected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSelected) blockedAppDao.insert(BlockedApp(packageName = app.packageName))
            else blockedAppDao.delete(BlockedApp(packageName = app.packageName))
        }
    }

    fun selectAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            blockedAppDao.insertAll(apps.value.map { BlockedApp(it.packageName) })
        }
    }

    fun unselectAllApps() {
        viewModelScope.launch(Dispatchers.IO) { blockedAppDao.deleteAll() }
    }
}

class AppSelectionViewModelFactory(private val application: Application, private val blockedAppDao: BlockedAppDao) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSelectionViewModel(blockedAppDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AppSelectionActivity : ComponentActivity() {
    private val viewModel: AppSelectionViewModel by viewModels {
        AppSelectionViewModelFactory(
            application,
            (application as App).database.blockedAppDao()
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TetherTheme {
                val appList by viewModel.apps.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "BLOCKED APPS",
                                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TetherNeon
                                    )
                                }
                            },
                            actions = {
                                TextButton(
                                    onClick = { viewModel.selectAllApps() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = TetherNeon)
                                ) { Text("All", style = MaterialTheme.typography.labelMedium) }
                                TextButton(
                                    onClick = { viewModel.unselectAllApps() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) { Text("None", style = MaterialTheme.typography.labelMedium) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    AppSelectionScreen(
                        apps = appList,
                        onAppCheckedChange = { app, isSelected ->
                            viewModel.onAppSelectionChanged(app, isSelected)
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppSelectionScreen(
    apps: List<AppInfo>,
    onAppCheckedChange: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.appName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // ── Dark search field ──────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            placeholder = {
                Text(
                    text = "Search apps...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = TetherNeon
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TetherNeon,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = TetherNeon
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(filteredApps, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    onCheckedChange = { isSelected -> onAppCheckedChange(app, isSelected) },
                    isEnabled = !isServiceRunning
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        try { context.packageManager.getApplicationIcon(app.packageName) }
        catch (e: PackageManager.NameNotFoundException) { null }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onCheckedChange(!app.isSelected) }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        // App icon with optional neon-ring when selected
        Box(contentAlignment = Alignment.Center) {
            if (app.isSelected) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, TetherNeon.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .background(TetherNeonContainer)
                )
            }
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context).data(appIcon).build()
                ),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (isEnabled) 1f else 0.5f
            )
        )

        Spacer(modifier = Modifier.width(16.dp))

        Checkbox(
            checked = app.isSelected,
            onCheckedChange = onCheckedChange,
            enabled = isEnabled,
            colors = CheckboxDefaults.colors(
                checkedColor = TetherNeon,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}
