package com.example.localbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.localbackup.R
import com.example.localbackup.ui.theme.LocalBackupTheme

@Composable
fun BackupScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
        // Placeholder: the document picker is wired in a later step.
        Button(onClick = { }) {
            Text(stringResource(R.string.select_files))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupScreenPreview() {
    LocalBackupTheme {
        BackupScreen()
    }
}
