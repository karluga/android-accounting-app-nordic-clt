package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.repository.PresetRepository

@Composable
fun ImportScreen(
    ocrNotesText: String,
    onOcrNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowsList: MutableList<ProductionRow> = mutableListOf()
) {
    val context = LocalContext.current

    // Sub-tab selection state: 0 = Dokumentu apstrāde, 1 = Lejupielādēt Excel importu, 2 = Tabulu Preseti
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Active selected process step
    var selectedProcess by rememberSaveable { mutableStateOf("1. Audzēšana") }

    // List of raw scanned rows for Tab 1 editing (starts empty until document is scanned)
    val rawRowsList = remember { mutableStateListOf<RawScannedRow>() }

    // Retrieve active Preset
    val activePreset = remember(selectedProcess) {
        PresetRepository.getPresetByTaskName(context, selectedProcess)
            ?: PresetRepository.getPreset(context, "diefenbacher")
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Row for switching between Document Processing, Excel Export, and Presets Tab
        SecondaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
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
                text = { Text("Tabulu Preseti", fontWeight = FontWeight.Bold) }
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
                        // Headers automatically update to active preset; rawRowsList holds user/scanned data
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
