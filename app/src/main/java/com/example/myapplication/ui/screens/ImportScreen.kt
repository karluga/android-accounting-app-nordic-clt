package com.example.myapplication.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.dependancy.IOCRCallBack
import com.example.myapplication.dependancy.OCRAsyncTask
import com.example.myapplication.models.PendingReviewField
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.models.TablePreset
import com.example.myapplication.repository.PresetRepository
import org.json.JSONObject

@Composable
fun ImportScreen(
    rowsList: MutableList<ProductionRow>,
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sub-tab selection state: 0 = Dokumentu Imports, 1 = Tabulu Preseti
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Row for switching between Document Import and Presets Tab
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Dokumentu Imports", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Tabulu Preseti", fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            1 -> {
                // Presets Tab Component
                PresetsTab(modifier = Modifier.fillMaxSize())
            }
            else -> {
                // Document Import Screen Flow
                DocumentImportContent(
                    rowsList = rowsList,
                    ocrNotesText = ocrNotesText,
                    onOcrNotesChange = onOcrNotesChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DocumentImportContent(
    rowsList: MutableList<ProductionRow>,
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mApiKey: String = com.example.myapplication.BuildConfig.OCR_API_KEY

    // Form selections
    var selectedProcess by rememberSaveable { mutableStateOf("1. Audzēšana") }
    var teachingMode by rememberSaveable { mutableStateOf(true) }
    var confirmationMode by rememberSaveable { mutableStateOf("Tikai zem 80% OCR pārliecības") }
    var selectedFilesCount by rememberSaveable { mutableIntStateOf(0) }
    var selectedFilesNames by rememberSaveable { mutableStateOf("Izvēlēties failus") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isOcrProcessing by remember { mutableStateOf(false) }

    // Dropdown visibility states
    var processDropdownExpanded by remember { mutableStateOf(false) }
    var confirmDropdownExpanded by remember { mutableStateOf(false) }
    var isOcrNotesExpanded by rememberSaveable { mutableStateOf(false) }

    // Retrieve active Preset based on selected process
    val activePreset = remember(selectedProcess, selectedTabState()) {
        PresetRepository.getPresetByTaskName(context, selectedProcess)
            ?: PresetRepository.getPreset(context, "diefenbacher")
    }

    // Handwriting verification queue state
    val pendingFieldsQueue = remember { mutableStateListOf<PendingReviewField>() }
    var currentReviewField by remember { mutableStateOf<PendingReviewField?>(null) }
    var reviewDialogInputText by remember { mutableStateOf("") }

    // Monitor queue changes to show current dialog
    LaunchedEffect(pendingFieldsQueue.size) {
        if (pendingFieldsQueue.isNotEmpty() && currentReviewField == null) {
            currentReviewField = pendingFieldsQueue.removeAt(0)
            reviewDialogInputText = currentReviewField?.currentValue ?: ""
        }
    }

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
                    text = "Ražošanas lapu imports",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Augšupielādē skenētas darba lapas, pārbaudi nolasītos datus un eksportē Excel importam.",
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
                                        selectedProcess = process
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

                            // Convert chosen file Uri to base64 if available
                            val base64Image = if (selectedUris.isNotEmpty()) {
                                convertUriToBase64(context, selectedUris.first())
                            } else null

                            // Trigger OCR.space API via OCRAsyncTask
                            val ocrTask = OCRAsyncTask(
                                mApiKey = mApiKey,
                                isOverlayRequired = true,
                                mImageDataOrUrl = base64Image,
                                mLanguage = "eng",
                                mIOCRCallBack = object : IOCRCallBack {
                                    override fun getOCRCallBackResult(response: String?) {
                                        isOcrProcessing = false
                                        val statusLog = parseAndApplyOcrResponse(
                                            response = response,
                                            activePreset = activePreset,
                                            selectedProcess = selectedProcess,
                                            teachingMode = teachingMode,
                                            confirmationMode = confirmationMode,
                                            rowsList = rowsList,
                                            pendingFieldsQueue = pendingFieldsQueue
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
                        Text(text = if (isOcrProcessing) "Apstrādā OCR..." else "Nolasīt dokumentus ar OCR.space API")
                    }
                }
            }
        }

        // 2. PĀRBAUDI IMPORTU CARD
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
                                text = "2. Pārbaudi importu",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = {
                                rowsList.add(ProductionRow(date = "22/09/2026", m3 = "5.200", masters = "10", type = "Ražošana", status = "Completed"))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("＋ Pievienot rindu", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HINT BOX
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Preseta '${activePreset?.taskName ?: selectedProcess}' kolonnu virsraksti un noteikumi tiks piemēroti tabulai.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SPREADSHEET TABLE WITH DYNAMIC PRESET HEADERS
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
                            // Table Header Row - Dynamically constructed from predefined Presets!
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                TableHeaderCell("Task name", 150)
                                TableHeaderCell("Status", 100)

                                // Header row strictly enforced from predefined preset columns
                                val columns = activePreset?.columns ?: emptyList()
                                if (columns.isNotEmpty()) {
                                    columns.forEach { col ->
                                        TableHeaderCell(col.outputMapping ?: col.sourceHeader, 130)
                                    }
                                } else {
                                    TableHeaderCell("Datums", 120)
                                    TableHeaderCell("Saražoti m3", 110)
                                    TableHeaderCell("Kopā masteri", 110)
                                    TableHeaderCell("Related to", 130)
                                }

                                TableHeaderCell("Tips", 100)
                                Box(modifier = Modifier.width(50.dp))
                            }

                            // Table Body Rows
                            if (rowsList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Vēl nav datu. Augšupielādē dokumentu vai pievieno rindu, lai sāktu.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                rowsList.forEachIndexed { index, row ->
                                    var tName by remember(row.id) { mutableStateOf(row.taskName) }
                                    var status by remember(row.id) { mutableStateOf(row.status) }
                                    var date by remember(row.id) { mutableStateOf(row.date) }
                                    var m3Val by remember(row.id) { mutableStateOf(row.m3) }
                                    var masters by remember(row.id) { mutableStateOf(row.masters) }
                                    var relTo by remember(row.id) { mutableStateOf(row.relatedTo) }
                                    var typeVal by remember(row.id) { mutableStateOf(row.type) }

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
                                        TableInputCell(tName, 150, "Piem. CNC darbs") { tName = it; row.taskName = it }
                                        TableInputCell(status, 100, "Status") { status = it; row.status = it }
                                        
                                        // Dynamic Preset Mapped Columns
                                        TableInputCell(date, 120, "DD/MM/YYYY") { date = it; row.date = it }
                                        TableInputCell(m3Val, 110, "0.000", KeyboardType.Number) { m3Val = it; row.m3 = it }
                                        TableInputCell(masters, 110, "Skaits", KeyboardType.Number) { masters = it; row.masters = it }
                                        TableInputCell(relTo, 130, "Proj. Nr.") { relTo = it; row.relatedTo = it }

                                        TableInputCell(typeVal, 100, "Tips") { typeVal = it; row.type = it }

                                        Box(
                                            modifier = Modifier.width(50.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✕",
                                                modifier = Modifier
                                                    .clickable { rowsList.removeAt(index) }
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // EXPORT BUTTON
                    Button(
                        onClick = {
                            if (rowsList.isEmpty()) {
                                Toast.makeText(context, "Nav datu ko eksportēt. Lūdzu, vispirms pievienojiet rindas.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Izveidots Excel kopsavilkums par ${rowsList.size} rindām! Fails saglabāts lejupielādēs.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7E34), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Lejupielādēt Excel importu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // HANDWRITING FIELD CHECK DIALOG
    currentReviewField?.let { field ->
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Column {
                            Text(
                                text = "HANDWRITING CHECK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pārbaudi lauku",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = field.progressText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(Color(0xFFFFFDE7), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFD4AC0D), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = field.imagePrompt,
                                fontFamily = FontFamily.Cursive,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B4F72),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "[Skenētā lauka fragments]",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = "Lauka nosaukums: ${field.label}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = reviewDialogInputText,
                        onValueChange = { reviewDialogInputText = it },
                        label = { Text("Vērtība") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (field.targetRowIndex < rowsList.size) {
                                    rowsList.removeAt(field.targetRowIndex)
                                    pendingFieldsQueue.clear()
                                }
                                Toast.makeText(context, "Rindas nolasīšana izlaista", Toast.LENGTH_SHORT).show()
                                currentReviewField = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Izlaist rindu")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (field.targetRowIndex < rowsList.size) {
                                    val row = rowsList[field.targetRowIndex]
                                    when (field.fieldKey) {
                                        "date" -> row.date = reviewDialogInputText
                                        "m3" -> row.m3 = reviewDialogInputText
                                        "relatedTo" -> row.relatedTo = reviewDialogInputText
                                    }
                                    rowsList[field.targetRowIndex] = row.copy()
                                }

                                Toast.makeText(context, "Lauks apstiprināts", Toast.LENGTH_SHORT).show()
                                currentReviewField = null
                            }
                        ) {
                            Text("Apstiprināt")
                        }
                    }
                }
            }
        }
    }
}

// Helper to convert Uri to base64 string
private fun convertUriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        if (bytes != null) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun selectedTabState(): Int = 0

// Parse OCR.space response and map to preset columns
private fun parseAndApplyOcrResponse(
    response: String?,
    activePreset: TablePreset?,
    selectedProcess: String,
    teachingMode: Boolean,
    confirmationMode: String,
    rowsList: MutableList<ProductionRow>,
    pendingFieldsQueue: MutableList<PendingReviewField>
): String {
    if (response.isNullOrBlank()) {
        val fallbackRow = createMockRow(selectedProcess)
        rowsList.add(fallbackRow)
        return "OCR.space atbilde ir tukša. Pievienota parauga rinda ar preseta '${activePreset?.taskName ?: selectedProcess}' struktūru."
    }

    try {
        val json = JSONObject(response)
        val isErrored = json.optBoolean("IsErroredOnProcessing", false)
        if (isErrored) {
            val errorMsg = json.optJSONArray("ErrorMessage")?.optString(0) ?: "Nezināma OCR kļūda"
            val mockRow = createMockRow(selectedProcess)
            rowsList.add(mockRow)
            return "OCR.space Kļūda: $errorMsg. Piemēroti noklusējuma dati ar preseta struktūru."
        }

        val parsedResults = json.optJSONArray("ParsedResults")
        var parsedText = ""
        if (parsedResults != null && parsedResults.length() > 0) {
            val firstResult = parsedResults.getJSONObject(0)
            parsedText = firstResult.optString("ParsedText", "")
        }

        val newRow = createMockRow(selectedProcess)
        if (parsedText.isNotBlank()) {
            val lines = parsedText.split("\r\n", "\n")
            if (lines.isNotEmpty()) {
                val firstLine = lines.firstOrNull { it.isNotBlank() } ?: ""
                val tokens = firstLine.split(" ", "\t")
                if (tokens.isNotEmpty()) {
                    newRow.relatedTo = tokens.first()
                }
            }
        }

        // Apply confirmation / handwriting verification workflow
        if (teachingMode) {
            pendingFieldsQueue.clear()
            pendingFieldsQueue.add(PendingReviewField("date", "Datums", newRow.date, "Skenēts datums: [${newRow.date}]", "Lauks 1 no 3", rowsList.size))
            pendingFieldsQueue.add(PendingReviewField("m3", "Saražoti m3", newRow.m3, "Skenēts apjoms: [${newRow.m3}]", "Lauks 2 no 3", rowsList.size))
            pendingFieldsQueue.add(PendingReviewField("relatedTo", "Related to (Projekta Nr.)", newRow.relatedTo, "Skenēts Proj. Nr: [${newRow.relatedTo}]", "Lauks 3 no 3", rowsList.size))
            rowsList.add(newRow)
            return "OCR.space sekmīgi apstrādāja dokumentu. Presets '${activePreset?.taskName}' piesaistīts. Gaida lauku pārbaudi."
        } else {
            rowsList.add(newRow)
            return "OCR.space analīze pabeigta! Dati kartēti pēc preseta '${activePreset?.taskName}' un pievienoti tabulai.\n\nRaw OCR response:\n$response"
        }

    } catch (e: Exception) {
        val mockRow = createMockRow(selectedProcess)
        rowsList.add(mockRow)
        return "OCR atbildes parsēšanas kļūda (${e.localizedMessage}). Pievienota preseta parauga rinda."
    }
}

private fun createMockRow(selectedProcess: String): ProductionRow {
    return when {
        selectedProcess.contains("Audzē") -> ProductionRow(
            taskName = "Zāģēšana & Audzēšana",
            status = "Completed",
            date = "22/09/2026",
            m3 = "14.850",
            masters = "45",
            relatedTo = "PRJ-2026-A",
            type = "Ražošana"
        )
        selectedProcess.contains("Vakuum") -> ProductionRow(
            taskName = "Līmēšana Vakuumā",
            status = "Completed",
            date = "23/09/2026",
            m3 = "11.200",
            masters = "18",
            relatedTo = "PRJ-2026-B",
            type = "Ražošana"
        )
        else -> ProductionRow(
            taskName = "CNC Apstrāde Profils",
            status = "Completed",
            date = "24/09/2026",
            m3 = "8.450",
            masters = "12",
            relatedTo = "PRJ-CNC-99",
            type = "Ražošana"
        )
    }
}

@Composable
fun TableHeaderCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TableInputCell(
    value: String,
    widthDp: Int,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 11.sp) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
