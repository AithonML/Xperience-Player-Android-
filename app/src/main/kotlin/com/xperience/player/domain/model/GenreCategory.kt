package com.xperience.player.domain.model

import androidx.compose.ui.graphics.Color

data class GenreCategory(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val description: String = ""
)

data class YtmMoodCategory(
    val id: String,
    val title: String,
    val browseId: String,
    val params: String? = null,
    val stripeColorArgb: Long = 0xFF6366F1L
) {
    val stripeColor: Color
        get() = Color(stripeColorArgb)
}

data class YtmMoodSection(
    val title: String,
    val items: List<YtmMoodCategory>
)

data class YtmMoodsAndGenresFeed(
    val sections: List<YtmMoodSection> = emptyList(),
    val isFromYouTubeMusic: Boolean = false
)

data class YtmMoodCategoryDetail(
    val categoryTitle: String,
    val browseId: String,
    val params: String? = null,
    val playlists: List<RecommendedPlaylistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val songs: List<TrackItem> = emptyList()
)

object GenreCatalog {
    val allGenres: List<GenreCategory> = listOf(
        GenreCategory(
            id = "pop",
            name = "Pop",
            primaryColor = Color(0xFFE91E63),
            secondaryColor = Color(0xFFFF4081),
            description = "Global hits, catchy hooks, and vibrant bright melodies"
        ),
        GenreCategory(
            id = "rock",
            name = "Rock",
            primaryColor = Color(0xFFD32F2F),
            secondaryColor = Color(0xFFEF5350),
            description = "Electric guitars, thunderous drums, and raw energy"
        ),
        GenreCategory(
            id = "blues",
            name = "Blues",
            primaryColor = Color(0xFF1976D2),
            secondaryColor = Color(0xFF42A5F5),
            description = "Nostalgic riffs, deep soul, and emotional roots"
        ),
        GenreCategory(
            id = "cumbia",
            name = "Cumbia",
            primaryColor = Color(0xFFF57C00),
            secondaryColor = Color(0xFFFFB74D),
            description = "Tropical rhythms, crisp percussion, and joyful dancing"
        ),
        GenreCategory(
            id = "kpop",
            name = "K-Pop",
            primaryColor = Color(0xFF9C27B0),
            secondaryColor = Color(0xFFCE93D8),
            description = "Cutting-edge Korean pop, sharp choreographies, and energetic beats"
        ),
        GenreCategory(
            id = "jazz",
            name = "Jazz",
            primaryColor = Color(0xFF795548),
            secondaryColor = Color(0xFFBCAAA4),
            description = "Sublime improvisation, elegant brass, and rich timeless harmonies"
        ),
        GenreCategory(
            id = "reggaeton",
            name = "Reggaeton",
            primaryColor = Color(0xFFE64A19),
            secondaryColor = Color(0xFFFF7043),
            description = "Urban dembow rhythms, infectious basslines, and Caribbean heat"
        ),
        GenreCategory(
            id = "hiphop",
            name = "Hip-Hop",
            primaryColor = Color(0xFF512DA8),
            secondaryColor = Color(0xFF7E57C2),
            description = "Heavy 808s, rhythmic flow, clever rhymes, and urban culture"
        ),
        GenreCategory(
            id = "electronic",
            name = "Electronic",
            primaryColor = Color(0xFF00ACC1),
            secondaryColor = Color(0xFF4DD0E1),
            description = "Pulsing synthesizers, dynamic drops, and club-ready soundscapes"
        ),
        GenreCategory(
            id = "synthwave",
            name = "Synthwave",
            primaryColor = Color(0xFFC2185B),
            secondaryColor = Color(0xFFEC407A),
            description = "Retro-futuristic neon vibes and nostalgic 80s analog synths"
        ),
        GenreCategory(
            id = "rnb",
            name = "R&B & Soul",
            primaryColor = Color(0xFF6A1B9A),
            secondaryColor = Color(0xFFAB47BC),
            description = "Velvety vocals, seductive grooves, and genuine emotional warmth"
        ),
        GenreCategory(
            id = "metal",
            name = "Metal",
            primaryColor = Color(0xFF37474F),
            secondaryColor = Color(0xFF78909C),
            description = "Heavy distortion, double bass pedals, and thunderous power"
        ),
        GenreCategory(
            id = "indie",
            name = "Indie & Alternative",
            primaryColor = Color(0xFF00796B),
            secondaryColor = Color(0xFF26A69A),
            description = "Creative songwriting, shimmering guitars, and indie spirit"
        ),
        GenreCategory(
            id = "salsa",
            name = "Salsa",
            primaryColor = Color(0xFFC62828),
            secondaryColor = Color(0xFFE57373),
            description = "Afro-Cuban rhythms, bright brass, and fiery Latin dance grooves"
        ),
        GenreCategory(
            id = "bachata",
            name = "Bachata",
            primaryColor = Color(0xFFAD1457),
            secondaryColor = Color(0xFFF48FB1),
            description = "Romantic requinto guitar, gentle bongo beats, and Caribbean passion"
        ),
        GenreCategory(
            id = "classical",
            name = "Classical",
            primaryColor = Color(0xFF827717),
            secondaryColor = Color(0xFFD4E157),
            description = "Timeless symphonies, virtuosic piano, and grand orchestral works"
        ),
        GenreCategory(
            id = "trap",
            name = "Trap",
            primaryColor = Color(0xFF303F9F),
            secondaryColor = Color(0xFF7986CB),
            description = "Rapid hi-hat rolls, rumbling sub-bass, and crisp modern flow"
        ),
        GenreCategory(
            id = "country",
            name = "Country",
            primaryColor = Color(0xFF5D4037),
            secondaryColor = Color(0xFF8D6E63),
            description = "Honest stories, acoustic twang, banjos, and wide open roads"
        ),
        GenreCategory(
            id = "punk",
            name = "Punk",
            primaryColor = Color(0xFFBF360C),
            secondaryColor = Color(0xFFFF5722),
            description = "Fast tempos, rebellious spirit, and straightforward three-chord energy"
        ),
        GenreCategory(
            id = "folk",
            name = "Folk & Acoustic",
            primaryColor = Color(0xFF33691E),
            secondaryColor = Color(0xFF7CB342),
            description = "Warm wooden acoustics, earnest lyrics, and organic melodies"
        ),
        GenreCategory(
            id = "latinurban",
            name = "Latin Urban",
            primaryColor = Color(0xFFF4511E),
            secondaryColor = Color(0xFFFF8A65),
            description = "Dynamic fusion of Latin rhythms with modern trap and hip-hop"
        ),
        GenreCategory(
            id = "reggae",
            name = "Reggae",
            primaryColor = Color(0xFF2E7D32),
            secondaryColor = Color(0xFF66BB6A),
            description = "Deep basslines, rhythmic offbeats, and uplifting positive vibrations"
        ),
        GenreCategory(
            id = "disco",
            name = "Disco",
            primaryColor = Color(0xFF7B1FA2),
            secondaryColor = Color(0xFFBA68C8),
            description = "Shimmering mirror balls, funky basslines, and euphoric 70s party vibes"
        ),
        GenreCategory(
            id = "funk",
            name = "Funk",
            primaryColor = Color(0xFFEF6C00),
            secondaryColor = Color(0xFFFFA726),
            description = "Punchy slap bass, syncopated horns, and irresistible pocket grooves"
        ),
        GenreCategory(
            id = "house",
            name = "House & Techno",
            primaryColor = Color(0xFF0288D1),
            secondaryColor = Color(0xFF29B6F6),
            description = "Four-on-the-floor kicks, hypnotic loops, and underground warehouse beats"
        ),
        GenreCategory(
            id = "lofi",
            name = "Lo-Fi Beats",
            primaryColor = Color(0xFF455A64),
            secondaryColor = Color(0xFF90A4AE),
            description = "Warm vinyl crackle and chill jazz chords for studying or relaxing"
        ),
        GenreCategory(
            id = "ambient",
            name = "Ambient & Chill",
            primaryColor = Color(0xFF00838F),
            secondaryColor = Color(0xFF26C6DA),
            description = "Ethereal soundscapes, spacious reverbs, and calming meditation"
        ),
        GenreCategory(
            id = "afrobeat",
            name = "Afrobeat",
            primaryColor = Color(0xFFFBC02D),
            secondaryColor = Color(0xFFFFF176),
            description = "Hypnotic polyrhythms, sun-drenched guitars, and energetic brass"
        ),
        GenreCategory(
            id = "flamenco",
            name = "Flamenco",
            primaryColor = Color(0xFFB71C1C),
            secondaryColor = Color(0xFFE57373),
            description = "Passionate Spanish guitar, expressive vocals, and rhythmic handclaps"
        ),
        GenreCategory(
            id = "bolero",
            name = "Bolero",
            primaryColor = Color(0xFF880E4F),
            secondaryColor = Color(0xFFC2185B),
            description = "Romantic acoustic ballads, delicate strings, and timeless poetry"
        ),
        GenreCategory(
            id = "tango",
            name = "Tango",
            primaryColor = Color(0xFF4A148C),
            secondaryColor = Color(0xFF8E24AA),
            description = "Expressive bandoneon, sharp dramatic melodies, and classic elegance"
        ),
        GenreCategory(
            id = "corridos",
            name = "Corridos",
            primaryColor = Color(0xFF6D4C41),
            secondaryColor = Color(0xFFA1887F),
            description = "Acoustic twelve-string guitars, accordion, and rich regional storytelling"
        )
    )
}
