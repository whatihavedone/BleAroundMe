package com.whatihavedone.blearoundme.data

import kotlinx.serialization.Serializable

@Serializable
data class MacPrefix(
    val address: String,
    val tag: String
) {
    companion object {
        fun getDefaultMacPrefixes(): Set<MacPrefix> {
            // Comprehensive wearable device MAC prefixes
            // Copied from
            // https://github.com/sh4d0wm45k/glass-detect/blob/39ab8c9dfddde3ebe26381615aec37d51f8a0532/glass-detect/glass-detect.ino#L21
            return setOf(
                // Meta AR Glasses & VR Devices
                MacPrefix("7C2A9E", "Meta Quest/Ray-Ban"),
                MacPrefix("CC660A", "Meta Quest"),
                MacPrefix("F40343", "Meta Quest"),
                MacPrefix("5CE91E", "Meta Quest"),
            )
        }
    }
}