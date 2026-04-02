package com.nkanaev.comics.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.BuildConfig;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.managers.DirectoryListingManager;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.DirectorySelectDialog;
import com.nkanaev.comics.view.PreCachingGridLayoutManager;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


public class LibraryFragment extends Fragment
        implements
        DirectorySelectDialog.OnDirectorySelectListener,
        AdapterView.OnItemClickListener,
        SwipeRefreshLayout.OnRefreshListener,
        UpdateHandlerTarget {
    private final static String BUNDLE_DIRECTORY_DIALOG_SHOWN = "BUNDLE_DIRECTORY_DIALOG_SHOWN";

    private DirectoryListingManager mComicsListManager;
    private DirectorySelectDialog mDirectorySelectDialog;
    private SwipeRefreshLayout mRefreshLayout;
    private View mEmptyView;
    private RecyclerView mFolderListView;
    private Picasso mPicasso;
    private boolean mIsRefreshPlanned = false;
    private Handler mUpdateHandler = new UpdateHandler(this);
    private MenuItem mRefreshItem;
    private int mSort = R.id.sort_name_asc;

    public LibraryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If you have access to the external storage, do whatever you need
        // https://developer.android.com/training/data-storage/manage-all-files#operations-allowed-manage-external-storage
        if (Utils.isROrLater()) {
            if (!Environment.isExternalStorageManager()) {
                // If you don't have access, launch a new activity to show the user the system's dialog
                // to allow access to the external storage
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", MainActivity.PACKAGE_NAME, null);
                intent.setData(uri);
                startActivity(intent);
            }
        } else {
            String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{permission},
                        1);
            }
        }

        /*
        // ways to get sdcard mount points
        // this is API R 30, N 24 only
        final StorageManager storageManager = (StorageManager) getContext()
                .getSystemService(MainActivity.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();
        for (StorageVolume v : volumes) {
            Log.i("Volumes", v.getDirectory().toString());
        }
        // this is Q 29
        Set<String> names = MediaStore.getExternalVolumeNames(getContext());
        for (String n : names) {
            Log.i("MS-Names", n);
        }
        */
        File[] externalStorageFiles = Utils.listExternalStorageDirs();
        for (File f : externalStorageFiles) {
            Log.d(getClass().getCanonicalName(), "External Storage -> " + f.toString());
        }

        // restore saved sorting
        int savedSortMode = MainApplication.getPreferences().getInt(
                Constants.SETTINGS_LIBRARY_SORT,
                Constants.SortMode.NAME_ASC.id);
        mSort = R.id.sort_name_asc;
        for (Constants.SortMode mode: Constants.SortMode.values()) {
            if (savedSortMode != mode.id)
                continue;
            mSort = mode.resId;
            break;
        }

        mDirectorySelectDialog = new DirectorySelectDialog(getActivity(), externalStorageFiles);
        mDirectorySelectDialog.setCurrentDirectory(Environment.getExternalStorageDirectory());
        mDirectorySelectDialog.setOnDirectorySelectListener(this);

        getComics();

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Scanner.getInstance().addUpdateHandler(mUpdateHandler);
        if (Scanner.getInstance().isRunning()) {
            setLoading(true);
        }
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        setLoading(false);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_library, container, false);

        mPicasso = ((MainActivity) getActivity()).getPicasso();

        mRefreshLayout = view.findViewById(R.id.fragmentLibraryRefreshLayout);

        mRefreshLayout.setColorSchemeResources(R.color.refreshProgress);
        mRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refreshProgressBackground);

        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        mFolderListView = view.findViewById(R.id.groupGridView);
        mFolderListView.setAdapter(new GroupGridAdapter());

        final int numColumns = calculateNumColumns();
        PreCachingGridLayoutManager layoutManager = new PreCachingGridLayoutManager(getActivity(), numColumns);
        int height = Utils.getDeviceHeightPixels();
        layoutManager.setExtraLayoutSpace(height*2);
        mFolderListView.setLayoutManager(layoutManager);

        int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
        mFolderListView.addItemDecoration(new GridSpacingItemDecoration(numColumns, spacing));

        // some performance optimizations
        mFolderListView.setHasFixedSize(true);
        mFolderListView.setItemViewCacheSize(Math.max(4 * numColumns,40));
        //mFolderListView.getRecycledViewPool().setMaxRecycledViews();

        mEmptyView = view.findViewById(R.id.library_empty);

        showEmptyMessageIfNeeded();
        getActivity().setTitle(R.string.menu_library);
        String folder = getLibraryDir();
        ((MainActivity) getActivity()).setSubTitle(Utils.appendSlashIfMissing(folder));

        return view;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.library, menu);
        super.onCreateOptionsMenu(menu, inflater);

        // hack to enable icons in overflow menu
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }

        // memorize refresh item
        mRefreshItem = menu.findItem(R.id.menuLibraryRefresh);
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
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // disable refresh if no folder selected so far
        String dir = getLibraryDir();
        menu.findItem(R.id.menuLibraryRefresh).setVisible(!getLibraryDir().isEmpty());
        menu.findItem(R.id.menuLibrarySort).setVisible(!getLibraryDir().isEmpty());
        // place select-folder item in overflow if already selected
        if (!getLibraryDir().isEmpty())
            menu.findItem(R.id.menuLibrarySetDir).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        int mode = AppCompatDelegate.getDefaultNightMode();
        Drawable icon;
        int item;
        switch(mode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                icon = ContextCompat.getDrawable(getContext(),R.drawable.ui_light_mode_24);
                item = R.id.menuLibrarySetThemeDay;
                break;
            case AppCompatDelegate.MODE_NIGHT_YES:
                icon = ContextCompat.getDrawable(getContext(),R.drawable.ui_dark_mode_24);
                item = R.id.menuLibrarySetThemeNight;
                break;
            default:
                icon = ContextCompat.getDrawable(getContext(),R.drawable.ui_system_mode_24);
                item = R.id.menuLibrarySetThemeAuto;
                break;
        }
        menu.findItem(R.id.menuLibrarySetTheme).setIcon(icon);
        menu.findItem(item).setChecked(true);
    }

    private static final HashMap<Integer, List<Integer>> sortIds = new HashMap<>();
    // sort menu entry ids (label, button ids with default first)
    static {
        sortIds.put(R.id.sort_name_label, Arrays.asList(new Integer[]{R.id.sort_name_asc, R.id.sort_name_desc}));
        sortIds.put(R.id.sort_access_label, Arrays.asList(new Integer[]{R.id.sort_access_desc, R.id.sort_access_asc}));
        sortIds.put(R.id.sort_size_label, Arrays.asList(new Integer[]{R.id.sort_size_asc, R.id.sort_size_desc}));
        sortIds.put(R.id.sort_creation_label, Arrays.asList(new Integer[]{R.id.sort_creation_desc, R.id.sort_creation_asc}));
        sortIds.put(R.id.sort_modified_label, Arrays.asList(new Integer[]{R.id.sort_modified_desc, R.id.sort_modified_asc}));
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menuLibrarySetDir:
                if (Scanner.getInstance().isRunning()) {
                    Scanner.getInstance().stop();
                }

                mDirectorySelectDialog.show();
                return true;
            case R.id.menuLibraryRefresh:
                // if running, stop is requested
                if (Scanner.getInstance().isRunning()) {
                    setLoading(false);
                    Scanner.getInstance().stop();
                    return true;
                }

                onRefresh();
                return true;
            case R.id.menuLibrarySetThemeAuto:
            case R.id.menuLibrarySetThemeDay:
            case R.id.menuLibrarySetThemeNight:
                final int mode;
                if (item.getItemId() == R.id.menuLibrarySetThemeNight)
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                else if (item.getItemId() == R.id.menuLibrarySetThemeDay)
                    mode = AppCompatDelegate.MODE_NIGHT_NO;
                else
                    mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                // save to settings
                SharedPreferences preferences = MainApplication.getPreferences();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt(Constants.SETTINGS_THEME, mode);
                editor.apply();
                // apply
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // crashes on Android9 if executed immediately
                        AppCompatDelegate.setDefaultNightMode(mode);
                        // activity.recreate() does not properly recreate views
                        Activity a = getActivity();
                        a.finish();
                        a.startActivity(a.getIntent());
                    }
                },500);
                return true;
            case R.id.menuLibrarySort:
                // apparently you need to implement custom layout submenus yourself
                View popupView = getLayoutInflater().inflate(R.layout.layout_library_sort, null);
                // show header conditionally
                if (((MenuItemImpl)item).isActionButton()) {
                    popupView.findViewById(R.id.sort_header).setVisibility(View.GONE);
                    popupView.findViewById(R.id.sort_header_divider).setVisibility(View.GONE);
                }
                // creation time needs java.nio only avail on API26+
                // disabled for now, folders give the same stamp for creation/lastmod why izzat?
                if (true || !Utils.isOreoOrLater()) {
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
                xOffset=Math.round(4*dp);
                // below status bar
                yOffset=Math.round(30*dp);
                // API21+ place submenu popups below status+actionbar
                if (Utils.isLollipopOrLater()) {
                    yOffset = Math.round(17 * dp) + ((MainActivity) getActivity()).getToolbar().getHeight();
                    popupWindow.setElevation(16);
                }
                // show at location
                popupWindow.showAtLocation(getActivity().getWindow().getDecorView(),Gravity.TOP|Gravity.RIGHT,xOffset,yOffset);
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
            editor.putInt(Constants.SETTINGS_LIBRARY_SORT, mode.id);
            editor.apply();
            break;
        }

        sortContent();
        mFolderListView.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_DIRECTORY_DIALOG_SHOWN,
                (mDirectorySelectDialog != null) && mDirectorySelectDialog.isShowing());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDirectorySelect(File file) {
        SharedPreferences preferences = MainApplication.getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.SETTINGS_LIBRARY_DIR, file.getAbsolutePath());
        editor.apply();

        // enable refresh button
        ActivityCompat.invalidateOptionsMenu(getActivity());

        ((MainActivity) getActivity()).setSubTitle(Utils.appendSlashIfMissing(file.getAbsolutePath()));

        Scanner.getInstance().scanLibrary();
        setLoading(true);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String path = mComicsListManager.getDirectoryAtIndex(position);
        LibraryBrowserFragment fragment = LibraryBrowserFragment.create(path);
        ((MainActivity) getActivity()).pushFragment(fragment);
    }

    @Override
    public void onRefresh() {
        onRefresh(false);
    }

    private void onRefresh(boolean refreshAll) {
        Scanner.getInstance().scanLibrary(null, refreshAll);
        String msg = getResources().getString( refreshAll ? R.string.reload_msg_slow : R.string.reload_msg_fast );
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        setLoading(true);
    }

    private void getComics() {
        List<Comic> comics = Storage.getStorage(getActivity()).listDirectoryComics();
        mComicsListManager = new DirectoryListingManager(comics, getLibraryDir());
        sortContent();
        if (mFolderListView!=null && mFolderListView.getAdapter()!=null)
            mFolderListView.getAdapter().notifyDataSetChanged();
    }

    private void sortContent(){
        if (mComicsListManager== null || mComicsListManager.getCount() < 1)
            return;

        Comparator comparator;
        switch (mSort){
            case R.id.sort_name_desc:
                comparator = new DirectoryListingManager.NameComparator.Reverse();
                break;
            case R.id.sort_size_asc:
                comparator = new DirectoryListingManager.SizeComparator();
                break;
            case R.id.sort_size_desc:
                comparator = new DirectoryListingManager.SizeComparator.Reverse();
                break;
            case R.id.sort_modified_asc:
                comparator = new DirectoryListingManager.ModifiedComparator();
                break;
            case R.id.sort_modified_desc:
                comparator = new DirectoryListingManager.ModifiedComparator.Reverse();
                break;
            case R.id.sort_access_asc:
                comparator = new DirectoryListingManager.AccessedComparator();
                break;
            case R.id.sort_access_desc:
                comparator = new DirectoryListingManager.AccessedComparator.Reverse();
                break;
            default:
                comparator = new DirectoryListingManager.NameComparator();
                break;
        }

        mComicsListManager.sort(comparator);
    }

    private void refreshLibrary( boolean finished ) {
        if (mFolderListView == null) return;

        if (!mIsRefreshPlanned || finished) {
            final Runnable updateRunnable = new Runnable() {
                @Override
                public void run() {
                    getComics();
                    mFolderListView.getAdapter().notifyDataSetChanged();
                    mIsRefreshPlanned = false;
                    showEmptyMessageIfNeeded();
                }
            };
            mIsRefreshPlanned = true;
            mFolderListView.postDelayed(updateRunnable, 100);
        }
    }

    public void refreshLibraryDelayed(){
        refreshLibrary(false);
    }

    public void refreshLibraryFinished() {
        refreshLibrary(true);
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
            showEmptyMessageIfNeeded();
        }
    }

    private String getLibraryDir() {
        return MainApplication.getPreferences()
                .getString(Constants.SETTINGS_LIBRARY_DIR, "");
    }

    private void showEmptyMessageIfNeeded() {
        boolean show = mComicsListManager.getCount() < 1;
        mEmptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        mRefreshLayout.setEnabled(!show);
    }

    public static class UpdateHandler extends Handler {
        private WeakReference<UpdateHandlerTarget> mOwner;

        public UpdateHandler(UpdateHandlerTarget fragment) {
            mOwner = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            UpdateHandlerTarget fragment = mOwner.get();
            if (fragment == null) {
                return;
            }

            if (msg.what == Constants.MESSAGE_MEDIA_UPDATED) {
                fragment.refreshLibraryDelayed();
            } else if (msg.what == Constants.MESSAGE_MEDIA_UPDATE_FINISHED) {
                fragment.refreshLibraryFinished();
            }
        }
    }

    private int calculateNumColumns() {
        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_group_column_width);

        float value = (float) deviceWidth / columnWidth;

        return Math.round(value);
    }

    private final class GroupGridAdapter extends RecyclerView.Adapter {

        public GroupGridAdapter() {
            super();
            // implemented getItemId() below
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            Context ctx = viewGroup.getContext();

            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.card_group, viewGroup, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            GroupViewHolder holder = (GroupViewHolder) viewHolder;
            holder.setup(position);
        }

        @Override
        public int getItemCount() {
            return mComicsListManager.getCount();
        }

        @Override
        public long getItemId(int position) {
            Comic comic = mComicsListManager.getComicAtIndex(position);
            return (long)comic.getId();
        }
    }

    private class GroupViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView groupImageView;
        TextView tv;

        public GroupViewHolder(View itemView) {
            super(itemView);
            groupImageView = (ImageView) itemView.findViewById(R.id.card_group_imageview);
            tv = (TextView) itemView.findViewById(R.id.comic_group_folder);

            itemView.setClickable(true);
            itemView.setOnClickListener(this);
        }

        public void setup(int position) {
            Comic comic = mComicsListManager.getComicAtIndex(position);
            Uri uri = LocalCoverHandler.getComicCoverUri(comic);
            mPicasso.load(uri).into(groupImageView);

            String dirDisplay = mComicsListManager.getDirectoryDisplayAtIndex(position);
            tv.setText(dirDisplay);
        }

        @Override
        public void onClick(View v) {
            int position = getAbsoluteAdapterPosition();
            try {
                String path = mComicsListManager.getDirectoryAtIndex(position);
                LibraryBrowserFragment fragment = LibraryBrowserFragment.create(path);
                ((MainActivity) getActivity()).pushFragment(fragment);
            } catch (Exception e) {
                // usually during refreshing when getAbsoluteAdapterPosition() returns -1 (NO_POSITION)
                Log.e("library","open library browser", e);
            }
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
            int column = position % mSpanCount;

            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        }
    }
}
