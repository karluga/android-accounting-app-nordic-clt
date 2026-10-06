package com.example.myapplication.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.BuildConfig
import com.example.myapplication.dependancy.IOCRCallBack
import com.example.myapplication.dependancy.OCRAsyncTask
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import com.example.myapplication.utils.ImportProcessor
import com.example.myapplication.viewmodel.MainViewModel

@Composable
fun DocumentImportContent(
    viewModel: MainViewModel,
    activePreset: TablePreset?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mApiKey = BuildConfig.OCR_API_KEY

    val rawRowsList = viewModel.rawRowsList
    val selectedProcess = viewModel.selectedProcess.value
    val ocrNotesText = viewModel.ocrNotesText.value

    // Skip manual check checkbox state (enabled by default)
    var skipManualCheck by rememberSaveable { mutableStateOf(true) }

    var selectedFilesCount by rememberSaveable { mutableIntStateOf(0) }
    var selectedFilesNames by rememberSaveable { mutableStateOf("Izvēlēties failus") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isOcrProcessing by remember { mutableStateOf(false) }

    // Multi-page document preview and rotation states
    val pageReferences = remember(selectedUris) {
        if (selectedUris.isNotEmpty()) {
            DocumentPreviewHelper.extractPageReferences(context, selectedUris)
        } else {
            emptyList()
        }
    }
    var currentPageIndex by rememberSaveable(selectedUris) { mutableIntStateOf(0) }
    val pageRotations = remember(selectedUris) { mutableStateMapOf<Int, Float>() }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Function to execute OCR scan for active page and rotation (full rotated image sent without cropping)
    fun executeOcrForPage(bmp: Bitmap?, rotation: Float, pageIndex: Int) {
        if (bmp == null || isOcrProcessing) return

        isOcrProcessing = true
        val rotatedBitmap = DocumentPreviewHelper.rotateBitmap(bmp, rotation)
        val base64Image = DocumentPreviewHelper.bitmapToBase64(rotatedBitmap)

        val ocrTask = OCRAsyncTask(
            mApiKey = mApiKey,
            isOverlayRequired = true,
            mImageDataOrUrl = base64Image,
            mLanguage = "lat",
            mIOCRCallBack = object : IOCRCallBack {
                override fun getOCRCallBackResult(response: String?) {
                    isOcrProcessing = false
                    val statusLog = ImportProcessor.parseAndApplyOcrToRawRows(
                        response = response,
                        activePreset = activePreset,
                        rawRowsList = rawRowsList,
                        currentPageNum = pageIndex + 1,
                        rotationAngle = rotation
                    )
                    viewModel.updateOcrNotes(statusLog)
                    viewModel.saveRawRows()
                }
            }
        )
        ocrTask.execute()
    }

    // Render bitmap when active page index changes & run automatic blue header detection
    LaunchedEffect(currentPageIndex, pageReferences) {
        if (pageReferences.isNotEmpty() && currentPageIndex in pageReferences.indices) {
            val pageRef = pageReferences[currentPageIndex]
            val bmp = DocumentPreviewHelper.renderPageBitmap(context, pageRef)
            currentBitmap = bmp

            if (bmp != null && !pageRotations.containsKey(currentPageIndex)) {
                val autoPos = DocumentPreviewHelper.detectBlueHeaderRotation(bmp)
                if (autoPos != DetectedHeaderPosition.NOT_FOUND) {
                    pageRotations[currentPageIndex] = autoPos.requiredRotationDegrees
                }
            }
        } else {
            currentBitmap = null
        }
    }

    // AUTOMATIC OCR TRIGGER: When skipManualCheck is enabled, automatically execute OCR as soon as page bitmap & rotation are ready
    val currentRotation = pageRotations[currentPageIndex] ?: 0f
    LaunchedEffect(currentPageIndex, currentBitmap, currentRotation, skipManualCheck) {
        if (skipManualCheck && currentBitmap != null && selectedUris.isNotEmpty() && !isOcrProcessing) {
            executeOcrForPage(currentBitmap, currentRotation, currentPageIndex)
        }
    }

    // Dropdown visibility states
    var processDropdownExpanded by remember { mutableStateOf(false) }
    var isOcrNotesExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "LOCAL-FIRST IMPORT TOOL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. Dokumentu apstrāde",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Augšupielādē skenētas darba lapas, pārbaudi un rediģē nolasītos datus tabulā, un ģenerē importu.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. PIEVIENO DOKUMENTUS CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Pievieno dokumentus",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Ražošanas solis Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedProcess,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ražošanas solis (Presets)") },
                            trailingIcon = {
                                Text(
                                    text = "▼",
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { processDropdownExpanded = !processDropdownExpanded },
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { processDropdownExpanded = !processDropdownExpanded }
                        )
                        DropdownMenu(
                            expanded = processDropdownExpanded,
                            onDismissRequest = { processDropdownExpanded = false }
                        ) {
                            listOf("1. Audzēšana", "3. Vakuumprese", "5. CNC").forEach { process ->
                                DropdownMenuItem(
                                    text = { Text(process) },
                                    onClick = {
                                        viewModel.updateSelectedProcess(process)
                                        processDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CHECKBOX: Skip manual verification dialog if rotation is correct (enabled by default)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { skipManualCheck = !skipManualCheck }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = skipManualCheck,
                            onCheckedChange = { skipManualCheck = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Izlaist manuālo pārbaudi dialogā, ja orientācija ir pareiza (noklusējums)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // File picker launcher
                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetMultipleContents()
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            selectedUris = uris
                            selectedFilesCount = uris.size
                            selectedFilesNames = uris.joinToString(", ") { uri ->
                                uri.lastPathSegment ?: "dokuments.pdf"
                            }
                            currentPageIndex = 0
                            Toast.makeText(context, "Atlasīti ${uris.size} faili sistēmas krātuvē", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Text(
                        text = "PDF vai attēli",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = selectedFilesNames)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // If skipManualCheck is enabled, OCR runs automatically. If disabled, show manual trigger button.
                    if (skipManualCheck) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isOcrProcessing) "⚡ Apstrādā OCR Engine 3 tabulas datus..." else "⚡ Automātiskā nolasīšana aktīva (OCR Engine 3 iedarbināsies automātiski)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (selectedFilesCount == 0 && selectedUris.isEmpty()) {
                                    Toast.makeText(context, "Lūdzu, vispirms pievienojiet dokumentus (uzklikšķiniet uz failu pogas)", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                executeOcrForPage(currentBitmap, currentRotation, currentPageIndex)
                            },
                            enabled = !isOcrProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isOcrProcessing) "Apstrādā OCR..." else "Nolasīt dokumentu ar OCR.space API (Engine 3)")
                        }
                    }
                }
            }
        }

        // 2. DOCUMENT PREVIEW & ROTATION CARD
        if (pageReferences.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "2. Dokumenta priekšskatījums un Orientācija",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automātiski vai manuāli pagriez zilo virsraksta joslu uz augšu",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "${currentRotation.toInt()}°",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // PAGE NAVIGATION CONTROLS
                        if (pageReferences.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                    enabled = currentPageIndex > 0,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("◀ Iepriekšējā lapa", fontSize = 12.sp)
                                }

                                Text(
                                    text = "Lapa ${currentPageIndex + 1} no ${pageReferences.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedButton(
                                    onClick = { if (currentPageIndex < pageReferences.size - 1) currentPageIndex++ },
                                    enabled = currentPageIndex < pageReferences.size - 1,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Nākamā lapa ▶", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // AUTO ROTATION BUTTON
                        Button(
                            onClick = {
                                val bmp = currentBitmap
                                if (bmp != null) {
                                    val detectedPos = DocumentPreviewHelper.detectBlueHeaderRotation(bmp)
                                    if (detectedPos != DetectedHeaderPosition.NOT_FOUND) {
                                        pageRotations[currentPageIndex] = detectedPos.requiredRotationDegrees
                                        Toast.makeText(
                                            context,
                                            "Zilais virsraksts atrasts [${detectedPos.labelLatvian}] ➔ Pagriezts uz augšu!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Zilā virsraksta josla netika atrasta. Izmantojiet manuālo pagriešanu.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("✨ Automātiski pielāgot (Meklēt zilo virsrakstu)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // MANUAL ROTATION BUTTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val nextAngle = (currentRotation - 90f + 360f) % 360f
                                    pageRotations[currentPageIndex] = nextAngle
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Text("↺ -90°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    val nextAngle = (currentRotation + 90f) % 360f
                                    pageRotations[currentPageIndex] = nextAngle
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Text("↻ +90°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { pageRotations[currentPageIndex] = 0f }
                            ) {
                                Text("Atiestatīt 0°", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // FULL PAGE PREVIEW (MAX WIDTH SCREEN)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(Color(0xFF212121), RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentBitmap != null) {
                                Image(
                                    bitmap = currentBitmap!!.asImageBitmap(),
                                    contentDescription = "Dokumenta lapas priekšskatījums",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(rotationZ = currentRotation),
                                    contentScale = ContentScale.FillWidth
                                )
                            } else {
                                Text(
                                    text = "Ielādē lapas ${currentPageIndex + 1} priekšskatījumu...",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. EDITABLE RAW SCANNED ROWS TABLE WITH COLOR FOCUS
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Skenētie dati - Manuālā pārbaude (${activePreset?.taskName ?: selectedProcess})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = {
                                val newRow = RawScannedRow()
                                activePreset?.columns?.forEach { col ->
                                    newRow.setCell(col.role, "")
                                }
                                viewModel.addRawRow(newRow)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("＋ Pievienot rindu", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // FORMULA & PRESET HINT BOX
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        val formulaText = when {
                            selectedProcess.contains("Audzē") ->
                                "Audzēšana (Diefenbacher): Pārbaudiet visas 13 kolonnas. Dīkstāve un komentāri satur master paneli (tikai (OK) tiek skaitīti pabeigtajos)."
                            selectedProcess.contains("Vakuum") ->
                                "Vakuumprese: Pārbaudiet 12 kolonnas (Mastera nr., dēļu izmērus garen/šķērs virzienā)."
                            else ->
                                "CNC: Pārbaudiet 11 kolonnas (Mastera. nr., CNC sāk, CNC beidz, divpusēju apstrādi)."
                        }

                        Text(
                            text = formulaText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // COLOR FOCUS LEGEND
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendBadge("Group 1: Import", Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                        LegendBadge("Group 2: Aprēķini", Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764))
                        LegendBadge("Sekundārs", Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SPREADSHEET TABLE WITH ALL PRESET RAW COLUMNS & COLOR FOCUS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // Table Header Row - Dynamically constructed from ALL active preset columns!
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                val columns = activePreset?.columns ?: emptyList()
                                if (columns.isNotEmpty()) {
                                    columns.forEach { col ->
                                        val (bgColor, borderColor, textColor) = getGroupColors(col.group)
                                        TableHeaderCell(col.sourceHeader, 130, bgColor, borderColor, textColor)
                                    }
                                } else {
                                    TableHeaderCell("Dat. 2026", 120, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                    TableHeaderCell("Operat.", 100, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                    TableHeaderCell("Proj.Nr.", 130, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                }

                                Box(modifier = Modifier.width(50.dp))
                            }

                            // Table Body Rows - Editable Cells for ALL preset columns
                            if (rawRowsList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Vēl nav skenētu datu. Augšupielādē dokumentu un spied 'Nolasīt dokumentu'.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                rawRowsList.forEachIndexed { index, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val columns = activePreset?.columns ?: emptyList()
                                        if (columns.isNotEmpty()) {
                                            columns.forEach { col ->
                                                val (bgColor, _, textColor) = getGroupColors(col.group)
                                                var cellVal by remember(row.id, col.role) {
                                                    mutableStateOf(row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) })
                                                }

                                                TableInputCell(
                                                    value = cellVal,
                                                    widthDp = 130,
                                                    placeholder = col.sourceHeader,
                                                    bgColor = bgColor,
                                                    textColor = textColor
                                                ) { newValue ->
                                                    cellVal = newValue
                                                    viewModel.updateRawCell(row, col.role, newValue)
                                                    viewModel.updateRawCell(row, col.sourceHeader, newValue)
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier.width(50.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✕",
                                                modifier = Modifier
                                                    .clickable { viewModel.removeRawRow(index) }
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

                    // GENERATE IMPORT BUTTON -> TRANSITIONS TO TAB 2 WITH CALCULATED DATA
                    Button(
                        onClick = {
                            if (rawRowsList.isEmpty()) {
                                Toast.makeText(context, "Nav datu ko ģenerēt! Lūdzu, vispirms pievienojiet vai nolasiet rindas.", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.updateImportTab(1)
                                Toast.makeText(context, "Aprēķināts Excel imports! Apskatiet un lejupielādējiet 2. cilnē.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7E34), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "⚡ Ģenerēt importu (Pāriet uz 2. cilni ➔)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // OCR PIEZĪMES COLLAPSIBLE DETAILS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isOcrNotesExpanded = !isOcrNotesExpanded }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OCR piezīmes un atbilde",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isOcrNotesExpanded) "▲" else "▼",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            AnimatedVisibility(visible = isOcrNotesExpanded) {
                                Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                    OutlinedTextField(
                                        value = ocrNotesText,
                                        onValueChange = { viewModel.updateOcrNotes(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                        minLines = 3
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
