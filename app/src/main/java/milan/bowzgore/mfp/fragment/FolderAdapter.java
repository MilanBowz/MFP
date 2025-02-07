package milan.bowzgore.mfp.fragment;


import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import milan.bowzgore.mfp.library.SongLibrary;
import static milan.bowzgore.mfp.MainActivity.viewPager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Objects;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.ViewPagerAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link String}.
 * TODO: Replace the implementation with code for your data type.
 */
class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private final Context context;

    protected FolderAdapter(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item layout, not the fragment layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        SongLibrary songLibrary = SongLibrary.get();
        String folderName = songLibrary.folders.get(position);
        holder.textView.setText(folderName);

        // Update text color based on selection
        if (Objects.equals(songLibrary.selectedFolder, folderName)) {
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.blue));
        }
        else {
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.color));
        }
        
        holder.itemView.setOnClickListener(v -> {
            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            if (adapter != null && position != RecyclerView.NO_POSITION) {
                SongLibrary.get().tempFolder = folderName;
                addSongsFragment();
            }
        });
    }

    private void addSongsFragment(){
        if(viewPagerAdapter != null){
            viewPagerAdapter.updateFragment(1,new SongsFragment());
            viewPager.setCurrentItem(1, true);
        }
    }

    @Override
    public int getItemCount() {
        return SongLibrary.get().folders.size();
    }

    protected static final class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.folder_title);
        }
    }
}