package com.example.mapmemories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class OnlineFragment extends BaseFragment {  // ← ИЗМЕНИЛ ТУТ

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_online, container, false);
    }

    @Override
    public String getFragmentTitle() {  // ← ДОБАВЬ ЭТОТ МЕТОД
        return "Онлайн";
    }
}