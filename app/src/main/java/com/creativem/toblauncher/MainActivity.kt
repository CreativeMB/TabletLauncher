package com.creativem.toblauncher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
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
import com.google.android.gms.location.*
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        promptDefaultLauncherSelection(this)

        // LEER EL TEMA GUARDADO ANTES DE INICIAR COMPOSE (SOLUCIONA EL ERROR DashBackground)
        val initialTheme = ThemeManager.getSavedTheme(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = initialTheme.dashBackground // Usa el color del tema guardado
                ) {
                    MainScreen()
                }
            }
        }
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

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // 1. ESTADOS DEL TEMA DE COLORES Y ESCALA DE TEXTOS
    var currentTheme by remember { mutableStateOf(ThemeManager.getSavedTheme(context)) }
    var currentTextScale by remember { mutableFloatStateOf(ThemeManager.getSavedTextScale(context)) }
    var currentIsBold by remember { mutableStateOf(ThemeManager.getSavedIsBold(context)) }

    // DENSIDAD PERSONALIZADA PARA ESCALAR TODA LA ESCRITURA Y NÚMEROS DE LA TABLET
    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, currentTextScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentTextScale // Aplica el multiplicador (80% al 280%)
        )
    }

    // ESTILO DE TEXTO GLOBAL: CAMBIA EL GROSOR DE TODA LA APP EN TIEMPO REAL
    val defaultTextStyle = LocalTextStyle.current
    val customTextStyle = remember(defaultTextStyle, currentIsBold) {
        defaultTextStyle.copy(
            fontWeight = if (currentIsBold) FontWeight.ExtraBold else FontWeight.Normal
        )
    }

    // 2. DETERMINACIÓN SÍNCRONA DEL PASO DE PERMISOS
    val initialStep = remember {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        when {
            !hasLocation -> 1
            !hasStorage -> 2
            !hasMic -> 3
            else -> 0
        }
    }

    var currentStep by remember { mutableStateOf(initialStep) }

    // 3. INYECCIÓN DOBLE DE TEMA Y ESCALA DE DENSIDAD DE TEXTO
    CompositionLocalProvider(
        LocalDensity provides customDensity,
        LocalDashboardTheme provides currentTheme,
        LocalIsBoldText provides currentIsBold,
        LocalTextStyle provides customTextStyle
    ) {
        when (currentStep) {
            1 -> PermissionStepScreen(
                title = "Permiso de GPS",
                description = "Necesitamos tu ubicación exacta para el mapa y velocímetro.",
                icon = Icons.Default.LocationOn,
                permissionsToRequest = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                onPermissionGranted = { currentStep = 2 }
            )
            2 -> {
                val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                PermissionStepScreen(
                    title = "Acceso a Multimedia",
                    description = "Permite acceso a tu música, videos y mapas.",
                    icon = Icons.Default.LibraryMusic,
                    permissionsToRequest = storagePermissions,
                    onPermissionGranted = { currentStep = 3 }
                )
            }
            3 -> PermissionStepScreen(
                title = "Permiso de Micrófono",
                description = "Necesario para comandos de voz mientras conduces.",
                icon = Icons.Default.Mic,
                permissionsToRequest = arrayOf(Manifest.permission.RECORD_AUDIO),
                onPermissionGranted = { currentStep = 0 }
            )
            0 -> CarDashboard(
                currentTheme = currentTheme,
                currentTextScale = currentTextScale,
                currentIsBold = currentIsBold,
                onThemeChanged = { newTheme -> currentTheme = newTheme },
                onTextScaleChanged = { newScale -> currentTextScale = newScale },
                onIsBoldChanged = { newIsBold -> currentIsBold = newIsBold }
            )
        }
    }
}

@Composable
fun PermissionStepScreen(
    title: String,
    description: String,
    icon: ImageVector,
    permissionsToRequest: Array<String>,
    onPermissionGranted: () -> Unit
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
            OutlinedButton(onClick = onPermissionGranted) { Text("Omitir", color = Color.White) }
            Button(
                onClick = { permissionLauncher.launch(permissionsToRequest) },
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
    onThemeChanged: (DashboardTheme) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onIsBoldChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    var isMapExpanded by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }

    var activeAppDrawerTarget by remember { mutableIntStateOf(0) }

    var currentSpeedKmH by remember { mutableFloatStateOf(0f) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
    var isMediaVideoMode by remember { mutableStateOf(false) }

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
                MapContainerWidget()

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.dashBackground)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // COLUMNA IZQUIERDA
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModernDashboardCard(
                            modifier = Modifier.weight(1.2f),
                            title = "NAVEGACIÓN GPS",
                            icon = Icons.Default.Map,
                            headerAction = {
                                IconButton(onClick = { isMapExpanded = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Expandir Mapa", tint = theme.accentCyan)
                                }
                            }
                        ) {
                            MapContainerWidget()
                        }

                        ModernDashboardCard(
                            modifier = Modifier.weight(1f),
                            title = null
                        ) {
                            ModernSpeedometerWidget(
                                speedKmH = currentSpeedKmH,
                                bearing = currentBearing,
                                onRequestAppSelection = { slot ->
                                    activeAppDrawerTarget = slot
                                }
                            )
                        }
                    }

                    // COLUMNA DERECHA
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModernDashboardCard(
                            modifier = Modifier.weight(1.2f),
                            title = if (isMediaVideoMode) "REPRODUCTOR DE VIDEO" else "REPRODUCTOR DE MÚSICA",
                            icon = if (isMediaVideoMode) Icons.Default.Movie else Icons.Default.MusicNote,
                            headerAction = {
                                TextButton(onClick = { isMediaVideoMode = !isMediaVideoMode }) {
                                    Text(
                                        text = if (isMediaVideoMode) "Ver Música" else "Ver Video",
                                        color = theme.accentCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        ) {
                            ModernMediaPlayerWidget(isVideoMode = isMediaVideoMode)
                        }

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernDashboardCard(
                                modifier = Modifier.weight(1f),
                                title = "SISTEMA",
                                icon = Icons.Default.Schedule
                            ) {
                                ModernClockWidget()
                            }

                            ModernDashboardCard(
                                modifier = Modifier.weight(1.1f),
                                title = "APLICACIONES",
                                icon = Icons.Default.Apps,
                                headerAction = {
                                    IconButton(onClick = { activeAppDrawerTarget = 99 }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Launch, contentDescription = "Ver Todas", tint = theme.accentCyan)
                                    }
                                }
                            ) {
                                ModernAppShortcutsWidget(context)
                            }
                        }
                    }
                }

                // BOTÓN FLOTANTE "MUNDO DE COLORES" EN LA ESQUINA INFERIOR IZQUIERDA
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

        // PANTALLA COMPLETA DEL CAJÓN DE APPS DEL SISTEMA
        if (activeAppDrawerTarget != 0) {
            FullscreenAppDrawerWidget(
                title = if (activeAppDrawerTarget == 99) "TODAS LAS APLICACIONES" else "SELECCIONAR APP PARA SLOT $activeAppDrawerTarget",
                onClose = { activeAppDrawerTarget = 0 },
                onAppSelected = { selectedPackage ->
                    if (activeAppDrawerTarget in 1..2) {
                        val prefs = context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("slot_$activeAppDrawerTarget", selectedPackage).apply()
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedPackage)
                        if (launchIntent != null) context.startActivity(launchIntent)
                    }
                    activeAppDrawerTarget = 0
                }
            )
        }

        // MODAL PERSONALIZADOR DE COLORES Y TAMAÑO DE TEXTO
        if (showThemeModal) {
            ThemeSelectorModal(
                currentTheme = currentTheme,
                currentTextScale = currentTextScale,
                currentIsBold = currentIsBold,
                onDismiss = { showThemeModal = false },
                onThemeSelected = { newTheme ->
                    onThemeChanged(newTheme)
                },
                onTextScaleChanged = { newScale ->
                    onTextScaleChanged(newScale)
                },
                onIsBoldChanged = { newIsBold ->
                    onIsBoldChanged(newIsBold)
                }
            )
        }
    }
}