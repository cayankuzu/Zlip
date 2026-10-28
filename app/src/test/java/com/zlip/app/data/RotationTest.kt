package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationTest {

    private fun text(id: String) = CoverItem.Text(id = id, name = id, text = id)

    private fun group(id: String, vararg steps: CoverItem) = CoverItem.Group(id = id, name = id, items = steps.toList())

    private fun ids(items: List<CoverItem>) = items.map { it.id }

    @Test
    fun withoutAPlaylistAGroupsStepsDoNotAlsoRotateOnTheirOwn() {
        val a = text("a")
        val b = text("b")
        val c = text("c")
        val other = text("other")
        // "Grup yap" after picking a, b and c: the photos stay in the library, the group plays them.
        val items = listOf(other, a, b, c, group("g", a, b, c))
        assertEquals(listOf("other", "g"), ids(Rotation.of(items, emptyList())))
    }

    @Test
    fun deletingTheGroupBringsItsStepsBack() {
        val a = text("a")
        val b = text("b")
        assertEquals(listOf("a", "b"), ids(Rotation.of(listOf(a, b), emptyList())))
    }

    @Test
    fun aStepDeletedFromTheLibraryChangesNothingElse() {
        // The group keeps its own copy of "a"; the library no longer has it.
        val items = listOf(text("b"), group("g", text("a"), text("c")))
        assertEquals(listOf("b", "g"), ids(Rotation.of(items, emptyList())))
    }

    @Test
    fun aCustomPlaylistIsUsedAsItIsStepsIncluded() {
        val a = text("a")
        val b = text("b")
        val items = listOf(a, b, group("g", a, b))
        assertEquals(listOf("a", "g"), ids(Rotation.of(items, listOf("a", "g"))))
    }

    @Test
    fun playlistIdsNoLongerInTheLibraryAreSkipped() {
        val items = listOf(text("a"), text("b"))
        assertEquals(listOf("b"), ids(Rotation.of(items, listOf("gone", "b"))))
    }

    @Test
    fun aPlaylistOfOnlyMissingItemsFallsBackToTheLibrary() {
        val items = listOf(text("a"), text("b"))
        assertEquals(listOf("a", "b"), ids(Rotation.of(items, listOf("gone"))))
    }

    @Test
    fun aLibraryOfOneGroupAndItsStepsRotatesTheGroupAlone() {
        val a = text("a")
        val b = text("b")
        assertEquals(listOf("g"), ids(Rotation.of(listOf(a, b, group("g", a, b)), emptyList())))
        assertEquals(emptyList<String>(), ids(Rotation.of(emptyList(), emptyList())))
    }

    @Test
    fun aStepSharedByTwoGroupsStaysOutWhileEitherGroupIsThere() {
        val a = text("a")
        val items = listOf(a, text("b"), group("g1", a, text("b2")), group("g2", a))
        assertEquals(listOf("b", "g1", "g2"), ids(Rotation.of(items, emptyList())))
    }

    @Test
    fun libraryCleanerRemovesGroupMembersAndDeduplicatesContent() {
        val a = text("a")
        val aDuplicate = CoverItem.Text(id = "a_dup", name = "Copy", text = "a")
        val b = text("b")
        val bCloneInGroup = CoverItem.Text(id = "b_clone", name = "b", text = "b")
        val c = text("c")
        val grp = group("g1", bCloneInGroup, c)
        val cleaned = LibraryCleaner.clean(listOf(a, aDuplicate, b, c, grp))
        assertEquals(listOf("a", "g1"), ids(cleaned))
    }
}
