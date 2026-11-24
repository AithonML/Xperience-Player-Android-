package com.xperience.player.ui.visualizer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.PlusJakartaSans

@Composable
fun VisualizerSelectorSheetContent(
    availableVisualizers: List<VisualizerDefinition>,
    selectedVisualizer: VisualizerDefinition,
    accentColor: Color,
    appColors: AppThemeColors,
    onSelectVisualizer: (VisualizerDefinition) -> Unit,
    onImportVisualizer: (jsonContent: String, fileName: String?) -> Unit,
    onDeleteVisualizer: (String) -> Unit
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = uri.lastPathSegment
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (!content.isNullOrBlank()) {
                    onImportVisualizer(content, fileName)
                    Toast.makeText(context, "Visualizer imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Empty visualizer file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to import visualizer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        // Sheet Header: Title & Import button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GPU Music Visualizers",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "60 FPS real-time geometry & .xviz.json compatible",
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary
                )
            }

            // + Import button
            ElevatedButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*", "application/json", "text/*"))
                },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Import",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visualizer presets list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(availableVisualizers, key = { it.id }) { viz ->
                val isSelected = viz.id.equals(selectedVisualizer.id, ignoreCase = true)

                val modeIcon = when (viz.renderMode) {
                    VisualizerRenderMode.NeonTerrainPeaks -> Icons.Default.GraphicEq
                    VisualizerRenderMode.HarmonicWireframeSphere -> Icons.Default.Public
                    VisualizerRenderMode.AuroraPrismaticWaves -> Icons.Default.Waves
                    VisualizerRenderMode.RadialCyberPulsar -> Icons.Default.Tune
                    VisualizerRenderMode.None -> Icons.Default.GraphicEq
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) {
                                accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.15f)
                            } else {
                                if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0C000000)
                            }
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else if (appColors.isDark) Color(0x22FFFFFF) else Color(0x12000000),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectVisualizer(viz) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Icon in styled circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.35f)
                                else if (appColors.isDark) Color(0x24FFFFFF) else Color(0x16000000)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = viz.renderMode.name,
                            tint = if (isSelected) accentColor else appColors.glassTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Info: Name, Author, Description & RenderMode chip
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = viz.name,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = if (isSelected) accentColor else appColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (viz.isCustomImported) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accentColor.copy(alpha = 0.20f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Custom",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 9.sp,
                                        color = accentColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${viz.author} • ${viz.renderMode.name}",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = appColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (viz.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viz.description,
                                fontFamily = PlusJakartaSans,
                                fontSize = 10.5.sp,
                                color = appColors.textSecondary.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete button if custom-imported
                    if (viz.isCustomImported) {
                        IconButton(
                            onClick = { onDeleteVisualizer(viz.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete visualizer",
                                tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Selected Indicator
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
