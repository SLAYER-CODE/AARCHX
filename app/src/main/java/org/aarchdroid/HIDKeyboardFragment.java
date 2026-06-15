package org.aarchdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class HIDKeyboardFragment extends Fragment {

    private static final String[] TAB_TITLES = {"Windows CMD", "PS HTTP", "PowerSploit", "Editor"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_hid_keyboard, container, false);

        ViewPager2 viewPager = root.findViewById(R.id.pagerHid);
        TabLayout tabLayout = root.findViewById(R.id.tabLayoutHid);

        viewPager.setAdapter(new HidPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(TAB_TITLES[position])).attach();

        return root;
    }

    private static class HidPagerAdapter extends FragmentStateAdapter {
        HidPagerAdapter(Fragment fragment) { super(fragment); }
        @NonNull @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return SimpleLayoutFragment.newInstance(R.layout.fragment_hid_windows_cmd);
                case 1: return SimpleLayoutFragment.newInstance(R.layout.fragment_hid_powershell_http);
                case 2: return SimpleLayoutFragment.newInstance(R.layout.fragment_hid_powersploit);
                case 3: return SimpleLayoutFragment.newInstance(R.layout.fragment_hid_editor);
                default: return SimpleLayoutFragment.newInstance(R.layout.fragment_hid_windows_cmd);
            }
        }
        @Override
        public int getItemCount() { return 4; }
    }
}
