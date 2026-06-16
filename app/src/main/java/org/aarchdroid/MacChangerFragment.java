package org.aarchdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.asynclayoutinflater.view.AsyncLayoutInflater;
import androidx.fragment.app.Fragment;

public class MacChangerFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FrameLayout placeholder = new FrameLayout(requireActivity());
        placeholder.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new AsyncLayoutInflater(requireActivity()).inflate(
            R.layout.fragment_mac_changer, container,
            (view, resid, parent) -> {
                if (getActivity() == null) return;
                view.setAlpha(0f);
                placeholder.addView(view,
                    new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                view.animate().alpha(1f).setDuration(250);
            });

        return placeholder;
    }
}
