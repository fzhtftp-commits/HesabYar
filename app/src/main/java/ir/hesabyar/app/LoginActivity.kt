package ir.hesabyar.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
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
        var error by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val prefs = getSharedPreferences("hesabyar_data", Context.MODE_PRIVATE)

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("ورود به حساب‌یار", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("ایمیل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز عبور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            loading = true
                            error = ""
                            scope.launch {
                                try {
                                    val session = withContext(Dispatchers.IO) {
                                        SupabaseApi.signIn(email.trim(), password)
                                    }
                                    prefs.edit()
                                        .putString("supabase_access_token", session.accessToken)
                                        .putString("supabase_refresh_token", session.refreshToken)
                                        .putString("supabase_user_id", session.userId)
                                        .apply()
                                    startActivity(android.content.Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                } catch (e: Exception) {
                                    error = e.message ?: "خطا در ورود"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(if (loading) "در حال ورود..." else "ورود")
                    }
                }
            }
        }
    }
}