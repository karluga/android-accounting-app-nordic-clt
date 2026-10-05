package com.example.myapplication.ui.screens

import android.R
import android.app.Activity
import android.app.ProgressDialog
import android.os.AsyncTask
import android.util.Log
import android.widget.TextView
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
import com.example.myapplication.models.PendingReviewField
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.dependancy.OCRAsyncTask
import com.example.myapplication.dependancy.IOCRCallBack
import com.example.myapplication.MainActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection


@Composable
fun ImportScreen(
    rowsList: MutableList<ProductionRow>,
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val mAPiKey: String = "K89893470788957" //TODO Add your own Registered API key
    var isOverlayRequired: Boolean
    var mImageUrl: String
    var mLanguage: String
    var mTxtResult: TextView
    var mIOCRCallBack: IOCRCallBack

    // Form selections
    var selectedProcess by rememberSaveable { mutableStateOf("1. Audzēšana") }
    var teachingMode by rememberSaveable { mutableStateOf(true) }
    var confirmationMode by rememberSaveable { mutableStateOf("Tikai zem 80% OCR pārliecības") }
    var selectedFilesCount by rememberSaveable { mutableIntStateOf(0) }
    var selectedFilesNames by rememberSaveable { mutableStateOf("Izvēlēties failus") }

    // Dropdown visibility states
    var processDropdownExpanded by remember { mutableStateOf(false) }
    var confirmDropdownExpanded by remember { mutableStateOf(false) }
    var isOcrNotesExpanded by rememberSaveable { mutableStateOf(false) }

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

    //need to fix
    fun init(mAPiKey: String, isOverlayRequired: Boolean, mImageUrl: String, mLanguage: String, mIOCRCallBack: IOCRCallBack, activity: Activity = this@MainActivity)
    {
        mTxtResult = activity.findViewById(R.id.actual_result)
        val btnCallAPI: TextView? = activity.findViewById<TextView>(R.id.btn_call_api)

        btnCallAPI?.setOnClickListener{
            val oCRAsyncTask = OCRAsyncTask(
                this@MainActivity,
                mAPiKey,
                isOverlayRequired,
                mImageUrl,
                mLanguage,
                mIOCRCallBack
            )
            oCRAsyncTask.execute()
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

                    // Ražošanas solis
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedProcess,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ražošanas solis") },
                            trailingIcon = {
                                Text(
                                    text = "▼",
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable {
                                            processDropdownExpanded = !processDropdownExpanded
                                        },
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
                                        .clickable {
                                            confirmDropdownExpanded = !confirmDropdownExpanded
                                        },
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

                    // File picker linked to actual Android system intent chooser contract
                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetMultipleContents()
                    ) { uris ->
                        if (uris.isNotEmpty()) {
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
                        onClick = {
                            // Launch standard system document/image picker contract channel
                            filePickerLauncher.launch("*/*")
                        },
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
                            if (selectedFilesCount == 0) {
                                Toast.makeText(context, "Lūdzu, vispirms pievienojiet dokumentus (uzklikšķiniet uz failu pogas)", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // Trigger PP-Structure document analysis mockup logic
                            Toast.makeText(context, "Uzsākta PP-Structure viedā dokumentu analīze...", Toast.LENGTH_SHORT).show()

                            //stuff

                            //var IOCRCallBack = mIOCRCallBack
                            //IOCRCallBack = this
                            mImageUrl = selectedFilesNames
                            mLanguage = "lv"
                            isOverlayRequired = true
                            init(MainActivity, )

                            // Pre-create simulated rows based on selected step
                            val mockRow = when (selectedProcess) {
                                "1. Audzēšana" -> ProductionRow(
                                    taskName = "Zāģēšana & Audzēšana",
                                    status = "Completed",
                                    date = "22/09/2026",
                                    m3 = "14.850",
                                    masters = "45",
                                    relatedTo = "PRJ-2026-A",
                                    type = "Ražošana"
                                )
                                "3. Vakuumprese" -> ProductionRow(
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

                            // Determine if we need to launch handwriting review dialog workflow
                            if (teachingMode) {
                                // In teaching mode, ask for validation for key fields
                                pendingFieldsQueue.clear()
                                pendingFieldsQueue.add(PendingReviewField("date", "Datums", mockRow.date, "Skenēts datums: [22/09/2026]", "Lauks 1 no 3", rowsList.size))
                                pendingFieldsQueue.add(PendingReviewField("m3", "Saražoti m3", mockRow.m3, "Skenēts apjoms: [" + mockRow.m3 + "]", "Lauks 2 no 3", rowsList.size))
                                pendingFieldsQueue.add(PendingReviewField("relatedTo", "Related to (Projekta Nr.)", mockRow.relatedTo, "Skenēts Proj. Nr: [" + mockRow.relatedTo + "]", "Lauks 3 no 3", rowsList.size))

                                // Insert base row which will get updated by fields
                                rowsList.add(mockRow)
                                onOcrNotesChange("PP-Structure identificēja tabulas struktūru. Gaida lauku rokasraksta verifikāciju mācīšanas režīmā.")
                            } else if (confirmationMode == "Tikai zem 80% OCR pārliecības") {
                                // Simulate one low-confidence field (e.g. Project Code had 68% confidence)
                                pendingFieldsQueue.clear()
                                pendingFieldsQueue.add(PendingReviewField("relatedTo", "Related to (Zema OCR pārliecība: 68%)", mockRow.relatedTo, "Neskaidrs Proj. Nr: [PRJ-????]", "Zema pārliecība 1 no 1", rowsList.size))

                                rowsList.add(mockRow)
                                onOcrNotesChange("PP-Structure analīze pabeigta. Atrasts 1 lauks ar zemu pārliecību (< 80%). Atvērts pārbaudes dialogs.")
                            } else {
                                // Bypassed confirmation completely
                                rowsList.add(mockRow)
                                onOcrNotesChange("PP-Structure sekmīgi nolasīja dokumentu bez apstiprinājumiem. OCR pārliecība virs 92%. Dati pievienoti tabulai.")
                                Toast.makeText(context, "Dati veiksmīgi nolasīti un pievienoti tabulai!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Nolasīt dokumentus")
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
                            text = "Audzēšana: m³ = Izejošie metri × Plat. × Biez. ÷ 1 000 000.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SPREADSHEET TABLE WITH HORIZONTAL SCROLL
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
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                TableHeaderCell("Task name", 150)
                                TableHeaderCell("Status", 100)
                                TableHeaderCell("Datums", 120)
                                TableHeaderCell("Saražoti m3", 110)
                                TableHeaderCell("Kopā masteri", 110)
                                TableHeaderCell("Related to", 130)
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
                                        text = "Vēl nav datu. Augšupielādē dokumentu, lai sāktu.",
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
                                    text = "OCR piezīmes",
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
                            if (rowsList.isEmpty())
                            {
                                Toast.makeText(context, "Nav datu ko eksportēt. Lūdzu, vispirms pievienojiet rindas.", Toast.LENGTH_LONG).show()
                            }
                            else
                            {
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