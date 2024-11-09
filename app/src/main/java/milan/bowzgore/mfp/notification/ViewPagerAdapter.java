package milan.bowzgore.mfp.notification;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final List<Fragment> fragmentList = new ArrayList<>();

    public ViewPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    public void addFragment(Fragment fragment) {
        fragmentList.add(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragmentList.get(position);
    }

    @Override
    public int getItemCount() {
        return fragmentList.size();
    }
    public void updateFragment(int position, Fragment fragment) {
        if (position >= 0 && position < fragmentList.size()) {
            fragmentList.set(position, fragment);
        }
        notifyDataSetChanged();
    }
    @Override
    public long getItemId(int position) {
        // You must override this method and use fragmentList.get(position).hashCode()
        // This is important to ensure that ViewPager2 re-creates the fragment.
        return fragmentList.get(position).hashCode();
    }

    public Fragment getItem(int position) {
        // You must override this method and use fragmentList.get(position).hashCode()
        // This is important to ensure that ViewPager2 re-creates the fragment.
        return fragmentList.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        // Make sure ViewPager2 recognizes the fragments by their hashCode
        for (Fragment fragment : fragmentList) {
            if (fragment.hashCode() == itemId) {
                return true;
            }
        }
        return false;
    }
}