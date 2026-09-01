package com.nkanaev.comics.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.fragment.*;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.view.NavBGImageView;
import com.squareup.picasso.Picasso;

import java.io.File;

public class MainActivity extends AppCompatActivity
        implements FragmentManager.OnBackStackChangedListener {
    private final static String STATE_CURRENT_MENU_ITEM = "STATE_CURRENT_MENU_ITEM";
    private final static String STATE_INITIAL_SCAN_RAN_ALREADY = "INITIAL_SCAN_FINISHED";
    public static String PACKAGE_NAME;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private int mCurrentNavItem;
    private Picasso mPicasso;

    private static boolean mInitialLibraryScanRanAlready = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.setContentView(R.layout.layout_main);
        super.onCreate(savedInstanceState);

        PACKAGE_NAME = getApplicationContext().getPackageName();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        if (Utils.isLollipopOrLater()) {
            toolbar.setElevation(8);
        }

        // API35 forced edge-to-edge fix: apply system bar paddings
        if (Utils.isVanillaIceCreamOrLater()) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    android.graphics.Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
                    v.setPadding(systemBarsInsets.left,systemBarsInsets.top,systemBarsInsets.right, systemBarsInsets.bottom);
                    return insets;
                }
            });
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.action_bar_title_layout);
            actionBar.setTitle("");
        }

        mPicasso = new Picasso.Builder(this)
                .addRequestHandler(new LocalCoverHandler(this))
                .build();

        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);
        setupNavigationView(navigationView);
        // API35 edge-to-edge fix: remove bottom padding in navmenu
        if (Utils.isVanillaIceCreamOrLater()) {
            ViewCompat.setOnApplyWindowInsetsListener(
                    navigationView,
                    new OnApplyWindowInsetsListener() {
                        @NonNull
                        @Override
                        public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                            // apply to frame to position scrollbar properly
                            view.setPadding(0,0,0,0);
                            return WindowInsetsCompat.CONSUMED;
                        }
                    }
            );
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        final NavBGImageView navBG = mDrawerLayout.findViewById(R.id.drawer_bg_image);
        mDrawerToggle = new ActionBarDrawerToggle(
                this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close){
            @Override
            public void onDrawerClosed(View drawerView) {
                if (navBG!=null) navBG.reset();
                super.onDrawerClosed(drawerView);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        // prevent rescan when view is rotated or activity just restarted
        if (savedInstanceState!=null)
            mInitialLibraryScanRanAlready = savedInstanceState.getBoolean(STATE_INITIAL_SCAN_RAN_ALREADY);
        if (!mInitialLibraryScanRanAlready) {
            Utils.cleanCacheDir();
            Scanner.getInstance().scanLibrary();
            mInitialLibraryScanRanAlready = true;
        }

        if (savedInstanceState == null) {
            setFragment(new LibraryFragment());
            mCurrentNavItem = R.id.drawer_menu_library;
            navigationView.getMenu().findItem(mCurrentNavItem).setChecked(true);
            restoreLastNavigationState();
        } else {
            onBackStackChanged();  // force-call method to ensure indicator is shown properly
            mCurrentNavItem = savedInstanceState.getInt(STATE_CURRENT_MENU_ITEM);
            navigationView.getMenu().findItem(mCurrentNavItem).setChecked(true);
        }
    }

    public Toolbar getToolbar(){
        return findViewById(R.id.toolbar);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        TextView titleView = findViewById(R.id.action_bar_title);
        if (titleView!=null)
            titleView.setText(title);
        else
            getSupportActionBar().setTitle(title);
    }

    public void setSubTitle(CharSequence title) {
        TextView subtitle = (TextView) findViewById(R.id.action_bar_subtitle);
        if (subtitle==null)
            return;

        if (title==null||title.toString().isEmpty()) {
            subtitle.setVisibility(View.GONE);
            title="";
        } else {
            subtitle.setVisibility(View.VISIBLE);
        }
        subtitle.setText(title);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_MENU_ITEM, mCurrentNavItem);
        outState.putBoolean(STATE_INITIAL_SCAN_RAN_ALREADY, mInitialLibraryScanRanAlready);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    public Picasso getPicasso() {
        return mPicasso;
    }

    private void setFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() >= 1) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        fragmentManager
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
    }

    public void pushFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();
    }

    private boolean popFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return true;
        }
        return false;
    }

    private void setupNavigationView(NavigationView view) {
        view.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                if (mCurrentNavItem == menuItem.getItemId()) {
                    mDrawerLayout.closeDrawers();
                    return true;
                }

                final int selectedItem = menuItem.getItemId();
                if (selectedItem == R.id.drawer_menu_library)
                    setFragment(new LibraryFragment());
                else if (selectedItem == R.id.drawer_menu_browser)
                    setFragment(new BrowserFragment());
                else if (selectedItem == R.id.drawer_menu_about)
                    setFragment(new AboutFragment());

                mCurrentNavItem = menuItem.getItemId();
                menuItem.setChecked(true);
                mDrawerLayout.closeDrawers();
                return true;
            }
        });
    }

    @Override
    public void onBackStackChanged() {
        mDrawerToggle.setDrawerIndicatorEnabled(getSupportFragmentManager().getBackStackEntryCount() == 0);
    }

    @Override
    public void onBackPressed() {
        if (!popFragment()) {
            finish();
        }
        // activating it will disable back button in library browser
        // so yeah, even if IntelliJ suggests it, keep the super call disabled
        //super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (!popFragment()) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
                mDrawerLayout.closeDrawers();
            else
                mDrawerLayout.openDrawer(GravityCompat.START);
        }
        return super.onSupportNavigateUp();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNavigationState();
    }

    private void saveNavigationState() {
        Log.d("BrowserFragment", "saveNavigationState");
        SharedPreferences.Editor editor = MainApplication.getPreferences().edit();
        FragmentManager fm = getSupportFragmentManager();

        Fragment currentFragment = fm.findFragmentById(R.id.content_frame);
        if (currentFragment instanceof LibraryBrowserFragment) {
            editor.putInt(Constants.SETTINGS_LAST_SCREEN, Constants.SCREEN_LIBRARY_BROWSER);
            editor.putString(Constants.SETTINGS_LAST_BROWSE_PATH, ((LibraryBrowserFragment) currentFragment).getPath());
        } else if (currentFragment instanceof BrowserFragment) {
            editor.putInt(Constants.SETTINGS_LAST_SCREEN, Constants.SCREEN_BROWSER);
            editor.putString(Constants.SETTINGS_LAST_BROWSE_PATH, ((BrowserFragment) currentFragment).getCurrentDir().getAbsolutePath());
        } else {
            editor.putInt(Constants.SETTINGS_LAST_SCREEN, Constants.SCREEN_LIBRARY);
            editor.putString(Constants.SETTINGS_LAST_BROWSE_PATH, "");
        }
        editor.apply();
    }

    private void restoreLastNavigationState() {
        SharedPreferences prefs = MainApplication.getPreferences();

        int lastScreen = prefs.getInt(Constants.SETTINGS_LAST_SCREEN, Constants.SCREEN_LIBRARY);
        String lastPath = prefs.getString(Constants.SETTINGS_LAST_BROWSE_PATH, "");

        switch (lastScreen) {
            case Constants.SCREEN_LIBRARY_BROWSER:
                restoreLibraryState(lastPath);
                break;
            case Constants.SCREEN_BROWSER:
                restoreBrowserState(lastPath);
                break;
            case Constants.SCREEN_READER:
                restoreReaderState(prefs, lastPath);
                break;
            default:
                break;
        }
    }

    private void restoreLibraryState(String lastPath) {
        if (!lastPath.isEmpty()) {
            File dir = new File(lastPath);
            if (dir.exists() && dir.isDirectory()) {
                pushFragment(LibraryBrowserFragment.create(lastPath));
            }
        }
    }

    private void restoreBrowserState(String lastPath) {
        if (!lastPath.isEmpty()) {
            File browseDir = new File(lastPath);
            if (browseDir.exists() && browseDir.isDirectory()) {
                BrowserFragment fragment = new BrowserFragment();
                Bundle args = new Bundle();
                args.putSerializable(Constants.SETTINGS_LAST_BROWSE_PATH, browseDir);
                fragment.setArguments(args);
                setFragment(fragment);
                mCurrentNavItem = R.id.drawer_menu_browser;
                ((NavigationView) findViewById(R.id.navigation_view)).getMenu().findItem(mCurrentNavItem).setChecked(true);
            }
        }
    }

    private void restoreReaderState(SharedPreferences prefs, String lastPath) {
        String comicPath = prefs.getString(Constants.SETTINGS_LAST_COMIC_PATH, "");
        if (!comicPath.isEmpty()) {
            File comicFile = new File(comicPath);
            if (comicFile.exists()) {
                restoreBrowserState(lastPath);
                Intent intent = new Intent(this, ReaderActivity.class);
                intent.putExtra(ReaderFragment.PARAM_HANDLER, comicFile);
                intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_BROWSER);
                startActivity(intent);
            }
        } else {
            restoreLibraryState(lastPath);
            Intent intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderFragment.PARAM_HANDLER, prefs.getInt(Constants.SETTINGS_LAST_COMIC_ID, -1));
            intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_LIBRARY);
            startActivity(intent);
        }
    }
}
