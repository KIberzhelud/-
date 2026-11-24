package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.UniversalSearchRepository
import com.example.model.GitHubBuildConfig
import com.example.model.ShizukuState
import com.example.model.ZipArchiveKind
import com.example.model.ZipUpdateInspection
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun ZipUpdateDialog(
    currentVersionName: String,
    currentVersionCode: Long,
    inspection: ZipUpdateInspection?,
    isBusy: Boolean,
    statusMessage: String?,
    shizukuState: ShizukuState,
    githubBuildConfig: GitHubBuildConfig = GitHubBuildConfig(),
    onPickZipFile: () -> Unit,
    onExportBackupZip: () -> Unit,
    onApplyUpdate: (preferShizuku: Boolean) -> Unit,
    onCloudBuildSourceZip: (GitHubBuildConfig, Boolean) -> Unit = { _, _ -> },
    onFetchLatestCloudApk: (GitHubBuildConfig, Boolean) -> Unit = { _, _ -> },
    onOpenUnknownAppSources: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val cfg = LocalConfiguration.current
    val maxDialogH = (cfg.screenHeightDp.dp * 0.90f).coerceAtLeast(280.dp)
    val shizukuReady = (shizukuState.isBinderAlive && shizukuState.isPermissionGranted) || shizukuState.isRootAvailable
    var preferShizuku by remember(shizukuReady) { mutableStateOf(shizukuReady) }

    var repoSlug by remember(githubBuildConfig.repoSlug) { mutableStateOf(githubBuildConfig.repoSlug) }
    var branchName by remember(githubBuildConfig.branch) { mutableStateOf(githubBuildConfig.branch.ifBlank { "main" }) }
    var patToken by remember(githubBuildConfig.token) { mutableStateOf(githubBuildConfig.token) }
    var showToken by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val isWide = maxWidth >= 560.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogH)
                    .vistaWindowFrame(RoundedCornerShape(6.dp))
                    .testTag("zip_update_dialog")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symbianMetallicBar()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "ОБНОВЛЕНИЕ ИЗ ZIP-АРХИВА",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = theme.focusBorder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Установлено: KeyDeck v$currentVersionName (#$currentVersionCode)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onDismiss)
                            .testTag("zip_update_close_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PickZipButton(
                                onClick = onPickZipFile,
                                modifier = Modifier.weight(1f)
                            )
                            ExportBackupZipButton(
                                onClick = onExportBackupZip,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PickZipButton(
                                onClick = onPickZipFile,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExportBackupZipButton(
                                onClick = onExportBackupZip,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isBusy) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = theme.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = statusMessage ?: "Обработка ZIP-архива…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    } else if (!statusMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox(isFocused = true)
                                .padding(10.dp)
                                .testTag("zip_update_status_banner")
                        ) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MusicSourceAmber
                            )
                        }
                    }

                    if (inspection != null) {
                        val badgeColor = when (inspection.kind) {
                            ZipArchiveKind.APK_UPDATE,
                            ZipArchiveKind.APK_SPLIT_BUNDLE,
                            ZipArchiveKind.KEYDECK_CONFIG_PACK -> CallIncomingGreen
                            ZipArchiveKind.FOREIGN_APK,
                            ZipArchiveKind.SOURCE_CODE_ARCHIVE -> MusicSourceAmber
                            ZipArchiveKind.UNSUPPORTED -> CallMissedRed
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox()
                                .padding(10.dp)
                                .testTag("zip_inspection_card"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = inspection.archiveName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (inspection.archiveSizeBytes > 0L) {
                                    Text(
                                        text = UniversalSearchRepository.formatSize(inspection.archiveSizeBytes),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = TextMuted
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
                                    .border(1.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = inspection.kind.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = badgeColor
                                )
                            }

                            Text(
                                text = inspection.summaryMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            if (inspection.changelog.isNotBlank()) {
                                HorizontalDivider(color = theme.divider)
                                Text(
                                    text = "Изменения: ${inspection.changelog}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }

                            if (shizukuReady && (inspection.canInstallApks || inspection.kind == ZipArchiveKind.SOURCE_CODE_ARCHIVE)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { preferShizuku = !preferShizuku },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = preferShizuku,
                                        onCheckedChange = { preferShizuku = it },
                                        colors = CheckboxDefaults.colors(checkedColor = theme.accent)
                                    )
                                    Text(
                                        text = "Тихая установка/обновление через Shizuku / Root",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }

                            if (inspection.signatureMatchesInstalled == false) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CallMissedRed.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                        .border(1.dp, CallMissedRed.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                        .testTag("zip_signature_mismatch_banner"),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = CallMissedRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "НЕСОВПАДЕНИЕ ПОДПИСИ (UPDATE_INCOMPATIBLE)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = CallMissedRed
                                        )
                                    }
                                    Text(
                                        text = "APK подписан другим сертификатом и Android не сможет обновить приложение поверх. Удалите текущую версию KeyDeck один раз и установите новый APK (дальнейшие обновления с этим debug.keystore будут ставиться поверх).",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = TextPrimary
                                    )
                                }
                            }

                            if (inspection.canInstallApks && !inspection.canRequestInstalls && !preferShizuku) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MusicSourceAmber.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                        .border(1.dp, MusicSourceAmber.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "Требуется разрешение на установку неизвестных приложений, чтобы открывалось системное окно обновления.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MusicSourceAmber
                                    )
                                    VistaButton(
                                        text = "Разрешить установку из KeyDeck",
                                        isPrimary = true,
                                        onClick = onOpenUnknownAppSources,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("zip_open_unknown_sources_btn")
                                    )
                                }
                            }

                            if (inspection.canInstallApks || inspection.canApplyConfigPack) {
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .vistaAeroButton(isPrimary = true)
                                        .clickable(enabled = !isBusy) { onApplyUpdate(preferShizuku) }
                                        .padding(vertical = 9.dp)
                                        .testTag("zip_apply_update_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (inspection.canInstallApks) Icons.Rounded.InstallMobile else Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = if (inspection.canInstallApks) {
                                                if (inspection.extractedApkPaths.size > 1) {
                                                    "Установить пакет (${inspection.extractedApkPaths.size} APK)"
                                                } else {
                                                    "Установить обновление из ZIP"
                                                }
                                            } else {
                                                "Применить пакет настроек и текстов"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (inspection.kind == ZipArchiveKind.SOURCE_CODE_ARCHIVE) {
                                HorizontalDivider(color = theme.divider, modifier = Modifier.padding(vertical = 2.dp))
                                CloudSourceBuildPanel(
                                    repoSlug = repoSlug,
                                    branchName = branchName,
                                    patToken = patToken,
                                    showToken = showToken,
                                    hasGradleFiles = inspection.hasGradleFiles,
                                    hasSigningKeystore = inspection.hasSigningKeystoreInZip,
                                    isBusy = isBusy,
                                    isWide = isWide,
                                    onRepoChange = { repoSlug = it },
                                    onBranchChange = { branchName = it },
                                    onTokenChange = { patToken = it },
                                    onToggleShowToken = { showToken = !showToken },
                                    onBuildAndInstall = {
                                        onCloudBuildSourceZip(
                                            GitHubBuildConfig(
                                                repoSlug = repoSlug.trim(),
                                                branch = branchName.trim().ifBlank { "main" },
                                                token = patToken.trim()
                                            ),
                                            preferShizuku
                                        )
                                    },
                                    onFetchLatestApk = {
                                        onFetchLatestCloudApk(
                                            GitHubBuildConfig(
                                                repoSlug = repoSlug.trim(),
                                                branch = branchName.trim().ifBlank { "main" },
                                                token = patToken.trim()
                                            ),
                                            preferShizuku
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Что можно выбрать:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = theme.accent
                            )
                            Text(
                                text = "• ZIP с исходниками проекта (скачанный прямо с сайта AI Studio) — KeyDeck отправит файлы из архива в ваш GitHub, соберёт APK в GitHub Actions и сразу установит обновление без Android Studio и ПК.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "• Готовый .apk или архив (.zip / .apks / .xapk) — распакует и обновит приложение через системный установщик или Shizuku/Root.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "• Пакет настроек KeyDeck (keydeck-update.json, *.palette.json, *.lrc, wallpaper.*) — применится мгновенно без пересборки.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                HorizontalDivider(color = theme.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Закрыть", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun CloudSourceBuildPanel(
    repoSlug: String,
    branchName: String,
    patToken: String,
    showToken: Boolean,
    hasGradleFiles: Boolean,
    hasSigningKeystore: Boolean,
    isBusy: Boolean,
    isWide: Boolean,
    onRepoChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleShowToken: () -> Unit,
    onBuildAndInstall: () -> Unit,
    onFetchLatestApk: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val ready = repoSlug.contains("/") && patToken.isNotBlank() && hasGradleFiles && !isBusy

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .border(1.dp, theme.accent.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(9.dp)
            .testTag("zip_cloud_build_panel"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Build, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Text(
                text = "Сборка APK из исходников без Android Studio (GitHub Actions)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = theme.focusBorder
            )
        }
        Text(
            text = if (hasSigningKeystore) {
                "В архиве найден debug.keystore.base64 — облачная сборка подпишет APK тем же ключом, что и установленное приложение (обновление пройдёт поверх без потери данных)."
            } else {
                "Укажите GitHub-репозиторий и токен (права Contents + Actions). KeyDeck сам загрузит изменённые файлы из ZIP, добавит workflow сборки, дождётся APK и запустит установку."
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary
        )

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZipCompactInputField(
                    value = repoSlug,
                    onValueChange = onRepoChange,
                    placeholder = "owner/repo (напр. user/keydeck)",
                    tag = "zip_cloud_repo_input",
                    modifier = Modifier.weight(0.68f)
                )
                ZipCompactInputField(
                    value = branchName,
                    onValueChange = onBranchChange,
                    placeholder = "main",
                    tag = "zip_cloud_branch_input",
                    modifier = Modifier.weight(0.32f)
                )
            }
        } else {
            ZipCompactInputField(
                value = repoSlug,
                onValueChange = onRepoChange,
                placeholder = "owner/repo (напр. user/keydeck)",
                tag = "zip_cloud_repo_input",
                modifier = Modifier.fillMaxWidth()
            )
            ZipCompactInputField(
                value = branchName,
                onValueChange = onBranchChange,
                placeholder = "Ветка (по умолчанию main)",
                tag = "zip_cloud_branch_input",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox(isFocused = patToken.isNotBlank())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = patToken,
                onValueChange = onTokenChange,
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(theme.accent),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (patToken.isEmpty()) {
                            Text(
                                text = "GitHub Token (ghp_... с правами repo + workflow)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("zip_cloud_token_input")
            )
            Box(
                modifier = Modifier
                    .clickable(onClick = onToggleShowToken)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Показать токен",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaAeroButton(isPrimary = ready, isActive = ready)
                    .clickable(enabled = ready, onClick = onBuildAndInstall)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("zip_cloud_build_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (hasGradleFiles) "Собрать APK из исходников и установить" else "В архиве нет build.gradle(.kts)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (ready) Color.White else TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaAeroButton()
                    .clickable(enabled = repoSlug.contains("/") && patToken.isNotBlank() && !isBusy, onClick = onFetchLatestApk)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("zip_cloud_fetch_latest_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Скачать последний готовый APK из репозитория",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ZipCompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Box(
        modifier = modifier
            .vistaInsetBox(isFocused = value.isNotBlank())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(theme.accent),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}

@Composable
private fun PickZipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .vistaAeroButton(isPrimary = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("zip_pick_file_btn"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                text = "Выбрать ZIP / APKS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExportBackupZipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Box(
        modifier = modifier
            .vistaAeroButton()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("zip_export_backup_btn"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Text(
                text = "Экспорт пакета (ZIP)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
