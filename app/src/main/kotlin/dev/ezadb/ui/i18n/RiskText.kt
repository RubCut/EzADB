package dev.ezadb.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ezadb.R

@Composable
fun riskReasonText(reason: String): String = when (reason) {
    "rm_root" -> stringResource(R.string.risk_rm_root)
    "rm_system" -> stringResource(R.string.risk_rm_system)
    "dd_block" -> stringResource(R.string.risk_dd_block)
    "flash" -> stringResource(R.string.risk_flash)
    "reboot_bootloader" -> stringResource(R.string.risk_reboot_bootloader)
    "pm_uninstall_clear" -> stringResource(R.string.risk_pm_uninstall_clear)
    "critical_global" -> stringResource(R.string.risk_critical_global)
    "disable_network" -> stringResource(R.string.risk_disable_network)
    "chmod_root" -> stringResource(R.string.risk_chmod_root)
    "overwrite_block" -> stringResource(R.string.risk_overwrite_block)
    "reboot" -> stringResource(R.string.risk_reboot)
    "settings_change" -> stringResource(R.string.risk_settings_change)
    "app_state" -> stringResource(R.string.risk_app_state)
    "force_stop" -> stringResource(R.string.risk_force_stop)
    "fs_process" -> stringResource(R.string.risk_fs_process)
    "svc", "cmd_service" -> stringResource(R.string.risk_svc)
    "low" -> stringResource(R.string.risk_low)
    else -> reason
}
