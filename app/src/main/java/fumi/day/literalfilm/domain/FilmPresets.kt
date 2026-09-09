package fumi.day.literalfilm.domain

import fumi.day.literalfilm.domain.model.FilmPreset

/** First-run seed data — consumed once by PresetRepository.ensureSeeded(), then just ordinary saved presets (deletable, editable like anything user-created). */
object FilmPresets {

    val SEED: List<FilmPreset> =
        listOf(
            FilmPreset.EDITOR_DEFAULT.copy(id = "seed_standard", displayName = "Standard", contrast = 0.35f, monochrome = false),
            FilmPreset.EDITOR_DEFAULT.copy(id = "seed_mono", displayName = "Mono", contrast = 0.55f, monochrome = true),
        )
}
