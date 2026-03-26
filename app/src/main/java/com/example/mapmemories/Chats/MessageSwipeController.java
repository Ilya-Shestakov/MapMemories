package com.example.mapmemories.Chats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class MessageSwipeController extends ItemTouchHelper.Callback {

    private final SwipeControllerActions buttonsActions;
    private final Drawable replyIcon;

    public MessageSwipeController(Context context, SwipeControllerActions buttonsActions) {
        this.buttonsActions = buttonsActions;
        this.replyIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_revert);
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        return makeMovementFlags(0, ItemTouchHelper.LEFT);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        buttonsActions.onSwipeToReply(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;

            // Эффект "резинки": делим сдвиг на 5, чтобы тянулось очень туго и плавно
            float translationX = dX / 5f;

            if (replyIcon != null && dX < 0) {
                int iconMargin = (itemView.getHeight() - replyIcon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + replyIcon.getIntrinsicHeight();

                int iconLeft = itemView.getRight() + (int)translationX + iconMargin;
                int iconRight = iconLeft + replyIcon.getIntrinsicWidth();

                replyIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                replyIcon.draw(c);
            }

            super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.15f; // Очень легкое срабатывание
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 5;
    }

    public interface SwipeControllerActions {
        void onSwipeToReply(int position);
    }
}