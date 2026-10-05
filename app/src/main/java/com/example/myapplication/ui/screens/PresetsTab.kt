package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.models.PresetColumn
import com.example.myapplication.models.TablePreset
import com.example.myapplication.repository.PresetRepository

@Composable
fun PresetsTab(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var presetsList by remember { mutableStateOf(PresetRepository.getAllPresets(context)) }
    var selectedPresetKey by remember { mutableStateOf(presetsList.firstOrNull()?.key ?: "diefenbacher") }

    // Form states
    var keyInput by remember { mutableStateOf("") }
    var taskNameInput by remember { mutableStateOf("") }
    var dateFormatInput by remember { mutableStateOf("%d/%m/%Y") }
    val columnsState = remember { mutableStateListOf<PresetColumn>() }

    var statusMessage by remember { mutableStateOf("") }
    var presetDropdownExpanded by remember { mutableStateOf(false) }

    // Function to load a selected preset into the editor form
    fun loadPreset(key: String) {
        val preset = PresetRepository.getPreset(context, key)
        if (preset != null) {
            selectedPresetKey = preset.key
            keyInput = preset.key
            taskNameInput = preset.taskName
            dateFormatInput = preset.dateFormat
            columnsState.clear()
            columnsState.addAll(preset.columns)
            statusMessage = "Ielādēts presets '${preset.taskName}'"
        }
    }

    // Load initial preset
    LaunchedEffect(Unit) {
        if (presetsList.isNotEmpty()) {
            loadPreset(selectedPresetKey)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TABULU PRESETU REDAKTORS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Konfigurē ražošanas soļu tabulas",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Definē skenējamo dokumentu kolonnu virsrakstus, datu tipus un validācijas noteikumus.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // PRESET SELECTION & MANAGEMENT
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Izvēlēties vai izveidot presetu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = presetsList.find { it.key == selectedPresetKey }?.taskName ?: selectedPresetKey,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Aktīvais presets") },
                                trailingIcon = {
                                    Text(
                                        text = "▼",
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .clickable { presetDropdownExpanded = !presetDropdownExpanded },
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = presetDropdownExpanded,
                                onDismissRequest = { presetDropdownExpanded = false }
                            ) {
                                presetsList.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.taskName} (${p.key})") },
                                        onClick = {
                                            presetDropdownExpanded = false
                                            loadPreset(p.key)
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                selectedPresetKey = "jauns_solis"
                                keyInput = "jauns_solis"
                                taskNameInput = "Jauns ražošanas solis"
                                dateFormatInput = "%d/%m/%Y"
                                columnsState.clear()
                                columnsState.add(
                                    PresetColumn(
                                        index = 0,
                                        sourceHeader = "Datums",
                                        role = "date",
                                        outputMapping = "Datums",
                                        dataType = "date",
                                        nullable = false
                                    )
                                )
                                statusMessage = "Izveidota jauna preseta veidne. Aizpildi un saglabā!"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("＋ Jauns presets")
                        }
                    }

                    if (statusMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // PRESET METADATA FORM
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Preseta pamatinformācija",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.lowercase().trim() },
                        label = { Text("Presets atslēga (ID)") },
                        placeholder = { Text("piem. diefenbacher, step_4") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = taskNameInput,
                        onValueChange = { taskNameInput = it },
                        label = { Text("Soļa nosaukums") },
                        placeholder = { Text("piem. 1. Audzētājs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dateFormatInput,
                        onValueChange = { dateFormatInput = it },
                        label = { Text("Datuma formāts") },
                        placeholder = { Text("%d/%m/%Y vai %d.%m.%Y") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // COLUMNS EDITOR TABLE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Kolonnu struktūra (${columnsState.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                val nextIdx = columnsState.size
                                columnsState.add(
                                    PresetColumn(
                                        index = nextIdx,
                                        sourceHeader = "Kolonna ${nextIdx + 1}",
                                        role = "passthrough",
                                        dataType = "string"
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("＋ Pievienot kolonnu", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal Scrollable Columns Table
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // Column Table Headers
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                PresetHeaderCell("#", 40)
                                PresetHeaderCell("Skenētais virsraksts", 160)
                                PresetHeaderCell("Loma (Role)", 120)
                                PresetHeaderCell("Izvades lauks (Mapping)", 140)
                                PresetHeaderCell("Datu tips", 100)
                                PresetHeaderCell("Obligāts", 70)
                                PresetHeaderCell("Atslēgvārdi (Keywords)", 180)
                                PresetHeaderCell("Enum opcijas", 150)
                                Box(modifier = Modifier.width(50.dp))
                            }

                            // Column Rows
                            if (columnsState.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Nav definēta neviena kolonna. Noklikšķiniet 'Pievienot kolonnu'.", fontSize = 13.sp)
                                }
                            } else {
                                columnsState.forEachIndexed { index, col ->
                                    var srcHeader by remember(index, col) { mutableStateOf(col.sourceHeader) }
                                    var role by remember(index, col) { mutableStateOf(col.role) }
                                    var outMap by remember(index, col) { mutableStateOf(col.outputMapping ?: "") }
                                    var dataType by remember(index, col) { mutableStateOf(col.dataType) }
                                    var isNullable by remember(index, col) { mutableStateOf(col.nullable) }
                                    var keywordsText by remember(index, col) { mutableStateOf(col.keywords.joinToString(", ")) }
                                    var enumText by remember(index, col) { mutableStateOf(col.enumValues.joinToString(", ")) }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Index
                                        Box(
                                            modifier = Modifier.width(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Source Header
                                        PresetInputCell(srcHeader, 160, "virsraksts") {
                                            srcHeader = it
                                            columnsState[index] = columnsState[index].copy(sourceHeader = it)
                                        }

                                        // Role
                                        PresetInputCell(role, 120, "date, project, etc.") {
                                            role = it
                                            columnsState[index] = columnsState[index].copy(role = it)
                                        }

                                        // Output Mapping
                                        PresetInputCell(outMap, 140, "Datums, Related to") {
                                            outMap = it
                                            columnsState[index] = columnsState[index].copy(outputMapping = if (it.isBlank()) null else it)
                                        }

                                        // Data Type
                                        PresetInputCell(dataType, 100, "string, float, int") {
                                            dataType = it
                                            columnsState[index] = columnsState[index].copy(dataType = it)
                                        }

                                        // Nullable / Obligāts Checkbox
                                        Box(
                                            modifier = Modifier.width(70.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Checkbox(
                                                checked = !isNullable,
                                                onCheckedChange = { checked ->
                                                    isNullable = !checked
                                                    columnsState[index] = columnsState[index].copy(nullable = !checked)
                                                }
                                            )
                                        }

                                        // Keywords
                                        PresetInputCell(keywordsText, 180, "kw1, kw2") {
                                            keywordsText = it
                                            val kwList = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                                            columnsState[index] = columnsState[index].copy(keywords = kwList)
                                        }

                                        // Enum Values
                                        PresetInputCell(enumText, 150, "Val1, Val2") {
                                            enumText = it
                                            val enumList = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                                            columnsState[index] = columnsState[index].copy(enumValues = enumList)
                                        }

                                        // Delete Column Button
                                        Box(
                                            modifier = Modifier.width(50.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✕",
                                                modifier = Modifier
                                                    .clickable {
                                                        columnsState.removeAt(index)
                                                        // Re-index remaining columns
                                                        for (i in columnsState.indices) {
                                                            columnsState[i] = columnsState[i].copy(index = i)
                                                        }
                                                    }
                                                    .padding(8.dp),
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ACTIONS: SAVE & DELETE PRESET
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (keyInput.isBlank()) {
                                    Toast.makeText(context, "Preseta atslēga nevar būt tukša!", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                PresetRepository.deletePreset(context, keyInput)
                                presetsList = PresetRepository.getAllPresets(context)
                                selectedPresetKey = presetsList.firstOrNull()?.key ?: "diefenbacher"
                                loadPreset(selectedPresetKey)
                                Toast.makeText(context, "Presets '$keyInput' izdzēsts!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Dzēst presetu")
                        }

                        Button(
                            onClick = {
                                if (keyInput.isBlank() || taskNameInput.isBlank()) {
                                    Toast.makeText(context, "Lūdzu, aizpildiet preseta atslēgu un nosaukumu!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val updatedPreset = TablePreset(
                                    key = keyInput,
                                    taskName = taskNameInput,
                                    dateFormat = dateFormatInput,
                                    columns = columnsState.toList()
                                )

                                PresetRepository.savePreset(context, updatedPreset)
                                presetsList = PresetRepository.getAllPresets(context)
                                selectedPresetKey = keyInput
                                statusMessage = "Presets '$taskNameInput' ($keyInput) veiksmīgi saglabāts!"
                                Toast.makeText(context, "Presets saglabāts!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7E34), contentColor = Color.White)
                        ) {
                            Text("Saglabāt presetu", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetHeaderCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PresetInputCell(
    value: String,
    widthDp: Int,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 2.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 10.sp) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 11.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
