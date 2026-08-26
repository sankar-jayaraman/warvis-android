package com.warvis.android.doom

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.MainActivity
import com.warvis.android.ui.theme.WarvisTheme

class DoomShieldInterventionActivity : ComponentActivity() {
    private var targetKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetKey = intent.getStringExtra(EXTRA_TARGET_KEY)
        val targetLabel = intent.getStringExtra(EXTRA_TARGET_LABEL) ?: "a blocked app or site"
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL) ?: "your phone"
        val suggestionTitle = intent.getStringExtra(EXTRA_SUGGESTION_TITLE)
        val suggestionBucket = intent.getStringExtra(EXTRA_SUGGESTION_BUCKET)
        val isBedtime = intent.getBooleanExtra(EXTRA_IS_BEDTIME, false)

        setContent {
            WarvisTheme {
                DoomShieldInterventionScreen(
                    targetLabel = targetLabel,
                    sourceLabel = sourceLabel,
                    suggestionTitle = suggestionTitle,
                    suggestionBucket = suggestionBucket,
                    isBedtime = isBedtime,
                    onOpenWarvis = ::openWarvis,
                    onGoHome = ::goHome,
                    onContinueAnyway = ::continueAnyway,
                )
            }
        }
    }

    private fun openWarvis() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun continueAnyway() {
        targetKey?.let { DoomShieldGracePeriod.start(this, it) }
        finish()
    }

    companion object {
        const val EXTRA_TARGET_KEY = "com.warvis.android.doom.EXTRA_TARGET_KEY"
        const val EXTRA_TARGET_LABEL = "com.warvis.android.doom.EXTRA_TARGET_LABEL"
        const val EXTRA_SOURCE_LABEL = "com.warvis.android.doom.EXTRA_SOURCE_LABEL"
        const val EXTRA_SUGGESTION_TITLE = "com.warvis.android.doom.EXTRA_SUGGESTION_TITLE"
        const val EXTRA_SUGGESTION_BUCKET = "com.warvis.android.doom.EXTRA_SUGGESTION_BUCKET"
        const val EXTRA_IS_BEDTIME = "com.warvis.android.doom.EXTRA_IS_BEDTIME"
    }
}

@Composable
private fun DoomShieldInterventionScreen(
    targetLabel: String,
    sourceLabel: String,
    suggestionTitle: String?,
    suggestionBucket: String?,
    isBedtime: Boolean,
    onOpenWarvis: () -> Unit,
    onGoHome: () -> Unit,
    onContinueAnyway: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Doom Shield caught $targetLabel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isBedtime) {
                Text(
                    text = "🌙 It's past 7 PM — time to wind down, not scroll.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (suggestionTitle != null) {
                Text(
                    text = "Instead of scrolling, try this:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = suggestionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (suggestionBucket != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = suggestionBucket,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "This is the loop that steals your PE sprint time. Take one deliberate action instead of scrolling.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Detected in: $sourceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenWarvis) {
                Text("Open WARVIS")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onGoHome) {
                Text("Go Home")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onContinueAnyway) {
                Text("Continue for 5 minutes")
            }
        }
    }
}
