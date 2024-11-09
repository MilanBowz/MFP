package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import milan.bowzgore.mfp.library.FolderLibrary;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongsFragment extends Fragment {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private TextView textFolder;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private BroadcastReceiver receiver;


    public SongsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBackNavigation();
    }
    @Override
    public void onDetach() {
        // Signal to stop background work
        isRunning.set(false);

        // Properly shut down executor service
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                // Handle the case where the executor service did not terminate properly
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        super.onDetach();
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
        Button backButton = view.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            addFolderFragment();
            /*if (getActivity() != null && getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getActivity().getSupportFragmentManager().popBackStack();
            }*/
        });

        if (FolderLibrary.selectedFolder != null) {
            adapter = new SongAdapter(getContext());
            recyclerView.setAdapter(adapter);
            textFolder.setText(FolderLibrary.getFolderDisplay());
        }
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        updateUI();
        if(adapter.items != SongLibrary.songsList){
            executorService.execute(() -> {
                for (AudioModel song : adapter.items) {
                    if (!isRunning.get()) break;
                    song.getEmbeddedArtwork(song.getPath());
                    requireActivity().runOnUiThread(this::updateUI);
                }
            });
        }
        receiver = new BroadcastReceiver() {
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
    private void setupBackNavigation() {
        // Register a callback for the back press
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Check if the current fragment is SongsFragment
                if (viewPager.getCurrentItem() == 1 && viewPagerAdapter.getItem(1) instanceof SongsFragment) {
                    // Replace SongsFragment with FolderFragment at position 1
                    viewPagerAdapter.updateFragment(1, new FolderFragment());
                    viewPager.setCurrentItem(1, true);  // Navigate to FolderFragment
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                this, // LifecycleOwner
                callback
        );
    }

}