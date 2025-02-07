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
import android.widget.TextView;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;

public class SongsFragment extends Fragment {
    private SongAdapter adapter;
    private BroadcastReceiver receiver;
    private RecyclerView recyclerView;
    private TextView textFolder;
    private ImageButton backButton;
    private int lastPlayedSong = -1;

    public SongsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        super.onDestroyView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(adapter!=null){
            adapter.recycle();
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
        backButton.setOnClickListener(v -> {
            addFolderFragment();
        });

        if (SongLibrary.get().tempFolder != null) {
            adapter = new SongAdapter(getContext());
            recyclerView.setAdapter(adapter);
            textFolder.setText(SongLibrary.get().getFolderDisplay());
        }
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        if(SongLibrary.get().isSyncTempSelectedFolder()){
            requireActivity().runOnUiThread(this::updateUI);
            // Update UI based on notification changes
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUI();  // Update UI based on notification changes
                }
            };
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("NEXT"));
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("PREV"));
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }


    private void updateUI() {
        if (adapter != null) {
            adapter.notifyItemChanged(SongLibrary.get().songNumber);
            if (lastPlayedSong != -1) {
                adapter.notifyItemChanged(lastPlayedSong);
            }
            lastPlayedSong = SongLibrary.get().songNumber;
        }
    }

    private void addFolderFragment(){
        if(viewPagerAdapter != null){
            viewPagerAdapter.updateFragment(new FolderFragment());
            viewPager.setCurrentItem(1, true);
        }
    }

    protected void updateCurrentSong(AudioModel song) {
        int songnumber = SongLibrary.get().songsList.indexOf(song);
        if (songnumber < 0 || songnumber >= adapter.items.size()) {
            return;  // Prevent IndexOutOfBoundsException
        }
        // Update image only if it's different
        adapter.notifyItemChanged(songnumber);
    }

}