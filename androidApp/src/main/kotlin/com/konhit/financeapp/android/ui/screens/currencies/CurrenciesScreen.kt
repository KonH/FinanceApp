package com.konhit.financeapp.android.ui.screens.currencies

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesScreen(onBack: () -> Unit) {
    val viewModel: CurrenciesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currencies") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.currencies, key = { it.id }) { currency ->
                ListItem(
                    headlineContent = { Text(currency.name) },
                    supportingContent = {
                        val symbol = currency.currencySymbol ?: currency.pfxSymbol ?: currency.sfxSymbol
                        if (symbol != null) Text(symbol)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
