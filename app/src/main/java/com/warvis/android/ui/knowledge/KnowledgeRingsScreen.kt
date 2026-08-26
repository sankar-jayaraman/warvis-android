package com.warvis.android.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.KnowledgeRing
import com.warvis.android.data.model.WarvisState
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.SectionTitle
import com.warvis.android.ui.components.WarvisCard
import com.warvis.android.ui.components.WarvisOutlinedCard

@Composable
fun KnowledgeRingsScreen(
    state: WarvisState,
    onScoreChange: (String, Int) -> Unit,
) {
    ScreenScaffold(
        title = "Knowledge Rings",
        subtitle = "Weekly self-scores for the five capability gaps.",
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.suggestedFocusRing?.let { ring ->
                WarvisCard {
                    SectionTitle("Suggested focus")
                    Text(text = ring.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Lowest current score: ${ring.score}/5. Use this as the next study focus.")
                }
            }
            state.knowledgeRings.forEach { ring ->
                KnowledgeRingCard(ring = ring, onScoreChange = onScoreChange)
            }
        }
    }
}

@Composable
private fun KnowledgeRingCard(
    ring: KnowledgeRing,
    onScoreChange: (String, Int) -> Unit,
) {
    WarvisOutlinedCard {
        Text(text = "${ring.ringNumber}. ${ring.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(ring.gapDescription)
        Text("Current score: ${ring.score}/5 - Last updated: ${ring.lastUpdatedLabel}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                if (score == ring.score) {
                    Button(onClick = { onScoreChange(ring.id, score) }) { Text(score.toString()) }
                } else {
                    OutlinedButton(onClick = { onScoreChange(ring.id, score) }) { Text(score.toString()) }
                }
            }
        }
    }
}
