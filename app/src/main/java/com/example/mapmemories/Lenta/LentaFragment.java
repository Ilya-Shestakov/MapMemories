package com.example.mapmemories.Lenta;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.mapmemories.Post.PostDetailsActivity;
import com.example.mapmemories.Post.ViewPostDetailsActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Post.Post;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LentaFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView memoriesRecyclerView;
    private PublicMemoriesAdapter publicAdapter;
    private List<Post> publicPostList;
    private DatabaseReference postsRef;
    private boolean isFirstLoad = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lenta, container, false);
        postsRef = FirebaseDatabase.getInstance().getReference("posts");
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        memoriesRecyclerView = view.findViewById(R.id.memoriesRecyclerView);

        setupRecyclerView();
        setupSwipeRefresh();
        loadPublicPosts();
        return view;
    }

    private void setupRecyclerView() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        publicPostList = new ArrayList<>();
        publicAdapter = new PublicMemoriesAdapter(getContext(), publicPostList, post -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent intent = new Intent(getActivity(), (currentUser != null && post.getUserId().equals(currentUser.getUid())) ? PostDetailsActivity.class : ViewPostDetailsActivity.class);
            intent.putExtra("postId", post.getId());
            if (currentUser != null && post.getUserId().equals(currentUser.getUid())) intent.putExtra("isEditMode", true);
            startActivity(intent);
        });
        memoriesRecyclerView.setAdapter(publicAdapter);

        // ВОЗВРАЩАЕМ СКРЫТИЕ ПАНЕЛИ
        memoriesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).toggleBottomDock(dy <= 0);
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            VibratorHelper.vibrate(getContext(), 30);
            loadPublicPosts();
        });
    }

    private void loadPublicPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                publicPostList.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    Post post = postSnap.getValue(Post.class);
                    if (post != null && post.isPublic()) publicPostList.add(post);
                }
                Collections.reverse(publicPostList);
                publicAdapter.notifyDataSetChanged();
                if (isFirstLoad) { runLayoutAnimation(); isFirstLoad = false; }
                swipeRefreshLayout.setRefreshing(false);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { swipeRefreshLayout.setRefreshing(false); }
        });
    }

    private void runLayoutAnimation() {
        final LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(getContext(), R.anim.layout_animation_fall_down);
        memoriesRecyclerView.setLayoutAnimation(controller);
        memoriesRecyclerView.scheduleLayoutAnimation();
    }
}