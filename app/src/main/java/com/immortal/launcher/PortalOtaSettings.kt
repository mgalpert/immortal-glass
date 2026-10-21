/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.immortal.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortal.launcher.settings.SettingsDomains
import org.json.JSONObject

@Composable
internal fun PortalOtaSettingsSection() {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(PortalOtaConfig.load(context)) }
  Spacer(Modifier.height(26.dp))
  Text("Daily Portal content", color = Color.White, fontSize = 20.sp)
  SettingsList(SettingsDomains.portal, settings) { key, value ->
    SettingsDomains.portal.apply(context, JSONObject().put(key, value))
    settings = PortalOtaConfig.load(context)
  }
}
