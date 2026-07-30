package com.creativem.toblauncher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
// =====================================================================
        // FORZAR A QUE LA PANTALLA DE LA TABLET ESTÉ SIEMPRE ENCENDIDA (GLOBAL)
        // =====================================================================
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // RECLAMAR EL 100% DE LA PANTALLA (Modo Inmersivo)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Inicializamos los gráficos de Mapsforge de forma segura
        try {
            AndroidGraphicFactory.createInstance(application)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Solicitar selección como Launcher por defecto
        promptDefaultLauncherSelection(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
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
            // Método clásico fallback para versiones anteriores de Android
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

    // Determina síncronamente el paso inicial exacto para evitar el parpadeo de pantalla inicial
    val initialStep = remember {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        when {
            !hasLocation -> 1  // Ir directo a GPS
            !hasStorage -> 2   // Ir directo a Almacenamiento
            !hasMic -> 3       // Ir directo a Micrófono
            else -> 0          // Arrancar directo en el Dashboard
        }
    }

    var currentStep by remember { mutableStateOf(initialStep) }

    when (currentStep) {
        1 -> PermissionStepScreen(
            title = "Permiso de GPS",
            description = "Necesitamos tu ubicación exacta para que el mapa funcione correctamente.",
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
                description = "Permite el acceso al almacenamiento para leer música, videos y mapas.",
                icon = Icons.Default.LibraryMusic,
                permissionsToRequest = storagePermissions,
                onPermissionGranted = { currentStep = 3 }
            )
        }
        3 -> PermissionStepScreen(
            title = "Permiso de Micrófono",
            description = "Necesario para usar comandos de voz mientras conduces.",
            icon = Icons.Default.Mic,
            permissionsToRequest = arrayOf(Manifest.permission.RECORD_AUDIO),
            onPermissionGranted = { currentStep = 0 }
        )
        0 -> CarDashboard()
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
    ) {
        onPermissionGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color(0xFF03DAC5))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, color = Color.LightGray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onPermissionGranted) { Text("Omitir por ahora", color = Color.White) }
            Button(
                onClick = { permissionLauncher.launch(permissionsToRequest) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
            ) {
                Text("Conceder Permiso", color = Color.Black)
            }
        }
    }
}

@Composable
fun CarDashboard() {
    var isMapExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapExpanded) {
            // MODO PANTALLA COMPLETA TOTAL (Toma el 100% de la tablet)
            Box(modifier = Modifier.fillMaxSize()) {
                MapContainerWidget()

                IconButton(
                    onClick = { isMapExpanded = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            color = Color(0xCC1E1E1E),
                            shape = MaterialTheme.shapes.medium
                        )
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Salir de Pantalla Completa",
                        tint = Color(0xFF03DAC5),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // MODO DASHBOARD NORMAL (PANTALLA DIVIDIDA)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapContainerWidget()

                        IconButton(
                            onClick = { isMapExpanded = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    color = Color(0xAA1E1E1E),
                                    shape = MaterialTheme.shapes.small
                                )
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expandir Mapa",
                                tint = Color(0xFF03DAC5)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                                Text("Reproductor de Música", color = Color.White)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Reproductor de Video", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}