package com.example.mapmemories;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

public class AnimUtils {

    public static void animateLike(ImageView likeIcon, boolean isLiked) {
        // Сбрасываем масштаб на случай, если анимация прервалась
        likeIcon.setScaleX(1f);
        likeIcon.setScaleY(1f);

        // 1. Сначала немного уменьшаем (подготовка к прыжку)
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(likeIcon, "scaleX", 0.8f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(likeIcon, "scaleY", 0.8f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        // 2. Резко увеличиваем (больше чем 1.0) с эффектом пружины
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(likeIcon, "scaleX", 1.2f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(likeIcon, "scaleY", 1.2f);
        scaleUpX.setInterpolator(new OvershootInterpolator(4f)); // 4f - сила пружины
        scaleUpY.setInterpolator(new OvershootInterpolator(4f));
        scaleUpX.setDuration(300);
        scaleUpY.setDuration(300);

        // 3. Возвращаем в норму (1.0)
        ObjectAnimator scaleNormalX = ObjectAnimator.ofFloat(likeIcon, "scaleX", 1f);
        ObjectAnimator scaleNormalY = ObjectAnimator.ofFloat(likeIcon, "scaleY", 1f);
        scaleNormalX.setDuration(100);
        scaleNormalY.setDuration(100);

        // Собираем последовательность
        AnimatorSet animatorSet = new AnimatorSet();

        // Секвенция: Сжать -> (Сменить картинку) -> Пружина -> Норма
        animatorSet.play(scaleDownX).with(scaleDownY);
        animatorSet.play(scaleUpX).with(scaleUpY).after(scaleDownX);
        animatorSet.play(scaleNormalX).with(scaleNormalY).after(scaleUpX);

        animatorSet.start();
    }
}