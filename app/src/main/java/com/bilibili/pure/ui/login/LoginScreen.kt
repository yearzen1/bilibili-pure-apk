package com.bilibili.pure.ui.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val QR_SIZE = 256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    var qrUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("扫码登录") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("账号密码登录") }
                )
            }

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (val s = state) {
                            is LoginUiState.Loading -> {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("正在生成二维码...")
                            }

                            is LoginUiState.QRReady -> {
                                qrUrl = s.url
                                QrImage(s.url)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "请使用哔哩哔哩 App 扫码登录",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "打开 Bilibili App → 扫一扫",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is LoginUiState.Scanned -> {
                                if (qrUrl.isNotEmpty()) QrImage(qrUrl)
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "已扫描，请在手机上确认登录",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            is LoginUiState.Expired -> {
                                Text(
                                    text = "二维码已失效",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text("重新生成")
                                }
                            }

                            is LoginUiState.Success -> {
                                Text(
                                    text = "登录成功！",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }

                            is LoginUiState.Error -> {
                                Text(
                                    text = s.message,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text("重试")
                                }
                            }

                            is LoginUiState.Idle -> {}
                        }
                    }
                }

                1 -> {
                    PasswordLoginContent(
                        onLoginSuccess = onLoginSuccess,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun QrImage(content: String) {
    val bitmap = remember(content) { generateQrBitmap(content, QR_SIZE) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(QR_SIZE.dp)
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) black else white)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
