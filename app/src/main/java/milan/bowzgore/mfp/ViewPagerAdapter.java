package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.MainActivity.viewPager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import milan.bowzgore.mfp.fragment.FolderFragment;
import milan.bowzgore.mfp.fragment.PlayingFragment;
import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.service.PowerHandler;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public enum SecondPage {
        FOLDER,
        SONGS
    }

    private static final long PLAYING_ID = 1L;
    private static final long FOLDER_ID = 2L;
    private static final long SONGS_ID = 3L;

    private SecondPage secondPage = SecondPage.FOLDER;

    private final FragmentManager fragmentManager;

    public ViewPagerAdapter(FragmentActivity activity) {
        super(activity);
        fragmentManager = activity.getSupportFragmentManager();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {

        if (position == 0) {
            return new PlayingFragment();
        }

        if (position == 1) {
            return secondPage == SecondPage.FOLDER
                    ? new FolderFragment()
                    : new SongsFragment();
        }

        throw new IllegalArgumentException("Invalid position");
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return PLAYING_ID;
        return secondPage == SecondPage.FOLDER
                ? FOLDER_ID : SONGS_ID;
    }

    @Override
    public boolean containsItem(long id) {
        if (id == PLAYING_ID) return true;
        if (id == FOLDER_ID) return secondPage == SecondPage.FOLDER;
        if (id == SONGS_ID) return secondPage == SecondPage.SONGS;
        return false;
    }

    private Fragment getActiveFragment(long id) {
        return fragmentManager.findFragmentByTag(
                "f" + id
        );
    }

    public void showFolder() {
        if (secondPage != SecondPage.FOLDER) {
            secondPage = SecondPage.FOLDER;
            notifyItemChanged(1);
        }
    }

    public void showSongs() {
        if (secondPage != SecondPage.SONGS) {
            secondPage = SecondPage.SONGS;
            notifyItemChanged(1);
        }
        else{
            // its prob another folder reinit or change values similar to init
            Fragment fragment = getActiveFragment(SONGS_ID);
            ((SongsFragment) fragment).reloadFolder();
        }
    }

    public void updatePlayingFragment() {
        Fragment fragment = getActiveFragment(PLAYING_ID);
        if (fragment instanceof PlayingFragment
                && fragment.isAdded()) {
            ((PlayingFragment) fragment).updatePlayingFrament();
        }
    }

    public void updateFolderFragment() {
        if (secondPage != SecondPage.FOLDER) return;
        Fragment fragment = getActiveFragment(FOLDER_ID);
        if (fragment instanceof FolderFragment
                && fragment.isAdded()) {
            ((FolderFragment) fragment).updateFolderfragment();
        }
    }
    public void updateSongsFragment() {
        if (secondPage != SecondPage.SONGS) return;

        Fragment fragment = getActiveFragment(SONGS_ID);
        if (fragment instanceof SongsFragment
                && fragment.isAdded()) {
            ((SongsFragment) fragment).updateCurrentSong();
        }
    }

    public boolean scrollToCurrentSong() {
        if (secondPage == SecondPage.FOLDER) {
            showSongs();
            viewPager.post(() -> {
                Fragment fragment = getActiveFragment(SONGS_ID);
                if (fragment instanceof SongsFragment
                        && fragment.isAdded()) {
                    ((SongsFragment) fragment).scrollToCurrentSong();
                }
            });
            return true;
        }

        if (secondPage == SecondPage.SONGS) {
            showSongs();
            Fragment fragment = getActiveFragment(SONGS_ID);
            if (fragment instanceof SongsFragment
                    && fragment.isAdded()) {
                ((SongsFragment) fragment).scrollToCurrentSong();
                return true;
            }
        }

        return false;
    }
}
