package com.xperience.player.domain.model

data class ArtistItem(
    val id: String,
    val name: String,
    val avatarResId: Int? = null,
    val avatarUri: String? = null,
    val genre: String = "Pop",
    val monthlyListeners: String = "2,88 M",
    val subscribers: String = "3,14 M"
)
