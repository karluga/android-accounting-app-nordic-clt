package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.sp
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.ui.screens.AutomaticPaymentsScreen
import com.example.myapplication.ui.screens.ImportScreen
import com.example.myapplication.ui.screens.SalariesScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    IMPORT("Imports", R.drawable.ic_import),
    SALARIES("Algas", R.drawable.ic_salaries),
    PAYMENTS("Maksājumi", R.drawable.ic_payments),
}

@PreviewScreenSizes
@Composable
fun MyApplicationApp()
{
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.IMPORT) }

    // Global state for shared table rows so salaries screen can dynamically calculate based on it
    val rowsList = remember { mutableStateListOf<ProductionRow>() }

    // OCR notes state
    var ocrNotesText by rememberSaveable {
        mutableStateOf("Sistēma gatava darbam. Gaida dokumentu augšupielādi...")
    }

    SelectionContainer {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach {
                    item(
                        icon = {
                            Icon(
                                painter = painterResource(it.icon),
                                contentDescription = it.label,
                                tint = if (it == AppDestinations.IMPORT && currentDestination == AppDestinations.IMPORT) Color.Unspecified else LocalContentColor.current
                            )
                        },
                        label = {
                            Text(
                                text = it.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.IMPORT -> ImportScreen(
                        rowsList = rowsList,
                        ocrNotesText = ocrNotesText,
                        onOcrNotesChange = { ocrNotesText = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                    AppDestinations.SALARIES -> SalariesScreen(
                        rowsList = rowsList,
                        modifier = Modifier.padding(innerPadding)
                    )
                    AppDestinations.PAYMENTS -> AutomaticPaymentsScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
