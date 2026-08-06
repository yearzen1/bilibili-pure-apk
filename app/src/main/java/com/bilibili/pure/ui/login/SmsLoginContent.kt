package com.bilibili.pure.ui.login

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SmsLoginContent(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }

    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var captchaToken by remember { mutableStateOf("") }
    var captchaGt by remember { mutableStateOf("") }
    var captchaChallenge by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaValidate by remember { mutableStateOf("") }
    var captchaSeccode by remember { mutableStateOf("") }
    var captchaKey by remember { mutableStateOf("") }
    var smsSent by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    if (showCaptcha) {
        GeetestCaptcha(
            gt = captchaGt,
            challenge = captchaChallenge,
            onResult = { result ->
                Log.d("BiliPure", "Geetest completed: validate=${result.validate.take(30)} seccode=${result.seccode.take(30)} token=${captchaToken.take(20)} challenge=${captchaChallenge.take(20)}")
                captchaValidate = result.validate
                captchaSeccode = result.seccode
                showCaptcha = false
                scope.launch {
                    isLoading = true
                    try {
                        Log.d("BiliPure", "Sending SMS: tel=$phone token=${captchaToken.take(20)} challenge=${captchaChallenge.take(20)}")
                        val sendResult = repository.sendSmsCode(
                            tel = phone,
                            token = captchaToken,
                            challenge = result.challenge,
                            validate = result.validate,
                            seccode = result.seccode
                        )
                        sendResult.onSuccess {
                            Log.d("BiliPure", "SMS sent OK: captchaKey=${it.captchaKey}")
                            smsSent = true
                            countdown = 60
                            captchaKey = it.captchaKey
                            Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Log.e("BiliPure", "SMS send failed: ${it.message}", it)
                            Toast.makeText(context, "发送失败: ${it.message}", Toast.LENGTH_SHORT).show()
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
            text = "短信验证码登录",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = smsCode,
                onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    if (phone.length < 11) {
                        Toast.makeText(context, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
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
                enabled = !isLoading && countdown == 0 && phone.length == 11,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (countdown > 0) "${countdown}s" else "获取验证码",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (smsCode.length < 4) {
                    Toast.makeText(context, "请输入验证码", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    try {
                        val result = repository.smsLoginRaw(tel = phone, code = smsCode.toInt(), captchaKey = captchaKey)
                        result.onSuccess { cookies ->
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
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && smsCode.length >= 4 && captchaKey.isNotEmpty(),
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
