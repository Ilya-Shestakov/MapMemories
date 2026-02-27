package com.example.mapmemories.systemHelpers;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;


public class VibratorHelper {

    /**
     * @param context Контекст приложения
     * @param milliseconds Время вибрации в миллисекундах
     */
    public static void vibrate(Context context, long milliseconds) {
        if (context == null || milliseconds <= 0) return;

        try {
            Vibrator vibrator = getVibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Для Android 8.0+ используем VibrationEffect
                VibrationEffect effect = VibrationEffect.createOneShot(
                        milliseconds,
                        VibrationEffect.DEFAULT_AMPLITUDE
                );
                vibrator.vibrate(effect);
            } else {
                // Для старых версий
                vibrator.vibrate(milliseconds);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context Контекст
     * @param milliseconds Время
     * @param amplitude Амплитуда (0-255)
     */
    public static void vibrate(Context context, long milliseconds, int amplitude) {
        if (context == null || milliseconds <= 0 || amplitude < 0) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Vibrator vibrator = getVibrator(context);
                if (vibrator == null || !vibrator.hasVibrator()) return;

                // Ограничиваем амплитуду 0-255
                int safeAmplitude = Math.min(amplitude, 255);

                VibrationEffect effect = VibrationEffect.createOneShot(milliseconds, safeAmplitude);
                vibrator.vibrate(effect);
            } else {
                vibrate(context, milliseconds);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context Контекст
     * @param pattern Паттерн вибрации (пауза, вибрация, пауза...)
     * @param repeat Повтор (-1 = не повторять, 0+ = индекс в pattern с которого повторять)
     */
    public static void vibratePattern(Context context, long[] pattern, int repeat) {
        if (context == null || pattern == null || pattern.length == 0) return;

        try {
            Vibrator vibrator = getVibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, repeat);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, repeat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context Контекст
     */
    public static void cancelVibration(Context context) {
        if (context == null) return;

        try {
            Vibrator vibrator = getVibrator(context);
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context Контекст
     * @return true если устройство поддерживает вибрацию
     */
    public static boolean hasVibrator(Context context) {
        if (context == null) return false;

        try {
            Vibrator vibrator = getVibrator(context);
            return vibrator != null && vibrator.hasVibrator();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получение объекта Vibrator с учетом версии Android
     */
    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Для Android 12+
            VibratorManager vibratorManager =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            // Для старых версий
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /**
     * Предустановленные паттерны вибрации
     */
    public static class Patterns {
        // Короткий клик
        public static final long[] CLICK = {0, 50};

        // Долгое нажатие
        public static final long[] LONG_PRESS = {0, 200};

        // Успех (два коротких импульса)
        public static final long[] SUCCESS = {0, 50, 100, 50};

        // Ошибка (два длинных импульса)
        public static final long[] ERROR = {0, 150, 100, 150};

        // Уведомление
        public static final long[] NOTIFICATION = {0, 300};

        // Загрузка/ожидание (пульсация)
        public static final long[] LOADING = {0, 100, 100, 100, 100, 100};

        // Переход/навигация
        public static final long[] NAVIGATION = {0, 80, 50, 80};

        // Волна (для анимаций)
        public static final long[] WAVE = {0, 50, 30, 50, 30, 50};
    }
}