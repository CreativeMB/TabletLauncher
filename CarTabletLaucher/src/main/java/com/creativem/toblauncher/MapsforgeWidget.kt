package com.creativem.toblauncher

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==========================================
// CLASE CONTENEDORA DE REFERENCIAS
// ==========================================
class MapRefs {
    var mapView: MapView? = null
    var marker: Marker? = null
    var locationCallback: LocationCallback? = null
    var currentAnimator: ValueAnimator? = null
}

// ==========================================
// DIBUJADO DEL INDICADOR GPS
// ==========================================
fun createStaticGpsBitmap(): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val innerDotRadius = 14f

    val pulsePaint = Paint().apply {
        color = android.graphics.Color.argb(70, 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val pulseStrokePaint = Paint().apply {
        color = android.graphics.Color.argb(120, 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    val dotPaint = Paint().apply {
        color = android.graphics.Color.rgb(66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val whiteBorderPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    canvas.drawCircle(center, center, 30f, pulsePaint)
    canvas.drawCircle(center, center, 30f, pulseStrokePaint)
    canvas.drawCircle(center, center, innerDotRadius, dotPaint)
    canvas.drawCircle(center, center, innerDotRadius, whiteBorderPaint)

    return bitmap
}

// ==========================================
// COMPOSABLE DEL MAPA (OPTIMIZADO PARA GPU)
// ==========================================
@SuppressLint("RememberReturnType")
@Composable
fun MapsforgeWidget(
    mapRefs: MapRefs,
    isMapAvailable: Boolean,
    isAutoCenterEnabled: Boolean,
    isNightMode: Boolean,
    targetMapFile: File,
    onLocationUpdated: (LatLong) -> Unit,
    mapPickerLauncher: ActivityResultLauncher<String>,
    onNoFileManagerError: () -> Unit,
    onMapLoadError: (String) -> Unit, // Callback para reportar fallos de lectura del .map
    onClose: () -> Unit = {}
) {
    // 👈 2. Y AQUÍ USAS EL BACKHANDLER LLAMANDO A onClose()
    BackHandler {
        onClose()
    }
    val context = LocalContext.current
    val currentAutoCenterEnabled by rememberUpdatedState(isAutoCenterEnabled)

    // Filtro de Noche de Alto Contraste
    LaunchedEffect(isNightMode) {
        val mapView = mapRefs.mapView ?: return@LaunchedEffect
        if (isNightMode) {
            val paint = Paint()
            val matrix = ColorMatrix(floatArrayOf(
                -0.8f,  0f,    0f,    0f, 245f,
                0f,   -0.8f,  0f,    0f, 245f,
                0f,    0f,   -0.8f,  0f, 255f,
                0f,    0f,    0f,    1f,   0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            mapView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        mapView.repaint()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRefs.currentAnimator?.cancel()
            mapRefs.locationCallback?.let { callback ->
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.removeLocationUpdates(callback)
            }
            mapRefs.mapView?.let { mv ->
                try {
                    mv.destroyAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mapRefs.mapView = null
            mapRefs.marker = null
            mapRefs.locationCallback = null
            mapRefs.currentAnimator = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds().clip(RoundedCornerShape(16.dp))) {
        if (isMapAvailable) {
            AndroidView(
                factory = { ctx ->
                    try {
                        AndroidGraphicFactory.createInstance(ctx.applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val mapView = MapView(ctx).apply {
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        model.frameBufferModel.overdrawFactor = 1.3

                        scaleX = 1.35f
                        scaleY = 1.35f

                        setZoomLevelMin(3.toByte())
                        setZoomLevelMax(22.toByte())
                        model.mapViewPosition.setMapPosition(
                            MapPosition(LatLong(4.6018403, -74.0796899), 16.toByte())
                        )
                    }

                    try {
                        mapView.mapScaleBar.setVisible(false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Se realiza una carga segura para prevenir cierres inesperados (Magic Byte check)
                    val mapFile = try {
                        org.mapsforge.map.reader.MapFile(targetMapFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onMapLoadError("El archivo seleccionado no es válido (¿es un .zip comprimido o está dañado?)")
                        null
                    }

                    if (mapFile != null) {
                        val tileCache = AndroidUtil.createTileCache(
                            ctx,
                            "mapcache",
                            mapView.model.displayModel.tileSize,
                            1f,
                            1.3
                        )

                        val rendererLayer = org.mapsforge.map.layer.renderer.TileRendererLayer(
                            tileCache,
                            mapFile,
                            mapView.model.mapViewPosition,
                            AndroidGraphicFactory.INSTANCE
                        ).apply {
                            setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
                        }

                        mapView.layerManager.layers.add(rendererLayer)
                    }

                    val staticGpsBitmap = createStaticGpsBitmap()
                    val mapsforgeDrawable = AndroidBitmap(staticGpsBitmap)

                    val cartMarker = Marker(
                        LatLong(4.6018403, -74.0796899),
                        mapsforgeDrawable,
                        0,
                        0
                    )

                    mapView.layerManager.layers.add(cartMarker)

                    val callback = startSmoothLocationTracking(
                        ctx,
                        mapRefs,
                        cartMarker,
                        isAutoCenterSupplier = { currentAutoCenterEnabled },
                        onLocationUpdated = onLocationUpdated
                    )

                    mapRefs.mapView = mapView
                    mapRefs.marker = cartMarker
                    mapRefs.locationCallback = callback

                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )

            // ==========================================
            // BOTONES FLOTANTES DE ZOOM (+ y -) PEGADOS A LA DERECHA
            // ==========================================
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { mapRefs.mapView?.model?.mapViewPosition?.zoomIn() },
                    containerColor = Color(0xCC1E1E1E),
                    contentColor = Color.White,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Acercar", modifier = Modifier.size(24.dp))
                }

                FloatingActionButton(
                    onClick = { mapRefs.mapView?.model?.mapViewPosition?.zoomOut() },
                    containerColor = Color(0xCC1E1E1E),
                    contentColor = Color.White,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Alejar", modifier = Modifier.size(24.dp))
                }
            }

        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🗺️ Configura tus Mapas Offline", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Selecciona tu archivo principal de mapa con extensión '.map' para iniciar el navegador.", color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        try {
                            mapPickerLauncher.launch("*/*")
                        } catch (e: Exception) {
                            onNoFileManagerError()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
                ) {
                    Text("Seleccionar archivo .map", color = Color.Black)
                }
            }
        }
    }
}

// ==========================================
// CONTENEDOR PRINCIPAL DEL MAPA
// ==========================================
// ==========================================
// CONTENEDOR PRINCIPAL DEL MAPA Y NAVEGACIÓN
// ==========================================
// ==========================================
// CONTENEDOR PRINCIPAL DEL MAPA Y NAVEGACIÓN
// ==========================================
@Composable
fun MapContainerWidget(
    onExpandClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("toblauncher_prefs", Context.MODE_PRIVATE) }

    var isOnlineNavActive by remember {
        mutableStateOf(prefs.getBoolean("online_nav_active_mode", false))
    }

    var showMenu by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(prefs.getBoolean("night_mode", false)) }
    var isAutoCenterEnabled by remember { mutableStateOf(prefs.getBoolean("auto_center", true)) }

    val targetMapFile = remember { OfflineMapManager.getMapFile(context) }
    val targetPoiFile = remember { OfflineMapManager.getPoiFile(context) }

    var isMapAvailable by remember { mutableStateOf(false) }
    var isPoiAvailable by remember { mutableStateOf(false) }
    var isCheckingFiles by remember { mutableStateOf(true) }
    var isLoadingFile by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var showNoFileManagerError by remember { mutableStateOf(false) }

    var mapLoadError by remember { mutableStateOf<String?>(null) }
    var lastKnownLocation by remember { mutableStateOf<LatLong?>(null) }
    val mapRefs = remember { MapRefs() }

    LaunchedEffect(Unit) {
        isMapAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isMapDownloaded(context) }
        isPoiAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isPoiDownloaded(context) }
        isCheckingFiles = false
    }

    fun safeLaunchPicker(launcher: ActivityResultLauncher<String>, onNotFound: () -> Unit) {
        try {
            launcher.launch("*/*")
        } catch (e: Exception) {
            onNotFound()
        }
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            mapLoadError = null
            loadingMessage = "Cargando mapa en la memoria del auto..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(targetMapFile).use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    if (OfflineMapManager.isMapDownloaded(context)) {
                        isMapAvailable = true
                    }
                    isLoadingFile = false
                }
            }
        }
    }

    val poiPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            loadingMessage = "Cargando Puntos de Interés (POI)..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(targetPoiFile).use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    if (OfflineMapManager.isPoiDownloaded(context)) isPoiAvailable = true
                    isLoadingFile = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        if (isCheckingFiles) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)))
        } else if (isLoadingFile) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = theme.accentCyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = loadingMessage, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {

                if (isOnlineNavActive) {
                    NativeAppGaugeWidget(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MapsforgeWidget(
                        mapRefs = mapRefs,
                        isMapAvailable = isMapAvailable && (mapLoadError == null),
                        isAutoCenterEnabled = isAutoCenterEnabled,
                        isNightMode = isNightMode,
                        targetMapFile = targetMapFile,
                        onLocationUpdated = { loc -> lastKnownLocation = loc },
                        mapPickerLauncher = mapPickerLauncher,
                        onNoFileManagerError = { showNoFileManagerError = true },
                        onMapLoadError = { error -> mapLoadError = error }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = false }
                    )
                }

                // ==========================================
                // PANEL DE CONTROL (Configuración, GPS y Pantalla Completa)
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, bottom = 6.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                        // 1. BOTÓN DE MENÚ / CONFIGURACIÓN
                        FloatingActionButton(
                            onClick = { showMenu = !showMenu },
                            containerColor = Color(0xAA1E1E1E),
                            contentColor = theme.accentCyan,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración", modifier = Modifier.size(22.dp))
                        }

                        // 2. BOTÓN CENTRAR GPS (Solo disponible en mapa Offline)
                        if (!isOnlineNavActive && isMapAvailable && mapLoadError == null) {
                            FloatingActionButton(
                                onClick = {
                                    isAutoCenterEnabled = true
                                    prefs.edit().putBoolean("auto_center", true).apply()
                                    lastKnownLocation?.let { location ->
                                        mapRefs.mapView?.model?.mapViewPosition?.center = location
                                        mapRefs.mapView?.repaint()
                                    }
                                },
                                containerColor = if (isAutoCenterEnabled) theme.accentCyan else Color(0xAA1E1E1E),
                                contentColor = if (isAutoCenterEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Centrar Auto", modifier = Modifier.size(22.dp))
                            }
                        }

                        // 3. BOTÓN PANTALLA COMPLETA (Solo disponible en mapa Offline)
                        if (!isOnlineNavActive) {
                            FloatingActionButton(
                                onClick = onExpandClicked,
                                containerColor = Color(0xAA1E1E1E),
                                contentColor = theme.accentCyan,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // ==========================================
                // MENÚ LATERAL DINÁMICO
                // ==========================================
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().width(280.dp),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isOnlineNavActive) "Navegación Online" else "Configuración Offline",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 10.sp,
                                color = Color.White
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x0DFFFFFF), shape = MaterialTheme.shapes.medium)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                Button(
                                    onClick = {
                                        isOnlineNavActive = !isOnlineNavActive
                                        prefs.edit().putBoolean("online_nav_active_mode", isOnlineNavActive).apply()
                                        showMenu = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOnlineNavActive) theme.accentCyan else Color(0xFF33CCFF),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isOnlineNavActive) "🗺️ Volver a Mapa Offline" else "🌐 Navegación Online (Waze)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!isOnlineNavActive) {
                                    Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Modo Noche",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 8.sp
                                        )

                                        Switch(
                                            checked = isNightMode,
                                            onCheckedChange = {
                                                isNightMode = it
                                                prefs.edit().putBoolean("night_mode", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accentCyan)
                                        )
                                    }

                                    Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    Button(
                                        onClick = { safeLaunchPicker(mapPickerLauncher) { showNoFileManagerError = true } },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A03DAC5), contentColor = theme.accentCyan),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "📁 Mapa (.map)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 8.sp
                                        )
                                    }

                                    Button(
                                        onClick = { safeLaunchPicker(poiPickerLauncher) { showNoFileManagerError = true } },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPoiAvailable) Color(0x1A03DAC5) else Color(0x1AFFFFFF),
                                            contentColor = if (isPoiAvailable) theme.accentCyan else Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isPoiAvailable) "📁 Puntos (.poi)" else "➕ Cargar Puntos (.poi)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(onClick = { showMenu = false }, modifier = Modifier.align(Alignment.End)) {
                                Text("Cerrar", color = Color.Gray)
                            }
                        }
                    }
                }

                if (!isOnlineNavActive && mapLoadError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⚠️ Archivo no soportado", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF44336))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = mapLoadError ?: "",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { safeLaunchPicker(mapPickerLauncher) { showNoFileManagerError = true } },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
                        ) {
                            Text("Seleccionar otro archivo .map", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showNoFileManagerError) {
            AlertDialog(
                onDismissRequest = { showNoFileManagerError = false },
                title = { Text("Explorador no disponible") },
                text = { Text("Esta radio no cuenta con un explorador de archivos instalado.") },
                confirmButton = { Button(onClick = { showNoFileManagerError = false }) { Text("Aceptar") } }
            )
        }
    }
}
// ==========================================
// RASTREO GPS FLUIDO
// ==========================================
@SuppressLint("MissingPermission")
fun startSmoothLocationTracking(
    context: Context,
    mapRefs: MapRefs,
    cartMarker: Marker,
    isAutoCenterSupplier: () -> Boolean,
    onLocationUpdated: (LatLong) -> Unit
): LocationCallback {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    var lastLat = cartMarker.latLong.latitude
    var lastLng = cartMarker.latLong.longitude
    var lastBearing = 0f

    fun getShortestAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L).apply {
        setMinUpdateDistanceMeters(1.5f)
    }.build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val targetLat = location.latitude
            val targetLng = location.longitude

            val speedKmH = location.speed * 3.6f
            val targetBearing = if (location.hasBearing() && speedKmH > 1.5f) location.bearing else lastBearing

            mapRefs.currentAnimator?.cancel()

            val deltaBearing = getShortestAngleDelta(lastBearing, targetBearing)
            val startBearing = lastBearing

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1300L
                interpolator = LinearInterpolator()

                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    val currentLat = lastLat + (targetLat - lastLat) * fraction
                    val currentLng = lastLng + (targetLng - lastLng) * fraction
                    val currentBearing = startBearing + (deltaBearing * fraction)

                    val currentPos = LatLong(currentLat, currentLng)
                    cartMarker.latLong = currentPos
                    onLocationUpdated(currentPos)

                    val mapView = mapRefs.mapView
                    if (mapView != null) {
                        if (isAutoCenterSupplier()) {
                            mapView.model.mapViewPosition.center = currentPos
                            mapView.rotation = -currentBearing
                        } else {
                            mapView.rotation = 0f
                        }
                    }
                }
            }

            animator.start()
            mapRefs.currentAnimator = animator

            lastLat = targetLat
            lastLng = targetLng
            lastBearing = targetBearing
        }
    }

    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    return locationCallback
}