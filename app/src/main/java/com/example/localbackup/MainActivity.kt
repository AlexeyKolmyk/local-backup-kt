package com.example.localbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.localbackup.ui.BackupScreen
import com.example.localbackup.ui.theme.LocalBackupTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalBackupTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BackupScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
