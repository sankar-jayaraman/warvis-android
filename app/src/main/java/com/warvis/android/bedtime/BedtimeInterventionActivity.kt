package com.warvis.android.bedtime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.ui.theme.WarvisTheme

class BedtimeInterventionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = MESSAGES.random()
        setContent {
            WarvisTheme {
                BedtimeScreen(
                    message = message,
                    onOpenAirplaneMode = {
                        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        private val MESSAGES = listOf(
            "Your best PE thinking happens after a good night's sleep. Rest now.",
            "Step away from the screen. Meditate for 10 minutes.",
            "Protect your recovery window. Sleep is your performance edge.",
            "The scroll loop costs you more than time. Put the phone away.",
            "Nothing on Instagram moves your PE goal forward tonight. Rest does.",
        )
    }
}

@Composable
private fun BedtimeScreen(
    message: String,
    onOpenAirplaneMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🌙 Wind Down",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "It's past 7 PM.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenAirplaneMode) {
                Text("Enable Airplane Mode")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}
