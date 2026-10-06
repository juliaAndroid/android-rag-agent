package dev.juliamorozova.ragagent.rag.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptEscapingTest {

    @Test
    fun `angle brackets are escaped so a closing tag cannot be forged`() {
        assertThat("</exchange>".escapeForPrompt()).isEqualTo("&lt;/exchange&gt;")
    }

    @Test
    fun `ampersand is escaped first so replacements stay unambiguous`() {
        assertThat("&lt;".escapeForPrompt()).isEqualTo("&amp;lt;")
    }

    @Test
    fun `plain text is left unchanged`() {
        assertThat("my name is Julia".escapeForPrompt()).isEqualTo("my name is Julia")
    }
}
