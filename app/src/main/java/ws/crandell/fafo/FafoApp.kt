package ws.crandell.fafo

import android.app.Application

/**
 * Runs in direct boot too (spec AC2.6a): must never touch credential-protected storage or Keystore.
 * Keep it empty apart from notification channel setup, which needs neither.
 */
class FafoApp : Application()
