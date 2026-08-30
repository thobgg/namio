package de.namio.core.model

/** Die Quizmodi. Wird in Lernkarte und QuizSession per Name persistiert – Reihenfolge und Namen nicht ändern. */
enum class QuizModus {
    FOTO_ZU_NAME_MC,
    FOTO_ZU_NAME_TIPPEN,
    NAME_ZU_FOTO,
    SITZPLAN,
    SPEEDRUN,
}
