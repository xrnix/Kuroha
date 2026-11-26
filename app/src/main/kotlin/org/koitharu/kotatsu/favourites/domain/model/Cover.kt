package org.koitharu.kotatsu.favourites.domain.model

import org.koitharu.kotatsu.parsers.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource: MangaSource by lazy { org.koitharu.kotatsu.core.model.MangaSource(source) }
}
