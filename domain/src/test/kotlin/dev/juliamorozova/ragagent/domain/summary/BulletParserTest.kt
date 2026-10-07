package dev.juliamorozova.ragagent.domain.summary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BulletParserTest {

    @Test
    fun `dash star and dot markers are stripped`() {
        val raw = "- first\n* second\n• third"

        assertThat(parseBullets(raw)).containsExactly("first", "second", "third").inOrder()
    }

    @Test
    fun `numbered markers are stripped`() {
        assertThat(parseBullets("1. one\n2) two\n3. three")).containsExactly("one", "two", "three").inOrder()
    }

    @Test
    fun `a lead-in without a marker is dropped when marked lines exist`() {
        val raw = "Here are three bullets:\n* a\n* b\n* c"

        assertThat(parseBullets(raw)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `plain lines are kept when nothing is marked`() {
        assertThat(parseBullets("one\n\ntwo")).containsExactly("one", "two").inOrder()
    }

    @Test
    fun `more than three bullets are cut to three`() {
        assertThat(parseBullets("- a\n- b\n- c\n- d\n- e")).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `the limit can be changed`() {
        assertThat(parseBullets("- a\n- b\n- c", maxBullets = 2)).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `bold text at the start of a line is not mistaken for a marker`() {
        assertThat(parseBullets("**Summary**\n- a")).containsExactly("a")
    }

    @Test
    fun `blank input gives no bullets`() {
        assertThat(parseBullets("   \n\n")).isEmpty()
    }

    @Test
    fun `a bullet with only a marker is dropped`() {
        assertThat(parseBullets("- \n- real")).containsExactly("real")
    }
}
