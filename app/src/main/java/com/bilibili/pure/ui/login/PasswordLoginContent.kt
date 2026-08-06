package com.bilibili.pure.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

@Composable
fun PasswordLoginContent(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf("") }
    var captchaGt by remember { mutableStateOf("") }
    var captchaChallenge by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }

    if (showCaptcha) {
        GeetestCaptcha(
            gt = captchaGt,
            challenge = captchaChallenge,
            onResult = { result ->
                showCaptcha = false
                scope.launch {
                    isLoading = true
                    try {
                        val keyResult = repository.getWebKey()
                        keyResult.onSuccess { keyData ->
                            val encryptedPassword = withContext(Dispatchers.IO) {
                                encryptPassword(keyData.hash, keyData.key, password)
                            }
                            val loginResult = repository.passwordLoginRaw(
                                username = username,
                                password = encryptedPassword,
                                token = captchaToken,
                                challenge = result.challenge,
                                validate = result.validate,
                                seccode = result.seccode
                            )
                            loginResult.onSuccess { cookies ->
                                val sessdata = cookies["SESSDATA"]
                                val biliJct = cookies["bili_jct"]
                                val dedeUserId = cookies["DedeUserID"]
                                if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
                                    BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
                                    context.getSharedPreferences("bili_prefs", 0).edit()
                                        .putString("sessdata", sessdata)
                                        .putString("bili_jct", biliJct)
                                        .putString("dede_userid", dedeUserId)
                                        .apply()
                                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    Toast.makeText(context, "登录失败: 未获取到凭证", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                Toast.makeText(context, "登录失败: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            Toast.makeText(context, "获取密钥失败: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            onDismiss = { showCaptcha = false }
        )
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "账号密码登录",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("手机号/邮箱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    try {
                        val captchaResult = repository.getCaptcha()
                        captchaResult.onSuccess {
                            captchaToken = it.token
                            captchaGt = it.geetest?.gt ?: ""
                            captchaChallenge = it.geetest?.challenge ?: ""
                            showCaptcha = true
                        }.onFailure {
                            Toast.makeText(context, "获取验证失败: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("登录")
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

private fun encryptPassword(salt: String, publicKeyPem: String, password: String): String {
    val pem = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\n", "")
        .replace("\r", "")
    val keyBytes = Base64.getDecoder().decode(pem)
    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(keySpec)
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val encrypted = cipher.doFinal((salt + password).toByteArray())
    return Base64.getEncoder().encodeToString(encrypted)
}
