package com.creativem.toblauncher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
// ==========================================
// CLASE CONTENEDORA DE REFERENCIAS (Previene bucles de recomposición)
// ==========================================
class MapRefs {
    var mapView: MapView? = null
    var marker: Marker? = null
    var locationCallback: LocationCallback? = null
}

// ==========================================
// FUNCIÓN AUXILIAR PARA EL DIBUJADO DEL PULSO CONSTANTE
// ==========================================
fun createPulseBitmap(progress: Float): Bitmap {
    val size = 260 // Lienzo más grande para soportar el tamaño ampliado en Tablets
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = size / 2f
    val innerDotRadius = 38f // Punto azul central notablemente más grande para tablet
    val maxPulseRadius = 115f // Rango de expansión de señal extendido

    // Expansión del pulso
    val currentPulseRadius = innerDotRadius + (progress * (maxPulseRadius - innerDotRadius))

    // Desvanecimiento del pulso
    val alpha = ((1.0f - progress) * 140).toInt().coerceIn(0, 255)

    // Pincel para el relleno del pulso (halo translúcido)
    val pulsePaint = Paint().apply {
        color = android.graphics.Color.argb(alpha, 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Pincel para la onda de señal
    val pulseStrokePaint = Paint().apply {
        color = android.graphics.Color.argb((alpha * 0.8f).toInt(), 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Pincel para el punto azul
    val dotPaint = Paint().apply {
        color = android.graphics.Color.rgb(66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Pincel para el contorno blanco de alto contraste
    val whiteBorderPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // 1. Dibujar pulso de señal
    canvas.drawCircle(center, center, currentPulseRadius, pulsePaint)
    canvas.drawCircle(center, center, currentPulseRadius, pulseStrokePaint)

    // 2. Dibujar indicador central
    canvas.drawCircle(center, center, innerDotRadius, dotPaint)
    canvas.drawCircle(center, center, innerDotRadius, whiteBorderPaint)

    return bitmap
}

// ==========================================
// COMPOSABLE DEL MAPA OFFLINE (SÓLO DIBUJADO Y GPS)
// ==========================================
@SuppressLint("RememberReturnType")
@Composable
fun MapsforgeWidget(
    mapRefs: MapRefs,
    isMapAvailable: Boolean,
    isAutoCenterEnabled: Boolean,
    targetMapFile: File,
    onLocationUpdated: (LatLong) -> Unit,
    mapPickerLauncher: ActivityResultLauncher<String>,
    onNoFileManagerError: () -> Unit
) {
    val context = LocalContext.current

    // Animación de pulso infinito y constante
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "PulseProgress"
    )

    // Efecto para animar el pulso dinámicamente
    LaunchedEffect(pulseProgress) {
        val mapView = mapRefs.mapView
        val marker = mapRefs.marker
        if (mapView != null && marker != null) {
            val updatedBitmap = createPulseBitmap(pulseProgress)
            marker.bitmap = AndroidBitmap(updatedBitmap)
            mapView.repaint()
        }
    }

    // LIMPIEZA ABSOLUTA DE RECURSOS SÓLO CUANDO EL WIDGET SE DESTRUYE COMPLETAMENTE (Key: Unit)
    DisposableEffect(Unit) {
        onDispose {
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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        setZoomLevelMin(3.toByte())
                        setZoomLevelMax(22.toByte())
                        model.mapViewPosition.setMapPosition(
                            MapPosition(LatLong(4.6018403, -74.0796899), 19.toByte()) // Zoom 19 (más de cerca)
                        )
                    }

                    try {
                        mapView.mapScaleBar.setVisible(false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val mapFile = org.mapsforge.map.reader.MapFile(targetMapFile)

                    val tileCache = AndroidUtil.createTileCache(
                        ctx,
                        "mapcache",
                        mapView.model.displayModel.tileSize,
                        1f,
                        mapView.model.frameBufferModel.overdrawFactor
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

                    val initialBitmap = createPulseBitmap(0f)
                    val mapsforgeDrawable = AndroidBitmap(initialBitmap)

                    val cartMarker = Marker(
                        LatLong(4.6018403, -74.0796899),
                        mapsforgeDrawable,
                        0,
                        0
                    )

                    mapView.layerManager.layers.add(cartMarker)

                    // Iniciar el GPS inmediatamente al crear el mapa
                    val callback = startLocationTracking(ctx, mapView, cartMarker) { newLatLong ->
                        onLocationUpdated(newLatLong)
                        if (isAutoCenterEnabled) {
                            mapView.model.mapViewPosition.center = newLatLong
                        }
                    }

                    // Guardamos las referencias en el objeto mutable sin disparar recomposiciones
                    mapRefs.mapView = mapView
                    mapRefs.marker = cartMarker
                    mapRefs.locationCallback = callback

                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Pantalla si no hay mapa offline configurado
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
// CONTENEDOR MAESTRO DE MAPAS CON MENÚ INTEGRADO
// ==========================================
@Composable
fun MapContainerWidget() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 0 = Offline, 1 = Google Maps, 2 = Waze
    var selectedMapMode by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val targetMapFile = remember { OfflineMapManager.getMapFile(context) }
    val targetPoiFile = remember { OfflineMapManager.getPoiFile(context) }

    var isMapAvailable by remember { mutableStateOf(false) }
    var isPoiAvailable by remember { mutableStateOf(false) }
    var isCheckingFiles by remember { mutableStateOf(true) }

    var isLoadingFile by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var showNoFileManagerError by remember { mutableStateOf(false) }

    var isAutoCenterEnabled by remember { mutableStateOf(true) }
    var lastKnownLocation by remember { mutableStateOf<LatLong?>(null) }

    val mapRefs = remember { MapRefs() }

    // Comprobación inicial de archivos en segundo plano
    LaunchedEffect(Unit) {
        isMapAvailable = withContext(Dispatchers.IO) {
            OfflineMapManager.isMapDownloaded(context)
        }
        isPoiAvailable = withContext(Dispatchers.IO) {
            OfflineMapManager.isPoiDownloaded(context)
        }
        isCheckingFiles = false
    }

    fun safeLaunchPicker(
        launcher: ActivityResultLauncher<String>,
        onNotFound: () -> Unit
    ) {
        try {
            launcher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            onNotFound()
        } catch (e: Exception) {
            e.printStackTrace()
            onNotFound()
        }
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            loadingMessage = "Cargando mapa en la memoria del auto..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        FileOutputStream(targetMapFile).use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        if (OfflineMapManager.isMapDownloaded(context)) {
                            isMapAvailable = true
                        }
                        isLoadingFile = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { isLoadingFile = false }
                }
            }
        }
    }

    val poiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            loadingMessage = "Cargando Puntos de Interés (POI)..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        FileOutputStream(targetPoiFile).use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        if (OfflineMapManager.isPoiDownloaded(context)) {
                            isPoiAvailable = true
                        }
                        isLoadingFile = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { isLoadingFile = false }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCheckingFiles) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)))
        } else if (isLoadingFile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF03DAC5))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = loadingMessage, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Renderizado del mapa según selección
                when (selectedMapMode) {
                    0 -> MapsforgeWidget(
                        mapRefs = mapRefs,
                        isMapAvailable = isMapAvailable,
                        isAutoCenterEnabled = isAutoCenterEnabled,
                        targetMapFile = targetMapFile,
                        onLocationUpdated = { lastKnownLocation = it },
                        mapPickerLauncher = mapPickerLauncher,
                        onNoFileManagerError = { showNoFileManagerError = true }
                    )
                    1 -> WebOrAppMapWidget(
                        webUrl = "https://www.google.com/maps",
                        packageName = "com.google.android.apps.maps",
                        appName = "Google Maps"
                    )
                    2 -> WebOrAppMapWidget(
                        webUrl = "https://www.waze.com/live-map",
                        packageName = "com.waze",
                        appName = "Waze"
                    )
                }

                // Fondo traslúcido de enfoque (al hacer clic en él se cierra el menú lateral)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable( // <-- AQUÍ ESTÁ LA CORRECCIÓN
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = false }
                    )
                }

                // Botones flotantes laterales
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingActionButton(
                            onClick = { showMenu = !showMenu },
                            containerColor = Color(0xAA1E1E1E),
                            contentColor = Color(0xFF03DAC5),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración", modifier = Modifier.size(20.dp))
                        }

                        if (selectedMapMode == 0 && isMapAvailable) {
                            FloatingActionButton(
                                onClick = {
                                    isAutoCenterEnabled = true
                                    lastKnownLocation?.let { location ->
                                        mapRefs.mapView?.model?.mapViewPosition?.center = location
                                        mapRefs.mapView?.repaint()
                                    }
                                },
                                containerColor = if (isAutoCenterEnabled) Color(0xFF03DAC5) else Color(0xAA1E1E1E),
                                contentColor = if (isAutoCenterEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Centrar Auto", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // ==========================================
                // MENÚ LATERAL MODERNO (DOCKADO A LA PARED IZQUIERDA)
                // ==========================================
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Navegación",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Items del Menú Estilo Panel de Control
                            PanelMenuItem(
                                text = "🗺️ Mapa Offline",
                                isSelected = selectedMapMode == 0,
                                onClick = {
                                    selectedMapMode = 0
                                    showMenu = false
                                }
                            )

                            PanelMenuItem(
                                text = "📍 Google Maps",
                                isSelected = selectedMapMode == 1,
                                onClick = {
                                    selectedMapMode = 1
                                    showMenu = false
                                }
                            )

                            PanelMenuItem(
                                text = "🚗 Waze",
                                isSelected = selectedMapMode == 2,
                                onClick = {
                                    selectedMapMode = 2
                                    showMenu = false
                                }
                            )

                            // Ajustes de archivos de Mapas (Sólo visibles si el modo Offline está seleccionado)
                            if (selectedMapMode == 0) {
                                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                                Button(
                                    onClick = {
                                        showMenu = false
                                        safeLaunchPicker(mapPickerLauncher) {
                                            showNoFileManagerError = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A03DAC5), contentColor = Color(0xFF03DAC5)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📁 Cambiar .map")
                                }

                                Button(
                                    onClick = {
                                        showMenu = false
                                        safeLaunchPicker(poiPickerLauncher) {
                                            showNoFileManagerError = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPoiAvailable) Color(0x1A03DAC5) else Color(0x1AFFFFFF),
                                        contentColor = if (isPoiAvailable) Color(0xFF03DAC5) else Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isPoiAvailable) "📁 Cambiar .poi" else "➕ Cargar .poi")
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(
                                onClick = { showMenu = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Cerrar", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        if (showNoFileManagerError) {
            AlertDialog(
                onDismissRequest = { showNoFileManagerError = false },
                title = { Text("Explorador no disponible") },
                text = { Text("Esta pantalla o radio no cuenta con un selector de archivos compatible instalado en el sistema.") },
                confirmButton = {
                    Button(onClick = { showNoFileManagerError = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}

// ==========================================
// COMPOSABLE AUXILIAR PARA DISEÑO DE BOTONES DEL MENÚ
// ==========================================
@Composable
private fun PanelMenuItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0x3303DAC5) else Color.Transparent,
            contentColor = if (isSelected) Color(0xFF03DAC5) else Color.White
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
// ==========================================
// COMPOSABLE DE MAPAS WEB (WEBVIEW)
// ==========================================
@Composable
fun WebOrAppMapWidget(webUrl: String, packageName: String, appName: String) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setGeolocationEnabled(true)
                    }
                    webViewClient = WebViewClient()
                    loadUrl(webUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        SmallFloatingActionButton(
            onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                    context.startActivity(webIntent)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = Color(0xFF03DAC5),
            contentColor = Color.Black
        ) {
            Text(
                text = "Abrir App de $appName",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ==========================================
// FUNCIÓN DE RASTREO GPS
// ==========================================
@SuppressLint("MissingPermission")
fun startLocationTracking(
    context: Context,
    mapView: MapView,
    cartMarker: Marker,
    onLocationUpdated: (LatLong) -> Unit
): LocationCallback {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1500L
    ).apply {
        setMinUpdateDistanceMeters(2f)
    }.build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val newLatLong = LatLong(location.latitude, location.longitude)

            cartMarker.latLong = newLatLong
            onLocationUpdated(newLatLong)
            mapView.repaint()
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )

    return locationCallback
}