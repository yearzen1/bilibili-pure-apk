package com.bilibili.pure.ui.profile

import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.api.BilibiliApi

@Composable
fun ProfileScreen(onLoginClick: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bili_prefs", 0) }
    var isLoggedIn by remember { mutableStateOf(prefs.contains("sessdata")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "个人中心",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoggedIn) {
            val dedeUserId = prefs.getString("dede_userid", "")
            Text(
                text = "已登录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "UID: $dedeUserId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                prefs.edit()
                    .remove("sessdata")
                    .remove("bili_jct")
                    .remove("dede_userid")
                    .apply()
                BilibiliApi.loginCookies = ""
                isLoggedIn = false
            }) {
                Text("退出登录")
            }
        } else {
            Button(onClick = onLoginClick) {
                Text(text = "登录")
            }
        }
    }
}
