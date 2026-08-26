package com.warvis.android.ui.reports

import android.content.Intent
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private const val REPORT_ASSET = "reports/dram_terafab_allin.pdf"

@Composable
fun ReportsScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "📄 Research Reports",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Tap a report to open it in your PDF reader.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ReportCard(
            title = "DRAM & Terafab — All In E278",
            subtitle = "All In Podcast E278 · June 2026",
            description = "DRAM market crisis, Terafab, Micron's record earnings, AI memory crunch, Apple price hikes, and geopolitical implications.",
            onOpen = {
                openPdfAsset(context, REPORT_ASSET, "dram_terafab_allin.pdf")
            },
        )
    }
}

@Composable
private fun ReportCard(
    title: String,
    subtitle: String,
    description: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("Open Report")
            }
        }
    }
}

private fun openPdfAsset(context: Context, assetPath: String, fileName: String) {
    val cacheFile = File(context.cacheDir, fileName)
    if (!cacheFile.exists()) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        }
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cacheFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Open with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
