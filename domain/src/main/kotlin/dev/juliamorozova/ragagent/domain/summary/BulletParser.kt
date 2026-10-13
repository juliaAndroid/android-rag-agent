package dev.juliamorozova.ragagent.domain.summary

private val BULLET_MARKER = Regex("""^\s*(?:[-*•–]|\d+[.)])\s+""")

/**
 * Turns a model's bulleted reply into plain bullets: if lines are marked only those count (drops a lead-in), else
 * every non-blank line.
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
