package com.astroloop.game.core

import android.content.Context
import android.content.res.Configuration

/**
 * The overscan margin UI keeps on a television.
 *
 * Many TVs crop the edges of the picture, so on a television every edge-anchored piece of UI
 * stays [FRACTION] of the screen clear of each edge; the playfield itself stays full-bleed. The
 * margin is merged with any real window inset by max, never by sum: a cutout or system bar
 * deeper than the margin already keeps the UI clear on its own edge.
 *
 * Insets only reach the views on API 30+, while TV boxes often run older Android, so the margin
 * is applied where the physical surface size is known rather than delivered as an inset.
 */
object TvOverscan {
    const val FRACTION = 0.05f

    /** [FRACTION] on a television, 0 everywhere else — off a TV, [merge] returns the raw inset. */
    fun fraction(context: Context): Float {
        val type = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (type == Configuration.UI_MODE_TYPE_TELEVISION) FRACTION else 0f
    }

    /** The inset for one edge: the real [raw] inset or [fraction] of [edgeLengthPx], whichever is larger. */
    fun merge(raw: Float, edgeLengthPx: Float, fraction: Float): Float =
        maxOf(raw, edgeLengthPx * fraction)
}
