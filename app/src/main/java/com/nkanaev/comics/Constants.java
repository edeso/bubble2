package com.nkanaev.comics;

public class Constants {
    public static final int COVER_THUMBNAIL_HEIGHT = 260;
    public static final int COVER_THUMBNAIL_WIDTH = 260;

    public static final int MIN_RECENT_COUNT = 5;

    public static final String SETTINGS_NAME = "SETTINGS_COMICS";
    public static final String SETTINGS_LIBRARY_DIR = "SETTINGS_LIBRARY_DIR";
    public static final String SETTINGS_THEME = "SETTINGS_THEME";

    public static final String SETTINGS_PAGE_VIEW_MODE = "SETTINGS_PAGE_VIEW_MODE";
    public static final String SETTINGS_READING_LEFT_TO_RIGHT = "SETTINGS_READING_LEFT_TO_RIGHT";

    public static final String SETTINGS_LIBRARY_SORT = "SETTINGS_LIBRARY_SORT";
    public static final String SETTINGS_LIBRARY_BROWSER_SORT = "SETTINGS_LIBRARY_BROWSER_SORT";
    public static final String SETTINGS_LIBRARY_BROWSER_RECENTS = "SETTINGS_LIBRARY_BROWSER_RECENTS";

    public static final int MESSAGE_MEDIA_UPDATE_FINISHED = 0;
    public static final int MESSAGE_MEDIA_UPDATED = 1;

    public enum PageViewMode {
        ASPECT_FILL(0),
        ASPECT_FIT(1),
        FIT_WIDTH(2);

        PageViewMode(int n) {
            native_int = n;
        }
        public final int native_int;
    }

    public enum SortMode {
        NAME_ASC(0,R.id.sort_name_asc),
        NAME_DESC(1,R.id.sort_name_desc),
        ACCESS_ASC(2,R.id.sort_access_asc),
        ACCESS_DESC(3,R.id.sort_access_desc),
        SIZE_ASC(4,R.id.sort_size_asc),
        SIZE_DESC(5,R.id.sort_size_desc),
        CREATION_ASC(6,R.id.sort_creation_asc),
        CREATION_DESC(7,R.id.sort_creation_desc),
        MODIFIED_ASC(8,R.id.sort_modified_asc),
        MODIFIED_DESC(9,R.id.sort_modified_desc),
        PAGES_READ_ASC(10,R.id.sort_pages_read_asc),
        PAGES_READ_DESC(11,R.id.sort_pages_read_desc),
        PAGES_LEFT_ASC(12,R.id.sort_pages_left_asc),
        PAGES_LEFT_DESC(13,R.id.sort_pages_left_desc);

        public final int id, resId;
        SortMode(int id, int resId) {
            this.id = id;
            this.resId = resId;
        }
    }
}
