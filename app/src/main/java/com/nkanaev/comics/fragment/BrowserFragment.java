package com.nkanaev.comics.fragment;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;


public class BrowserFragment extends Fragment
        implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, SwipeRefreshLayout.OnRefreshListener {
    private final static String STATE_CURRENT_DIR = "stateCurrentDir";

    private ListView mListView;
    private File mCurrentDir;
    private final File mRootDir = new File("/");
    private File[] mSubdirs = new File[]{};
    private SwipeRefreshLayout mRefreshLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentDir = (File) savedInstanceState.getSerializable(STATE_CURRENT_DIR);
        } else if (getArguments() != null && getArguments().containsKey(Constants.SETTINGS_LAST_BROWSE_PATH)) {
            mCurrentDir = (File) getArguments().getSerializable(Constants.SETTINGS_LAST_BROWSE_PATH);
        } else {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        getActivity().setTitle(R.string.menu_browser);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_browser, container, false);

        /*
        ViewGroup toolbar = (ViewGroup) getActivity().findViewById(R.id.toolbar);
        ViewGroup breadcrumbLayout = (ViewGroup) inflater.inflate(R.layout.breadcrumb, toolbar, false);
        toolbar.addView(breadcrumbLayout);
        mDirTextView = (TextView) breadcrumbLayout.findViewById(R.id.dir_textview);
        */
        setCurrentDirectory(mCurrentDir);

        mListView = (ListView) view.findViewById(R.id.listview_browser);
        mListView.setAdapter(new DirectoryAdapter());
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.SwipeRefreshLayout);
        if (mRefreshLayout!=null) {
            mRefreshLayout.setColorSchemeResources(R.color.primary);
            mRefreshLayout.setOnRefreshListener(this);
            mRefreshLayout.setEnabled(true);
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_CURRENT_DIR, mCurrentDir);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        /*
        ViewGroup toolbar = (ViewGroup) getActivity().findViewById(R.id.toolbar);
        ViewGroup breadcrumb = (ViewGroup) toolbar.findViewById(R.id.breadcrumb_layout);
        toolbar.removeView(breadcrumb);
        */
        super.onDestroyView();
    }

    public File getCurrentDir() {
        return mCurrentDir;
    }

    private void setCurrentDirectory(File dir) {
        mCurrentDir = dir;
        ArrayList<File> subDirs = new ArrayList<>();

        // list only folders and known archive types
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() || Utils.isArchive(f.getName())) {
                    subDirs.add(f);
                }
            }
        }

        File[] validFolders = Utils.listExternalStorageDirs();
        // ensure paths to known storages are listed, even if not browsable
        if (Utils.isOreoOrLater()) {
            Path parent = mCurrentDir.toPath();
            for (File validPath : validFolders) {
                if (!validPath.toPath().startsWith(parent))
                    continue;

                Path relPath = parent.relativize(validPath.toPath());
                if (relPath.getNameCount() < 1)
                    continue;

                File entry = new File(mCurrentDir, relPath.getName(0).toString());
                if (!subDirs.contains(entry))
                    subDirs.add(entry);
            }
        }

        // sort alphabetically ignore-case
        Collections.sort(subDirs, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return ((File) o).getName();
            }
        });

        // add '..' to top
        if (!mCurrentDir.getAbsolutePath().equals(mRootDir.getAbsolutePath())) {
            subDirs.add(0,mCurrentDir.getParentFile());
        }

        mSubdirs = subDirs.toArray(new File[subDirs.size()]);

        if (mListView != null) {
            mListView.invalidateViews();
        }

        //mDirTextView.setText(dir.getAbsolutePath());
        ((MainActivity)getActivity()).setSubTitle(dir.getAbsolutePath());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mSubdirs[position];

        // folders can be opened via long click below
        if (file.isDirectory()) {
            setCurrentDirectory(file);
            return;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, file);
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_BROWSER);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mSubdirs[position];

        if (!file.isDirectory())
            return true;

        // check if directory is folder-based comic
        try {
            Parser p = ParserFactory.create(file);
            if (p.numPages() < 1)
                return true;
        } catch (Exception e) {
            // TODO: not rly expecting an exception here
            Log.e("BrowserFragment","onItemLongClick",e);
            return true;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, file);
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_BROWSER);
        startActivity(intent);
        return true;
    }

    private void setIcon(int position, View convertView, File file) {
        ImageView view = (ImageView) convertView.findViewById(R.id.directory_row_icon);
        ImageView circle = (ImageView) convertView.findViewById(R.id.directory_row_circle);
        GradientDrawable circleDrawable = (GradientDrawable) circle.getDrawable();
        //GradientDrawable shape = (GradientDrawable) view.getBackground();
        //ImageView rainbow = (ImageView) convertView.findViewById(R.id.directory_row_rainbow);
        //rainbow.setVisibility(View.INVISIBLE);

        // default is folder icon on grey circle
        view.setImageResource(R.drawable.ic_folder_24);
        int colorRes = R.color.circle_grey;
        circleDrawable.setColor(getResources().getColor(colorRes));
        circle.setVisibility(View.VISIBLE);

        // ignore top parent dir entry
        if (position == 0)
            return;

        if (file.isDirectory()) {
            // is it a dir comic?
            try {
                Parser p = ParserFactory.create(file);
                if (p.numPages()>0) {
                    view.setImageResource(R.drawable.ic_image_folder_24);
                    colorRes = R.color.circle_teal;
                }
            } catch (Exception ignored) {
            }

            // TODO: file listing on folders with many files slows down scrolling
            File[] files = file.listFiles();
            if (files != null)
                for (File f : files) {
                    if (Utils.isArchive(f.getName())) {
                        // show rainbow
                        circle.setVisibility(View.INVISIBLE);
                        break;
                    }
                }

            circleDrawable.setColor(getResources().getColor(colorRes));
            return;
        }

        view.setImageResource(R.drawable.ic_article_24);
        String name = file.getName();
        if (!Utils.isArchive(name))
            return;

        view.setImageResource(R.drawable.ic_text_image_document_24);
        if (Utils.isPdf(name)) {
            colorRes = R.color.circle_blue;
        } else if (Utils.isZip(name)) {
            colorRes = R.color.circle_green;
        } else if (Utils.isRar(name)) {
            colorRes = R.color.circle_red;
        } else if (Utils.isSevenZ(name)) {
            colorRes = R.color.circle_yellow;
        } else if (Utils.isTarball(name)){
            colorRes = R.color.circle_orange;
        }

        //shape.setColor(getResources().getColor(colorRes));
        circleDrawable.setColor(getResources().getColor(colorRes));
    }

    @Override
    public void onRefresh() {
        // refresh on show
        if (mCurrentDir!=null)
            setCurrentDirectory(mCurrentDir);
        mRefreshLayout.setRefreshing(false);
    }

    private final class DirectoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mSubdirs.length;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return mSubdirs[position];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R.layout.row_directory, parent, false);
            }

            File file = mSubdirs[position];
            TextView textView = (TextView) convertView.findViewById(R.id.directory_row_text);

            if (position == 0 && !mCurrentDir.getAbsolutePath().equals(mRootDir.getAbsolutePath())) {
                textView.setText("..");
            } else {
                textView.setText(file.getName());
            }

            setIcon(position, convertView, file);

            return convertView;
        }
    }
}
