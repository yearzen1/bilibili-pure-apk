package com.bilibili.pure.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.update.UpdateChecker
import com.bilibili.pure.data.update.UpdateDownloader
import com.bilibili.pure.ui.theme.THEME_DARK
import com.bilibili.pure.ui.theme.THEME_FOLLOW_SYSTEM
import com.bilibili.pure.ui.theme.THEME_LIGHT
import com.bilibili.pure.ui.theme.themeModeLabels
import com.bilibili.pure.ui.theme.themeRowTitle
import com.bilibili.pure.ui.theme.resolveDarkTheme
import com.bilibili.pure.ui.update.UpdateDialogs
import com.bilibili.pure.ui.update.UpdateFlowState

private val SectionShape = RoundedCornerShape(16.dp)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    var wifiOnlyPlayback by remember { mutableStateOf(AppSettings.wifiOnlyPlayback) }
    var wifiOnlyDownload by remember { mutableStateOf(AppSettings.wifiOnlyDownload) }
    val themeMode = AppSettings.themeMode
    var themeDialogVisible by remember { mutableStateOf(false) }
    var themeSelected by remember { mutableStateOf(themeMode) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateFlow = remember {
        UpdateFlowState(
            scope = scope,
            downloader = UpdateDownloader(context.applicationContext)
        )
    }

    var feedbackDialogVisible by remember { mutableStateOf(false) }
    var feedbackType by remember { mutableStateOf(FeedbackType.BUG) }
    var feedbackContent by remember { mutableStateOf("") }
    var feedbackContact by remember { mutableStateOf("") }

    fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(FeedbackEmail.mailtoUri())).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackEmail.RECIPIENT))
            putExtra(
                Intent.EXTRA_SUBJECT,
                FeedbackEmail.subject(feedbackType, BuildConfig.VERSION_NAME)
            )
            putExtra(
                Intent.EXTRA_TEXT,
                FeedbackEmail.body(
                    content = feedbackContent,
                    contact = feedbackContact,
                    version = BuildConfig.VERSION_NAME,
                    androidVersion = Build.VERSION.RELEASE,
                    deviceModel = Build.MODEL
                )
            )
        }
        try {
            context.startActivity(intent)
            feedbackDialogVisible = false
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未找到邮件应用，无法发送反馈", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionTitle("外观设置")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
            val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
            SettingsRow(
                title = themeRowTitle(dark),
                subtitle = "当前：${themeModeLabels[themeMode]}",
                leadingIcon = if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    onClick = {
                        themeSelected = themeMode
                        themeDialogVisible = true
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("网络设置")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingsRow(
                    title = "仅WiFi播放视频",
                    subtitle = "开启后，移动网络下不会播放视频",
                    leadingIcon = Icons.Outlined.Wifi,
                    trailing = {
                        Switch(
                            checked = wifiOnlyPlayback,
                            onCheckedChange = {
                                wifiOnlyPlayback = it
                                AppSettings.wifiOnlyPlayback = it
                            }
                        )
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp)
                )
                SettingsRow(
                    title = "仅WiFi下载视频",
                    subtitle = "开启后，移动网络下不会下载视频",
                    leadingIcon = Icons.Outlined.FileDownload,
                    trailing = {
                        Switch(
                            checked = wifiOnlyDownload,
                            onCheckedChange = {
                                wifiOnlyDownload = it
                                AppSettings.wifiOnlyDownload = it
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("关于")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingsRow(
                    title = "检查更新",
                    subtitle = "当前版本 v${UpdateChecker.CURRENT_VERSION}",
                    onClick = { updateFlow.startCheck() },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp))
                SettingsRow(
                    title = "用户反馈",
                    subtitle = "Bug 反馈与功能建议",
                    onClick = {
                        feedbackType = FeedbackType.BUG
                        feedbackContent = ""
                        feedbackContact = ""
                        feedbackDialogVisible = true
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }

    if (feedbackDialogVisible) {
        AlertDialog(
            onDismissRequest = { feedbackDialogVisible = false },
            title = { Text("用户反馈") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    FeedbackType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { feedbackType = type }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = feedbackType == type,
                                onClick = { feedbackType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(type.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackContent,
                        onValueChange = { feedbackContent = it },
                        label = { Text("反馈内容 *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        minLines = 4,
                        maxLines = 8
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackContact,
                        onValueChange = { feedbackContact = it },
                        label = { Text("联系方式（选填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = FeedbackEmail.isValid(feedbackContent),
                    onClick = { sendFeedback() }
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { feedbackDialogVisible = false }) { Text("取消") }
            }
        )
    }

    if (themeDialogVisible) {
        AlertDialog(
            onDismissRequest = { themeDialogVisible = false },
            title = { Text("深色模式") },
            text = {
                Column {
                    listOf(THEME_FOLLOW_SYSTEM, THEME_LIGHT, THEME_DARK).forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeSelected = mode
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeSelected == mode,
                                onClick = { themeSelected = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = themeModeLabels.getValue(mode),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.themeMode = themeSelected
                    themeDialogVisible = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { themeDialogVisible = false }) { Text("取消") }
            }
        )
    }

    UpdateDialogs(updateFlow)
}