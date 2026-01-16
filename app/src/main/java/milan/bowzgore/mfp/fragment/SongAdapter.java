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

import java.util.List;
import java.util.Objects;

class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private final Context context;
    protected final List<AudioModel> items ;
    private int lastPlayedSong = -1;
    private String lastPlayedSongName;

    protected SongAdapter(Context context) {
        SongLibrary lib = SongLibrary.get();
        if(!lib.songsList.isEmpty() && lib.isSyncTempSelectedFolder()){
            this.items = lib.songsList;
            if (lib.songNumber == - 1) {
                lib.songNumber = SongLibrary.get().songsList.indexOf(lib.currentSong);
            }
        }
        else {
            this.items = SongLibrary.get().getTempAudioFromDevice(context);
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

        if (SongLibrary.get().currentSong != null && Objects.equals(SongLibrary.get().currentSong.getTitle(), songData.getTitle())) {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.blue));
            lastPlayedSongName = (String) holder.titleTextView.getText();
        } else {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.color));
        }
        songData.setGlideImage(holder.iconImageView,96, 96, holder.iconImageView);

        holder.itemView.setOnClickListener(v -> {
            // Navigate to PlayingFragment
            if (context instanceof AppCompatActivity && holder.getAbsoluteAdapterPosition() != RecyclerView.NO_POSITION) {
                SongLibrary library = SongLibrary.get();
                // get currentsong by: holder.titleTextView
                library.currentSong = songData;
                library.songNumber = library.songsList.indexOf(library.currentSong);
                startMusicService();
                library.selectedFolder = library.tempFolder;
                viewPagerAdapter.updatePlayingFragment();
                viewPager.setCurrentItem(0,true);
                updateUI();
                library.saveCurrentSong(context);
            }
        });

    }

    private void startMusicService() {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction("NEW");
        ContextCompat.startForegroundService(context,intent);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    protected void updateUI() {
        if (SongLibrary.get().isSyncTempSelectedFolder()) {
            if(lastPlayedSongName == null){
                lastPlayedSongName = SongLibrary.get().currentSong.getTitle();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notifyItemChanged(items.stream()
                        .map(AudioModel::getTitle)
                        .toList()
                        .indexOf(lastPlayedSongName));
            }
            else if(lastPlayedSong > -1){
                notifyItemChanged(lastPlayedSong);
            }

            int position = 0;
            String currentTitle = SongLibrary.get().currentSong.getTitle(); if (currentTitle == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                position = items.stream()
                        .map(AudioModel::getTitle)
                        .toList()
                        .indexOf(SongLibrary.get().currentSong.getTitle());
                notifyItemChanged(position);
            }
            else {
                position = SongLibrary.get().songNumber;
            }
            if (position == -1) return;
            notifyItemChanged(position);
            lastPlayedSong = position;
        }
    }

    protected class ViewHolder extends RecyclerView.ViewHolder{
        TextView titleTextView;
        ImageView iconImageView;
        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.music_title_text);
            iconImageView = itemView.findViewById(R.id.icon_view);
        }
    }

}