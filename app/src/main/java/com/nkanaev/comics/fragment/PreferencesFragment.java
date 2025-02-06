package com.nkanaev.comics.fragment;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.nkanaev.comics.R;

public class PreferencesFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.menu_preferences);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
