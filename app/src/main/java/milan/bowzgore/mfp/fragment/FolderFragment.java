package milan.bowzgore.mfp.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import milan.bowzgore.mfp.R;
/**
 * A fragment representing a list of Items.
 */
public class FolderFragment extends Fragment {
    private RecyclerView recyclerView;
    private FolderAdapter folderAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_folder_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.folder_recyclerview); // Ensure this ID matches the XML
        if (recyclerView == null) {
            throw new NullPointerException("RecyclerView is null. Check your layout file for correct ID.");
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        folderAdapter = new FolderAdapter(getContext());
        recyclerView.setAdapter(folderAdapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recyclerView != null) {
            recyclerView.setAdapter(null); // Remove adapter reference
            recyclerView.setLayoutManager(null); // Remove layout manager
            recyclerView = null; // Help GC

        }
        if(folderAdapter !=null){
            folderAdapter = null;
        }
    }
}