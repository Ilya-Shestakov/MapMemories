package com.example.mapmemories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

public class OnlineFragment extends BaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // TODO: Здесь будет карта и онлайн-функции
        View view = inflater.inflate(R.layout.fragment_online, container, false);

        // Временный контент
        View tempView = new android.widget.TextView(getContext());
        ((android.widget.TextView) tempView).setText("Онлайн режим\n(Карта появится здесь)");
        ((android.widget.TextView) tempView).setTextSize(18);
        ((android.widget.TextView) tempView).setTextColor(getResources().getColor(R.color.text_primary));
        ((android.widget.TextView) tempView).setGravity(android.view.Gravity.CENTER);

        return view;
    }

    @Override
    public String getFragmentTitle() {
        return "Онлайн";
    }
}