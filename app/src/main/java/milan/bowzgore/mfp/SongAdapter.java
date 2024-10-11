package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.library.FolderLibrary.selectedFolder;
import static milan.bowzgore.mfp.library.SongLibrary.currentSong;
import static milan.bowzgore.mfp.library.SongLibrary.getSongLibrary;
import static milan.bowzgore.mfp.library.SongLibrary.songsList;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.notification.NotificationService;

import java.util.List;
import java.util.Objects;


/**
 * {@link RecyclerView.Adapter} that can display a {@link AudioModel}.
 * TODO: Replace the implementation with code for your data type.
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private final Context context;
    SongLibrary library = getSongLibrary();

    public List<AudioModel> items ;

    public SongAdapter(Context context) {
        if(!songsList.isEmpty() && songsList.get(0).getPath().contains(selectedFolder)){
            this.items = songsList;
        }
        else {
            this.items = SongLibrary.getAllAudioFromDevice(context,selectedFolder );
        }
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.fragment_songs,parent,false);
        return new SongAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        AudioModel songData = items.get(position);
        holder.titleTextView.setText(songData.getTitle());

        if (currentSong != null) {
            if (SongLibrary.songNumber == holder.getBindingAdapterPosition()
                    && Objects.equals(currentSong.getTitle(), songData.getTitle())) {
                holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.blue));
            } else {
                holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.color));
            }
        }

        if (songData.getImage() != null) {
            holder.iconImageView.setImageBitmap(songData.getImage());
        } else {
            holder.iconImageView.setImageResource(R.drawable.music_icon); // Default image if no artwork is found
        }

        holder.itemView.setOnClickListener(v -> {
            int previousSongNumber = library.songNumber;
            library.songNumber = holder.getAbsoluteAdapterPosition();
            library.songsList = items;
            // Navigate to PlayingFragment
            if (context instanceof AppCompatActivity && library.songNumber != RecyclerView.NO_POSITION) {
                // Begin the fragment transaction
                viewPager.setCurrentItem(0,true);
                library.changePlaying(library.songNumber);
                startMusicService();
                notifyItemChanged(previousSongNumber); // Notify that the previous item has changed
                notifyItemChanged(library.songNumber); // Notify that the current item has changed
            }
        });
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        TextView titleTextView;
        ImageView iconImageView;
        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.music_title_text);
            iconImageView = itemView.findViewById(R.id.icon_view);
        }
    }
    private void startMusicService() {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction("START");
        context.startService(intent);
    }
}