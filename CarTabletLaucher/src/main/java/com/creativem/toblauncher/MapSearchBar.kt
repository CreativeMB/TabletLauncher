package com.creativem.toblauncher

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import java.io.File
import kotlin.math.max

@Composable
fun MapSearchBar(
    currentLocation: LatLong?,
    poiFile: File,
    theme: DashboardTheme,
    onLocationSelected: (LatLong) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Abre el teclado automáticamente
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(150)
            focusRequester.requestFocus()
        }
    }

    // Longitud máxima de texto encontrada en los resultados
    val maxChars = remember(searchResults) {
        searchResults.maxOfOrNull { max(it.title.length, it.description.length) } ?: 0
    }

    // Ancho dinámico: se agranda generosamente según los caracteres hasta un máximo de 900.dp
    val dynamicWidth = remember(maxChars, isSearchOpen, searchResults.isNotEmpty()) {
        when {
            !isSearchOpen -> 40.dp
            searchResults.isEmpty() -> 540.dp
            else -> {
                val calculated = (maxChars * 9f).dp + 140.dp
                calculated.coerceIn(540.dp, 900.dp) // 👈 Amplio para tablet
            }
        }
    }

    val animatedWidth by animateDpAsState(
        targetValue = dynamicWidth,
        animationSpec = tween(durationMillis = 260),
        label = "searchBarWidth"
    )

    if (!isSearchOpen) {
        // =========================================================================
        // 1. ESTADO REPOSO (LUPA)
        // =========================================================================
        FloatingActionButton(
            onClick = { isSearchOpen = true },
            containerColor = theme.cardBackground.copy(alpha = 0.95f),
            contentColor = theme.accentCyan,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier
                .size(40.dp)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(10.dp)),
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        // =========================================================================
        // 2. ESTADO ACTIVO (EXPANDIDO SIN CORTAR TEXTO)
        // =========================================================================
        Column(
            modifier = modifier
                .width(animatedWidth)
                .wrapContentHeight()
                .animateContentSize()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, theme.accentCyan),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = theme.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar dirección, cruce o lugar...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { newQuery ->
                                searchQuery = newQuery
                                searchJob?.cancel()
                                if (newQuery.trim().length >= 2) {
                                    isSearching = true
                                    searchJob = coroutineScope.launch {
                                        delay(260)
                                        val results = HybridSearchEngine.search(context, newQuery, currentLocation, poiFile)
                                        searchResults = results
                                        isSearching = false
                                    }
                                } else {
                                    searchResults = emptyList()
                                    isSearching = false
                                }
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(theme.accentCyan),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = theme.accentCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.LightGray,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    isSearchOpen = false
                                    focusManager.clearFocus()
                                }
                        )
                    }
                }
            }

            // LISTA DE RESULTADOS (MUESTRA TODO EL CONTENIDO SIN PUNTOS SUSPENSIVOS)
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.98f)),
                    border = BorderStroke(1.dp, theme.cardBorder),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        items(searchResults) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        isSearchOpen = false
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        onLocationSelected(item.latLong)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icono
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.isOnline) theme.accentCyan.copy(alpha = 0.18f) else theme.accentPurple.copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isOnline) Icons.Default.Language else Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (item.isOnline) theme.accentCyan else theme.accentPurple,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 👇 CONTENIDO COMPLETO: SE ELIMINÓ EL CORTE FORZADO (Ellipsis)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = true // 👈 Permite mostrar el título entero
                                    )
                                    if (item.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.description,
                                            color = Color.LightGray,
                                            fontSize = 10.5.sp,
                                            lineHeight = 13.sp,
                                            softWrap = true // 👈 Muestra toda la dirección/cruce completa
                                        )
                                    }
                                }

                                // Distancia en km fija
                                if (item.formattedDistance.isNotBlank()) {
                                    Text(
                                        text = item.formattedDistance,
                                        color = theme.accentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            HorizontalDivider(color = theme.cardBorder.copy(alpha = 0.35f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}