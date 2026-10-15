package com.kiylx.tools.compose_ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kiylx.tools.compose_ui.R

@Composable
fun ConfirmButton(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.confirm),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text)
    }
}

@Composable
fun DismissButton(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.dismiss),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.padding(end = 8.dp)) {
        Text(text)
    }
}