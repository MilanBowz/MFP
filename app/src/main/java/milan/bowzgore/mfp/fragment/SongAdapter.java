package milan.bowzgore.mfp.fragment;

import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;
import milan.bowzgore.mfp.service.PowerHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private final Context context;
    protected final List<AudioModel> items; // Display list
    private final List<AudioModel> originalItems; // Reference to original data
    private String lastPlayedSongName;

    protected SongAdapter(Context context) {
        SongLibrary lib = SongLibrary.get();
        if(!lib.songsList.isEmpty() && lib.isSyncTempSelectedFolder()){
            this.originalItems = lib.songsList;
            this.items = new ArrayList<>(lib.songsList); // Create a copy
            if (lib.songNumber == - 1) {
                lib.songNumber = SongLibrary.get().songsList.indexOf(lib.currentSong);
            }
        }
        else {
            this.originalItems = SongLibrary.get().getTempAudioFromDevice(context);
            this.items = new ArrayList<>(originalItems); // Create a copy
        }
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.fragment_songs,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        AudioModel songData = items.get(position);
        holder.titleTextView.setText(songData.getTitle());
        holder.songNumberTextView.setText(
                String.format(java.util.Locale.getDefault(), "%03d", position + 1)
        );

        if (SongLibrary.get().currentSong != null && Objects.equals(SongLibrary.get().currentSong.getTitle(), songData.getTitle())) {
            if(PowerHandler.currentMode == 2){
                holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.green));
            }
            else {
                holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.blue));
            }
            holder.songNumberTextView.setTextColor(ContextCompat.getColor(context, R.color.blue));
            lastPlayedSongName = (String) holder.titleTextView.getText();
        } else {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.color));
            holder.songNumberTextView.setTextColor(ContextCompat.getColor(context, R.color.color));

        }
        songData.setGlideImage(holder.iconImageView,96, 96, holder.iconImageView);

        holder.itemView.setOnClickListener(v -> {
            // Navigate to PlayingFragment
            if (context instanceof AppCompatActivity && holder.getAbsoluteAdapterPosition() != RecyclerView.NO_POSITION) {
                SongLibrary library = SongLibrary.get();
                if(!Objects.equals(library.selectedFolder, library.tempFolder) || originalItems != library.songsList){
                    library.songsList.clear();
                    library.songsList.addAll(originalItems);
                    library.selectedFolder = library.tempFolder;
                }
                // get currentsong by: holder.titleTextView
                library.currentSong = songData;
                library.songNumber = library.songsList.indexOf(library.currentSong);
                startMusicService();
                viewPagerAdapter.updatePlayingFragment();
                viewPager.setCurrentItem(0,true);
                updateUI();
                library.saveCurrentSong(context);
            }
        });

    }

    private void startMusicService() {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction("LIST_PLAY");
        ContextCompat.startForegroundService(context,intent);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    protected void updateUI() {
        if (items.isEmpty()) return;
        AudioModel current = SongLibrary.get().currentSong;
        if (current == null) return;
        if (lastPlayedSongName == null) {
            lastPlayedSongName = current.getTitle();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Find indices safely
            int lastIdx = -1;
            int curIdx  = -1;
            for (int i = 0; i < items.size(); i++) {
                String title = items.get(i).getTitle();
                if (Objects.equals(title, lastPlayedSongName)) lastIdx = i;
                if (Objects.equals(title, current.getTitle()))  curIdx  = i;
            }
            if (lastIdx >= 0) notifyItemChanged(lastIdx);
            if (curIdx  >= 0) notifyItemChanged(curIdx);
        } else {
            notifyDataSetChanged();
        }
    }
    // Add method to refresh from original data
    public void refreshFromOriginal() {
        items.clear();
        items.addAll(originalItems);
        notifyDataSetChanged();
    }

    // Add method to get original list
    public List<AudioModel> getOriginalItems() {
        return originalItems;
    }

    protected class ViewHolder extends RecyclerView.ViewHolder{
        TextView titleTextView;
        TextView songNumberTextView;
        ImageView iconImageView;
        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.music_title_text);
            songNumberTextView = itemView.findViewById(R.id.song_number_text);
            iconImageView = itemView.findViewById(R.id.icon_view);
        }
    }

}