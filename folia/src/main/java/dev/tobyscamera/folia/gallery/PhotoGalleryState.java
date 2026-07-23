package dev.tobyscamera.folia.gallery;

import dev.tobyscamera.folia.storage.PhotoQuery;

/** Per-admin browsing state without Bukkit inventory dependencies. */
public final class PhotoGalleryState {
    public static final int PAGE_SIZE = 45;

    private String term = "";
    private PhotoQuery.Sort sort = PhotoQuery.Sort.NEWEST;
    private int page;

    public PhotoQuery query() { return new PhotoQuery(term, sort, page, PAGE_SIZE); }
    public int page() { return page; }
    public String term() { return term; }
    public PhotoQuery.Sort sort() { return sort; }

    public void setTerm(String value) {
        term = value == null ? "" : value.trim();
        page = 0;
    }

    public void clearTerm() { setTerm(""); }
    public void nextPage() { page++; }
    public void previousPage() { if (page > 0) page--; }

    public void toggleSort() {
        sort = sort == PhotoQuery.Sort.NEWEST ? PhotoQuery.Sort.OLDEST : PhotoQuery.Sort.NEWEST;
        page = 0;
    }
}
