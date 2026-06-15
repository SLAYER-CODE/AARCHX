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

public class BluetoothFragment extends Fragment {

    private static final String[] TAB_TITLES = {"Main", "Tools", "Spoof", "BadBT", "CarWhisperer"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        ViewPager2 viewPager = root.findViewById(R.id.pagerBt);
        TabLayout tabLayout = root.findViewById(R.id.tabLayoutBt);

        viewPager.setAdapter(new BtPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(TAB_TITLES[position])).attach();

        return root;
    }

    private static class BtPagerAdapter extends FragmentStateAdapter {
        BtPagerAdapter(Fragment fragment) { super(fragment); }
        @NonNull @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_main);
                case 1: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_tools);
                case 2: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_spoof);
                case 3: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_badbt);
                case 4: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_carwhisperer);
                default: return SimpleLayoutFragment.newInstance(R.layout.fragment_bt_main);
            }
        }
        @Override
        public int getItemCount() { return 5; }
    }
}
