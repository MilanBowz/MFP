package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import milan.bowzgore.mfp.fragment.PlayingFragment;

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

    public void updateFragment(Fragment fragment) {
            Fragment oldFragment = fragmentList.set(1, fragment);
            if (oldFragment != null) {
                if (oldFragment.isAdded()) {
                    oldFragment.getParentFragmentManager()
                            .beginTransaction()
                            .remove(oldFragment)
                            .commitNowAllowingStateLoss();
                }
                oldFragment.onDestroy();
                notifyItemChanged(1);
            }
    }

    public void updatePlayingFragment() { // Update to new song in PlayingFragment
        if (fragmentList.get(0) instanceof PlayingFragment) {
            ((PlayingFragment) fragmentList.get(0)).setMusicResources();
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