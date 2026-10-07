package dev.juliamorozova.ragagent.domain.summary

/** A list marker at the start of a line: "- ", "* ", "• ", "– ", "1. " or "1) ". */
private val BULLET_MARKER = Regex("""^\s*(?:[-*•–]|\d+[.)])\s+""")

/**
 * Turns a model's bulleted reply into plain bullet strings, so the on-device and the cloud
 * summarizer return the same shape.
 *
 * When the reply has marked lines, only those count, which drops a lead-in such as
 * "Here are three bullets:"; when it has none, every non-blank line counts.
 */
fun parseBullets(raw: String, maxBullets: Int = 3): List<String> {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val marked = lines.filter { BULLET_MARKER.containsMatchIn(it) }
    val chosen = marked.ifEmpty { lines }
    return chosen
        .map { it.replace(BULLET_MARKER, "").trim() }
        .filter { it.isNotEmpty() }
        .take(maxBullets)
}
