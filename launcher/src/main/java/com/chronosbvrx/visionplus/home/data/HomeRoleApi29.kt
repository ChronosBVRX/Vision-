package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi

/**
 * RoleManager vive en Android 10+. Se aísla en su propia clase para que el launcher de Android 9
 * (por ejemplo la TCL) nunca intente cargar una API que no existe al verificar `AppsRepository`.
 */
@RequiresApi(29)
internal object HomeRoleApi29 {

    fun isAvailable(context: Context): Boolean = runCatching {
        role(context)?.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) == true
    }.getOrDefault(false)

    fun isHeld(context: Context): Boolean = runCatching {
        role(context)?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true
    }.getOrDefault(false)

    /** El intent que abre la ventana del sistema para elegir el launcher predeterminado. */
    fun requestIntent(context: Context): Intent? = runCatching {
        role(context)?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    }.getOrNull()

    private fun role(context: Context) = context.getSystemService(android.app.role.RoleManager::class.java)
}
