package com.zlip.app.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.QrColors
import com.zlip.app.data.QrKind
import com.zlip.app.data.QrPayload
import com.zlip.app.data.takeSafe
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.render.icon
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow

private const val MAX_URL = 400
private const val MAX_SSID = 32
private const val MAX_PASSWORD = 64
private const val MAX_CONTACT_FIELD = 80
private const val MAX_CAPTION = 60

/** One-tap starts for the links people share most; the user types the rest (their handle). */
private val QUICK_LINKS = listOf(
    R.string.qr_link_quick_instagram to "instagram.com/",
    R.string.qr_link_quick_tiktok to "tiktok.com/@",
    R.string.qr_link_quick_whatsapp to "wa.me/",
    R.string.qr_link_quick_youtube to "youtube.com/@",
)

/** [prefillUrl] starts a new link card with this address (shared from another app); it counts as an unsaved change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrEditorScreen(
    initial: CoverItem.Qr?,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Qr) -> Unit,
    onBack: () -> Unit,
    prefillUrl: String = "",
) {
    val captions = rememberQrCaptions()
    val fallbackName = stringResource(R.string.type_qr)
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    // Built from what the editor was opened with (not from restored state), so edits that survived
    // process death still count as unsaved.
    val start = remember { QrDraft.from(initial, captions, fallbackName) }
    var draft by rememberSaveable(stateSaver = QrDraft.Saver) { mutableStateOf(if (prefillUrl.isEmpty()) start else start.copy(url = prefillUrl)) }
    // The link field keeps its cursor, so a quick-start prefix leaves it ready for the handle.
    var urlValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft.url, TextRange(draft.url.length)))
    }

    val item = draft.toItem(id, captions, fallbackName)
    val baseline = remember { start.toItem(id, captions, fallbackName) }
    val problem = draft.problem
    var focusedField by remember { mutableStateOf<String?>(null) }
    val problems = Problems(problem, focusedField) { field, focused ->
        if (focused) focusedField = field else if (focusedField == field) focusedField = null
    }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, problem == null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.qr_title_new else R.string.qr_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }, enabled = problem == null) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoverPreviewFrame(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally),
            ) {
                CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback)
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 5.dp, end = 8.dp)
                        .size(16.dp),
                )
                HintText(stringResource(R.string.qr_unlock_hint))
            }

            KindChooser(selected = draft.kind, onSelect = { draft = draft.copy(kind = it) })

            SectionCard {
                when (draft.kind) {
                    QrKind.LINK -> LinkFields(
                        value = urlValue,
                        problems = problems,
                        onChange = {
                            val text = it.text.replace("\n", "").takeSafe(MAX_URL)
                            urlValue = if (text == it.text) it else TextFieldValue(text, TextRange(text.length))
                            draft = draft.copy(url = text)
                        },
                    )
                    QrKind.WIFI -> WifiFields(draft, problems, onChange = { draft = it })
                    QrKind.CONTACT -> ContactFields(draft, problems, onChange = { draft = it })
                    QrKind.TEXT -> TextFields(draft, problems, onChange = { draft = it })
                }
                problems.note?.let { ProblemNote(it) }
            }

            SectionCard {
                CaptionField(
                    // The raw text while typing: trimming here would swallow a space before the next word.
                    caption = if (draft.autoCaption) captions.suggest(draft.kind, draft.fields) else draft.caption,
                    auto = draft.autoCaption,
                    onChange = { draft = draft.copy(caption = it.replace("\n", "").takeSafe(MAX_CAPTION), autoCaption = false) },
                    onReset = { draft = draft.copy(caption = "", autoCaption = true) },
                )
                Spacer(Modifier.height(14.dp))
                Label(stringResource(R.string.qr_colors))
                ThemeRow(
                    code = draft.codeColor,
                    background = draft.background,
                    onSelect = { draft = draft.copy(codeColor = it.code, background = it.background) },
                )
                HintText(
                    stringResource(
                        if (QrColors.isScannable(draft.codeColor, draft.background)) R.string.qr_colors_hint else R.string.qr_colors_fixed,
                    ),
                )
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Four big, equal options with icons; a radio group for TalkBack. */
@Composable
private fun KindChooser(selected: QrKind, onSelect: (QrKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QrKind.entries.forEach { kind ->
            val isSelected = kind == selected
            val shape = MaterialTheme.shapes.medium
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(kind) })
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    kind.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(kind.label()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LinkFields(value: TextFieldValue, problems: Problems, onChange: (TextFieldValue) -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val link = QrPayload.link(value.text)
    val error = problems.errorFor(QrPayload.URL)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.qr_link_field)) },
        placeholder = { Text(stringResource(R.string.qr_link_placeholder)) },
        leadingIcon = { Icon(QrKind.LINK.icon, contentDescription = null) },
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(problemText(error)) })
            // Show what scanners will open when we completed the address (https:// added).
            link.isNotEmpty() && link != value.text.trim() -> ({ Text(stringResource(R.string.qr_link_opens, link), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .then(problems.track(QrPayload.URL)),
    )
    if (value.text.isBlank()) {
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_LINKS.forEach { (label, prefix) ->
                SuggestionChip(
                    onClick = {
                        onChange(TextFieldValue(prefix, TextRange(prefix.length)))
                        focus.requestFocus()
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun WifiFields(draft: QrDraft, problems: Problems, onChange: (QrDraft) -> Unit) {
    val focusManager = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = draft.ssid,
        onValueChange = { onChange(draft.copy(ssid = it.replace("\n", "").takeSafe(MAX_SSID))) },
        label = { Text(stringResource(R.string.qr_wifi_ssid)) },
        leadingIcon = { Icon(QrKind.WIFI.icon, contentDescription = null) },
        singleLine = true,
        isError = problems.errorFor(QrPayload.SSID) != null,
        supportingText = problems.errorTextFor(QrPayload.SSID),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .then(problems.track(QrPayload.SSID)),
    )
    Spacer(Modifier.height(12.dp))
    Label(stringResource(R.string.qr_wifi_security))
    ChoiceChips(
        options = QrPayload.Security.entries,
        selected = draft.security,
        label = {
            stringResource(
                when (it) {
                    QrPayload.Security.WPA -> R.string.qr_wifi_security_wpa
                    QrPayload.Security.WEP -> R.string.qr_wifi_security_wep
                    QrPayload.Security.NONE -> R.string.qr_wifi_security_none
                },
            )
        },
        onSelect = { onChange(draft.copy(security = it)) },
    )
    if (draft.security != QrPayload.Security.NONE) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.password,
            onValueChange = { onChange(draft.copy(password = it.replace("\n", "").takeSafe(MAX_PASSWORD))) },
            label = { Text(stringResource(R.string.qr_wifi_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (showPassword) R.string.qr_password_hide else R.string.qr_password_show),
                    )
                }
            },
            isError = problems.errorFor(QrPayload.PASSWORD) != null,
            supportingText = problems.errorTextFor(QrPayload.PASSWORD),
            // Always a password keyboard, even while shown: keyboards must not learn the password.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(problems.track(QrPayload.PASSWORD)),
        )
    }
    Spacer(Modifier.height(4.dp))
    SwitchRow(
        title = stringResource(R.string.qr_wifi_hidden),
        description = stringResource(R.string.qr_wifi_hidden_desc),
        checked = draft.hidden,
        onCheckedChange = { onChange(draft.copy(hidden = it)) },
    )
    HintText(stringResource(R.string.qr_wifi_hint))
}

@Composable
private fun ContactFields(draft: QrDraft, problems: Problems, onChange: (QrDraft) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContactField(
            value = draft.contactName,
            onValueChange = { onChange(draft.copy(contactName = it)) },
            label = R.string.qr_contact_name,
            icon = QrKind.CONTACT.icon,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            problems = problems,
            field = QrPayload.NAME,
        )
        ContactField(
            value = draft.phone,
            onValueChange = { onChange(draft.copy(phone = it)) },
            label = R.string.qr_contact_phone,
            icon = Icons.Filled.Phone,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            problems = problems,
            field = QrPayload.PHONE,
        )
        ContactField(
            value = draft.email,
            onValueChange = { onChange(draft.copy(email = it)) },
            label = R.string.qr_contact_email,
            icon = Icons.Filled.Email,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            problems = problems,
            field = QrPayload.EMAIL,
        )
        ContactField(
            value = draft.org,
            onValueChange = { onChange(draft.copy(org = it)) },
            label = R.string.qr_contact_org,
            icon = Icons.Filled.Business,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            problems = problems,
            field = QrPayload.ORG,
        )
    }
    HintText(stringResource(R.string.qr_contact_hint))
}

@Composable
private fun ContactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    icon: ImageVector,
    keyboard: KeyboardOptions,
    problems: Problems,
    field: String,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "").takeSafe(MAX_CONTACT_FIELD)) },
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        isError = problems.errorFor(field) != null,
        supportingText = problems.errorTextFor(field),
        keyboardOptions = keyboard,
        keyboardActions = keyboardActions,
        modifier = Modifier
            .fillMaxWidth()
            .then(problems.track(field)),
    )
}

@Composable
private fun TextFields(draft: QrDraft, problems: Problems, onChange: (QrDraft) -> Unit) {
    OutlinedTextField(
        value = draft.text,
        onValueChange = { onChange(draft.copy(text = it.takeSafe(QrPayload.MAX_CHARS))) },
        label = { Text(stringResource(R.string.qr_text_field)) },
        supportingText = { Text("${draft.text.length} / ${QrPayload.MAX_CHARS}") },
        isError = problems.problem == QrPayload.Problem.TOO_LONG,
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    HintText(stringResource(R.string.qr_text_hint))
}

@Composable
private fun CaptionField(caption: String, auto: Boolean, onChange: (String) -> Unit, onReset: () -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = caption,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.qr_caption)) },
        singleLine = true,
        trailingIcon = if (auto) {
            null
        } else {
            {
                IconButton(onClick = onReset) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.qr_caption_reset))
                }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Scanner-safe colour pairs as little cards in their own colours. */
@Composable
private fun ThemeRow(code: Int, background: Int, onSelect: (QrTheme) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QrTheme.entries.forEach { theme ->
            val selected = theme.code == code && theme.background == background
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(theme) })
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 54.dp)
                        .clip(shape)
                        .background(Color(theme.background))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color(theme.code), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(theme.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Why Save is off: calm while something is still being filled in, red when it cannot work (too long). */
@Composable
private fun ProblemNote(problem: QrPayload.Problem) {
    val serious = !problem.missing && problem.field == null
    val color = if (serious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (serious) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Where to show what keeps the code from being made. A field's format error turns red only once the user
 * leaves that field; while they are still typing there, or a field is simply empty, [note] explains it
 * calmly under the fields instead.
 */
private class Problems(
    val problem: QrPayload.Problem?,
    private val focused: String?,
    private val onFocus: (field: String, focused: Boolean) -> Unit,
) {
    /** The problem to show on [field] itself, in red. */
    fun errorFor(field: String): QrPayload.Problem? = problem?.takeIf { !it.missing && it.field == field && field != focused }

    fun errorTextFor(field: String): (@Composable () -> Unit)? = errorFor(field)?.let { error -> { Text(problemText(error)) } }

    /** The problem to explain under the fields, if it is not already shown on one. */
    val note: QrPayload.Problem? get() = problem?.takeIf { it.missing || it.field == null || it.field == focused }

    /** Reports focus changes of [field], which decide where its problem shows. */
    fun track(field: String): Modifier = Modifier.onFocusChanged { onFocus(field, it.isFocused) }
}

@Composable
private fun problemText(problem: QrPayload.Problem): String = when (problem) {
    QrPayload.Problem.LINK_MISSING -> stringResource(R.string.qr_problem_link_missing)
    QrPayload.Problem.LINK_INVALID -> stringResource(R.string.qr_problem_link_invalid)
    QrPayload.Problem.SSID_MISSING -> stringResource(R.string.qr_problem_ssid_missing)
    QrPayload.Problem.SSID_TOO_LONG ->
        pluralStringResource(R.plurals.qr_problem_ssid_too_long, QrPayload.MAX_SSID_BYTES, QrPayload.MAX_SSID_BYTES)
    QrPayload.Problem.PASSWORD_MISSING -> stringResource(R.string.qr_problem_password_missing)
    QrPayload.Problem.PASSWORD_TOO_SHORT ->
        pluralStringResource(R.plurals.qr_problem_password_short, QrPayload.MIN_WPA_PASSWORD, QrPayload.MIN_WPA_PASSWORD)
    QrPayload.Problem.NAME_MISSING -> stringResource(R.string.qr_problem_name_missing)
    QrPayload.Problem.CONTACT_DETAILS_MISSING -> stringResource(R.string.qr_problem_contact_details)
    QrPayload.Problem.PHONE_INVALID -> stringResource(R.string.qr_problem_phone_invalid)
    QrPayload.Problem.EMAIL_INVALID -> stringResource(R.string.qr_problem_email_invalid)
    QrPayload.Problem.TEXT_MISSING -> stringResource(R.string.qr_problem_text_missing)
    QrPayload.Problem.TOO_LONG -> stringResource(R.string.qr_problem_too_long)
}

private fun QrKind.label(): Int = when (this) {
    QrKind.LINK -> R.string.qr_kind_link
    QrKind.WIFI -> R.string.qr_kind_wifi
    QrKind.CONTACT -> R.string.qr_kind_contact
    QrKind.TEXT -> R.string.qr_kind_text
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
