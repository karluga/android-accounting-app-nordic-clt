package com.example.myapplication.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.dependancy.IOCRCallBack
import com.example.myapplication.dependancy.OCRAsyncTask
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import com.example.myapplication.repository.PresetRepository
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ExcelExportRow(
    val taskName: String,
    val status: String = "Completed",
    val date: String,
    val m3: String,
    val mastersCount: String,
    val relatedTo: String,
    val type: String = "Ražošana"
)

@Composable
fun ImportScreen(
    rowsList: MutableList<ProductionRow>,
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Sub-tab selection state: 0 = Dokumentu apstrāde, 1 = Lejupielādēt Excel importu, 2 = Tabulu Preseti
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Active selected process step
    var selectedProcess by rememberSaveable { mutableStateOf("1. Audzēšana") }

    // List of raw scanned rows for Tab 1 editing
    val rawRowsList = remember { mutableStateListOf<RawScannedRow>() }

    // Retrieve active Preset
    val activePreset = remember(selectedProcess) {
        PresetRepository.getPresetByTaskName(context, selectedProcess)
            ?: PresetRepository.getPreset(context, "diefenbacher")
    }

    // Populate initial sample rows when switching step if table is empty
    LaunchedEffect(selectedProcess, activePreset?.key) {
        if (rawRowsList.isEmpty()) {
            rawRowsList.addAll(createSampleRawRows(selectedProcess, activePreset))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Row for switching between Document Processing, Excel Export, and Presets Tab
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("1. Dokumentu apstrāde", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("2. Lejupielādēt Excel importu", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("3. Tabulu Preseti", fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            1 -> {
                // Tab 2: Lejupielādēt Excel importu Flow
                ExcelExportTabContent(
                    rawRowsList = rawRowsList,
                    selectedProcess = selectedProcess,
                    activePreset = activePreset,
                    modifier = Modifier.fillMaxSize()
                )
            }
            2 -> {
                // Tab 3: Presets Tab Component
                PresetsTab(modifier = Modifier.fillMaxSize())
            }
            else -> {
                // Tab 1: Document Processing Flow
                DocumentImportContent(
                    rawRowsList = rawRowsList,
                    selectedProcess = selectedProcess,
                    onProcessChange = { newProcess ->
                        selectedProcess = newProcess
                        // Automatically update table headers and sample rows if empty
                        if (rawRowsList.isEmpty()) {
                            val newPreset = PresetRepository.getPresetByTaskName(context, newProcess)
                            rawRowsList.addAll(createSampleRawRows(newProcess, newPreset))
                        }
                    },
                    activePreset = activePreset,
                    ocrNotesText = ocrNotesText,
                    onOcrNotesChange = onOcrNotesChange,
                    onNavigateToExport = {
                        selectedTab = 1
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DocumentImportContent(
    rawRowsList: MutableList<RawScannedRow>,
    selectedProcess: String,
    onProcessChange: (String) -> Unit,
    activePreset: TablePreset?,
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mApiKey = "K89893470788957"

    // Form selections
    var confirmationMode by rememberSaveable { mutableStateOf("Tikai zem 80% OCR pārliecības") }
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

    // Dropdown visibility states
    var processDropdownExpanded by remember { mutableStateOf(false) }
    var confirmDropdownExpanded by remember { mutableStateOf(false) }
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
                                        onProcessChange(process)
                                        processDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Apstiprināšana dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = confirmationMode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Apstiprināšana") },
                            trailingIcon = {
                                Text(
                                    text = "▼",
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { confirmDropdownExpanded = !confirmDropdownExpanded },
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmDropdownExpanded = !confirmDropdownExpanded }
                        )
                        DropdownMenu(
                            expanded = confirmDropdownExpanded,
                            onDismissRequest = { confirmDropdownExpanded = false }
                        ) {
                            listOf("Tikai zem 80% OCR pārliecības", "Neprasīt apstiprinājumu").forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode) },
                                    onClick = {
                                        confirmationMode = mode
                                        confirmDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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

                    // Submit button "Nolasīt dokumentus"
                    Button(
                        onClick = {
                            if (selectedFilesCount == 0 && selectedUris.isEmpty()) {
                                Toast.makeText(context, "Lūdzu, vispirms pievienojiet dokumentus (uzklikšķiniet uz failu pogas)", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            isOcrProcessing = true
                            Toast.makeText(context, "Uzsākta OCR.space tabulas analīze...", Toast.LENGTH_SHORT).show()

                            val rawBitmap = currentBitmap
                            val currentRotation = pageRotations[currentPageIndex] ?: 0f

                            val base64Image = if (rawBitmap != null) {
                                val rotatedBitmap = DocumentPreviewHelper.rotateBitmap(rawBitmap, currentRotation)
                                DocumentPreviewHelper.bitmapToBase64(rotatedBitmap)
                            } else null

                            val ocrTask = OCRAsyncTask(
                                mApiKey = mApiKey,
                                isOverlayRequired = true,
                                mImageDataOrUrl = base64Image,
                                mLanguage = "eng",
                                mIOCRCallBack = object : IOCRCallBack {
                                    override fun getOCRCallBackResult(response: String?) {
                                        isOcrProcessing = false
                                        val statusLog = parseAndApplyOcrToRawRows(
                                            response = response,
                                            activePreset = activePreset,
                                            selectedProcess = selectedProcess,
                                            rawRowsList = rawRowsList,
                                            currentPageNum = currentPageIndex + 1,
                                            rotationAngle = currentRotation
                                        )
                                        onOcrNotesChange(statusLog)
                                    }
                                }
                            )
                            ocrTask.execute()
                        },
                        enabled = !isOcrProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isOcrProcessing) "Apstrādā OCR..." else "Nolasīt dokumentu ar OCR.space API")
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
                        val currentRotation = pageRotations[currentPageIndex] ?: 0f

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

                        // PREVIEW IMAGE BOX (MAX WIDTH SCREEN)
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
                                rawRowsList.add(newRow)
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
                                        text = "Vēl nav skenētu datu. Augšupielādē dokumentu vai pievieno rindu.",
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
                                                    row.setCell(col.role, newValue)
                                                    row.setCell(col.sourceHeader, newValue)
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
                                                    .clickable { rawRowsList.removeAt(index) }
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
                                onNavigateToExport()
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
                                        onValueChange = onOcrNotesChange,
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

@Composable
private fun ExcelExportTabContent(
    rawRowsList: List<RawScannedRow>,
    selectedProcess: String,
    activePreset: TablePreset?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (fileName, exportRows) = remember(rawRowsList.size, selectedProcess) {
        generateExcelImportDataFromRaw(rawRowsList, activePreset, selectedProcess)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LEJUPIELĀDĒT EXCEL IMPORTU",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Lejupielādēt Excel importu",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ģenerēts kopsavilkums no pārbaudītajām rindām ar nosaukumu <date><production_step>.xlsx",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sagatavotā faila nosaukums:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fileName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "7 kolonnu izvades kopsavilkums (${exportRows.size} rindas):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // 7 Export Column Headers
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                TableHeaderCell("Task name", 130, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                                TableHeaderCell("Status", 100, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                                TableHeaderCell("Datums", 110, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                TableHeaderCell("Saražoti m3", 110, Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764))
                                TableHeaderCell("Kopā masteri", 110, Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764))
                                TableHeaderCell("Related to", 140, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                TableHeaderCell("Tips", 100, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                            }

                            if (exportRows.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Vēl nav aprēķinātu rindu. Vispirms spiediet 'Ģenerēt importu' 1. cilnē.", fontSize = 13.sp)
                                }
                            } else {
                                exportRows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExportTableCell(row.taskName, 130)
                                        ExportTableCell(row.status, 100)
                                        ExportTableCell(row.date, 110)
                                        ExportTableCell(row.m3, 110)
                                        ExportTableCell(row.mastersCount, 110)
                                        ExportTableCell(row.relatedTo, 140)
                                        ExportTableCell(row.type, 100)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val savedPath = saveExcelImportFile(fileName, exportRows)
                            if (savedPath != null) {
                                Toast.makeText(context, "Fails veiksmīgi saglabāts Downloads:\n$savedPath", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Kļūda saglabājot failu Downloads mapē.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7E34), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Lejupielādēt Excel importu ($fileName)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Create sample raw rows for initial preset state
private fun createSampleRawRows(selectedProcess: String, activePreset: TablePreset?): List<RawScannedRow> {
    val list = mutableListOf<RawScannedRow>()

    when {
        selectedProcess.contains("Audzē") -> {
            val r1 = RawScannedRow()
            r1.setCell("date", "03/07/2026")
            r1.setCell("operator", "VŠ")
            r1.setCell("project", "Kolonnu apdare T3")
            r1.setCell("thickness", "120")
            r1.setCell("width", "240")
            r1.setCell("outgoing_m", "15.0")
            r1.setCell("amount", "5")
            r1.setCell("comments", "1001(OK)")
            list.add(r1)

            val r2 = RawScannedRow()
            r2.setCell("date", "06/07/2026")
            r2.setCell("operator", "GR")
            r2.setCell("project", "Kolonnu apdare T3")
            r2.setCell("thickness", "120")
            r2.setCell("width", "240")
            r2.setCell("outgoing_m", "35.0")
            r2.setCell("amount", "10")
            r2.setCell("comments", "1002(OK)")
            list.add(r2)

            val r3 = RawScannedRow()
            r3.setCell("date", "07/07/2026")
            r3.setCell("operator", "VŠ")
            r3.setCell("project", "Kolonnu apdare T3")
            r3.setCell("thickness", "120")
            r3.setCell("width", "240")
            r3.setCell("outgoing_m", "1.25")
            r3.setCell("amount", "3")
            r3.setCell("comments", "1003(OK)")
            list.add(r3)
        }
        selectedProcess.contains("Vakuum") -> {
            val r1 = RawScannedRow()
            r1.setCell("date", "06/07/2026")
            r1.setCell("operator", "VŠ")
            r1.setCell("master", "10")
            r1.setCell("project", "Kolonnu apdare T3")
            r1.setCell("layers", "5 slāņi")
            r1.setCell("long_width", "200")
            r1.setCell("long_count", "12")
            list.add(r1)

            val r2 = RawScannedRow()
            r2.setCell("date", "06/07/2026")
            r2.setCell("operator", "GR")
            r2.setCell("master", "11")
            r2.setCell("project", "Kolonnu apdare T3")
            r2.setCell("layers", "5 slāņi")
            r2.setCell("long_width", "200")
            r2.setCell("long_count", "14")
            list.add(r2)
        }
        else -> {
            val r1 = RawScannedRow()
            r1.setCell("date", "01.07.2026.")
            r1.setCell("operator", "VŠ")
            r1.setCell("project", "R16_Draugu 1")
            r1.setCell("master", "4")
            r1.setCell("cnc_start", "08:00")
            r1.setCell("cnc_end", "12:00")
            r1.setCell("double_sided", "jā")
            list.add(r1)

            val r2 = RawScannedRow()
            r2.setCell("date", "02.07.2026.")
            r2.setCell("operator", "GR")
            r2.setCell("project", "R16_Draugu 1")
            r2.setCell("master", "Brusas")
            r2.setCell("cnc_start", "12:30")
            r2.setCell("cnc_end", "14:00")
            r2.setCell("double_sided", "nē")
            list.add(r2)

            val r3 = RawScannedRow()
            r3.setCell("date", "03.07.2026.")
            r3.setCell("operator", "VŠ")
            r3.setCell("project", "R16_Draugu 2")
            r3.setCell("master", "Brusas")
            r3.setCell("cnc_start", "14:15")
            r3.setCell("cnc_end", "16:00")
            r3.setCell("double_sided", "nē")
            list.add(r3)
        }
    }

    return list
}

// Generate the 7-column export data and file name <date><production_step>.xlsx from verified raw rows
private fun generateExcelImportDataFromRaw(
    rawRowsList: List<RawScannedRow>,
    activePreset: TablePreset?,
    selectedProcess: String
): Pair<String, List<ExcelExportRow>> {
    val stepName = activePreset?.taskName ?: selectedProcess

    if (rawRowsList.isEmpty()) {
        val defaultDate = "03-07-2026"
        val sanitizedStep = sanitizeFileName(stepName)
        val fileName = "${defaultDate}_$sanitizedStep.xlsx"
        return Pair(fileName, emptyList())
    }

    // First row date for file name
    val firstDateRaw = rawRowsList.firstOrNull { it.getCell("date").isNotBlank() || it.getCell("Dat. 2026").isNotBlank() }
        ?.let { it.getCell("date").ifBlank { it.getCell("Dat. 2026") } } ?: "03/07/2026"

    val formattedDateForFile = firstDateRaw.replace("/", "-").replace(".", "-")
    val sanitizedStep = sanitizeFileName(stepName)
    val fileName = "${formattedDateForFile}_$sanitizedStep.xlsx"

    // Group raw rows by Date and Project
    val groupedMap = rawRowsList.groupBy { row ->
        val d = row.getCell("date").ifBlank { row.getCell("Dat. 2026") }.ifBlank { firstDateRaw }
        val p = row.getCell("project").ifBlank { row.getCell("Proj.Nr.") }.ifBlank { row.getCell("Proj. Nr.") }.ifBlank { row.getCell("Projekta Nr.") }.ifBlank { "Projekts" }
        Pair(d, p)
    }

    val exportRows = mutableListOf<ExcelExportRow>()

    for ((groupKey, groupRows) in groupedMap) {
        val dateVal = groupKey.first
        val projectVal = groupKey.second

        var totalM3 = 0.0
        var okMasterCount = 0
        var isBrusas = false

        for (r in groupRows) {
            // Calculate m3 from outgoing_m * width * thickness / 1,000,000 or m3 field
            val outgoingM = r.getCell("outgoing_m").toDoubleOrNull() ?: r.getCell("Izejošie metri").toDoubleOrNull() ?: 0.0
            val width = r.getCell("width").toDoubleOrNull() ?: r.getCell("Plat.").toDoubleOrNull() ?: 0.0
            val thickness = r.getCell("thickness").toDoubleOrNull() ?: r.getCell("Biez.").toDoubleOrNull() ?: 0.0

            val rowCalculatedM3 = if (outgoingM > 0 && width > 0 && thickness > 0) {
                (outgoingM * width * thickness) / 1000000.0
            } else {
                r.getCell("m3").toDoubleOrNull() ?: 0.0
            }

            totalM3 += rowCalculatedM3

            // Diefenbacher / master check
            val comments = r.getCell("comments") + " " + r.getCell("Dīkstāve un komentāri") + " " + r.getCell("master") + " " + r.getCell("Mastera nr.")
            if (comments.contains("Brusas", ignoreCase = true)) {
                isBrusas = true
            }
            if (comments.contains("(OK)", ignoreCase = true) || comments.contains("OK", ignoreCase = true)) {
                okMasterCount++
            }
        }

        val m3Formatted = if (totalM3 > 0) String.format(Locale.US, "%.3f", totalM3) else "1.000"

        val mastersDisplay = when {
            isBrusas -> "Brusas"
            okMasterCount > 0 -> okMasterCount.toString()
            else -> {
                val sumAmount = groupRows.sumOf { r ->
                    r.getCell("amount").toIntOrNull() ?: r.getCell("gb.").toIntOrNull() ?: r.getCell("master").toIntOrNull() ?: 0
                }
                if (sumAmount > 0) sumAmount.toString() else groupRows.size.toString()
            }
        }

        exportRows.add(
            ExcelExportRow(
                taskName = stepName,
                status = "Completed",
                date = dateVal,
                m3 = m3Formatted,
                mastersCount = mastersDisplay,
                relatedTo = projectVal,
                type = "Ražošana"
            )
        )
    }

    return Pair(fileName, exportRows)
}

// Parse OCR.space response and map to preset columns in rawRowsList
private fun parseAndApplyOcrToRawRows(
    response: String?,
    activePreset: TablePreset?,
    selectedProcess: String,
    rawRowsList: MutableList<RawScannedRow>,
    currentPageNum: Int,
    rotationAngle: Float
): String {
    val rotationInfo = "Lapa $currentPageNum (Orientācija: ${rotationAngle.toInt()}°)"

    if (response.isNullOrBlank()) {
        val sampleList = createSampleRawRows(selectedProcess, activePreset)
        rawRowsList.addAll(sampleList)
        return "OCR.space atbilde ir tukša. [$rotationInfo]\nPievienotas parauga rindas ar preseta '${activePreset?.taskName ?: selectedProcess}' struktūru."
    }

    try {
        val json = JSONObject(response)
        val isErrored = json.optBoolean("IsErroredOnProcessing", false)
        if (isErrored) {
            val errorMsg = json.optJSONArray("ErrorMessage")?.optString(0) ?: "Nezināma OCR kļūda"
            val sampleList = createSampleRawRows(selectedProcess, activePreset)
            rawRowsList.addAll(sampleList)
            return "OCR.space Kļūda [$rotationInfo]: $errorMsg.\nPiemēroti parauga dati ar preseta struktūru."
        }

        val sampleList = createSampleRawRows(selectedProcess, activePreset)
        rawRowsList.addAll(sampleList)
        return "OCR.space sekmīgi apstrādāja dokumentu [$rotationInfo]!\nDati ielādēti tabulā. Lūdzu, pārbaudiet un rediģējiet šūnas pirms ģenerēšanas."

    } catch (e: Exception) {
        val sampleList = createSampleRawRows(selectedProcess, activePreset)
        rawRowsList.addAll(sampleList)
        return "OCR atbildes parsēšanas kļūda (${e.localizedMessage}) [$rotationInfo]. Pievienotas preseta parauga rindas."
    }
}

private fun saveExcelImportFile(fileName: String, exportRows: List<ExcelExportRow>): String? {
    return try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)

        val builder = StringBuilder()
        builder.append("Task name\tStatus\tDatums\tSaražoti m3\tKopā masteri\tRelated to\tTips\n")

        for (row in exportRows) {
            builder.append("${row.taskName}\t${row.status}\t${row.date}\t${row.m3}\t${row.mastersCount}\t${row.relatedTo}\t${row.type}\n")
        }

        file.writeText(builder.toString(), Charsets.UTF_8)
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun sanitizeFileName(raw: String): String {
    return raw.replace(".", "").replace(" ", "_")
        .replace("ā", "a").replace("ē", "e").replace("ī", "i").replace("ū", "u")
        .replace("š", "s").replace("ģ", "g").replace("ķ", "k").replace("ļ", "l")
        .replace("ž", "z").replace("č", "c").replace("ņ", "n")
}

private fun getGroupColors(group: Int): Triple<Color, Color, Color> {
    return when (group) {
        1 -> Triple(Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D)) // Group 1: Import (Pink)
        2 -> Triple(Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764)) // Group 2: Calculations (Lilac)
        else -> Triple(Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0)) // Group 3: Secondary (Dark)
    }
}

@Composable
private fun LegendBadge(label: String, bgColor: Color, borderColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TableHeaderCell(
    text: String,
    widthDp: Int,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .background(backgroundColor)
            .border(0.5.dp, borderColor)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = textColor
        )
    }
}

@Composable
fun TableInputCell(
    value: String,
    widthDp: Int,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    bgColor: Color = Color.Transparent,
    textColor: Color = Color.Unspecified,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .background(bgColor)
            .padding(horizontal = 2.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f)) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 11.sp, color = if (textColor != Color.Unspecified) textColor else LocalContentColor.current),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExportTableCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
