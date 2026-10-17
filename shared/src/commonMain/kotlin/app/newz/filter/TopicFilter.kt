package app.newz.filter

import app.newz.feed.Article

/**
 * The topic chip bar's view filter (SPEC S14): keeps, in order, the articles carrying any of the
 * [selected] topic ids. It never touches preferences; an empty selection returns [list] itself.
 */
fun applyTopicFilter(
    list: List<Article>,
    selected: Set<String>,
): List<Article> = if (selected.isEmpty()) list else list.filter { a -> a.topics.any { it in selected } }
