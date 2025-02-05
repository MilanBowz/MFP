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
    public SongAdapter adapter;

    public SongsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        if (adapter != null) {
            adapter = null;  // Remove reference to adapter to help garbage collection
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter = null;  // Remove reference to adapter to help garbage collection
        }
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_songs_list, container, false);
        // Initialize RecyclerView and Button
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        TextView textFolder = view.findViewById(R.id.songs_text);
        ImageButton backButton = view.findViewById(R.id.back_button);
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
        requireActivity().runOnUiThread(this::updateUI);
        // Update UI based on notification changes
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateUI();  // Update UI based on notification changes
            }
        };
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("NEXT"));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("PREV"));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void updateUI() {
        if(adapter != null ){
            adapter.notifyDataSetChanged();
        }
    }
    public static void addFolderFragment(){
        if(viewPagerAdapter != null){
            viewPagerAdapter.updateFragment(1,new FolderFragment());
            viewPager.setCurrentItem(1, true);
        }
    }

    public void updateCurrentSong(AudioModel song) {
        if (SongLibrary.get().songNumber < 0 || SongLibrary.get().songNumber >= adapter.items.size()) {
            return;  // Prevent IndexOutOfBoundsException
        }
        // Update image only if it's different
        adapter.notifyItemChanged(SongLibrary.get().songNumber);
    }


}