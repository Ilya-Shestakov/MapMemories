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
        // Используем стандартную иконку стрелочки Android (можешь заменить на свою R.drawable.ic_reply)
        this.replyIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_revert);
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // Разрешаем свайп только ВЛЕВО
        return makeMovementFlags(0, ItemTouchHelper.LEFT);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Вызываем интерфейс ответа
        buttonsActions.onSwipeToReply(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;

            // Ограничиваем свайп, чтобы элемент не улетал за экран (максимум сдвиг на четверть экрана)
            float maxSwipe = itemView.getWidth() / 4f;
            float translationX = dX;
            if (translationX < -maxSwipe) {
                translationX = -maxSwipe;
            }

            // Рисуем иконку ответа
            if (replyIcon != null && dX < 0) {
                int iconMargin = (itemView.getHeight() - replyIcon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + (itemView.getHeight() - replyIcon.getIntrinsicHeight()) / 2;
                int iconBottom = iconTop + replyIcon.getIntrinsicHeight();

                // Иконка появляется справа
                int iconLeft = itemView.getRight() + (int)dX + iconMargin;
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
        return 0.5f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 5;
    }

    public interface SwipeControllerActions {
        void onSwipeToReply(int position);
    }
}