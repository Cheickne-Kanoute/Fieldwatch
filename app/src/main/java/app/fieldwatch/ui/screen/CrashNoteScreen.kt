package app.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.ui.component.FieldwatchActionButton

@Composable
fun CrashNoteScreen(
    note: String,
    onCopy: () -> Unit,
    onContinue: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            LanguageText.text("crash.closed"),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            LanguageText.text("crash.send"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        Spacer(Modifier.height(12.dp))
        FieldwatchActionButton(
            onClick = {
                onCopy()
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(LanguageText.text(if (copied) "ui.copied.label" else "ui.copy.label"))
        }
        Spacer(Modifier.height(8.dp))
        FieldwatchActionButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(LanguageText.text("ui.continue.label"))
        }
    }
}
