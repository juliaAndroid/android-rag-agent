package dev.juliamorozova.ragagent.rag.agent

/**
 * Neutralises `&`, `<` and `>` so untrusted text placed inside an XML-style prompt tag
 * (e.g. `<exchange>`) can't close that tag early and smuggle text out as if it were part
 * of the prompt. `&` goes first so the replacements stay unambiguous.
 */
internal fun String.escapeForPrompt(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
