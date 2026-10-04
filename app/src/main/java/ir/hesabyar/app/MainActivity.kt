package ir.hesabyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import androidx.compose.ui.platform.LocalLayoutDirection

data class Transaction(val title: String, val amount: Long, val income: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HesabYarApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabYarApp() {
    var transactions by remember {
        mutableStateOf(listOf(
            Transaction("فروش روزانه", 8500000, true),
            Transaction("خرید مواد اولیه", 3200000, false)
        ))
    }
    var showDialog by remember { mutableStateOf(false) }
    var isIncome by remember { mutableStateOf(true) }

    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expense = transactions.filter { !it.income }.sumOf { it.amount }
    val balance = income - expense
    val fmt = DecimalFormat("#,###")

   CompositionLocalProvider(
    LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) 
            Scaffold(
            topBar = {
                TopAppBar(title = { Text("حساب‌یار", fontWeight = FontWeight.Bold) })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("مدیریت مالی کسب‌وکار", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("موجودی / سود", style = MaterialTheme.typography.labelLarge)
                            Text("${fmt.format(balance)} تومان",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("درآمد", income, Modifier.weight(1f), fmt)
                        SummaryCard("هزینه", expense, Modifier.weight(1f), fmt)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { isIncome = true; showDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("➕ ثبت درآمد") }
                        OutlinedButton(
                            onClick = { isIncome = false; showDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("➖ ثبت هزینه") }
                    }
                }
                item { Text("آخرین تراکنش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(transactions) { tx ->
                    ListItem(
                        headlineContent = { Text(tx.title) },
                        supportingContent = { Text(if (tx.income) "درآمد" else "هزینه") },
                        trailingContent = {
                            Text("${if (tx.income) "+" else "-"}${fmt.format(tx.amount)}",
                                fontWeight = FontWeight.Bold)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showDialog) {
        AddTransactionDialog(
            income = isIncome,
            onDismiss = { showDialog = false },
            onAdd = { title, amount ->
                transactions = listOf(Transaction(title, amount, isIncome)) + transactions
                showDialog = false
            }
        )
    }
}

@Composable
fun SummaryCard(title: String, value: Long, modifier: Modifier, fmt: DecimalFormat) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text("${fmt.format(value)}", fontWeight = FontWeight.Bold)
            Text("تومان", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AddTransactionDialog(income: Boolean, onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (income) "ثبت درآمد" else "ثبت هزینه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("عنوان") }, singleLine = true
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("مبلغ به تومان") }, singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = amount.toLongOrNull()
                if (title.isNotBlank() && n != null && n > 0) onAdd(title, n)
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
