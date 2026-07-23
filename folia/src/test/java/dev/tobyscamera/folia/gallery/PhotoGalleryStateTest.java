package dev.tobyscamera.folia.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.storage.PhotoQuery;
import org.junit.jupiter.api.Test;

class PhotoGalleryStateTest {
    @Test
    void resetsThePageWhenSearchOrSortChanges() {
        PhotoGalleryState state = new PhotoGalleryState();

        state.nextPage();
        state.setTerm("Toby");
        assertEquals(new PhotoQuery("Toby", PhotoQuery.Sort.NEWEST, 0, 45), state.query());

        state.nextPage();
        state.toggleSort();
        assertEquals(new PhotoQuery("Toby", PhotoQuery.Sort.OLDEST, 0, 45), state.query());
    }

    @Test
    void neverMovesBeforeTheFirstPage() {
        PhotoGalleryState state = new PhotoGalleryState();

        state.previousPage();

        assertEquals(0, state.page());
    }
}
