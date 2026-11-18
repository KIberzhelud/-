package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CallDirection
import com.example.model.CallLogEntry
import com.example.model.CallsSubTab
import com.example.model.ContactEntry
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.CallOutgoingBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun CallsContactsView(
    subTab: CallsSubTab,
    isLandscape: Boolean,
    searchQuery: String,
    callLogs: List<CallLogEntry>,
    contacts: List<ContactEntry>,
    focusedCallLogIndex: Int,
    focusedContactIndex: Int,
    hasCallLogPermission: Boolean,
    hasContactsPermission: Boolean,
    highlightedLetter: Char?,
    callLogListState: LazyListState,
    contactsGridState: LazyGridState,
    callsSearchFocusRequester: FocusRequester,
    onSearchFocusChange: (Boolean) -> Unit,
    onSelectSubTab: (CallsSubTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCallClick: (String) -> Unit,
    onAddContactClick: () -> Unit,
    onResetClick: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Column(modifier = modifier.fillMaxSize()) {
        if (!isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SubTabItem(
                    selected = subTab == CallsSubTab.LOG,
                    iconRes = R.drawable.ic_symbian_log,
                    label = CallsSubTab.LOG.title,
                    onClick = { onSelectSubTab(CallsSubTab.LOG) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calls_subtab_log")
                )
                SubTabItem(
                    selected = subTab == CallsSubTab.CONTACTS,
                    iconRes = R.drawable.ic_symbian_contacts,
                    label = CallsSubTab.CONTACTS.title,
                    onClick = { onSelectSubTab(CallsSubTab.CONTACTS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calls_subtab_contacts")
                )
            }
            HorizontalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)
        }

        // Vista inset Search & Action Strip: [icon] [Поиск или номер...] [ВЫЗОВ] [+КОНТАКТ] [СБРОС]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .vistaInsetBox()
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_phone),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (highlightedLetter != null) {
                                "Буква: $highlightedLetter • Поиск или номер…"
                            } else {
                                "Поиск или номер…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (highlightedLetter != null) theme.accent else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(ElectricCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onCallClick(searchQuery) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(callsSearchFocusRequester)
                            .onFocusChanged { onSearchFocusChange(it.isFocused) }
                            .testTag("calls_search_input")
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            ActionChipButton(
                text = "ВЫЗОВ",
                iconRes = R.drawable.ic_symbian_phone,
                isPrimary = true,
                onClick = { onCallClick(searchQuery) },
                modifier = Modifier.testTag("calls_dial_button")
            )
            Spacer(Modifier.width(4.dp))
            ActionChipButton(
                text = "+КОНТ.",
                iconRes = R.drawable.ic_symbian_add_contact,
                isPrimary = false,
                onClick = onAddContactClick,
                modifier = Modifier.testTag("calls_add_contact_button")
            )
            Spacer(Modifier.width(4.dp))
            ActionChipButton(
                text = "СБРОС",
                iconRes = R.drawable.ic_symbian_clear,
                isPrimary = false,
                onClick = onResetClick,
                modifier = Modifier.testTag("calls_reset_button")
            )
        }
        HorizontalDivider(color = NavyDivider, thickness = 1.dp)

        when (subTab) {
            CallsSubTab.LOG -> {
                if (!hasCallLogPermission) {
                    PermissionPromptCard(
                        iconRes = R.drawable.ic_symbian_phone,
                        title = "Доступ к журналу звонков",
                        description = "Разрешите чтение истории вызовов, чтобы видеть последние звонки и быстро перезванивать с клавиатуры.",
                        buttonText = "Разрешить доступ",
                        onClick = onRequestPermissions
                    )
                } else if (callLogs.isEmpty()) {
                    EmptyCallsState(R.drawable.ic_symbian_log, "История звонков пуста")
                } else {
                    LazyColumn(
                        state = callLogListState,
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("call_log_list")
                    ) {
                        itemsIndexed(callLogs, key = { _, item -> item.id }) { idx, entry ->
                            CallLogTile(
                                entry = entry,
                                isFocused = idx == focusedCallLogIndex,
                                bucket = idx,
                                onClick = { onCallClick(entry.number) }
                            )
                        }
                    }
                }
            }

            CallsSubTab.CONTACTS -> {
                if (!hasContactsPermission) {
                    PermissionPromptCard(
                        iconRes = R.drawable.ic_symbian_contacts,
                        title = "Доступ к контактам",
                        description = "Разрешите чтение контактов для алфавитного поиска Niagara и быстрого набора с клавиатуры.",
                        buttonText = "Разрешить доступ",
                        onClick = onRequestPermissions
                    )
                } else if (contacts.isEmpty()) {
                    EmptyCallsState(
                        iconRes = R.drawable.ic_symbian_contacts,
                        message = if (highlightedLetter != null) {
                            "Контакты на букву «$highlightedLetter» не найдены"
                        } else {
                            "Контакты не найдены"
                        }
                    )
                } else {
                    val cols = if (isLandscape) 3 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        state = contactsGridState,
                        contentPadding = PaddingValues(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("contacts_grid")
                    ) {
                        itemsIndexed(contacts, key = { _, item -> "${item.id}_${item.number}" }) { idx, contact ->
                            ContactTile(
                                contact = contact,
                                bucket = if (contact.isStarred) 1 else (idx % 2) + 2,
                                isFocused = idx == focusedContactIndex,
                                isHighlighted = false,
                                onClick = { onCallClick(contact.number) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabItem(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun ActionChipButton(
    text: String,
    iconRes: Int,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .vistaAeroButton(isPrimary = isPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = if (isPrimary) Color.White else TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CallLogTile(
    entry: CallLogEntry,
    isFocused: Boolean,
    bucket: Int,
    onClick: () -> Unit
) {
    val (directionColor, directionIconRes) = when (entry.direction) {
        CallDirection.INCOMING -> CallIncomingGreen to R.drawable.ic_symbian_call_in
        CallDirection.MISSED, CallDirection.REJECTED -> CallMissedRed to R.drawable.ic_symbian_call_missed
        CallDirection.OUTGOING -> CallOutgoingBlue to R.drawable.ic_symbian_call_out
        CallDirection.OTHER -> TextSecondary to R.drawable.ic_symbian_phone
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .symbianGlossyTile(
                bucket = if (bucket % 2 == 0) 2 else 3,
                isFocused = isFocused
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .vistaInsetBox(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(directionIconRes),
                contentDescription = entry.direction.label,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.number} | ",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = entry.direction.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = directionColor,
                    maxLines = 1
                )
                Text(
                    text = " | ${entry.timeFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_symbian_phone),
            contentDescription = "Позвонить",
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ContactTile(
    contact: ContactEntry,
    bucket: Int,
    isFocused: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .symbianGlossyTile(
                bucket = bucket,
                isFocused = isFocused,
                isLetterHighlighted = isHighlighted
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .vistaInsetBox(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_contacts),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = contact.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (contact.isStarred) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Избранный контакт",
                tint = com.example.ui.theme.MusicSourceAmber,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
            )
        }
    }
}

@Composable
private fun PermissionPromptCard(
    iconRes: Int,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            VistaButton(
                text = buttonText,
                isPrimary = true,
                onClick = onClick,
                modifier = Modifier.testTag("grant_calls_contacts_permission")
            )
        }
    }
}

@Composable
private fun EmptyCallsState(iconRes: Int, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
        }
    }
}
