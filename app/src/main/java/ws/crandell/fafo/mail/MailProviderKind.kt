package ws.crandell.fafo.mail

/** How photo email leaves the phone. SMTP is in every build; GMAIL only in the play flavor. */
enum class MailProviderKind { SMTP, GMAIL }
