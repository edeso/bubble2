package com.nkanaev.comics.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.BuildConfig;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.PreCachingGridLayoutManager;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class LibraryBrowserFragment extends Fragment
        implements SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener,
        UpdateHandlerTarget {
    public static final String PARAM_PATH = "browserCurrentPath";

    final int ITEM_VIEW_TYPE_COMIC = -1;
    final int ITEM_VIEW_TYPE_HEADER_RECENT = -2;
    final int ITEM_VIEW_TYPE_HEADER_ALL = -3;

    final int NUM_HEADERS = 2;

    private List<Comic> mComics = new ArrayList<>();
    private List<Comic> mAllItems = new ArrayList<>();
    private List<Comic> mRecentItems = new ArrayList<>();

    private String mPath;
    private Picasso mPicasso;
    private String mFilterSearch = "";
    private int mFilterRead = R.id.menu_browser_filter_all;
    private int mSort = R.id.sort_name_asc;

    private RecyclerView mComicListView;
    private SwipeRefreshLayout mRefreshLayout;
    private Handler mUpdateHandler = new LibraryFragment.UpdateHandler(this);
    private MenuItem mRefreshItem;
    //private Long mCacheStamp = Long.valueOf(System.currentTimeMillis());
    //private HashMap<Uri, Long> mCache = new HashMap();

    public static LibraryBrowserFragment create(String path) {
        LibraryBrowserFragment fragment = new LibraryBrowserFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    public LibraryBrowserFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPath = getArguments().getString(PARAM_PATH);

        // restore saved sorting
        int savedSortMode = MainApplication.getPreferences().getInt(
                Constants.SETTINGS_LIBRARY_BROWSER_SORT,
                Constants.SortMode.NAME_ASC.id);
        mSort = R.id.sort_name_asc;
        for (Constants.SortMode mode: Constants.SortMode.values()) {
            if (savedSortMode != mode.id)
                continue;
            mSort = mode.resId;
            break;
        }

        getComics();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_librarybrowser, container, false);

        final int numColumns = calculateNumColumns();
        int spacing = (int) getResources().getDimension(R.dimen.grid_margin);

        PreCachingGridLayoutManager layoutManager = new PreCachingGridLayoutManager(getActivity(), numColumns);
        layoutManager.setSpanSizeLookup(createSpanSizeLookup());
        int height = Utils.getDeviceHeightPixels();
        layoutManager.setExtraLayoutSpace(height * 2);

        mComicListView = (RecyclerView) view.findViewById(R.id.library_grid);
        // some preformance optimizations
        mComicListView.setHasFixedSize(true);
        // raise default cache values (number of cards) from a very low DEFAULT_CACHE_SIZE=2
        mComicListView.setItemViewCacheSize(Math.max(4 * numColumns, 40));
        //mComicListView.getRecycledViewPool().setMaxRecycledViews(ITEM_VIEW_TYPE_COMIC,20);

        mComicListView.setLayoutManager(layoutManager);
        mComicListView.setAdapter(new ComicGridAdapter());
        mComicListView.addItemDecoration(new GridSpacingItemDecoration(numColumns, spacing));
        registerForContextMenu(mComicListView);

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.fragmentLibraryBrowserRefreshLayout);
        mRefreshLayout.setColorSchemeResources(R.color.refreshProgress);
        mRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refreshProgressBackground);
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        File path = new File(getArguments().getString(PARAM_PATH));
        getActivity().setTitle(path.getName());
        ((MainActivity) getActivity()).setSubTitle(Utils.appendSlashIfMissing(path.getPath()));
        mPicasso = ((MainActivity) getActivity()).getPicasso();

        return view;
    }

    @Override
    public void onResume() {
        getComics();
        Scanner.getInstance().addUpdateHandler(mUpdateHandler);
        setLoading(Scanner.getInstance().isRunning());
        super.onResume();
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        setLoading(false);
        super.onPause();
    }

    private Menu mFilterMenu, mSortMenu;

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    SearchView mSearchView;

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.browser, menu);
        // hack to enable icons in overflow menu
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }

        MenuItem searchItem = menu.findItem(R.id.search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setOnQueryTextListener(this);

        MenuItem filterItem = menu.findItem(R.id.menu_browser_filter);
        mFilterMenu = filterItem.getSubMenu();

        // fixup menu formatting
        updateColors();

        super.onCreateOptionsMenu(menu, inflater);

        // memorize refresh item
        mRefreshItem = menu.findItem(R.id.menu_browser_refresh);
        // show=always is precondition to have an ActionView
        mRefreshItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        final int mRefreshItemId = mRefreshItem.getItemId();
        View mRefreshItemActionView = mRefreshItem.getActionView();

        final View.OnLongClickListener toolbarItemLongClicked = new View.OnLongClickListener() {
            int counter;

            @Override
            public boolean onLongClick(View view) {
                onRefresh(true);
                // return false so tooltip is shown
                return false;
            }
        };

        // attach longclicklistener after itemview is created
        final androidx.appcompat.widget.Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (mRefreshItemActionView == null)
            toolbar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                    if (view.getId() == toolbar.getId()) {
                        View itemView = view.findViewById(mRefreshItemId);
                        if (itemView != null) {
                            itemView.setOnLongClickListener(toolbarItemLongClicked);
                            view.removeOnLayoutChangeListener(this);
                        }
                    }
                }
            });
        else
            mRefreshItemActionView.setOnLongClickListener(toolbarItemLongClicked);

        // switch refresh icon
        setLoading(Scanner.getInstance().isRunning());

        // align header title of generated sub menu right
        SpannableString title = new SpannableString(getResources().getString(R.string.menu_browser_filter));
        title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.findItem(R.id.menu_browser_filter).getSubMenu().setHeaderTitle(title);
    }

    private static final HashMap<Integer, List<Integer>> sortIds = new HashMap<>();
    // sort menu entry ids (label, button ids with default first)
    static {
        sortIds.put(R.id.sort_name_label, Arrays.asList(new Integer[]{R.id.sort_name_asc, R.id.sort_name_desc}));
        sortIds.put(R.id.sort_access_label, Arrays.asList(new Integer[]{R.id.sort_access_desc, R.id.sort_access_asc}));
        sortIds.put(R.id.sort_size_label, Arrays.asList(new Integer[]{R.id.sort_size_asc, R.id.sort_size_desc}));
        sortIds.put(R.id.sort_creation_label, Arrays.asList(new Integer[]{R.id.sort_creation_desc, R.id.sort_creation_asc}));
        sortIds.put(R.id.sort_modified_label, Arrays.asList(new Integer[]{R.id.sort_modified_desc, R.id.sort_modified_asc}));
        sortIds.put(R.id.sort_pages_label, Arrays.asList(new Integer[]{R.id.sort_pages_asc, R.id.sort_pages_desc}));
        sortIds.put(R.id.sort_pages_read_label, Arrays.asList(new Integer[]{R.id.sort_pages_read_asc, R.id.sort_pages_read_desc}));
        sortIds.put(R.id.sort_pages_left_label, Arrays.asList(new Integer[]{R.id.sort_pages_left_asc, R.id.sort_pages_left_desc}));
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null)
            return false;

        switch (item.getItemId()) {
            case R.id.menu_browser_filter_all:
            case R.id.menu_browser_filter_read:
            case R.id.menu_browser_filter_unread:
            case R.id.menu_browser_filter_unfinished:
            case R.id.menu_browser_filter_reading:
                item.setChecked(true);
                // TODO: workaround
                //  should probably done with xml styles properly
                //  couldn't find out how though
                updateColors();
                mFilterRead = item.getItemId();
                filterContent();
                refreshAdapter();
                return true;
            case R.id.menu_browser_refresh:
                // if running, stop is requested
                if (Scanner.getInstance().isRunning()) {
                    setLoading(false);
                    Scanner.getInstance().stop();
                } else {
                    onRefresh();
                }
                return true;
            case R.id.sort:
                // apparently you need to implement custom layout submenus yourself
                View popupView = getLayoutInflater().inflate(R.layout.layout_librarybrowser_sort, null);
                // show header conditionally
                if (((MenuItemImpl) item).isActionButton()) {
                    popupView.findViewById(R.id.sort_header).setVisibility(View.GONE);
                    popupView.findViewById(R.id.sort_header_divider).setVisibility(View.GONE);
                }
                // creation time needs java.nio only avail on API26+
                if (!Utils.isOreoOrLater()) {
                    popupView.findViewById(R.id.sort_creation).setVisibility(View.GONE);
                    popupView.findViewById(R.id.sort_creation_divider).setVisibility(View.GONE);
                }

                @StyleRes int theme = ((MainActivity) getActivity()).getToolbar().getPopupTheme();
                @ColorInt int normal = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlNormal, theme);
                @ColorInt int active = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlActivated, theme);

                PopupWindow popupWindow = new PopupWindow(popupView, RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT, true);
                // weirdly needed on preAPI21 to dismiss on tap outside
                popupWindow.setBackgroundDrawable(new ColorDrawable(androidx.appcompat.R.attr.colorPrimary));

                // add click listener/apply styling according to selected sort mode
                for (int labelId : sortIds.keySet()) {
                    TextView tv = popupView.findViewById(labelId);
                    // attach clicklistener
                    tv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onSortItemSelected(labelId);
                            popupWindow.dismiss();
                        }
                    });

                    List<Integer> buttonIds = sortIds.get(labelId);
                    // is it selected?
                    boolean label_active = buttonIds.contains(mSort);
                    int label_tint = label_active ? active : normal;
                    // reset formatting
                    CharSequence text = tv.getText();
                    SpannableString s = new SpannableString(text);
                    // style away
                    s.setSpan(new ForegroundColorSpan(label_tint), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    s.setSpan(new StyleSpan(label_active ? Typeface.BOLD : Typeface.NORMAL), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(s);

                    // handle buttons
                    for (int buttonId : buttonIds) {
                        ImageView iv = popupView.findViewById(buttonId);
                        // attach clicklistener
                        iv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                onSortItemSelected(buttonId);
                                popupWindow.dismiss();
                            }
                        });

                        // switch button colors, for buttons only
                        int tint = buttonId == mSort ? active : normal;
                        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(tint));
                    }
                }

                float dp = getResources().getDisplayMetrics().density;
                // place popup window top right
                int xOffset, yOffset;
                // lil space on the right side
                xOffset = Math.round(4 * dp);
                // below status bar
                yOffset = Math.round(30 * dp);
                // API21+ place submenu popups below status+actionbar
                if (Utils.isLollipopOrLater()) {
                    yOffset = Math.round(17 * dp) + ((MainActivity) getActivity()).getToolbar().getHeight();
                    popupWindow.setElevation(16);
                }
                // show at location
                popupWindow.showAtLocation(getActivity().getWindow().getDecorView(), Gravity.TOP | Gravity.RIGHT, xOffset, yOffset);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onSortItemSelected(int id) {
        if (false && BuildConfig.DEBUG)
            Toast.makeText(
                    getActivity(),
                    "sort " + id,
                    Toast.LENGTH_SHORT).show();

        // filter label clicks
        if (sortIds.containsKey(id)) {
            int defaultId = sortIds.get(id).get(0);
            int alternateId = sortIds.get(id).get(1);
            mSort = mSort == defaultId ? alternateId : defaultId;
        } else
            mSort = id;

        // save sortMode
        for (Constants.SortMode mode: Constants.SortMode.values()) {
            if (mSort != mode.resId)
                continue;
            SharedPreferences.Editor editor = MainApplication.getPreferences().edit();
            editor.putInt(Constants.SETTINGS_LIBRARY_BROWSER_SORT, mode.id);
            editor.apply();
            break;
        }

        sortContent();
        refreshAdapter();
    }

    private void updateColors() {
        if (false) return;

        // fetch styling
        @StyleRes int theme = ((MainActivity) getActivity()).getToolbar().getPopupTheme();
        @ColorInt int normal = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlNormal, theme);
        @ColorInt int active = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlActivated, theme);

        for (int i = 0; mFilterMenu != null && i < mFilterMenu.size(); i++) {
            MenuItem item = mFilterMenu.getItem(i);
            if (item.isChecked())
                styleItem(item, active, true);
            else
                styleItem(item, normal, false);
        }

        for (int i = 0; mSortMenu != null && i < mSortMenu.size(); i++) {
            MenuItem item = mSortMenu.getItem(i);
            if (item.isChecked())
                styleItem(item, active, true);
            else
                styleItem(item, normal, false);
        }
    }

    // this is a workaround, couldn't find a way to style popup menu item text color/type
    // depending on selection state
    private void styleItem(MenuItem item, @ColorInt int colorInt, boolean bold) {
        if (item == null) return;

        // reset formatting
        CharSequence text = item.getTitle().toString();
        SpannableString s = new SpannableString(text);
        // style away
        s.setSpan(new ForegroundColorSpan(colorInt), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(bold ? Typeface.BOLD : Typeface.NORMAL), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        item.setTitle(s);
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mFilterSearch = s;
        filterContent();
        refreshAdapter();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    public void openComic(Comic comic) {
        if (!comic.getFile().exists()) {
            Toast.makeText(
                    getActivity(),
                    R.string.warning_missing_file,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, comic.getId());
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_LIBRARY);
        startActivity(intent);
        Utils.disablePendingTransition(getActivity());
    }

    private void refreshAdapter(){
        if (mComicListView == null)
            return;

        mComicListView.getAdapter().notifyDataSetChanged();
    }

    private void getComics() {
        //mCacheStamp = Long.valueOf(System.currentTimeMillis());
        mComics = Storage.getStorage(getActivity()).listComics(mPath);
        findRecents();
        limitRecents( calculateNumColumns() );
        filterContent();
        sortContent();
        refreshAdapter();
    }

    private void findRecents() {
        mRecentItems.clear();

        for (Comic c : mComics) {
            if (c.getUpdatedAt() > 0) {
                mRecentItems.add(c);
            }
        }

        if (mRecentItems.size() > 0) {
            Collections.sort(mRecentItems, new Comparator<Comic>() {
                @Override
                public int compare(Comic lhs, Comic rhs) {
                    return Long.compare(rhs.getUpdatedAt(), lhs.getUpdatedAt());
                }
            });
        }
    }

    private void limitRecents( final int numColumns ){
        if (mRecentItems == null || mRecentItems.isEmpty())
            return;

        // we default to 2 columns -1 unless min recent count is bigger
        int recentsNum = 2*numColumns-1;
        while ( Constants.MIN_RECENT_COUNT > recentsNum ) {
            recentsNum += numColumns;
        }

        // cut to two cols max
        if (mRecentItems.size() > recentsNum) {
            mRecentItems
                    .subList(recentsNum, mRecentItems.size())
                    .clear();
        }
    }

    private void filterContent() {
        mAllItems.clear();

        for (Comic c : mComics) {
            if (mFilterSearch.length() > 0 && !Utils.containsIgnoreCase(c.getFile().getName(),mFilterSearch))
                continue;
            if (mFilterRead != R.id.menu_browser_filter_all) {
                if (mFilterRead == R.id.menu_browser_filter_read && c.getCurrentPage() != c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unread && c.getCurrentPage() != 0)
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unfinished && c.getCurrentPage() == c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_reading &&
                        (c.getCurrentPage() == 0 || c.getCurrentPage() == c.getTotalPages()))
                    continue;
            }
            mAllItems.add(c);
        }
    }

    private void sortContent() {
        if (mAllItems == null || mAllItems.isEmpty())
            return;

        Comparator comparator;
        switch (mSort) {
            case R.id.sort_name_desc:
                comparator = new IgnoreCaseComparator.Reverse() {
                    @Override
                    public String stringValue(Object o) {
                        return ((Comic) o).getFile().getName();
                    }
                };
                break;
            case R.id.sort_size_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aSize = getSize(a.getFile());
                        long bSize = getSize(b.getFile());
                        long diff = aSize - bSize;
                        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                    }

                    private long getSize(File f) {
                        if (f == null)
                            return 0;
                        else if (f.isDirectory())
                            return Utils.getFolderSize(f, false);

                        return f.length();
                    }
                };
                break;
            case R.id.sort_size_desc:
                comparator = new Comparator<Comic>() {
                    public int compare(Comic a, Comic b) {
                        long aSize = getSize(b.getFile());
                        long bSize = getSize(a.getFile());
                        long diff = aSize - bSize;
                        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                    }

                    private long getSize(File f) {
                        if (f == null)
                            return 0;
                        else if (f.isDirectory())
                            return Utils.getFolderSize(f, false);

                        return f.length();
                    }
                };
                break;
            case R.id.sort_creation_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = creationTime(a.getFile());
                        long bTime = creationTime(b.getFile());
                        return Long.compare(aTime, bTime);
                    }

                    private long creationTime(File f) {
                        try {
                            FileTime creationTime = (FileTime) Files.getAttribute(f.toPath(), "creationTime");
                            return creationTime.toMillis();
                        } catch (IOException ex) {
                            return 0;
                        }
                    }
                };
                break;
            case R.id.sort_creation_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = creationTime(b.getFile());
                        long bTime = creationTime(a.getFile());
                        return Long.compare(aTime, bTime);
                    }

                    private long creationTime(File f) {
                        try {
                            FileTime creationTime = (FileTime) Files.getAttribute(f.toPath(), "creationTime");
                            return creationTime.toMillis();
                        } catch (IOException ex) {
                            return 0;
                        }
                    }
                };
                break;
            case R.id.sort_modified_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = a.getFile().lastModified();
                        long bTime = b.getFile().lastModified();
                        return Long.compare(aTime, bTime);
                    }
                };
                break;
            case R.id.sort_modified_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = b.getFile().lastModified();
                        long bTime = a.getFile().lastModified();
                        return Long.compare(aTime, bTime);
                    }
                };
                break;
            case R.id.sort_pages_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return a.getTotalPages() - b.getTotalPages();
                    }
                };
                break;
            case R.id.sort_pages_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return b.getTotalPages() - a.getTotalPages();
                    }
                };
                break;
            case R.id.sort_pages_read_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return a.getCurrentPage() - b.getCurrentPage();
                    }
                };
                break;
            case R.id.sort_pages_read_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return b.getCurrentPage() - a.getCurrentPage();
                    }
                };
                break;
            case R.id.sort_pages_left_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return (a.getTotalPages() - a.getCurrentPage()) -
                                (b.getTotalPages() - b.getCurrentPage());
                    }
                };
                break;
            case R.id.sort_pages_left_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return (b.getTotalPages() - b.getCurrentPage()) -
                                (a.getTotalPages() - a.getCurrentPage());
                    }
                };
                break;
            case R.id.sort_access_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return Long.compare(a.getUpdatedAt(), b.getUpdatedAt());
                    }
                };
                break;
            case R.id.sort_access_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return Long.compare(b.getUpdatedAt(), a.getUpdatedAt());
                    }
                };
                break;
            default:
                comparator = new IgnoreCaseComparator() {
                    @Override
                    public String stringValue(Object o) {
                        return ((Comic) o).getFile().getName();
                    }
                };
                break;
        }

        Collections.sort(mAllItems, comparator);
    }

    private Comic getComicAtPosition(int position) {
        Comic comic;
        if (hasRecent()) {
            if (position > 0 && position < mRecentItems.size() + 1)
                comic = mRecentItems.get(position - 1);
            else
                comic = mAllItems.get(position - mRecentItems.size() - NUM_HEADERS);
        } else {
            comic = mAllItems.get(position);
        }
        return comic;
    }

    private int getItemViewTypeAtPosition(int position) {
        if (hasRecent()) {
            if (position == 0)
                return ITEM_VIEW_TYPE_HEADER_RECENT;
            else if (position == mRecentItems.size() + 1)
                return ITEM_VIEW_TYPE_HEADER_ALL;
        }
        return ITEM_VIEW_TYPE_COMIC;
    }

    private boolean hasRecent() {
        return mFilterSearch.length() == 0
                && mFilterRead == R.id.menu_browser_filter_all
                && mSort != R.id.sort_access_desc
                && mRecentItems.size() > 0;
    }

    private int calculateNumColumns() {
        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_comic_column_width);

        int cols = Math.round((float) deviceWidth / columnWidth);
        return cols > 0 ? cols : 1;
    }

    private GridLayoutManager.SpanSizeLookup createSpanSizeLookup() {
        final int numColumns = calculateNumColumns();

        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (getItemViewTypeAtPosition(position) == ITEM_VIEW_TYPE_COMIC)
                    return 1;
                return numColumns;
            }
        };
    }

    @Override
    public void onRefresh() {
        onRefresh(false);
    }

    private void onRefresh(boolean refreshAll) {
        setLoading(true);
        String msg = getResources().getString( refreshAll ? R.string.reload_msg_slow : R.string.reload_msg_fast );
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        Scanner.getInstance().scanLibrary(new File(mPath), refreshAll);
    }

    public void refreshLibraryDelayed() {
    }

    public void refreshLibraryFinished() {
        getComics();
        setLoading(false);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            mRefreshLayout.setRefreshing(true);
            if (mRefreshItem != null)
                mRefreshItem.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh_stop_24));
        } else {
            if (mRefreshItem != null)
                mRefreshItem.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh_24));
            mRefreshLayout.setRefreshing(false);
        }
    }

    private final class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int mSpanCount;
        private int mSpacing;

        public GridSpacingItemDecoration(int spanCount, int spacing) {
            mSpanCount = spanCount;
            mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);

            if (hasRecent()) {
                // those are headers
                if (position == 0 || position == mRecentItems.size() + 1)
                    return;

                if (position > 0 && position < mRecentItems.size() + 1) {
                    position -= 1;
                } else {
                    position -= (NUM_HEADERS + mRecentItems.size());
                }
            }

            int column = position % mSpanCount;

            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        }
    }


    private final class ComicGridAdapter extends RecyclerView.Adapter {

        public ComicGridAdapter() {
            super();
            // implemented getItemId() below
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            if (hasRecent()) {
                return mAllItems.size() + mRecentItems.size() + NUM_HEADERS;
            }
            return mAllItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return getItemViewTypeAtPosition(position);
        }

        @Override
        public long getItemId(int position) {
            int type = getItemViewTypeAtPosition(position);
            if (type == ITEM_VIEW_TYPE_COMIC) {
                Comic comic = getComicAtPosition(position);
                return comic.getId();
            }
            return type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            Context ctx = viewGroup.getContext();

            if (i == ITEM_VIEW_TYPE_HEADER_RECENT) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_recent);

                int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                lp.setMargins(0, spacing, 0, 0);

                return new HeaderViewHolder(view);
            } else if (i == ITEM_VIEW_TYPE_HEADER_ALL) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_all);

                return new HeaderViewHolder(view);
            }

            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.card_comic, viewGroup, false);
            return new ComicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == ITEM_VIEW_TYPE_COMIC) {
                Comic comic = getComicAtPosition(i);
                ComicViewHolder holder = (ComicViewHolder) viewHolder;
                holder.setupComic(comic);
            }
        }

    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(View itemView) {
            super(itemView);
        }

        public void setTitle(int titleRes) {
            ((TextView) itemView).setText(titleRes);
        }
    }

    private class ComicViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener,
            View.OnCreateContextMenuListener,
            MenuItem.OnMenuItemClickListener {
        private ImageView mComicImageView;
        private TextView mTitleTextView;
        private TextView mPagesTextView;
        private Comic mComic = null;

        public ComicViewHolder(View itemView) {
            super(itemView);
            mComicImageView = (ImageView) itemView.findViewById(R.id.comicImageView);
            mTitleTextView = (TextView) itemView.findViewById(R.id.comicTitleTextView);
            mPagesTextView = (TextView) itemView.findViewById(R.id.comicPagerTextView);

            itemView.setClickable(true);
            itemView.setOnCreateContextMenuListener(this);
            itemView.setOnClickListener(this);
        }

        public void setupComic(Comic comic) {
            mComic = comic;
            Uri uri = LocalCoverHandler.getComicCoverUri(comic);
            //Long lastCacheStamp = mCache.get(uri);
            //if (lastCacheStamp != null && !lastCacheStamp.equals(mCacheStamp))
            //   mPicasso.invalidate(uri);
            mPicasso.load(uri).into(mComicImageView);
           //mCache.put(uri, mCacheStamp);

            // reload comic (in case it was updated by the cover loading)
            comic = Storage.getStorage(getContext()).getComic(comic.getId());
            mTitleTextView.setText(comic.getFile().getName());
            String totalPages = comic.getTotalPages() < 1 ? "?" : Integer.toString(comic.getTotalPages());
            mPagesTextView.setText(Integer.toString(comic.getCurrentPage()) + '/' + totalPages);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu,
                                        View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            getActivity().getMenuInflater().inflate(R.menu.browser_context, menu);
            menu.findItem(R.id.reset).setOnMenuItemClickListener(this);
        }

        @Override
        public boolean onMenuItemClick(@NonNull MenuItem item) {
            if (mComic == null)
                return false;

            // just one menu item reset just now
            Storage.getStorage(getContext()).resetBook(mComic.getId());
            Utils.deleteCoverCacheFile(mComic);
            // complete reload (recents, sorting etc.)
            getComics();
            return true;
        }

        @Override
        public void onClick(View v) {
            //int i = getAdapterPosition();
            //Comic comic = getComicAtPosition(i);
            if (mComic == null)
                return;

            openComic(mComic);
        }
    }
}
