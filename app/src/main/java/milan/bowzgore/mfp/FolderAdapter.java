package milan.bowzgore.mfp;


import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.library.FolderLibrary.folders;
import static milan.bowzgore.mfp.library.FolderLibrary.selectedFolder;
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

import milan.bowzgore.mfp.library.FolderLibrary;
import milan.bowzgore.mfp.notification.ViewPagerAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link String}.
 * TODO: Replace the implementation with code for your data type.
 */
public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private final Context context;

    public FolderAdapter(Context context) {
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
        String folderName = folders.get(position);
        holder.textView.setText(folderName);

        // Update text color based on selection
        if (Objects.equals(selectedFolder, folderName)) {
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.blue));
        }
        else {
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.color));
        }
        
        holder.itemView.setOnClickListener(v -> {
            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            if (adapter != null && position != RecyclerView.NO_POSITION) {
                int previousSelectedPosition = folders.indexOf(selectedFolder);
                FolderLibrary.selectedFolder = folderName; // Update the selected folder
                int newSelectedPosition = folders.indexOf(folderName);
                if (previousSelectedPosition != -1) {
                    notifyItemChanged(previousSelectedPosition);
                }
                notifyItemChanged(newSelectedPosition);

                if(viewPagerAdapter.getItemCount() > 2)
                    viewPagerAdapter.updateFragment(2,new SongsFragment());
                else{
                    viewPagerAdapter.addFragment(new SongsFragment());// not swipable
                }
                viewPager.setCurrentItem(2,true); // Navigate to SongsFragment
            }
        });
    }

    public static void addSongsFragment(){
        if(viewPagerAdapter != null){
            if(viewPagerAdapter.getItemCount() > 2)
                viewPagerAdapter.updateFragment(2,new SongsFragment());
            else{
                viewPagerAdapter.addFragment(new SongsFragment());// not swipable
            }
        }
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.folder_title);
        }
    }
}