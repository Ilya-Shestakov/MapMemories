package com.example.mapmemories;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class SwipeBackHelper {
    private final Activity activity;
    private final View decorView;
    private final int screenWidth;
    private final int touchSlop;

    private float downX;
    private float downY;
    private boolean isSwiping;
    private boolean canSwipe;
    private boolean isEnabled = true;

    public SwipeBackHelper(Activity activity) {
        this.activity = activity;
        this.decorView = activity.getWindow().getDecorView();
        this.screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    public void setSwipeEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    public boolean dispatchTouchEvent(MotionEvent event, MotionEventSuperCaller superCaller) {
        if (!isEnabled) {
            return superCaller.callSuper(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                isSwiping = false;
                // Свайп работает только если начать тянуть от левого края (первые 150px)
                canSwipe = downX < 150;
                break;

            case MotionEvent.ACTION_MOVE:
                if (canSwipe) {
                    float deltaX = event.getRawX() - downX;
                    float deltaY = Math.abs(event.getRawY() - downY);

                    // Если тянем вправо сильнее, чем вверх/вниз -> это свайп назад
                    if (!isSwiping && deltaX > touchSlop && deltaX > deltaY) {
                        isSwiping = true;
                        // Отменяем клики по кнопкам под пальцем, чтобы они не нажались
                        MotionEvent cancelEvent = MotionEvent.obtain(event);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        superCaller.callSuper(cancelEvent);
                        cancelEvent.recycle();
                    }

                    if (isSwiping) {
                        // Двигаем весь экран вправо
                        decorView.setTranslationX(deltaX);
                        return true; // Перехватываем событие
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwiping) {
                    float deltaX = event.getRawX() - downX;
                    // Если свайпнули больше чем на 30% экрана -> закрываем активити
                    if (deltaX > screenWidth / 3f) {
                        decorView.animate()
                                .translationX(screenWidth)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    activity.finish();
                                    activity.overridePendingTransition(0, 0);
                                })
                                .start();
                    } else {
                        // Иначе возвращаем экран на место
                        decorView.animate()
                                .translationX(0)
                                .setDuration(200)
                                .start();
                    }
                    isSwiping = false;
                    canSwipe = false;
                    return true;
                }
                canSwipe = false;
                break;
        }
        return superCaller.callSuper(event);
    }

    public interface MotionEventSuperCaller {
        boolean callSuper(MotionEvent event);
    }
}