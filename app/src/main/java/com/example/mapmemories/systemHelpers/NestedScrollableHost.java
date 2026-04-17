package com.example.mapmemories.systemHelpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

public class NestedScrollableHost extends FrameLayout {
    private int touchSlop;
    private float initialX;
    private float initialY;

    public NestedScrollableHost(@NonNull Context context) {
        super(context);
        init();
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private ViewPager2 getParentViewPager() {
        android.view.ViewParent v = getParent();
        while (v != null && !(v instanceof ViewPager2)) {
            // ДОБАВЛЕНА ЭТА ПРОВЕРКА, ЧТОБЫ ИЗБЕЖАТЬ КРАША
            if (!(v instanceof android.view.View)) {
                return null;
            }
            v = v.getParent();
        }
        return (ViewPager2) v;
    }

    private View getChild() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private boolean canChildScroll(int orientation, float delta) {
        int direction = -((int) Math.signum(delta));
        View child = getChild();
        if (child == null) return false;

        if (orientation == 0) { // Горизонтальный скролл
            return child.canScrollHorizontally(direction);
        } else { // Вертикальный скролл
            return child.canScrollVertically(direction);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        handleInterceptTouchEvent(e);
        return super.onInterceptTouchEvent(e);
    }

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 parentViewPager = getParentViewPager();
        if (parentViewPager == null) return;

        int orientation = parentViewPager.getOrientation();

        // Если внутренний элемент вообще не может скроллиться, ничего не делаем
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f)) {
            return;
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - initialX;
            float dy = e.getY() - initialY;
            boolean isVpHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL;

            float scaledDx = Math.abs(dx) * (isVpHorizontal ? .5f : 1f);
            float scaledDy = Math.abs(dy) * (isVpHorizontal ? 1f : .5f);

            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isVpHorizontal == (scaledDy > scaledDx)) {
                    // Свайп идет перпендикулярно (вверх-вниз), отдаем скролл родителю
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    // Свайп идет параллельно (влево-вправо)
                    if (canChildScroll(orientation, isVpHorizontal ? dx : dy)) {
                        // Фотки еще есть, блокируем родителя, скроллим фотки
                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else {
                        // Фотки закончились, отдаем скролл родителю (переход на вкладку Чаты)
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
            }
        }
    }
}