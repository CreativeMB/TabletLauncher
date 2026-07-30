package com.creativem.toblauncher

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.mapsforge.map.layer.overlay.Marker
import android.graphics.BitmapFactory

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Looper
import com.google.android.gms.location.*

// Importaciones oficiales de Mapsforge
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidBitmap

import android.content.Intent

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher


@SuppressLint("RememberReturnType")
@Composable
fun MapsforgeWidget() {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    remember {
        try {
            AndroidGraphicFactory.createInstance(context.applicationContext)
        } catch (e: Exception) {}
    }

    val targetMapFile = remember { OfflineMapManager.getMapFile(context) }
    val targetPoiFile = remember { OfflineMapManager.getPoiFile(context) }

    var isMapAvailable by remember { mutableStateOf(OfflineMapManager.isMapDownloaded(context)) }
    var isPoiAvailable by remember { mutableStateOf(OfflineMapManager.isPoiDownloaded(context)) }

    var isLoadingFile by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    // Estado para controlar la alerta si la radio no tiene explorador de archivos
    var showNoFileManagerError by remember { mutableStateOf(false) }

    // Estado para saber si el mapa debe centrar automáticamente al auto
    var isAutoCenterEnabled by remember { mutableStateOf(true) }
    var lastKnownLocation by remember { mutableStateOf<LatLong?>(null) }
    var currentMapView by remember { mutableStateOf<MapView?>(null) }

    // FUNCIÓN SEGURA PARA EVITAR CRASHES EN RADIOS CHINAS / TABLETS
    // 1. La función segura ahora trabaja con String directo
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

// 2. Launcher para el mapa .map usando GetContent
    val mapPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            loadingMessage = "Cargando mapa en la memoria del auto..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Intentar obtener persistencia si la fuente lo soporta
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

// 3. Launcher para el archivo .poi usando GetContent
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
        if (isLoadingFile) {
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
        } else if (isMapAvailable) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->

                        val mapView = MapView(ctx)
                        currentMapView = mapView

                        mapView.setZoomLevelMin(3.toByte())
                        mapView.setZoomLevelMax(20.toByte())

                        mapView.model.mapViewPosition.setMapPosition(
                            MapPosition(
                                LatLong(4.6018403, -74.0796899),
                                19.toByte()
                            )
                        )

                        val mapFile = org.mapsforge.map.reader.MapFile(targetMapFile)

                        val tileCache = AndroidUtil.createTileCache(
                            ctx,
                            "mapcache",
                            mapView.model.displayModel.tileSize,
                            1f,
                            mapView.model.frameBufferModel.overdrawFactor
                        )

                        val rendererLayer =
                            org.mapsforge.map.layer.renderer.TileRendererLayer(
                                tileCache,
                                mapFile,
                                mapView.model.mapViewPosition,
                                AndroidGraphicFactory.INSTANCE
                            )

                        rendererLayer.setXmlRenderTheme(
                            org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT
                        )

                        mapView.layerManager.layers.add(rendererLayer)

                        val originalBitmap = BitmapFactory.decodeResource(
                            ctx.resources,
                            R.drawable.car
                        )

                        val targetWidth = 120
                        val targetHeight = 120
                        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                        val mapsforgeDrawable = AndroidBitmap(scaledBitmap)

                        val cartMarker = Marker(
                            LatLong(4.5709, -74.2973),
                            mapsforgeDrawable,
                            0,
                            0
                        )

                        mapView.layerManager.layers.add(cartMarker)

                        startLocationTracking(
                            ctx,
                            mapView,
                            cartMarker
                        ) { newLatLong ->
                            lastKnownLocation = newLatLong
                            if (isAutoCenterEnabled) {
                                mapView.model.mapViewPosition.center = newLatLong
                            }
                        }

                        var autoCenterJob: kotlinx.coroutines.Job? = null

                        mapView.setOnTouchListener { _, event ->
                            if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                                isAutoCenterEnabled = false
                                autoCenterJob?.cancel()

                                autoCenterJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(5000L)
                                    isAutoCenterEnabled = true
                                    lastKnownLocation?.let { loc ->
                                        mapView.model.mapViewPosition.center = loc
                                        mapView.repaint()
                                    }
                                }
                            }
                            false
                        }

                        mapView.repaint()
                        mapView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // BOTONES FLOTANTES DE CONTROL
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
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        }

                        FloatingActionButton(
                            onClick = {
                                isAutoCenterEnabled = true
                                lastKnownLocation?.let { location ->
                                    currentMapView?.model?.mapViewPosition?.center = location
                                    currentMapView?.repaint()
                                }
                            },
                            containerColor = if (isAutoCenterEnabled) Color(0xFF03DAC5) else Color(0xAA1E1E1E),
                            contentColor = if (isAutoCenterEnabled) Color.Black else Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Centrar Auto", modifier = Modifier.size(20.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF2D2D2D))
                    ) {
                        DropdownMenuItem(
                            text = { Text("✅ Cambiar archivo .map", color = Color(0xFF03DAC5)) },
                            onClick = {
                                showMenu = false
                                safeLaunchPicker(mapPickerLauncher) {
                                    showNoFileManagerError = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isPoiAvailable) "✅ Cambiar archivo .poi" else "➕ Cargar archivo .poi",
                                    color = if (isPoiAvailable) Color(0xFF03DAC5) else Color.White
                                )
                            },
                            onClick = {
                                showMenu = false
                                safeLaunchPicker(poiPickerLauncher) {
                                    showNoFileManagerError = true
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Pantalla si no hay mapa
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
                        safeLaunchPicker(mapPickerLauncher) {
                            showNoFileManagerError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
                ) {
                    Text("Seleccionar archivo .map", color = Color.Black)
                }
            }
        }

        // ALERTA DE ERROR SI LA TABLET / RADIO NO TIENE EXPLORADOR NATIVO
        if (showNoFileManagerError) {
            AlertDialog(
                onDismissRequest = { showNoFileManagerError = false },
                title = { Text("Explorador no disponible") },
                text = { Text("Esta pantalla o radio no cuenta con un selector de archivos compatible instalados en el sistema.") },
                confirmButton = {
                    Button(onClick = { showNoFileManagerError = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}

@Composable
fun MapContainerWidget() {
    val context = LocalContext.current

    // Estado para controlar qué mapa se muestra: 0 = Offline (Mapsforge), 1 = Google Maps, 2 = Waze
    var selectedMapMode by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // BARRA SUPERIOR SELECCIONADORA DE MAPAS
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Botón 1: Mapa Offline (Mapsforge)
                FilterChip(
                    selected = selectedMapMode == 0,
                    onClick = { selectedMapMode = 0 },
                    label = { Text("🗺️ Offline") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF03DAC5),
                        selectedLabelColor = Color.Black
                    )
                )

                // Botón 2: Google Maps
                FilterChip(
                    selected = selectedMapMode == 1,
                    onClick = { selectedMapMode = 1 },
                    label = { Text("📍 Google Maps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF03DAC5),
                        selectedLabelColor = Color.Black
                    )
                )

                // Botón 3: Waze
                FilterChip(
                    selected = selectedMapMode == 2,
                    onClick = { selectedMapMode = 2 },
                    label = { Text("🚗 Waze") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF03DAC5),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        // ==========================================
        // CONTENIDO SEGÚN LA SELECCIÓN DEL USUARIO
        // ==========================================
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedMapMode) {
                0 -> {
                    // MUESTRA NUESRO NAVEGADOR OFFLINE NATIVO (Mapsforge)
                    MapsforgeWidget()
                }
                1 -> {
                    // MUESTRA GOOGLE MAPS (Obre la web o lanza la app si está instalada)
                    WebOrAppMapWidget(
                        webUrl = "https://www.google.com/maps",
                        packageName = "com.google.android.apps.maps",
                        appName = "Google Maps"
                    )
                }
                2 -> {
                    // MUESTRA WAZE (Abre la web o lanza la app si está instalada)
                    WebOrAppMapWidget(
                        webUrl = "https://www.waze.com/live-map",
                        packageName = "com.waze",
                        appName = "Waze"
                    )
                }
            }
        }
    }
}

@Composable
fun WebOrAppMapWidget(webUrl: String, packageName: String, appName: String) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Carga la versión Web interactiva dentro del recuadro
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(webUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón flotante para abrir la App Oficial si la tiene instalada en la radio china
        SmallFloatingActionButton(
            onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    // Si no tiene Waze o Google Maps instalado en la radio, abre la tienda o el navegador
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
            Text("Abrir App de $appName", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
@SuppressLint("MissingPermission")
fun startLocationTracking(
    context: android.content.Context,
    mapView: MapView,
    cartMarker: Marker,
    onLocationUpdated: (LatLong) -> Unit
): LocationCallback {

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    ).setMinUpdateDistanceMeters(2f).build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val newLatLong = LatLong(location.latitude, location.longitude)

            // Actualizamos la posición del marcador del auto
            cartMarker.latLong = newLatLong

            // Reportamos la posición al escuchador para el centrado automático
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