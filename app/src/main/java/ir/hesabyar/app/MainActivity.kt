package ir.hesabyar.app

import android.content.Context
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject

data class Transaction(
    val title: String,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val date: String,
    val remoteId: Long? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // فقط هنگام ایجاد واقعی MainActivity اعتبار جلسه را بررسی می‌کنیم.
        // بررسی onResume حذف شده تا بعد از ورود، بین LoginActivity و MainActivity
        // یک redirect ناخواسته ایجاد نشود.
        if (!hasValidLogin(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra("session_expired", true)
            })
            finish()
            return
        }

        setContent { HesabYarApp() }
    }
}

private const val PREFS = "hesabyar_data"
private const val KEY_TRANSACTIONS = "transactions"
private const val KEY_WELCOME_SHOWN = "welcome_shown"
private const val KEY_LOGIN_AT = "supabase_login_at"
private const val LOGIN_TIMEOUT_MS = 24L * 60L * 60L * 1000L

private fun hasValidLogin(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val accessToken = prefs.getString("supabase_access_token", null)
    val userId = prefs.getString("supabase_user_id", null)

    if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) return false

    val loginAt = prefs.getLong(KEY_LOGIN_AT, 0L)
    if (loginAt <= 0L) {
        prefs.edit().putLong(KEY_LOGIN_AT, System.currentTimeMillis()).apply()
        return true
    }

    if (System.currentTimeMillis() - loginAt >= LOGIN_TIMEOUT_MS) {
        prefs.edit()
            .remove("supabase_access_token")
            .remove("supabase_refresh_token")
            .remove("supabase_user_id")
            .remove(KEY_LOGIN_AT)
            .apply()
        return false
    }

    return true
}

private fun shareText(context: Context, subject: String, text: String, mime: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }, "اشتراک‌گذاری"))
}

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
                o.optString("category", "عمومی"), o.optString("date", "امروز"), o.optLong("remoteId").takeIf { it != 0L })
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
            it.remoteId?.let { id -> put("remoteId", id) }
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabYarApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val accessToken = prefs.getString("supabase_access_token", null) ?: return
    val userId = prefs.getString("supabase_user_id", null) ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    var syncMessage by remember { mutableStateOf("") }
    var showSplash by remember { mutableStateOf(true) }
    var showWelcome by remember { mutableStateOf(!prefs.getBoolean(KEY_WELCOME_SHOWN, false)) }
    var transactions by remember { mutableStateOf(loadTransactions(context)) }

    DisposableEffect(lifecycleOwner, accessToken, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !accessToken.isNullOrBlank()) {
                scope.launch {
                    try {
                        val remote = withContext(Dispatchers.IO) {
                            SupabaseApi.getTransactions(accessToken)
                        }
                        val mapped = remote.map { t ->
                            Transaction(t.title, t.amount, t.income, t.category, t.date, t.id)
                        }
                        transactions = mapped
                        saveTransactions(context, mapped)
                        syncMessage = ""
                    } catch (e: Exception) {
                        syncMessage = e.message ?: "خطا در تازه‌سازی اطلاعات"
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accessToken, userId) {
        try {
            val remote = withContext(Dispatchers.IO) { SupabaseApi.getTransactions(accessToken) }
            if (remote.isNotEmpty() || !prefs.contains(KEY_TRANSACTIONS)) {
                val mapped = remote.map { t ->
                    Transaction(t.title, t.amount, t.income, t.category, t.date, t.id)
                }
                transactions = mapped
                saveTransactions(context, mapped)
            } else {
                val local = transactions
                if (local.isNotEmpty()) {
                    val migrated = mutableListOf<Transaction>()
                    local.forEach { localItem ->
                        try {
                            val id = withContext(Dispatchers.IO) {
                                SupabaseApi.insertTransaction(accessToken, localItem, userId)
                            }
                            migrated.add(localItem.copy(remoteId = id))
                        } catch (_: Exception) {
                            migrated.add(localItem)
                        }
                    }
                    transactions = migrated
                    saveTransactions(context, migrated)
                }
            }
        } catch (e: Exception) {
            syncMessage = e.message ?: "خطا در همگام‌سازی"
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }
    var showDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var isIncome by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = buildString {
                append("\uFEFF")
                append("عنوان,مبلغ,نوع,دسته‌بندی,تاریخ\n")
                transactions.forEach { t ->
                    val title = t.title.replace("\"", "\"\"")
                    val category = t.category.replace("\"", "\"\"")
                    val type = if (t.income) "درآمد" else "هزینه"
                    append("\"" + title + "\"," + t.amount + "," + type + ",\"" + category + "\",\"" + t.date + "\"\n")
                }
            }
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // The user can retry export if the selected location is unavailable.
            }
        }
    }

    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expense = transactions.filter { !it.income }.sumOf { it.amount }
    val balance = income - expense
    val formatter = DecimalFormat("#,###")
    val nowMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val monthItems = transactions.filter { it.date.startsWith(nowMonth) }
    val monthIncome = monthItems.filter { it.income }.sumOf { it.amount }
    val monthExpense = monthItems.filter { !it.income }.sumOf { it.amount }
    val monthProfit = monthIncome - monthExpense
    val maxMonth = maxOf(monthIncome, monthExpense, 1L)

    if (showSplash) {
        SplashScreen()
    } else if (showWelcome) {
        WelcomeScreen {
            prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply()
            showWelcome = false
        }
    } else {
        val visibleTransactions = transactions.filter {
            val textMatch = search.isBlank() || it.title.contains(search, true) || it.category.contains(search, true)
            val filterMatch = filter == "all" || (filter == "income" && it.income) || (filter == "expense" && !it.income)
            textMatch && filterMatch
        }

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
                        if (syncMessage.isNotBlank()) {
                            Text(syncMessage, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("موجودی / سود", style = MaterialTheme.typography.labelLarge)
                                Text("${formatter.format(balance)} تومان", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("گزارش ماه جاری", fontWeight = FontWeight.Bold)
                                Text("درآمد ماه: " + formatter.format(monthIncome) + " تومان")
                                Text("هزینه ماه: " + formatter.format(monthExpense) + " تومان")
                                Text("سود ماه: " + formatter.format(monthProfit) + " تومان")
                                Text("نمودار درآمد")
                                LinearProgressIndicator(progress = { (monthIncome.toFloat() / maxMonth).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("نمودار هزینه")
                                LinearProgressIndicator(progress = { (monthExpense.toFloat() / maxMonth).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جستجوی عنوان یا دسته‌بندی") }, singleLine = true)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterButton("همه", filter == "all", Modifier.weight(1f)) { filter = "all" }
                                FilterButton("درآمد", filter == "income", Modifier.weight(1f)) { filter = "income" }
                                FilterButton("هزینه", filter == "expense", Modifier.weight(1f)) { filter = "expense" }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton({
                                csvLauncher.launch("HesabYar_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".csv")
                            }, Modifier.weight(1f)) { Text("خروجی Excel") }
                            OutlinedButton({
                                val arr = JSONArray()
                                transactions.forEach { t ->
                                    arr.put(JSONObject().apply {
                                        put("title", t.title); put("amount", t.amount); put("income", t.income)
                                        put("category", t.category); put("date", t.date)
                                    })
                                }
                                shareText(context, "پشتیبان حساب‌یار", arr.toString(2), "application/json")
                            }, Modifier.weight(1f)) { Text("پشتیبان JSON") }
                        }
                    }
                    item {
                        OutlinedButton({
                            transactions = emptyList()
                            saveTransactions(context, emptyList())
                        }, Modifier.fillMaxWidth()) { Text("پاک‌کردن همه تراکنش‌ها") }
                    }
                    item { Text("تراکنش‌ها (" + visibleTransactions.size + ")", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

                    itemsIndexed(visibleTransactions) { _, transaction ->
                        val index = transactions.indexOf(transaction)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(transaction.title, fontWeight = FontWeight.Bold)
                                        Text("${transaction.category} • ${transaction.date}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        if (transaction.income) "+${formatter.format(transaction.amount)}"
                                        else "-${formatter.format(transaction.amount)}",
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
                                        transaction.remoteId?.let { remoteId ->
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { SupabaseApi.deleteTransaction(accessToken, remoteId) }
                                                } catch (e: Exception) {
                                                    syncMessage = e.message ?: "خطا در حذف ابری"
                                                }
                                            }
                                        }
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
                    val edit = editingIndex
                    val existing = edit?.let { transactions[it] }
                    val item = Transaction(title, amount, isIncome, category, date, existing?.remoteId)
                    if (edit == null) updated.add(0, item) else updated[edit] = item
                    transactions = updated
                    saveTransactions(context, updated)
                    scope.launch {
                        try {
                            if (existing?.remoteId != null) {
                                withContext(Dispatchers.IO) {
                                    SupabaseApi.updateTransaction(accessToken, existing.remoteId, item)
                                }
                            } else {
                                val id = withContext(Dispatchers.IO) {
                                    SupabaseApi.insertTransaction(accessToken, item, userId)
                                }
                                val current = transactions.toMutableList()
                                val target = if (edit == null) 0 else edit
                                if (target in current.indices) {
                                    current[target] = current[target].copy(remoteId = id)
                                    transactions = current
                                    saveTransactions(context, current)
                                }
                            }
                        } catch (e: Exception) {
                            syncMessage = e.message ?: "ذخیره ابری انجام نشد"
                        }
                    }
                    showDialog = false
                    editingIndex = null
                }
            )
        }
    }
}

@Composable
fun SplashScreen() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hesabyar_splash),
                    contentDescription = "لوگوی حساب‌یار",
                    modifier = Modifier.size(210.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "حساب‌یار",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "مدیریت هوشمند حساب‌های شما",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {Spacer(Modifier.height(24.dp))
                Text("حساب‌یار", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("به حساب‌یار خوش آمدید", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "مدیریت ساده، سریع و هوشمند حساب‌های شما\nدرآمد، هزینه و گزارش‌های مالی در یکجا",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(36.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("شروع کنیم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text("اطلاعات شما در این دستگاه ذخیره می‌شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FilterButton(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick, modifier) { Text(title) }
    else OutlinedButton(onClick, modifier) { Text(title) }
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
