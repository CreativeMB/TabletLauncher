package com.creativem.toblauncher

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

// =========================================================================
// TARJETA DE RUTAS 100% SIMÉTRICA Y RESPONSIVA
// =========================================================================
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RouteOptionsCard(
    routes: List<RouteOption>,
    selectedRouteId: Int,
    theme: DashboardTheme,
    onRouteSelected: (Int) -> Unit,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedRoutes = remember(routes) {
        routes.sortedBy { it.distanceMeters }
    }

    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier.wrapContentHeight()
    ) {
        // Ancho adaptativo según la cantidad de rutas
        val dynamicMaxWidth = when (sortedRoutes.size) {
            1 -> 180.dp
            2 -> 240.dp
            else -> 320.dp
        }

        Card(
            modifier = Modifier
                .wrapContentHeight()
                .widthIn(min = 140.dp, max = dynamicMaxWidth),
            colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.96f)),
            border = BorderStroke(1.dp, theme.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. FILA DE PASTILLAS DE RUTAS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (sortedRoutes.size > 2) Modifier.horizontalScroll(scrollState) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sortedRoutes.forEachIndexed { index, route ->
                        val isSelected = (route.id == selectedRouteId)
                        val label = when (index) {
                            0 -> "Princ."
                            1 -> "Alt 1"
                            else -> "Alt 2"
                        }

                        // Si hay 1 o 2 rutas, ocupan todo el ancho proporcional (weight). Si son 3, usan ancho fijo con scroll.
                        val itemModifier = if (sortedRoutes.size <= 2) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.widthIn(min = 95.dp)
                        }

                        Surface(
                            color = if (isSelected) theme.accentCyan.copy(alpha = 0.22f) else theme.dashBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) theme.accentCyan else theme.cardBorder
                            ),
                            modifier = itemModifier.clickable { onRouteSelected(route.id) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = route.formattedDistance,
                                    color = if (isSelected) theme.accentCyan else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${route.formattedTime} • $label",
                                    color = if (isSelected) theme.accentPurple else Color.Gray,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // 2. FILA DE ACCESOS: BOTÓN INICIAR Y CANCELAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Iniciar
                    Surface(
                        color = theme.accentCyan,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { onStartNavigation() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Iniciar",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Botón Cancelar (X)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.accentOrange.copy(alpha = 0.22f))
                            .border(1.dp, theme.accentOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = theme.accentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Margen de seguridad inferior para evitar cortes
                Spacer(modifier = Modifier.height(15.dp))
            }
        }
    }
}