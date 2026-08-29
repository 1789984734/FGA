package io.github.fate_grand_automata.ui.exit

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fate_grand_automata.R
import io.github.fate_grand_automata.scripts.entrypoints.AutoSkillUpgrade
import io.github.fate_grand_automata.ui.FgaScreen
import io.github.fate_grand_automata.util.KnownException
import io.github.fate_grand_automata.util.messageAndStackTrace

@Composable
fun SkillExit(
    exception: AutoSkillUpgrade.ExitException,
    onClose: () -> Unit,
    onCopy: () -> Unit
) {
    FgaScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Text(
                        text = exception.reasonText(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(
                    items = exception.state.skillSummaryList,
                    key = { it.skillNumber }
                ) { summary ->
                    SkillSummary(
                        summary = summary,
                        wasStoppedByUser = exception.reason == AutoSkillUpgrade.ExitReason.Abort
                    )
                    HorizontalDivider()
                }

                val reason = exception.reason
                if (reason is AutoSkillUpgrade.ExitReason.Unexpected) {
                    item {
                        SelectionContainer {
                            Text(
                                text = reason.e.messageAndStackTrace,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (exception.reason is AutoSkillUpgrade.ExitReason.Unexpected) {
                    TextButton(onClick = onCopy) {
                        Text(stringResource(R.string.unexpected_error_copy))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onClose) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun AutoSkillUpgrade.ExitException.reasonText(): String = when (val reason = reason) {
    AutoSkillUpgrade.ExitReason.Done -> stringResource(R.string.skill_upgrade_done)
    AutoSkillUpgrade.ExitReason.RanOutOfQP -> stringResource(R.string.skill_upgrade_qp_insufficient)
    AutoSkillUpgrade.ExitReason.NoServantSelected ->
        stringResource(R.string.skill_upgrade_no_servant_selected)

    AutoSkillUpgrade.ExitReason.PageRecognitionFailed ->
        stringResource(R.string.skill_upgrade_page_recognition_failed)

    is AutoSkillUpgrade.ExitReason.OcrFailed ->
        "${stringResource(R.string.skill_upgrade_number, reason.skillNumber)}: " +
            stringResource(R.string.skill_upgrade_ocr_failed)

    is AutoSkillUpgrade.ExitReason.NoProgress -> {
        val skill = stringResource(R.string.skill_upgrade_number, reason.skillNumber)
        val targetLevel = state.skillSummaryList
            .firstOrNull { it.skillNumber == reason.skillNumber }
            ?.targetLevel
        val detail = targetLevel?.let {
            stringResource(R.string.skill_upgrade_target_not_reached, it)
        } ?: stringResource(R.string.unexpected_error)

        "$skill: $detail"
    }

    AutoSkillUpgrade.ExitReason.Abort -> stringResource(R.string.skill_upgrade_stopped_by_user)
    is AutoSkillUpgrade.ExitReason.Unexpected -> {
        val error = reason.e
        if (error is KnownException) {
            stringResource(error.reason.resId, *error.reason.args)
        } else {
            "${stringResource(R.string.unexpected_error)}: ${error.message.orEmpty()}"
        }
    }
}

@Composable
private fun SkillSummary(
    summary: AutoSkillUpgrade.Summary,
    wasStoppedByUser: Boolean
) {
    val startingLevel = summary.startingLevel
    val endLevel = summary.endLevel

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.skill_upgrade_number, summary.skillNumber),
            style = MaterialTheme.typography.titleMedium
        )

        if (startingLevel != null && endLevel != null) {
            Text(
                text = stringResource(
                    R.string.skill_upgrade_level_change,
                    startingLevel,
                    endLevel
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = summary.resultText(wasStoppedByUser),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun AutoSkillUpgrade.Summary.resultText(wasStoppedByUser: Boolean): String {
    val endingLevel = endLevel

    return when (result) {
        AutoSkillUpgrade.EnhancementExitReason.TargetLevelMet ->
            stringResource(R.string.skill_upgrade_target_reached)

        AutoSkillUpgrade.EnhancementExitReason.OutOfMaterials ->
            stringResource(R.string.skill_upgrade_materials_insufficient)

        AutoSkillUpgrade.EnhancementExitReason.OutOfQP ->
            stringResource(R.string.skill_upgrade_qp_insufficient)

        AutoSkillUpgrade.EnhancementExitReason.SkippedAfterOutOfQP ->
            stringResource(R.string.skill_upgrade_not_processed_qp)

        AutoSkillUpgrade.EnhancementExitReason.OcrFailed ->
            stringResource(R.string.skill_upgrade_ocr_failed)

        AutoSkillUpgrade.EnhancementExitReason.PageRecognitionFailed ->
            stringResource(R.string.skill_upgrade_page_recognition_failed)

        AutoSkillUpgrade.EnhancementExitReason.NoProgress ->
            stringResource(R.string.skill_upgrade_target_not_reached, targetLevel)

        null -> when {
            wasStoppedByUser -> stringResource(R.string.skill_upgrade_stopped_by_user)

            endingLevel != null && endingLevel >= targetLevel ->
                stringResource(R.string.skill_upgrade_target_reached)

            startingLevel == null && endingLevel == null ->
                stringResource(R.string.skill_upgrade_unavailable)

            else -> stringResource(R.string.skill_upgrade_target_not_reached, targetLevel)
        }
    }
}

@Preview(name = "Light", widthDp = 600, heightDp = 360)
@Preview(
    name = "Dark",
    widthDp = 600,
    heightDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewSkillExit() {
    SkillExit(
        exception = AutoSkillUpgrade.ExitException(
            reason = AutoSkillUpgrade.ExitReason.Done,
            state = AutoSkillUpgrade.ExitState(
                skillSummaryList = listOf(
                    AutoSkillUpgrade.Summary(
                        skillNumber = 1,
                        startingLevel = 1,
                        endLevel = 9,
                        targetLevel = 9,
                        result = AutoSkillUpgrade.EnhancementExitReason.TargetLevelMet
                    ),
                    AutoSkillUpgrade.Summary(
                        skillNumber = 2,
                        startingLevel = 6,
                        endLevel = 7,
                        targetLevel = 9,
                        result = AutoSkillUpgrade.EnhancementExitReason.OutOfMaterials
                    ),
                    AutoSkillUpgrade.Summary(
                        skillNumber = 3,
                        startingLevel = 9,
                        endLevel = 9,
                        targetLevel = 9,
                        result = AutoSkillUpgrade.EnhancementExitReason.TargetLevelMet
                    )
                )
            )
        ),
        onClose = {},
        onCopy = {}
    )
}
