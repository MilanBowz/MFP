package milan.bowzgore.mfp.fragment;

import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.library.FolderLibrary.tempFolder;


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

import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.FolderLibrary;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;

import java.util.List;
import java.util.Objects;


/**
 * {@link RecyclerView.Adapter} that can display a {@link AudioModel}.
 * TODO: Replace the implementation with code for your data type.
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private final Context context;

    public List<AudioModel> items ;

    public SongAdapter(Context context) {
        if(!SongLibrary.get().songsList.isEmpty() && SongLibrary.get().songsList.get(0).getPath().contains(tempFolder)){
            this.items = SongLibrary.get().songsList;
        }
        else {
            this.items = SongLibrary.get().getAllAudioFromDevice(context,tempFolder);
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

        if (SongLibrary.get().currentSong != null) {
            if (SongLibrary.get().songNumber == holder.getBindingAdapterPosition()
                    && Objects.equals(SongLibrary.get().currentSong.getTitle(), songData.getTitle())) {
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
            int previousSongNumber = SongLibrary.get().songNumber;
            SongLibrary.get().songNumber = holder.getAbsoluteAdapterPosition();
            SongLibrary.get().songsList = items;
            // Navigate to PlayingFragment
            if (context instanceof AppCompatActivity && SongLibrary.get().songNumber != RecyclerView.NO_POSITION) {
                // Begin the fragment transaction
                viewPager.setCurrentItem(0,true);
                FolderLibrary.selectedFolder = tempFolder;
                NotificationService.changePlaying(context,SongLibrary.get().songNumber);
                startMusicService();
                notifyItemChanged(previousSongNumber); // Notify that the previous item has changed
                notifyItemChanged(SongLibrary.get().songNumber); // Notify that the current item has changed
            }
        });

    }
    private void startMusicService() {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction("START");
        context.startService(intent);
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

}