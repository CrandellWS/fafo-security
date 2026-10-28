package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.mail.Email
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.MailStatus
import ws.crandell.fafo.mail.SmtpSecurity
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.ui.components.ActionKind
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.PrimaryActionButton
import ws.crandell.fafo.ui.theme.FafoColors

/** The "Send test email" button's state. */
sealed interface MailTestUi {
    data object Idle : MailTestUi
    data object Sending : MailTestUi
    data object Ok : MailTestUi
    data class Error(val message: String) : MailTestUi
}

/** What Settings > Photo email can do; the activity / ViewModel implements it. */
interface MailActions {
    fun update(t: (CeSettings) -> CeSettings)
    fun savePassword(password: String)
    fun removePassword()
    fun sendTest()
    fun connectGmail() {}
    fun sendQueuedNow() {}
    fun discardQueued() {}
}

/**
 * Settings > Photo email (round 3, opt-in): on trigger, photos go to up to five addresses through the
 * owner's own mail account. SMTP in every build; Gmail one-tap sign-in in the play build ([providers]).
 */
@Composable
fun MailSettings(
    ce: CeSettings,
    providers: List<MailProviderKind>,
    test: MailTestUi,
    status: MailStatus,
    actions: MailActions,
    showOpenWifi: Boolean,
) {
    fun change(t: (CeSettings) -> CeSettings) = actions.update { t(it).copy(mailTestOk = false) }
    SwitchRow(
        "Email photos on alarm", ce.mailEnabled, { v -> actions.update { it.copy(mailEnabled = v) } }, "mailEnabled",
        subtitle = "Off by default. Sends trigger photos from your own mail account to the addresses below. No FAFO server.",
    )
    if (!ce.mailEnabled) return

    var to by remember(ce.mailRecipients) { mutableStateOf(ce.mailRecipients.joinToString(", ")) }
    val (good, bad) = Email.parseList(to)
    FafoField(
        to, { to = it }, "Send to (up to 5, comma separated)", "mailTo", KeyboardType.Email,
        error = if (bad.isNotEmpty()) "Not an email address: ${bad.joinToString()}" else null,
    )
    if (good != ce.mailRecipients) {
        TextButton(onClick = { change { it.copy(mailRecipients = good) } }, modifier = Modifier.testTag("mailToSave")) {
            Text("Save ${good.size} ${if (good.size == 1) "address" else "addresses"}")
        }
    }

    if (providers.size > 1) {
        ChoiceRow(
            "Send with", providers, MailProviderKind.entries.firstOrNull { it.name == ce.mailProvider } ?: MailProviderKind.SMTP,
            { if (it == MailProviderKind.GMAIL) "Google account" else "Mail server (SMTP)" },
            { v -> change { it.copy(mailProvider = v.name) } }, "mailProvider",
        )
    }

    if (ce.mailProvider == MailProviderKind.GMAIL.name && MailProviderKind.GMAIL in providers) {
        FafoCard {
            Text(
                if (ce.gmailAccount.isBlank()) "Not connected" else "Connected: ${ce.gmailAccount}",
                style = MaterialTheme.typography.titleMedium, color = FafoColors.White,
            )
            Text(
                "FAFO asks only for permission to send email as you (gmail.send). It cannot read your mail.",
                style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = actions::connectGmail, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("gmailConnect")) {
                Text(if (ce.gmailAccount.isBlank()) "Sign in with Google" else "Switch Google account")
            }
        }
    } else {
        SmtpFields(ce, ::change, actions)
    }

    Spacer(Modifier.height(12.dp))
    PrimaryActionButton(
        "Send test email", ActionKind.ARM, actions::sendTest, Modifier.testTag("mailTest"),
        enabled = test != MailTestUi.Sending, busy = test == MailTestUi.Sending,
    )
    when (test) {
        MailTestUi.Ok -> Text("Test email sent. Check the inbox (and spam).", style = MaterialTheme.typography.bodyLarge, color = FafoColors.Guard, modifier = Modifier.padding(top = 6.dp).testTag("mailTestResult"))
        is MailTestUi.Error -> Text(test.message, style = MaterialTheme.typography.bodyLarge, color = FafoColors.RedText, modifier = Modifier.padding(top = 6.dp).testTag("mailTestResult"))
        else -> if (ce.mailTestOk) Text("Last test email went through.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
    }

    if (status.pending > 0 || status.failedPermanently > 0) {
        FafoCard(Modifier.padding(top = 10.dp).testTag("mailQueue"), accent = FafoColors.Amber) {
            Text("${status.pending} photo ${if (status.pending == 1) "email" else "emails"} not sent yet", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
            status.lastError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions::sendQueuedNow) { Text("Send now") }
                TextButton(onClick = actions::discardQueued) { Text("Discard") }
            }
        }
    }

    if (showOpenWifi) {
        SwitchRow(
            "Use open Wi-Fi if there is no connection", ce.mailOpenWifi, { v -> actions.update { it.copy(mailOpenWifi = v) } }, "mailOpenWifi",
            subtitle = "When queued photos can't go out (no mobile data, no known Wi-Fi), FAFO suggests nearby open networks to Android. Sign-in pages (hotels, airports) can't be passed.",
        )
    }
}

@Composable
private fun SmtpFields(ce: CeSettings, change: ((CeSettings) -> CeSettings) -> Unit, actions: MailActions) {
    FafoField(ce.smtpHost, { v -> change { it.copy(smtpHost = v.trim()) } }, "Mail server (e.g. smtp of your provider)", "smtpHost", KeyboardType.Uri)
    ChoiceRow(
        "Security", SmtpSecurity.entries, SmtpSecurity.entries.firstOrNull { it.name == ce.smtpSecurity } ?: SmtpSecurity.SSL_TLS,
        { if (it == SmtpSecurity.SSL_TLS) "SSL/TLS (465)" else "STARTTLS (587)" },
        { v -> change { it.copy(smtpSecurity = v.name, smtpPort = v.defaultPort) } }, "smtpSecurity",
        hint = "Always encrypted. FAFO never sends your password without TLS.",
    )
    FafoField(ce.smtpPort.toString(), { v -> v.toIntOrNull()?.let { p -> change { it.copy(smtpPort = p) } } }, "Port", "smtpPort", KeyboardType.Number)
    FafoField(ce.smtpUser, { v -> change { it.copy(smtpUser = v.trim()) } }, "Username (usually your email address)", "smtpUser", KeyboardType.Email)
    FafoField(ce.smtpFrom, { v -> change { it.copy(smtpFrom = v.trim()) } }, "From address (blank = username)", "smtpFrom", KeyboardType.Email)

    if (ce.mailSecret.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("App password: saved ••••", style = MaterialTheme.typography.titleMedium, color = FafoColors.White, modifier = Modifier.weight(1f).testTag("mailSecretSaved"))
            TextButton(onClick = actions::removePassword) { Text("Remove") }
        }
    } else {
        var pw by remember { mutableStateOf("") }
        FafoField(pw, { pw = it }, "App password", "smtpPassword", KeyboardType.Password, password = true)
        Text(
            "Use an app password from your mail provider, not your main password. You can revoke it any time.",
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
        )
        TextButton(onClick = { actions.savePassword(pw); pw = "" }, enabled = pw.isNotBlank(), modifier = Modifier.testTag("smtpPasswordSave")) {
            Text("Save password")
        }
    }
}

@Composable
private fun FafoField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    type: KeyboardType,
    password: Boolean = false,
    error: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FafoColors.White, unfocusedTextColor = FafoColors.White,
                focusedBorderColor = FafoColors.Steel, unfocusedBorderColor = FafoColors.Outline,
                focusedLabelColor = FafoColors.White, unfocusedLabelColor = FafoColors.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = FafoColors.RedText)
    }
}
