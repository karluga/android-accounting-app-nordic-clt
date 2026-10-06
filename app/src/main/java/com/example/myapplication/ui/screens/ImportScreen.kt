package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.repository.PresetRepository
import com.example.myapplication.viewmodel.MainViewModel

@Composable
fun ImportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val selectedTab = viewModel.selectedImportTab.intValue
    val selectedProcess = viewModel.selectedProcess.value
    val rawRowsList = viewModel.rawRowsList

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
                onClick = { viewModel.updateImportTab(0) },
                text = { Text("1. Dokumentu apstrāde", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.updateImportTab(1) },
                text = { Text("2. Lejupielādēt Excel importu", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { viewModel.updateImportTab(2) },
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
                    viewModel = viewModel,
                    activePreset = activePreset,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
