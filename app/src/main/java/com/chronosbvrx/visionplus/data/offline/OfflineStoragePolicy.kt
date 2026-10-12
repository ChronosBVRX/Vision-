package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import com.chronosbvrx.visionplus.DeviceKind
import java.io.File

/**
 * Información de un volumen de almacenamiento disponible (memoria interna o almacenamiento extraíble USB/SD).
 */
data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val rootDir: File,
    val vaultDir: File,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
    val usableBytes: Long,
) {
    val totalFormatted: String get() = OfflineStoragePolicy.formatBytes(totalBytes)
    val freeFormatted: String get() = OfflineStoragePolicy.formatBytes(freeBytes)
    val usableFormatted: String get() = OfflineStoragePolicy.formatBytes(usableBytes)
}

/**
 * Resultado de evaluación de elegibilidad para descargas offline.
 */
sealed interface StorageEligibility {
    data class Allowed(val target: StorageVolumeInfo) : StorageEligibility
    data object DisallowedTvNeedsExternal : StorageEligibility
    data object DisallowedInsufficientSpace : StorageEligibility
}

/**
 * Política de almacenamiento para el Modo Offline de Vision+:
 *
 * 1. En Smart TV (DeviceKind.isTelevision == true):
 *    - REGLA ESTRICTA: Las descargas NO están permitidas en la memoria interna para evitar
 *      saturar los escasos 4GB-8GB del sistema operativo y prevenir cierres forzados.
 *    - EXCLUSIVIDAD: Se exige una memoria USB o disco externo extraíble montado.
 *
 * 2. En Teléfonos y Tablets (DeviceKind.isTelevision == false):
 *    - Se utiliza el almacenamiento interno protegido de la app por defecto, o MicroSD si
 *      el usuario cuenta con una.
 */
object OfflineStoragePolicy {

    private const val PREFS = "vision_offline_policy"
    private const val K_ACTIVE_VOLUME_ID = "active_volume_id"
    private const val VAULT_FOLDER_NAME = "vision_vault"
    private const val MIN_REQUIRED_FREE_SPACE_BYTES = 400L * 1024 * 1024 // 400 MB mínimo

    /**
     * Lista todos los volúmenes de almacenamiento disponibles para la aplicación.
     * Utiliza `ContextCompat.getExternalFilesDirs` que no requiere permisos MANAGE_EXTERNAL_STORAGE.
     */
    fun getAvailableVolumes(context: Context): List<StorageVolumeInfo> {
        val app = context.applicationContext
        val dirs = ContextCompat.getExternalFilesDirs(app, null).filterNotNull()
        val sm = runCatching { app.getSystemService(Context.STORAGE_SERVICE) as? StorageManager }.getOrNull()
        val storageVolumes: List<StorageVolume> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sm?.storageVolumes.orEmpty()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val result = mutableListOf<StorageVolumeInfo>()

        dirs.forEachIndexed { index, filesDir ->
            val isPrimary = index == 0
            val stat = runCatching { StatFs(filesDir.absolutePath) }.getOrNull()
            val total = stat?.totalBytes ?: 0L
            val free = stat?.freeBytes ?: 0L
            val usable = stat?.availableBytes ?: 0L

            // Determinar si es removible
            var isRemovable = !isPrimary
            var label = if (isPrimary) "Almacenamiento interno" else "Almacenamiento externo USB/SD"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sm != null) {
                val sv = runCatching { sm.getStorageVolume(filesDir) }.getOrNull()
                if (sv != null) {
                    isRemovable = sv.isRemovable
                    val svDesc = runCatching { sv.getDescription(app) }.getOrNull()
                    if (!svDesc.isNullOrBlank()) {
                        label = svDesc
                    }
                }
            }

            // Si es un USB en Smart TV, formatear etiqueta amigable
            if (isRemovable && label.contains("externo", ignoreCase = true)) {
                label = "Memoria USB / Disco externo"
            }

            val volumeId = if (isPrimary) "internal_primary" else "external_${filesDir.hashCode()}"
            val vaultDir = File(filesDir, VAULT_FOLDER_NAME).apply { mkdirs() }

            result.add(
                StorageVolumeInfo(
                    id = volumeId,
                    label = label,
                    rootDir = filesDir,
                    vaultDir = vaultDir,
                    isRemovable = isRemovable,
                    totalBytes = total,
                    freeBytes = free,
                    usableBytes = usable,
                ),
            )
        }

        // Si no se detectó ningún directorio externo vía getExternalFilesDirs, asegurar que el filesDir privado siempre esté disponible
        if (result.isEmpty()) {
            val privateDir = File(app.filesDir, VAULT_FOLDER_NAME).apply { mkdirs() }
            val stat = runCatching { StatFs(privateDir.absolutePath) }.getOrNull()
            result.add(
                StorageVolumeInfo(
                    id = "internal_files",
                    label = "Almacenamiento interno",
                    rootDir = app.filesDir,
                    vaultDir = privateDir,
                    isRemovable = false,
                    totalBytes = stat?.totalBytes ?: 0L,
                    freeBytes = stat?.freeBytes ?: 0L,
                    usableBytes = stat?.availableBytes ?: 0L,
                ),
            )
        }

        return result
    }

    /**
     * Evalúa si el dispositivo actual es elegible para realizar descargas bajo las políticas de Vision+.
     */
    fun evaluateEligibility(
        context: Context,
        requiredBytes: Long = MIN_REQUIRED_FREE_SPACE_BYTES,
    ): StorageEligibility {
        val isTv = DeviceKind.isTelevision(context)
        val volumes = getAvailableVolumes(context)

        if (isTv) {
            // Política Smart TV: OBLIGATORIO almacenamiento externo USB o disco extraíble
            val externalVolume = volumes.firstOrNull { it.isRemovable }
            if (externalVolume == null) {
                return StorageEligibility.DisallowedTvNeedsExternal
            }
            if (externalVolume.usableBytes < requiredBytes) {
                return StorageEligibility.DisallowedInsufficientSpace
            }
            return StorageEligibility.Allowed(externalVolume)
        }

        // Política Móvil / Tablet: utiliza el volumen activo o el primario
        val active = getActiveVolume(context) ?: volumes.firstOrNull()
        if (active == null || active.usableBytes < requiredBytes) {
            return StorageEligibility.DisallowedInsufficientSpace
        }
        return StorageEligibility.Allowed(active)
    }

    /**
     * Obtiene el volumen actualmente seleccionado para almacenar descargas.
     */
    fun getActiveVolume(context: Context): StorageVolumeInfo? {
        val volumes = getAvailableVolumes(context)
        val isTv = DeviceKind.isTelevision(context)

        // En Smart TV siempre prioriza el primer volumen extraíble
        if (isTv) {
            return volumes.firstOrNull { it.isRemovable }
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedId = prefs.getString(K_ACTIVE_VOLUME_ID, null)
        return volumes.firstOrNull { it.id == savedId } ?: volumes.firstOrNull()
    }

    /**
     * Define el volumen activo para guardar descargas (solo relevante para móviles con múltiples volúmenes).
     */
    fun setActiveVolumeId(context: Context, id: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(K_ACTIVE_VOLUME_ID, id)
            .apply()
    }

    /**
     * Obtiene el directorio seguro (vault) del volumen activo.
     */
    fun getVaultDirectory(context: Context): File {
        val volume = getActiveVolume(context)
        if (volume != null) {
            return volume.vaultDir.apply { mkdirs() }
        }
        // Fallback seguro a sandbox interno
        return File(context.applicationContext.filesDir, VAULT_FOLDER_NAME).apply { mkdirs() }
    }

    /**
     * Calcula el tamaño total ocupado por las descargas de Vision+ en el vault activo.
     */
    fun getUsedVaultBytes(context: Context): Long {
        val vault = getVaultDirectory(context)
        if (!vault.exists() || !vault.isDirectory) return 0L
        return runCatching {
            vault.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * Formatea bytes a una cadena legible (MB o GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.0f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }
    }
}
