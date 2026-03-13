package com.lavacrafter.maptimelinetool.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.BuildConfig
import com.lavacrafter.maptimelinetool.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.action_back))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_title))
        Text(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        Spacer(modifier = Modifier.height(12.dp))
        Divider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = stringResource(R.string.about_project_label))
        Text(text = stringResource(R.string.settings_project_url))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.about_project_license_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.about_project_license_summary))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_project_mit_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.about_project_mit_text))

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.about_open_source_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_open_source_list_full))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_third_party_license_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.about_apache_license_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.about_apache_license_text))

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.about_ai_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_ai_text))
    }
}
