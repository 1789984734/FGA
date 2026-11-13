package io.github.fate_grand_automata.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fate_grand_automata.R
import io.github.fate_grand_automata.prefs.core.ServantEnhancementPrefsCore
import io.github.fate_grand_automata.ui.FGATheme
import io.github.fate_grand_automata.ui.prefs.remember


@Composable
fun servantLevelLauncher(
    servantEnhancementPrefsCore: ServantEnhancementPrefsCore,
    modifier: Modifier = Modifier
): ScriptLauncherResponseBuilder {
    var shouldPerformAscension by servantEnhancementPrefsCore.shouldPerformAscension.remember()
    var shouldPerformGrail by servantEnhancementPrefsCore.shouldPerformGrail.remember()

    ServantLevelLauncherContent(
        shouldPerformAscension = shouldPerformAscension,
        shouldPerformGrail = shouldPerformGrail,
        onPerformAscensionChange = { shouldPerformAscension = it },
        onPerformGrailChange = { shouldPerformGrail = it },
        modifier = modifier
    )

    return ScriptLauncherResponseBuilder(
        canBuild = { true },
        build = { ScriptLauncherResponse.ServantEnhancement }
    )
}

@Composable
private fun ServantLevelLauncherContent(
    shouldPerformAscension: Boolean,
    shouldPerformGrail: Boolean,
    onPerformAscensionChange: (Boolean) -> Unit,
    onPerformGrailChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 5.dp)
    ) {
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.background
                    )
            ) {
                Text(
                    text = stringResource(id = R.string.servant_enhancement),
                    style = MaterialTheme.typography.headlineSmall
                )
                HorizontalDivider()
            }
        }
        item {
            Text(
                text = stringResource(R.string.note),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                text = stringResource(id = R.string.servert_enhancement_warning_notice),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
        }
        item {
            HorizontalDivider()
        }

        item {
            RowTextCheckBox(
                text = stringResource(R.string.servant_enhancement_perform_ascension_question),
                status = shouldPerformAscension,
                onStatusChange = onPerformAscensionChange
            )
            Text(
                text = stringResource(R.string.servant_enhancement_perform_ascension_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }
        item {
            RowTextCheckBox(
                text = stringResource(R.string.servant_enhancement_perform_grail),
                status = shouldPerformGrail,
                onStatusChange = onPerformGrailChange
            )
        }
    }
}

@Composable
private fun RowTextCheckBox(
    status: Boolean,
    text: String,
    onStatusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = status,
                role = Role.Checkbox,
                onValueChange = onStatusChange
            )
            .heightIn(min = 48.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Justify,
            modifier = Modifier.weight(1f)
        )

        Checkbox(
            checked = status,
            onCheckedChange = null
        )
    }
}

@Preview(name = "Chinese", locale = "zh-rCN", widthDp = 360)
@Preview(name = "German", locale = "de", widthDp = 360)
@Preview(name = "Russian", locale = "ru", widthDp = 360)
@Preview(name = "Arabic", locale = "ar", widthDp = 360)
@Composable
private fun ServantLevelLauncherPreview() {
    FGATheme {
        ServantLevelLauncherContent(
            shouldPerformAscension = true,
            shouldPerformGrail = false,
            onPerformAscensionChange = {},
            onPerformGrailChange = {}
        )
    }
}
