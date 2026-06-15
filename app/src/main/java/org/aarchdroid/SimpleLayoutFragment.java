package org.aarchdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

public class SimpleLayoutFragment extends Fragment {
    private static final String ARG_LAYOUT_ID = "layout_id";

    public static SimpleLayoutFragment newInstance(int layoutResId) {
        SimpleLayoutFragment fragment = new SimpleLayoutFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = getArguments().getInt(ARG_LAYOUT_ID);
        return inflater.inflate(layoutId, container, false);
    }
}
