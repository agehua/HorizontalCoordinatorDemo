package com.agehua.horizontalcoordinatordemo.test;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.agehua.horizontalcoordinatordemo.HorizontalAppBarLayout;
import com.agehua.horizontalcoordinatordemo.R;

import java.util.Arrays;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        HorizontalAppBarLayout appbar = findViewById(R.id.appbar);
        RecyclerView listView = (RecyclerView) findViewById(R.id.list_view);
        String[] data = new String[]{
                "0",
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
                "0",
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
                "0",
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
        };
        listView.setNestedScrollingEnabled(true);
        listView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(listView);
        listView.setAdapter(adapter);
        adapter.setData(Arrays.asList(data));

    }
}
