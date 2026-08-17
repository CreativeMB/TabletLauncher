package com.creativem.toblauncher

import android.graphics.Color
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Polyline

class RouteLayerManager(private val mapView: MapView) {
    private val activePolylines = mutableListOf<Polyline>()

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
        primaryColorInt: Int = Color.parseColor("#FF03DAC5"),   // 1er tono (theme.accentCyan)
        secondaryColorInt: Int = Color.parseColor("#FF7C4DFF"), // 2do tono (theme.accentPurple)
        accentColorInt: Int = Color.parseColor("#FFFF9100")     // 3er tono (theme.accentOrange)
    ) {
        clearRoutes()
        val nonSelected = routes.filter { it.id != selectedRouteId }
        val selected = routes.find { it.id == selectedRouteId }

        // 1. Alternativas secundarias (2do Tono translúcido)
        for (route in nonSelected) {
            val altColor = Color.argb(
                150,
                Color.red(secondaryColorInt),
                Color.green(secondaryColorInt),
                Color.blue(secondaryColorInt)
            )
            val polyline = Polyline(createPaint(altColor, 7f), AndroidGraphicFactory.INSTANCE)
            polyline.latLongs.addAll(route.points)
            activePolylines.add(polyline)
            mapView.layerManager.layers.add(polyline)
        }

        // 2. Ruta seleccionada (Borde oscuro + 1er Tono neón)
        selected?.let { route ->
            val outline = Polyline(
                createPaint(Color.parseColor("#E6060A10"), 14f),
                AndroidGraphicFactory.INSTANCE
            )
            outline.latLongs.addAll(route.points)
            activePolylines.add(outline)
            mapView.layerManager.layers.add(outline)

            val mainLine = Polyline(
                createPaint(primaryColorInt, 9f),
                AndroidGraphicFactory.INSTANCE
            )
            mainLine.latLongs.addAll(route.points)
            activePolylines.add(mainLine)
            mapView.layerManager.layers.add(mainLine)
        }

        mapView.layerManager.redrawLayers()
        mapView.repaint()
    }

    fun clearRoutes() {
        for (pl in activePolylines) {
            mapView.layerManager.layers.remove(pl)
        }
        activePolylines.clear()
        mapView.layerManager.redrawLayers()
        mapView.repaint()
    }
}