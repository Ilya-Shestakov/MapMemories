package com.example.mapmemories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

public class OfflineFragment extends BaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offline, container, false);

        // Временный контент
        View tempView = new android.widget.TextView(getContext());
        ((android.widget.TextView) tempView).setText("Оффлайн режим\n(Сохранённые места)");
        ((android.widget.TextView) tempView).setTextSize(18);
        ((android.widget.TextView) tempView).setTextColor(getResources().getColor(R.color.text_primary));
        ((android.widget.TextView) tempView).setGravity(android.view.Gravity.CENTER);

        return view;
    }

    @Override
    public String getFragmentTitle() {
        return "Оффлайн";
    }
}