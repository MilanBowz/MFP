package milan.bowzgore.mfp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final List<Fragment> fragmentList = new ArrayList<>();

    protected ViewPagerAdapter(FragmentActivity fa) { // MainActivity only
        super(fa);
    }

    protected void addFragment(Fragment fragment) { // MainActivity only
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
            Fragment oldFragment = fragmentList.set(position, fragment);
            if (oldFragment != null) {
                if (oldFragment.isAdded()) {
                    oldFragment.getParentFragmentManager()
                            .beginTransaction()
                            .remove(oldFragment)
                            .commitNowAllowingStateLoss();
                }
                oldFragment.onDestroy();
                notifyItemChanged(position);
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return fragmentList.get(position).hashCode();
    }

    public Fragment getItem(int position) {
        return fragmentList.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        for (Fragment fragment : fragmentList) {
            if (fragment.hashCode() == itemId) {
                return true;
            }
        }
        return false;
    }
}