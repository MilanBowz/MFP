package milan.bowzgore.mfp.fragment;

import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.PowerHandler;

public class SongsFragment extends Fragment {
    private SongAdapter adapter;
    private BroadcastReceiver receiver;
    private RecyclerView recyclerView;
    private TextView textFolder;
    private ImageButton backButton;
    private SearchView searchSong;
    private boolean isFiltered = false;
    private List<AudioModel> originalData;  // ADD THIS LINE

    public SongsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(adapter!=null){
            if(!SongLibrary.get().isSyncTempSelectedFolder())
            {
                adapter.items.clear();
            }
            adapter = null;
        }
        if (receiver != null) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
            receiver = null; // Helps garbage collection
        }
        if(recyclerView != null){
            recyclerView.setAdapter(null);
            recyclerView.setLayoutManager(null);
            recyclerView = null; // Help GC
        }
        textFolder = null;
        backButton = null;
        View view = getView();
        if(view!= null){
            view.setBackground(null);
        }

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_songs_list, container, false);
        // Initialize RecyclerView and Button
        recyclerView = view.findViewById(R.id.recycler_view);
        textFolder = view.findViewById(R.id.songs_text);
        backButton = view.findViewById(R.id.back_button);
        searchSong = view.findViewById(R.id.search_songs);

        backButton.setOnClickListener(v -> {
            addFolderFragment();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));


        if (SongLibrary.get().tempFolder != null) {
            adapter = new SongAdapter(getContext());
            recyclerView.setAdapter(adapter);
            textFolder.setText(SongLibrary.get().getFolderDisplay());

            // STORE THE SOURCE LIST REFERENCE
            originalData = new ArrayList<>(adapter.items);  // ADD THIS LINE

            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    adapter.updateUI();
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("NEXT");
            filter.addAction("PREV");
            filter.addAction("IM_UPDATE");
            filter.addAction("PLAYER_READY");
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filter);
            if(PowerHandler.currentMode == 2){
                SongLibrary.get().makeRandomList();
            }
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchSong.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filter(query);
                searchSong.clearFocus(); // Hide keyboard after submit
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText); // THIS WAS MISSING - now filters as you type
                return true;
            }
        });
        searchSong.setOnCloseListener(() -> {
            restoreOriginalList(); // Use helper method instead of direct manipulation
            return false; // Let SearchView handle its own collapse
        });
    }

    private void addFolderFragment(){
        if(viewPagerAdapter != null){
            viewPagerAdapter.updateFragment(new FolderFragment());
            viewPager.setCurrentItem(1, true);
        }
    }
    public void updateCurrentSong() {
        adapter.updateUI();
    }
    public void filter(String text) {
        if (adapter == null || originalData == null) return;

        adapter.items.clear();

        if (text == null || text.trim().isEmpty()) {
            adapter.items.addAll(originalData); // USE CACHED LIST
            isFiltered = false;
        } else {
            String query = text.toLowerCase().trim();
            for (AudioModel song : originalData) { // USE CACHED LIST
                if (song.getTitle().toLowerCase().contains(query)) {
                    adapter.items.add(song);
                }
            }
            isFiltered = true;
        }
        adapter.notifyDataSetChanged();
    }

    private void restoreOriginalList() {
        if (adapter != null && originalData != null) {
            adapter.items.clear();
            adapter.items.addAll(originalData); // USE CACHED LIST
            adapter.notifyDataSetChanged();
            isFiltered = false;
        }
    }

}