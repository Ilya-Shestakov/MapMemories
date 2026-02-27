package com.example.mapmemories.systemHelpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.example.mapmemories.Lenta.OfflineQueueBottomSheet;
import com.example.mapmemories.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DialogHelper {

    public interface OnInputListener {
        void onSave(String text);
    }

    public interface OnConfirmListener {
        void onConfirm();
    }

    // --- 1. ОЧЕРЕДЬ ЗАГРУЗКИ (Шторка снизу) ---
    public static void showOfflineQueue(FragmentActivity activity, Object ignored) {
        // Второй параметр ignored оставил для совместимости вызова, если ты передавал LifecycleOwner
        // Но для BottomSheet нужен FragmentManager
        OfflineQueueBottomSheet bottomSheet = new OfflineQueueBottomSheet();
        bottomSheet.show(activity.getSupportFragmentManager(), "OfflineQueueTag");
    }

    // --- 2. ВВОД ТЕКСТА (С размытием фона) ---
    public static void showInput(Activity activity, String title, String currentValue, int inputType, int iconRes, OnInputListener listener) {
        applyBlur(activity, true); // ВКЛЮЧАЕМ БЛЮР

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Инициализация
        TextView tvTitle = view.findViewById(R.id.dialogTitle);
        TextView tvMessage = view.findViewById(R.id.dialogMessage);
        TextInputLayout inputLayout = view.findViewById(R.id.inputLayout);
        TextInputEditText etInput = view.findViewById(R.id.dialogInput);
        ImageView icon = view.findViewById(R.id.dialogIcon);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        tvTitle.setText(title);
        tvMessage.setVisibility(View.GONE);
        icon.setImageResource(iconRes);

        etInput.setText(currentValue);
        etInput.setInputType(inputType);
        if (currentValue != null) etInput.setSelection(currentValue.length());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String newValue = etInput.getText().toString().trim();
            listener.onSave(newValue);
            dialog.dismiss();
        });

        // ПРИ ЗАКРЫТИИ УБИРАЕМ БЛЮР
        dialog.setOnDismissListener(d -> applyBlur(activity, false));

        dialog.show();
    }

    // --- 3. ПОДТВЕРЖДЕНИЕ (С размытием фона) ---
    public static void showConfirmation(Activity activity, String title, String message, OnConfirmListener listener) {
        applyBlur(activity, true); // ВКЛЮЧАЕМ БЛЮР

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = view.findViewById(R.id.dialogTitle);
        TextView tvMessage = view.findViewById(R.id.dialogMessage);
        TextInputLayout inputLayout = view.findViewById(R.id.inputLayout);
        ImageView icon = view.findViewById(R.id.dialogIcon);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        tvTitle.setText(title);
        tvMessage.setText(message);
        tvMessage.setVisibility(View.VISIBLE);
        inputLayout.setVisibility(View.GONE);

        icon.setImageResource(R.drawable.ic_logout);
        btnConfirm.setText("Да");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            listener.onConfirm();
            dialog.dismiss();
        });

        // ПРИ ЗАКРЫТИИ УБИРАЕМ БЛЮР
        dialog.setOnDismissListener(d -> applyBlur(activity, false));

        dialog.show();
    }

    // --- ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ БЛЮРА (Как в Profile.java) ---
    private static void applyBlur(Activity activity, boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            View rootView = activity.getWindow().getDecorView();
            if (enable) {
                rootView.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
            } else {
                rootView.setRenderEffect(null);
            }
        }
    }
}