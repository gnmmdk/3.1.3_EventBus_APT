package com.kangjj.eventbus;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.kangjj.eventbus.annotation.Subscribe;
import com.kangjj.eventbus.annotation.mode.ThreadMode;
import com.kangjj.eventbus.apt.EventBusIndex;
import com.kangjj.eventbus.library.EventBus;
import com.kangjj.eventbus.model.UserInfo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().addIndex(new EventBusIndex());
        EventBus.getDefault().register(this);
    }


    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void abc(UserInfo user) {
//        tv.setText(user.toString());
        Log.e("abc", user.toString());
//        Message msg = new Message();
//        msg.obj = user;
//        msg.what = 163;
//        handler.sendMessage(msg);
//        Log.e("abc", user.toString());
    }
}
