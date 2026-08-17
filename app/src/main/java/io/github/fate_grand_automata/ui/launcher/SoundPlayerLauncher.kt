package io.github.fate_grand_automata.ui.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fate_grand_automata.R

@Composable
fun soundPlayerLauncher(
    modifier: Modifier = Modifier
): ScriptLauncherResponseBuilder {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 5.dp)
    ) {
        Text(
            stringResource(R.string.sound_player_title),
            style = MaterialTheme.typography.titleLarge
        )

        HorizontalDivider(
            modifier = Modifier
                .padding(vertical = 8.dp)
        )

        Text(
            stringResource(R.string.sound_player_description),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    return ScriptLauncherResponseBuilder(
        canBuild = { true },
        build = { ScriptLauncherResponse.SoundPlayer }
    )
}
