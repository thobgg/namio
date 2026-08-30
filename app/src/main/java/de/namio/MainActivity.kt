package de.namio

import android.app.KeyguardManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.namio.feature.einstellungen.SperreViewModel
import de.namio.ui.components.SperrBildschirm
import de.namio.ui.navigation.NamioNavHost
import de.namio.ui.theme.NamioTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val sperre: SperreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamioTheme {
                val gesperrt by sperre.gesperrt.collectAsStateWithLifecycle()
                // NavHost bleibt bestehen; die Sperre legt sich als Overlay darüber, damit Systemdialoge die Navigation nicht zurücksetzen.
                Box {
                    NamioNavHost()
                    if (gesperrt) SperrBildschirm(onEntsperren = ::entsperren)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sperre.beimStart()
        if (sperre.gesperrt.value) entsperren()
    }

    override fun onStop() {
        super.onStop()
        sperre.beimStop()
    }

    private var promptOffen = false

    /**
     * Zeigt den System-Dialog (Biometrie oder Geräte-PIN). Nur wenn das Gerät gar keine
     * Bildschirmsperre hat, bleibt die App offen – jeder andere Fehler lässt sie gesperrt.
     */
    private fun entsperren() {
        if (promptOffen) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) { sperre.entsperrt(); return }
        val erlaubt = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val status = BiometricManager.from(this).canAuthenticate(erlaubt)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w("Namio", "Sperre: canAuthenticate=$status – Gerät gesichert, Dialog nicht verfügbar; bleibt gesperrt")
            return
        }
        promptOffen = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { promptOffen = false; sperre.entsperrt() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptOffen = false
                    Log.w("Namio", "Sperre: Fehler $errorCode $errString")
                }
                override fun onAuthenticationFailed() = Unit
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.sperre_titel))
                .setSubtitle(getString(R.string.sperre_untertitel))
                .setAllowedAuthenticators(erlaubt)
                .build(),
        )
    }
}
