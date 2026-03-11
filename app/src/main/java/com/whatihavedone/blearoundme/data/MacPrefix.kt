package com.whatihavedone.blearoundme.data

import kotlinx.serialization.Serializable

@Serializable
data class MacPrefix(
    val address: String,
    val tag: String,
    val isManufacturerId: Boolean = false
) {
    companion object {
        fun getDefaultMacPrefixes(): Set<MacPrefix> {
            return setOf(
                // Meta AR Glasses & VR Devices
                MacPrefix("7C2A9E", "Meta Quest/Ray-Ban"),
                MacPrefix("CC660A", "Meta Quest"),
                MacPrefix("F40343", "Meta Quest"),
                MacPrefix("5CE91E", "Meta Quest"),
                
                // Requested Device IDs (Manufacturer IDs)
                MacPrefix("0x01AB", "Smart Glasses 01AB", true),
                MacPrefix("0x058E", "Smart Glasses 058E", true),
                MacPrefix("0x0D53", "Smart Glasses 0D53", true)
            )
        }
    }
}
