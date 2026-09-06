package com.jashanpreet.uniquecollage

data class PersonIdentity(
    val id: Int,
    val appearances: MutableList<AppearanceTrack> = mutableListOf()
) {

    fun addAppearance(
        appearance: AppearanceTrack
    ) {
        appearances.add(appearance)
    }

    val appearanceCount: Int
        get() = appearances.size

    val detectionCount: Int
        get() = appearances.sumOf {
            it.faces.size
        }
}