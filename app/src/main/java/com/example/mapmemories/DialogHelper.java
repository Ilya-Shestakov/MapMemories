package com.example.mapmemories;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DialogHelper {

    // Интерфейс, чтобы передавать результат нажатия
    public interface OnInputListener {
        void onSave(String text);
    }

    public interface OnConfirmListener {
        void onConfirm();
    }

    // 1. Диалог для редактирования (с полем ввода)
    public static void showInput(Context context, String title, String currentValue, int inputType, int iconRes, OnInputListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_custom, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        // Делаем фон прозрачным, чтобы углы CardView были видны
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Инициализация View
        TextView tvTitle = view.findViewById(R.id.dialogTitle);
        TextView tvMessage = view.findViewById(R.id.dialogMessage);
        TextInputLayout inputLayout = view.findViewById(R.id.inputLayout);
        TextInputEditText etInput = view.findViewById(R.id.dialogInput);
        ImageView icon = view.findViewById(R.id.dialogIcon);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        // Настройка
        tvTitle.setText(title);
        tvMessage.setVisibility(View.GONE); // Скрываем сообщение, так как это ввод
        icon.setImageResource(iconRes);

        etInput.setText(currentValue);
        etInput.setInputType(inputType);
        // Ставим курсор в конец текста
        if (currentValue != null) etInput.setSelection(currentValue.length());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String newValue = etInput.getText().toString().trim();
            listener.onSave(newValue);
            dialog.dismiss();
        });

        dialog.show();
    }

    // 2. Диалог подтверждения (Да/Нет) - например для Выхода
    public static void showConfirmation(Context context, String title, String message, OnConfirmListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_custom, null);
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
        inputLayout.setVisibility(View.GONE); // Скрываем ввод

        icon.setImageResource(R.drawable.ic_logout); // Или иконка вопроса
        btnConfirm.setText("Да");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            listener.onConfirm();
            dialog.dismiss();
        });

        dialog.show();
    }
}