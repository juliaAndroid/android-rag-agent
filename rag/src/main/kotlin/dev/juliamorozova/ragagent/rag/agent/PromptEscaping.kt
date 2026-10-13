package dev.juliamorozova.ragagent.rag.agent

/** Escapes `&`, `<`, `>` so untrusted text in an XML-style prompt tag can't close it early; `&` goes first. */
internal fun String.escapeForPrompt(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
