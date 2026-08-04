package com.creativem.toblauncher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🛡️ BLOQUEAR EL BOTÓN ATRÁS EN LA PANTALLA PRINCIPAL DEL LAUNCHER
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Evitamos que la tableta minimice el Launcher
            }
        })

        // ✅ FONDO NEGRO ABSOLUTO DESDE EL PRIMER MILISEGUNDO DE ARRANQUE
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        // PANTALLA SIEMPRE ENCENDIDA
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // MODO INMERSIVO 100% PANTALLA COMPLETA
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        try {
            AndroidGraphicFactory.createInstance(application)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Solo solicitar launcher por defecto si NO es el launcher actual
        if (!isCurrentlyDefaultLauncher(this)) {
            promptDefaultLauncherSelection(this)
        }

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            setContent {
                MaterialTheme {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            SmartMusicPlayer.getInstance(this).release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isCurrentlyDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun promptDefaultLauncherSelection(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    startActivityForResult(intent, 1001)
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTheme by remember { mutableStateOf(ThemeManager.getSavedTheme(context)) }
    var currentEqStyle by remember { mutableStateOf(ThemeManager.getSavedEqualizerStyle(context)) }
    var currentTextScale by remember { mutableFloatStateOf(ThemeManager.getSavedTextScale(context)) }
    var currentIsBold by remember { mutableStateOf(ThemeManager.getSavedIsBold(context)) }
    var currentButtonScale by remember { mutableFloatStateOf(ThemeManager.getSavedButtonScale(context)) }

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, currentTextScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentTextScale
        )
    }

    val defaultTextStyle = LocalTextStyle.current
    val customTextStyle = remember(defaultTextStyle, currentIsBold) {
        defaultTextStyle.copy(
            fontWeight = if (currentIsBold) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
    // 🎛️ BUCLE DE ECUALIZADORES ALEATORIOS AUTOMÁTICO
    LaunchedEffect(Unit) {
        while (isActive) {
            val intervalSec = ThemeManager.getSavedAutoRotateEqInterval(context)

            delay(intervalSec * 1000L) // Espera el tiempo configurado

            if (ThemeManager.getSavedAutoRotateEqEnabled(context)) {
                val otherStyles = EqualizerStyle.values().filter { it != currentEqStyle }
                if (otherStyles.isNotEmpty()) {
                    val nextStyle = otherStyles.random()
                    currentEqStyle = nextStyle
                    ThemeManager.saveEqualizerStyle(context, nextStyle)
                }
            }
        }
    }

    fun calculateInitialStep(): Int {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasBrightness = BrightnessManager.hasWriteSettingsPermission(context)
        val hasBatteryExemption = isIgnoringBatteryOptimizations(context)

        return when {
            !hasLocation -> 1
            !hasStorage -> 2
            !hasMic -> 3
            !hasBrightness -> 4
            !hasBatteryExemption -> 5
            else -> 0
        }
    }

    var currentStep by remember { mutableIntStateOf(calculateInitialStep()) }

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        currentStep = calculateInitialStep()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentStep = calculateInitialStep()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(
        LocalDensity provides customDensity,
        LocalDashboardTheme provides currentTheme,
        LocalIsBoldText provides currentIsBold,
        LocalButtonScale provides currentButtonScale,
        LocalTextStyle provides customTextStyle,
        LocalEqualizerStyle provides currentEqStyle // 👈 PROVEEDOR DEL ECUALIZADOR INDEPENDIENTE
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentTheme.dashBackground
        ) {
            when (currentStep) {
                1 -> PermissionStepScreen(
                    title = "Permiso de GPS",
                    description = "Necesitamos tu ubicación exacta para el mapa y velocímetro.",
                    icon = Icons.Default.LocationOn,
                    permissionsToRequest = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    onPermissionGranted = { currentStep = calculateInitialStep() }
                )

                2 -> {
                    val isAndroid11OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

                    PermissionStepScreen(
                        title = if (isAndroid11OrAbove) "Acceso Total a USB" else "Acceso a Multimedia",
                        description = if (isAndroid11OrAbove)
                            "Para leer videos y música desde memorias USB, Android requiere acceso a todos los archivos."
                        else
                            "Permite acceso a tu música, videos y mapas.",
                        icon = if (isAndroid11OrAbove) Icons.Default.Folder else Icons.Default.LibraryMusic,
                        permissionsToRequest = if (isAndroid11OrAbove) emptyArray() else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        onPermissionGranted = { currentStep = calculateInitialStep() },
                        customAction = if (isAndroid11OrAbove) {
                            {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    systemSettingsLauncher.launch(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    systemSettingsLauncher.launch(intent)
                                }
                            }
                        } else null
                    )
                }

                3 -> PermissionStepScreen(
                    title = "Permiso de Micrófono",
                    description = "Necesario para comandos de voz mientras conduces.",
                    icon = Icons.Default.Mic,
                    permissionsToRequest = arrayOf(Manifest.permission.RECORD_AUDIO),
                    onPermissionGranted = { currentStep = calculateInitialStep() }
                )

                4 -> PermissionStepScreen(
                    title = "Control de Brillo para Tablet",
                    description = "Permite ajustar automáticamente la pantalla para que no encandile de Noche (6 PM) y sea visible de Día (6 AM).",
                    icon = Icons.Default.Brightness6,
                    permissionsToRequest = emptyArray(),
                    onPermissionGranted = { currentStep = calculateInitialStep() },
                    customAction = {
                        BrightnessManager.requestWriteSettingsPermission(context)
                    }
                )

                5 -> PermissionStepScreen(
                    title = "Modo Alto Rendimiento Auto",
                    description = "Como la tablet funciona con la energía del vehículo, quita los límites de energía para que el Launcher, la Radio y los Mapas NUNCA se detengan.",
                    icon = Icons.Default.FlashOn,
                    permissionsToRequest = emptyArray(),
                    onPermissionGranted = { currentStep = 0 },
                    customAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                systemSettingsLauncher.launch(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    systemSettingsLauncher.launch(intent)
                                } catch (ex: Exception) {
                                    currentStep = 0
                                }
                            }
                        } else {
                            currentStep = 0
                        }
                    }
                )

                0 -> CarDashboard(
                    currentTheme = currentTheme,
                    currentTextScale = currentTextScale,
                    currentIsBold = currentIsBold,
                    currentButtonScale = currentButtonScale,
                    currentEqualizerStyle = currentEqStyle, // 👈 1. PASADO A CARDASHBOARD
                    onThemeChanged = { newTheme -> currentTheme = newTheme },
                    onTextScaleChanged = { newScale -> currentTextScale = newScale },
                    onIsBoldChanged = { newIsBold -> currentIsBold = newIsBold },
                    onButtonScaleChanged = { newButtonScale -> currentButtonScale = newButtonScale },
                    onEqualizerStyleChanged = { newStyle -> currentEqStyle = newStyle } // 👈 2. CALLBACK DE CAMBIO
                )
            }
        }
    }
}

@Composable
fun PermissionStepScreen(
    title: String,
    description: String,
    icon: ImageVector,
    permissionsToRequest: Array<String>,
    onPermissionGranted: () -> Unit,
    customAction: (() -> Unit)? = null
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionGranted() }

    val theme = LocalDashboardTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(90.dp), tint = theme.accentCyan)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, color = Color.LightGray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = {
                onPermissionGranted()
            }) { Text("Omitir", color = Color.White) }

            Button(
                onClick = {
                    if (customAction != null) {
                        customAction()
                    } else {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
            ) {
                Text("Conceder Permiso", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CarDashboard(
    currentTheme: DashboardTheme,
    currentTextScale: Float,
    currentIsBold: Boolean,
    currentButtonScale: Float,
    currentEqualizerStyle: EqualizerStyle,
    onThemeChanged: (DashboardTheme) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onIsBoldChanged: (Boolean) -> Unit,
    onButtonScaleChanged: (Float) -> Unit,
    onEqualizerStyleChanged: (EqualizerStyle) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val activity = context as? android.app.Activity

    var isMapExpanded by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var showFullscreenMusic by remember { mutableStateOf(false) }
    var showFullscreenVideo by remember { mutableStateOf(false) }
    var showFullscreenIptv by remember { mutableStateOf(false) }
    var showFullscreenRadio by remember { mutableStateOf(false) }

    var activeAppDrawerTarget by remember { mutableIntStateOf(0) }
    var currentSpeedKmH by remember { mutableFloatStateOf(0f) }
    var currentBearing by remember { mutableFloatStateOf(0f) }

    val initialMediaMode = remember { getSavedMediaTabOrder(context).firstOrNull() ?: MediaMode.MUSIC }
    var currentMediaMode by remember { mutableStateOf(initialMediaMode) }

    LaunchedEffect(Unit) {
        activity?.let { act ->
            BrightnessManager.init(act)
            while (isActive) {
                BrightnessManager.applyBrightnessToActivity(act)
                kotlinx.coroutines.delay(30000L)
            }
        }
    }

    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentSpeedKmH = (loc.speed * 3.6f).coerceAtLeast(0f)
                if (loc.hasBearing()) {
                    currentBearing = loc.bearing
                }

                activity?.let { act ->
                    BrightnessManager.applyBrightnessToActivity(act)
                }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapExpanded) {
            Box(modifier = Modifier.fillMaxSize()) {
                MapContainerWidget(
                    onExpandClicked = { isMapExpanded = false }
                )

                IconButton(
                    onClick = { isMapExpanded = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color(0xCC1E1E1E), shape = RoundedCornerShape(12.dp))
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Salir de Pantalla Completa",
                        tint = theme.accentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // 🌌 🚀 AQUÍ ESTÁ EL CAMBIO: REEMPLAZAMOS EL BOX PLANO POR EL FONDO 3D
            Interactive3DBackground(
                theme = theme,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernDashboardCard(
                                modifier = Modifier.weight(0.4f),
                                title = null,
                                icon = Icons.Default.Map,
                                headerAction = {
                                    IconButton(onClick = { isMapExpanded = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Expandir Mapa", tint = theme.accentCyan)
                                    }
                                }
                            ) {
                                MapContainerWidget(
                                    onExpandClicked = { isMapExpanded = true }
                                )
                            }

                            ModernDashboardCard(
                                modifier = Modifier.weight(0.6f),
                                title = null
                            ) {
                                ModernMediaPlayerWidget(
                                    currentMode = currentMediaMode,
                                    onModeChange = { newMode: MediaMode -> currentMediaMode = newMode },
                                    onExpandMusicFullscreen = { showFullscreenMusic = true },
                                    onExpandVideoFullscreen = { showFullscreenVideo = true },
                                    onExpandIptvFullscreen = { showFullscreenIptv = true },
                                    onExpandRadioFullscreen = { showFullscreenRadio = true }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernDashboardCard(
                                modifier = Modifier.weight(1f),
                                title = null
                            ) {
                                ModernSpeedometerWidget(
                                    speedKmH = currentSpeedKmH,
                                    bearing = currentBearing,
                                    onRequestAppSelection = { slot: Int ->
                                        activeAppDrawerTarget = slot
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ModernDashboardCard(
                                    modifier = Modifier.weight(1.1f),
                                    title = "APLICACIONES",
                                    icon = Icons.Default.Apps,
                                    headerAction = {
                                        IconButton(
                                            onClick = { activeAppDrawerTarget = 99 },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Launch,
                                                contentDescription = "Ver Todas",
                                                tint = theme.accentCyan
                                            )
                                        }
                                    }
                                ) {
                                    CustomApps3DGridWidget(
                                        onRequestAppSelection = { slot -> activeAppDrawerTarget = slot }
                                    )
                                }

                                ModernDashboardCard(
                                    modifier = Modifier.weight(1f),
                                    title = null,
                                    icon = Icons.Default.Schedule
                                ) {
                                    ModernClockWidget()
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { showThemeModal = true },
                        containerColor = theme.cardBackground,
                        contentColor = theme.accentCyan,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 24.dp)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Mundo de Colores",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (BrightnessManager.nightOverlayAlpha > 0.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = BrightnessManager.nightOverlayAlpha))
            )
        }

        if (showFullscreenMusic) {
            FullscreenMusicPlayerWidget(
                onClose = { showFullscreenMusic = false }
            )
        }

        if (showFullscreenVideo) {
            FullscreenVideoPlayerWidget(
                onClose = { showFullscreenVideo = false }
            )
        }

        if (showFullscreenIptv) {
            FullscreenIptvPlayerWidget(
                onClose = { showFullscreenIptv = false }
            )
        }

        if (showFullscreenRadio) {
            FullscreenRadioPlayerWidget(
                onClose = { showFullscreenRadio = false }
            )
        }

        if (activeAppDrawerTarget != 0) {
            FullscreenAppDrawerWidget(
                title = if (activeAppDrawerTarget == 99) "TODAS LAS APLICACIONES" else "SELECCIONAR APP PARA SLOT",
                selectedSlotId = if (activeAppDrawerTarget != 99) activeAppDrawerTarget else null,
                onClose = { activeAppDrawerTarget = 0 },
                onAppSelected = { selectedPackage: String ->
                    when (activeAppDrawerTarget) {
                        in 1..2 -> {
                            val prefs = context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("slot_$activeAppDrawerTarget", selectedPackage).apply()
                        }
                        in 101..120 -> {
                            val slotNum = activeAppDrawerTarget - 100
                            val prefs = context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("grid_slot_$activeAppDrawerTarget", selectedPackage)
                                .putString("grid_slot_$slotNum", selectedPackage)
                                .apply()
                        }
                        else -> {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedPackage)
                            if (launchIntent != null) context.startActivity(launchIntent)
                        }
                    }
                    activeAppDrawerTarget = 0
                }
            )
        }

        if (showThemeModal) {
            ThemeSelectorModal(
                currentTheme = currentTheme,
                currentTextScale = currentTextScale,
                currentIsBold = currentIsBold,
                currentButtonScale = currentButtonScale,
                currentEqualizerStyle = currentEqualizerStyle,
                onDismiss = { showThemeModal = false },
                onThemeSelected = { newTheme: DashboardTheme -> onThemeChanged(newTheme) },
                onTextScaleChanged = { newScale: Float -> onTextScaleChanged(newScale) },
                onIsBoldChanged = { newIsBold: Boolean -> onIsBoldChanged(newIsBold) },
                onButtonScaleChanged = { newButtonScale: Float -> onButtonScaleChanged(newButtonScale) },
                onEqualizerStyleChanged = { newStyle: EqualizerStyle -> onEqualizerStyleChanged(newStyle) }
            )
        }
    }
}