package com.example.body_care;

import android.app.Activity;
import android.app.LauncherActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class Workout_List extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gym_list);
        GridView gymList = (GridView) findViewById(R.id.gridview);
        GridListAdapter adapter = new GridListAdapter();

        adapter.addItem(new Exercise("팔 운동", R.drawable.arm));
        adapter.addItem(new Exercise("하체 운동" , R.drawable.squart));
        adapter.addItem(new Exercise("등 운동", R.drawable.pull_up));
        adapter.addItem(new Exercise("가슴 운동" , R.drawable.chest));
        adapter.addItem(new Exercise("코어 운동", R.drawable.core));
        adapter.addItem(new Exercise("자세 교정" , R.drawable.feeling_bad));

        gymList.setAdapter(adapter);
        gymList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Exercise item = (Exercise) adapter.getItem(i);
                Intent intent = new Intent(getApplicationContext(), StartActivity.class);
                intent.putExtra("et1", item.getName());
                startActivity(intent);
            }
        });
    }


class GridListAdapter extends BaseAdapter {
    ArrayList<Exercise> items = new ArrayList<Exercise>();

    public void addItem(Exercise item){
        items.add(item);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Context context = parent.getContext();
        final Exercise exercise = items.get(position);

        if(convertView == null){
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_item, parent, false);
        }

        ImageView setImage = (ImageView) convertView.findViewById(R.id.setImage);
        TextView setText = (TextView) convertView.findViewById(R.id.setText);

        setImage.setImageResource(exercise.getImageResID());
        setText.setText(exercise.getName());

        return convertView;
    }
    }
}

