package ir.hesabyar.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoginScreen() }
    }

    @Composable
    private fun LoginScreen() {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        var success by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var registerMode by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val prefs = getSharedPreferences("hesabyar_data", Context.MODE_PRIVATE)

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val scheme = MaterialTheme.colorScheme

            Surface(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF071A33),
                                    Color(0xFF0D2D4F),
                                    Color(0xFFF6F8FC)
                                )
                            )
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(18.dp))

                        Box(
                            Modifier
                                .size(82.dp)
                                .background(
                                    Color.White.copy(alpha = 0.14f),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ح",
                                color = Color.White,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "حساب‌یار",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "مدیریت هوشمند مالی کسب‌وکار شما",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(28.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = scheme.surface.copy(alpha = 0.98f)
                            )
                        ) {
                            Column(
                                Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    if (registerMode) "ساخت حساب جدید" else "خوش آمدید 👋",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    if (registerMode)
                                        "برای شروع، اطلاعات حساب خود را وارد کنید."
                                    else
                                        "برای ادامه وارد حساب حساب‌یار شوید.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = {
                                        email = it
                                        error = ""
                                    },
                                    label = { Text("ایمیل") },
                                    placeholder = { Text("example@email.com") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        error = ""
                                    },
                                    label = { Text("رمز عبور") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (registerMode) {
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = {
                                            confirmPassword = it
                                            error = ""
                                        },
                                        label = { Text("تکرار رمز عبور") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (error.isNotBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = scheme.errorContainer
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            error,
                                            modifier = Modifier.padding(12.dp),
                                            color = scheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (success.isNotBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = scheme.primaryContainer
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            success,
                                            modifier = Modifier.padding(12.dp),
                                            color = scheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Button(
                                    enabled = !loading &&
                                        email.isNotBlank() &&
                                        password.isNotBlank() &&
                                        (!registerMode || confirmPassword.isNotBlank()),
                                    onClick = {
                                        error = ""
                                        success = ""

                                        if (registerMode) {
                                            if (password.length < 6) {
                                                error = "رمز عبور باید حداقل ۶ کاراکتر باشد."
                                                return@Button
                                            }
                                            if (password != confirmPassword) {
                                                error = "رمز عبور و تکرار آن یکسان نیست."
                                                return@Button
                                            }

                                            loading = true
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        SupabaseApi.signUp(email.trim(), password)
                                                    }
                                                    registerMode = false
                                                    confirmPassword = ""
                                                    password = ""
                                                    success = "ثبت‌نام با موفقیت انجام شد. اکنون وارد حساب شوید."
                                                } catch (e: Exception) {
                                                    error = e.message ?: "خطا در ثبت‌نام"
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        } else {
                                            loading = true
                                            scope.launch {
                                                try {
                                                    val session = withContext(Dispatchers.IO) {
                                                        SupabaseApi.signIn(email.trim(), password)
                                                    }
                                                    prefs.edit()
                                                        .putString("supabase_access_token", session.accessToken)
                                                        .putString("supabase_refresh_token", session.refreshToken)
                                                        .putString("supabase_user_id", session.userId)
                                                        .putLong("supabase_login_at", System.currentTimeMillis())
                                                        .apply()
                                                    startActivity(
                                                        android.content.Intent(
                                                            this@LoginActivity,
                                                            MainActivity::class.java
                                                        )
                                                    )
                                                    finish()
                                                } catch (e: Exception) {
                                                    error = e.message ?: "خطا در ورود"
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                ) {
                                    Text(
                                        when {
                                            loading && registerMode -> "در حال ثبت‌نام..."
                                            loading -> "در حال ورود..."
                                            registerMode -> "ساخت حساب"
                                            else -> "ورود به حساب"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    enabled = !loading,
                                    onClick = {
                                        registerMode = !registerMode
                                        error = ""
                                        success = ""
                                        confirmPassword = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (registerMode)
                                            "حساب دارید؟ ورود به حساب"
                                        else
                                            "حساب جدید ندارید؟ ثبت‌نام کنید"
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            "امن، ساده و همیشه همراه کسب‌وکار شما",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
