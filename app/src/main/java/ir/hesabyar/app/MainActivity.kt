package ir.hesabyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

data class Transaction(
    val title: String,
    val amount: Long,
    val income: Boolean
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HesabYarApp()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HesabYarApp() {

    var transactions by remember {
        mutableStateOf(
            listOf(
                Transaction(
                    title = "فروش روزانه",
                    amount = 8500000,
                    income = true
                ),
                Transaction(
                    title = "خرید مواد اولیه",
                    amount = 3200000,
                    income = false
                )
            )
        )
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    var isIncome by remember {
        mutableStateOf(true)
    }

    val income = transactions
        .filter { it.income }
        .sumOf { it.amount }

    val expense = transactions
        .filter { !it.income }
        .sumOf { it.amount }

    val balance = income - expense

    val formatter = DecimalFormat("#,###")

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "حساب‌یار",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        ) { paddingValues ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                item {

                    Text(
                        text = "مدیریت مالی کسب‌وکار",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {

                            Text(
                                text = "موجودی / سود",
                                style = MaterialTheme.typography.labelLarge
                            )

                            Text(
                                text = "${formatter.format(balance)} تومان",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        SummaryCard(
                            title = "درآمد",
                            value = income,
                            modifier = Modifier.weight(1f),
                            formatter = formatter
                        )

                        SummaryCard(
                            title = "هزینه",
                            value = expense,
                            modifier = Modifier.weight(1f),
                            formatter = formatter
                        )
                    }
                }

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = {
                                isIncome = true
                                showDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➕ ثبت درآمد")
                        }

                        OutlinedButton(
                            onClick = {
                                isIncome = false
                                showDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➖ ثبت هزینه")
                        }
                    }
                }

                item {

                    Text(
                        text = "آخرین تراکنش‌ها",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(transactions) { transaction ->

                    ListItem(

                        headlineContent = {
                            Text(transaction.title)
                        },

                        supportingContent = {
                            Text(
                                if (transaction.income)
                                    "درآمد"
                                else
                                    "هزینه"
                            )
                        },

                        trailingContent = {

                            Text(
                                text =
                                    if (transaction.income) {
                                        "+${formatter.format(transaction.amount)}"
                                    } else {
                                        "-${formatter.format(transaction.amount)}"
                                    },

                                fontWeight = FontWeight.Bold
                            )
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

            onDismiss = {
                showDialog = false
            },

            onAdd = { title, amount ->

                transactions =
                    listOf(
                        Transaction(
                            title = title,
                            amount = amount,
                            income = isIncome
                        )
                    ) + transactions

                showDialog = false
            }
        )
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: Long,
    modifier: Modifier,
    formatter: DecimalFormat
) {

    Card(
        modifier = modifier
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = formatter.format(value),
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "تومان",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun AddTransactionDialog(
    income: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, Long) -> Unit
) {

    var title by remember {
        mutableStateOf("")
    }

    var amount by remember {
        mutableStateOf("")
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(
                if (income)
                    "ثبت درآمد"
                else
                    "ثبت هزینه"
            )
        },

        text = {

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                OutlinedTextField(
                    value = title,

                    onValueChange = {
                        title = it
                    },

                    label = {
                        Text("عنوان")
                    },

                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,

                    onValueChange = {
                        amount = it.filter { char ->
                            char.isDigit()
                        }
                    },

                    label = {
                        Text("مبلغ به تومان")
                    },

                    singleLine = true
                )
            }
        },

        confirmButton = {

            Button(
                onClick = {

                    val number =
                        amount.toLongOrNull()

                    if (
                        title.isNotBlank() &&
                        number != null &&
                        number > 0
                    ) {

                        onAdd(
                            title,
                            number
                        )
                    }
                }
            ) {

                Text("ثبت")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text("انصراف")
            }
        }
    )
}
