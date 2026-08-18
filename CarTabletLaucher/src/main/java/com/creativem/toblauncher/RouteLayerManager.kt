package com.creativem.toblauncher

import android.graphics.Color
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline

class RouteLayerManager(private val mapView: MapView) {
    private val activePolylines = mutableListOf<Polyline>()

    private var selectedOutline: Polyline? = null
    private var selectedMainLine: Polyline? = null
    private val altPolylines = mutableListOf<Polyline>()

    private fun getMainStrokeWidth(zoom: Int): Float = when {
        zoom >= 17 -> 16f
        zoom in 14..16 -> 11f
        zoom in 11..13 -> 7f
        else -> 4f
    }

    private fun getOutlineStrokeWidth(zoom: Int): Float = when {
        zoom >= 17 -> 26f
        zoom in 14..16 -> 18f
        zoom in 11..13 -> 11f
        else -> 6f
    }

    private fun getAltStrokeWidth(zoom: Int): Float = when {
        zoom >= 17 -> 10f
        zoom in 14..16 -> 7f
        zoom in 11..13 -> 5f
        else -> 3f
    }

    private fun createPaint(colorInt: Int, strokeWidth: Float): Paint {
        val paint = AndroidGraphicFactory.INSTANCE.createPaint()
        paint.color = colorInt
        paint.strokeWidth = strokeWidth
        paint.setStyle(Style.STROKE)
        return paint
    }

    fun renderRoutes(
        routes: List<RouteOption>,
        selectedRouteId: Int,
        primaryColorInt: Int = Color.parseColor("#FF03DAC5"),   // theme.accentCyan
        secondaryColorInt: Int = Color.parseColor("#FF7C4DFF"), // theme.accentPurple
        accentColorInt: Int = Color.parseColor("#FFFF9100")
    ) {
        clearRoutes()
        if (routes.isEmpty()) return

        val currentZoom = mapView.model.mapViewPosition.zoomLevel.toInt()
        val nonSelected = routes.filter { it.id != selectedRouteId }
        val selected = routes.find { it.id == selectedRouteId }

        // 1. Rutas alternativas secundarias
        for (route in nonSelected) {
            val altColor = Color.argb(
                140,
                Color.red(secondaryColorInt),
                Color.green(secondaryColorInt),
                Color.blue(secondaryColorInt)
            )
            val polyline = Polyline(createPaint(altColor, getAltStrokeWidth(currentZoom)), AndroidGraphicFactory.INSTANCE)
            polyline.latLongs.addAll(route.points)
            activePolylines.add(polyline)
            altPolylines.add(polyline)
            insertBelowMarkers(polyline)
        }

        // 2. Ruta seleccionada
        selected?.let { route ->
            val outline = Polyline(
                createPaint(Color.parseColor("#E6060A10"), getOutlineStrokeWidth(currentZoom)),
                AndroidGraphicFactory.INSTANCE
            )
            outline.latLongs.addAll(route.points)
            activePolylines.add(outline)
            selectedOutline = outline
            insertBelowMarkers(outline)

            val mainLine = Polyline(
                createPaint(primaryColorInt, getMainStrokeWidth(currentZoom)),
                AndroidGraphicFactory.INSTANCE
            )
            mainLine.latLongs.addAll(route.points)
            activePolylines.add(mainLine)
            selectedMainLine = mainLine
            insertBelowMarkers(mainLine)
        }

        mapView.layerManager.redrawLayers()
        mapView.repaint()
    }

    /**
     * 🎨 CAMBIO DE TEMA EN TIEMPO REAL (Actualiza colores de inmediato)
     */
    fun updateThemeColors(primaryColorInt: Int, secondaryColorInt: Int) {
        selectedMainLine?.paintStroke?.color = primaryColorInt

        val altColor = Color.argb(
            140,
            Color.red(secondaryColorInt),
            Color.green(secondaryColorInt),
            Color.blue(secondaryColorInt)
        )
        for (alt in altPolylines) {
            alt.paintStroke?.color = altColor
        }

        mapView.layerManager.redrawLayers()
        mapView.repaint()
    }

    /**
     * 🚀 Actualización en vivo durante la marcha
     */
    fun updateActiveRouteProgress(
        remainingPoints: List<LatLong>,
        currentColorInt: Int? = null
    ) {
        if (remainingPoints.isEmpty() || selectedMainLine == null) return

        val currentZoom = mapView.model.mapViewPosition.zoomLevel.toInt()

        selectedOutline?.paintStroke?.strokeWidth = getOutlineStrokeWidth(currentZoom)
        selectedMainLine?.paintStroke?.strokeWidth = getMainStrokeWidth(currentZoom)

        currentColorInt?.let { color ->
            selectedMainLine?.paintStroke?.color = color
        }

        selectedOutline?.let { outline ->
            synchronized(outline.latLongs) {
                outline.latLongs.clear()
                outline.latLongs.addAll(remainingPoints)
            }
        }

        selectedMainLine?.let { mainLine ->
            synchronized(mainLine.latLongs) {
                mainLine.latLongs.clear()
                mainLine.latLongs.addAll(remainingPoints)
            }
        }

        mapView.layerManager.redrawLayers() // 👈 Fundamental para aplicar color en Mapsforge
        mapView.repaint()
    }

    private fun insertBelowMarkers(polyline: Polyline) {
        val layers = mapView.layerManager.layers
        val firstMarkerIndex = layers.indexOfFirst { it is Marker }

        if (firstMarkerIndex != -1) {
            layers.add(firstMarkerIndex, polyline)
        } else {
            layers.add(polyline)
        }
    }

    fun clearRoutes() {
        for (pl in activePolylines) {
            mapView.layerManager.layers.remove(pl)
        }
        activePolylines.clear()
        altPolylines.clear()
        selectedOutline = null
        selectedMainLine = null
        mapView.layerManager.redrawLayers()
        mapView.repaint()
    }
}