package milan.bowzgore.mfp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import milan.bowzgore.mfp.fragment.PlayingFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final Fragment[] fragments = new Fragment[2]; // Always 2 items

    protected ViewPagerAdapter(FragmentActivity fa) { // MainActivity only
        super(fa);
    }

    public void initFragments(Fragment first, Fragment second) {
        fragments[0] = first;
        fragments[1] = second;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return fragments.length;
    }

    public void updateFragment(Fragment fragment) {
        Fragment oldFragment = fragments[1];
        fragments[1] = fragment;
        if (oldFragment != null && oldFragment.isAdded()) {
            oldFragment.getParentFragmentManager()
                    .beginTransaction()
                    .detach(oldFragment)
                    .remove(oldFragment)
                    .commitNowAllowingStateLoss();
        }
        notifyItemChanged(1);
        System.gc();
    }

    public void updatePlayingFragment() { // Update to new song in PlayingFragment
        if (fragments[0] instanceof PlayingFragment) {
            ((PlayingFragment) fragments[0]).setMusicResources();
        }
    }

    @Override
    public long getItemId(int position) {
        return fragments[position].hashCode();
    }

    public Fragment getItem(int position) {
        return fragments[position];
    }

    @Override
    public boolean containsItem(long itemId) {
        for (Fragment fragment : fragments) {
            if (fragment.hashCode() == itemId) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        if (fragments[0] != null && fragments[0].isAdded()) {
            fragments[0].getParentFragmentManager()
                    .beginTransaction()
                    .detach(fragments[0])
                    .remove(fragments[0])
                    .commitNowAllowingStateLoss();
        }
        if (fragments[1] != null && fragments[1].isAdded()) {
            fragments[1].getParentFragmentManager()
                    .beginTransaction()
                    .detach(fragments[1])
                    .remove(fragments[1])
                    .commitNowAllowingStateLoss();
        }
        fragments[0] = null;
        fragments[1] = null;
        System.gc();
    }
}
