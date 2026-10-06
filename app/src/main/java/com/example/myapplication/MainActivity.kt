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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.screens.AutomaticPaymentsScreen
import com.example.myapplication.ui.screens.ImportScreen
import com.example.myapplication.ui.screens.SalariesScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MainViewModel

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
fun MyApplicationApp(viewModel: MainViewModel = viewModel())
{
    val currentDestination = viewModel.currentDestination.value
    val rowsList = viewModel.rowsList

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
                        onClick = { viewModel.updateDestination(it) }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.IMPORT -> ImportScreen(
                        viewModel = viewModel,
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
