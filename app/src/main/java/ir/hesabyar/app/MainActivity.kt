package ir.hesabyar.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class Transaction(
    val title: String,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HesabYarApp() }
    }
}

private const val PREFS = "hesabyar_data"
private const val KEY_TRANSACTIONS = "transactions"

private fun loadTransactions(context: Context): List<Transaction> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TRANSACTIONS, null) ?: return listOf(
        Transaction("فروش روزانه", 8500000, true, "فروش", "امروز"),
        Transaction("خرید مواد اولیه", 3200000, false, "خرید", "امروز")
    )
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Transaction(o.getString("title"), o.getLong("amount"), o.getBoolean("income"),
                o.optString("category", "عمومی"), o.optString("date", "امروز"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveTransactions(context: Context, list: List<Transaction>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("title", it.title)
            put("amount", it.amount)
            put("income", it.income)
            put("category", it.category)
            put("date", it.date)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabYarApp() {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf(loadTransactions(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var isIncome by remember { mutableStateOf(true) }

    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expense = transactions.filter { !it.income }.sumOf { it.amount }
    val balance = income - expense
    val formatter = DecimalFormat("#,###")

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("حساب‌یار", fontWeight = FontWeight.Bold) })
        }) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Text("مدیریت مالی کسب‌وکار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("موجودی / سود", style = MaterialTheme.typography.labelLarge)
                            Text("\${formatter.format(balance)} تومان", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("درآمد", income, Modifier.weight(1f), formatter)
                        SummaryCard("هزینه", expense, Modifier.weight(1f), formatter)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ isIncome = true; editingIndex = null; showDialog = true }, Modifier.weight(1f)) { Text("➕ ثبت درآمد") }
                        OutlinedButton({ isIncome = false; editingIndex = null; showDialog = true }, Modifier.weight(1f)) { Text("➖ ثبت هزینه") }
                    }
                }
                item { Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                itemsIndexed(transactions) { index, transaction ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.title, fontWeight = FontWeight.Bold)
                                    Text("\${transaction.category} • \${transaction.date}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    if (transaction.income) "+\${formatter.format(transaction.amount)}"
                                    else "-\${formatter.format(transaction.amount)}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton({
                                    isIncome = transaction.income
                                    editingIndex = index
                                    showDialog = true
                                }) { Text("ویرایش") }
                                TextButton({
                                    val updated = transactions.toMutableList()
                                    updated.removeAt(index)
                                    transactions = updated
                                    saveTransactions(context, updated)
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddTransactionDialog(
            initial = editingIndex?.let { transactions[it] },
            income = isIncome,
            onDismiss = { showDialog = false },
            onSave = { title, amount, category ->
                val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
                val updated = transactions.toMutableList()
                val item = Transaction(title, amount, isIncome, category, date)
                val edit = editingIndex
                if (edit == null) updated.add(0, item) else updated[edit] = item
                transactions = updated
                saveTransactions(context, updated)
                showDialog = false
                editingIndex = null
            }
        )
    }
}

@Composable
fun SummaryCard(title: String, value: Long, modifier: Modifier, formatter: DecimalFormat) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(formatter.format(value), fontWeight = FontWeight.Bold)
            Text("تومان", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AddTransactionDialog(
    initial: Transaction?,
    income: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) { if (income) "ثبت درآمد" else "ثبت هزینه" } else "ویرایش تراکنش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("مبلغ به تومان") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, singleLine = true)
            }
        },
        confirmButton = {
            Button({
                val number = amount.toLongOrNull()
                if (title.isNotBlank() && number != null && number > 0)
                    onSave(title, number, category.ifBlank { "عمومی" })
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
