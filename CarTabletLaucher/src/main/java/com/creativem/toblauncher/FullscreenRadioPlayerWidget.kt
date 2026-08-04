package com.creativem.toblauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity

@Composable
fun FullscreenRadioPlayerWidget(
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }

    // 🎨 DECLARACIÓN DE CONTEXTO, TEMA Y ESCALAS
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val buttonScale = LocalButtonScale.current ?: 1.0f
    val textScale = LocalDensity.current.fontScale

    val radioManager = remember { SmartRadioManager.getInstance(context) }

    val currentStation = radioManager.stationList.getOrNull(radioManager.currentStationIndex)
    var favoriteIds by remember { mutableStateOf(radioManager.getSavedFavorites()) }
    val isFavorite = currentStation != null && favoriteIds.contains(currentStation.id)

    var selectedCountry by remember { mutableStateOf(radioManager.getSavedCountry()) }
    var showCountryModal by remember { mutableStateOf(false) }

    // 🔍 BÚSQUEDA
    var searchQuery by remember { mutableStateOf("") }


    // ⚡ AUTO-PLAY SEGURO (Solo actúa si no estaba sonando)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400L)
        if (!radioManager.isPlaying && !radioManager.isLoading && radioManager.stationList.isNotEmpty()) {
            radioManager.togglePlayPause()
        }
    }
    // 📋 LISTA FILTRADA Y FAVORITAS PRIMERO
    val displayStations = remember(radioManager.stationList, favoriteIds, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            radioManager.stationList
        } else {
            radioManager.stationList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.city.contains(searchQuery, ignoreCase = true) ||
                        it.genre.contains(searchQuery, ignoreCase = true)
            }
        }
        filtered.sortedByDescending { favoriteIds.contains(it.id) }
    }

    // CONTENEDOR PRINCIPAL PANTALLA COMPLETA
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 1. BARRA SUPERIOR (HEADER) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size((42 * buttonScale).dp)
                            .background(Color(0xFF1E1E28), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size((22 * buttonScale).dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "RADIO EN VIVO ONLINE",
                        color = Color.White,
                        fontSize = (10 * textScale).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // BOTÓN CAMBIAR PAÍS ADAPTADO AL TEMA
                Button(
                    onClick = { showCountryModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = theme.accentCyan,
                        modifier = Modifier.size((18 * buttonScale).dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🌎 $selectedCountry",
                        color = Color.White,
                        fontSize = (8 * textScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- 2. CONTENIDO PRINCIPAL (PANEL IZQUIERDO Y DERECHO) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PANEL IZQUIERDO: DETALLES Y LOGO
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(theme.accentCyan.copy(alpha = 0.3f), Color(0xFF14141E))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // INDICADOR EN VIVO
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (radioManager.isPlaying) Color(0xFF00C853) else theme.accentCyan.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (radioManager.isPlaying) "● EN VIVO" else "SEÑAL RADIO",
                                color = if (radioManager.isPlaying) Color.Black else theme.accentCyan,
                                fontSize = (8 * textScale).sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ÍCONO CENTRAL RADIO CON GRADIENTE DEL TEMA
                        Box(
                            modifier = Modifier
                                .size((130 * buttonScale).dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(theme.accentCyan, theme.accentPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((65 * buttonScale).dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // NOMBRE DE EMISORA
                        Text(
                            text = currentStation?.name ?: "Sin Emisora",
                            color = Color.White,
                            fontSize = (10 * textScale).sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${currentStation?.freqLabel ?: ""} • ${currentStation?.city ?: selectedCountry}",
                            color = theme.accentCyan,
                            fontSize = (8 * textScale).sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = currentStation?.genre ?: "Variada",
                            color = Color.Gray,
                            fontSize = (7 * textScale).sp
                        )
                    }
                }

                // PANEL DERECHO: BUSCADOR AMPLIO + LISTA DE EMISORAS
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF14141E))
                        .padding(10.dp)
                ) {
                    // 🔍 BUSCADOR AMPLIO Y CÓMODO
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "🔍 Buscar emisora...",
                                fontSize = (7 * textScale).sp,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (64 * buttonScale).dp), // 👈 Mínimo 64dp para evitar recortes
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = (8 * textScale).sp,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentCyan,
                            unfocusedBorderColor = Color(0xFF282836),
                            focusedContainerColor = Color(0xFF1E1E2A),
                            unfocusedContainerColor = Color(0xFF181822)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size((24 * buttonScale).dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // LISTA DE EMISORAS
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (radioManager.isFetchingApi) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = theme.accentCyan)
                            }
                        } else if (displayStations.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Sin resultados para \"$searchQuery\"" else "No hay emisoras disponibles",
                                    color = Color.Gray,
                                    fontSize = (8 * textScale).sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(displayStations) { index, station ->
                                    val isSelected = currentStation?.id == station.id
                                    val isFav = favoriteIds.contains(station.id)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) theme.accentCyan.copy(alpha = 0.22f) else Color(0xFF1A1A24))
                                            .clickable {
                                                val originalIndex = radioManager.stationList.indexOfFirst { it.id == station.id }
                                                if (originalIndex != -1) {
                                                    radioManager.playStationAtIndex(originalIndex)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                color = if (isSelected) theme.accentCyan else Color.Gray,
                                                fontSize = (7 * textScale).sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(28.dp)
                                            )

                                            Column {
                                                Text(
                                                    text = station.name,
                                                    color = if (isSelected) theme.accentCyan else Color.White,
                                                    fontSize = (8 * textScale).sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = station.city.ifEmpty { selectedCountry },
                                                    color = Color.Gray,
                                                    fontSize = (7 * textScale).sp
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected && radioManager.isPlaying) {
                                                Icon(
                                                    imageVector = Icons.Default.GraphicEq,
                                                    contentDescription = "Sonando",
                                                    tint = theme.accentCyan,
                                                    modifier = Modifier
                                                        .size((18 * buttonScale).dp)
                                                        .padding(end = 4.dp)
                                                )
                                            }

                                            // CORAZÓN DE FAVORITO
                                            IconButton(
                                                onClick = {
                                                    val newFavs = if (isFav) favoriteIds - station.id else (favoriteIds + station.id).distinct()
                                                    favoriteIds = newFavs
                                                    radioManager.saveFavorites(newFavs)
                                                },
                                                modifier = Modifier.size((32 * buttonScale).dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favorito",
                                                    tint = if (isFav) Color(0xFFFF5252) else Color.DarkGray,
                                                    modifier = Modifier.size((16 * buttonScale).dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 3. BARRA INFERIOR DE CONTROLES ADAPTADA ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // FAVORITO EMISORA ACTUAL
                    IconButton(
                        onClick = {
                            if (currentStation != null) {
                                val newFavs = if (isFavorite) favoriteIds - currentStation.id else (favoriteIds + currentStation.id).distinct()
                                favoriteIds = newFavs
                                radioManager.saveFavorites(newFavs)
                            }
                        },
                        modifier = Modifier.size((44 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito Actual",
                            tint = if (isFavorite) Color(0xFFFF5252) else Color.DarkGray,
                            modifier = Modifier.size((24 * buttonScale).dp)
                        )
                    }

                    // ANTERIOR
                    IconButton(
                        onClick = { radioManager.playPreviousStation() },
                        modifier = Modifier
                            .size((48 * buttonScale).dp)
                            .background(Color(0xFF22222E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = theme.accentCyan,
                            modifier = Modifier.size((30 * buttonScale).dp)
                        )
                    }

                    // PLAY / PAUSA DENTRO DEL TEMA
                    IconButton(
                        onClick = { radioManager.togglePlayPause() },
                        modifier = Modifier
                            .size((58 * buttonScale).dp)
                            .background(theme.accentCyan, CircleShape)
                    ) {
                        if (radioManager.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size((28 * buttonScale).dp),
                                color = Color.Black,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (radioManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pausa",
                                tint = Color.Black,
                                modifier = Modifier.size((34 * buttonScale).dp)
                            )
                        }
                    }

                    // SIGUIENTE
                    IconButton(
                        onClick = { radioManager.playNextStation() },
                        modifier = Modifier
                            .size((48 * buttonScale).dp)
                            .background(Color(0xFF22222E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = theme.accentCyan,
                            modifier = Modifier.size((30 * buttonScale).dp)
                        )
                    }

                    // CANTIDAD EMISORAS
                    Box(
                        modifier = Modifier
                            .size((44 * buttonScale).dp)
                            .background(Color(0xFF22222E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${radioManager.currentStationIndex + 1}/${radioManager.stationList.size}",
                            color = Color.Gray,
                            fontSize = (7 * textScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- VENTANA EMERGENTE: SELECTOR DE PAÍS CON TEMA ---
        if (showCountryModal) {
            CountryPickerModal(
                currentCountry = selectedCountry,
                theme = theme,
                onDismiss = { showCountryModal = false },
                onCountrySelected = { displayName, apiName ->
                    selectedCountry = displayName
                    radioManager.selectedCountry = displayName
                    showCountryModal = false

                    radioManager.fetchStationsByCountry(apiName) {
                        if (radioManager.stationList.isNotEmpty()) {
                            radioManager.playStationAtIndex(0)
                        }
                    }
                }
            )
        }
    }
}