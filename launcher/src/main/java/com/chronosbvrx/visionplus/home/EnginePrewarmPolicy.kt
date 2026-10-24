package com.chronosbvrx.visionplus.home

import com.chronosbvrx.visionplus.home.contract.EnginePrewarmContract

/**
 * Pura y testeable: el launcher solo pide el motor en TV con 2 GB o más.
 *
 * Los equipos de 1.5 GB (Chromecast HD/4K, Fire TV Stick) se quedan con el camino
 * diferido de Vision+; arrancar `:x4k` en su arranque en frío provoca LMK.
 */
object EnginePrewarmPolicy {
    fun shouldPrewarm(
        totalRamMb: Long,
        isTv: Boolean,
        enabled: Boolean = true,
    ): Boolean = enabled && isTv && totalRamMb >= EnginePrewarmContract.MIN_RAM_MB
}
