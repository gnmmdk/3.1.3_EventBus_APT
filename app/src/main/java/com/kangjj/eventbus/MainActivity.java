package com.kangjj.eventbus;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.kangjj.eventbus.annotation.Subscribe;
import com.kangjj.eventbus.annotation.mode.ThreadMode;
import com.kangjj.eventbus.apt.EventBusIndex;
import com.kangjj.eventbus.library.EventBus;
import com.kangjj.eventbus.model.UserInfo;

/**
 *
 * 1、发布粘性事件：触发了所有同类型订阅方法（粘性和非粘性），已修复
 * 2、粘性事件订阅方法无法第2次消费（很难满足复杂项目要求），已修复
 * 3、多次调用和移除粘性事件时，post会执行多次粘性事件订阅方法（非粘性正常），已修复
 * 4、优化索引方法，让api更简单直接
 * 5、重写注解处理器，方式：apt + javapoet（官方是传统写法）
 * 6、弱化了线程池，使用缓存线程池替代
 * 7、去除了对象池概念，考虑recycle问题。暂未测试内存泄漏情况
 * 8、修复Subscription对象匹配bug，删除hashCode无用方法
 * 9、纯反射技术完全剥离，第二个项目有完整详细介绍
 *
 */
public class MainActivity extends AppCompatActivity {
    private TextView tv;
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 163) {
                UserInfo user = (UserInfo) msg.obj;
                tv.setText(user.toString());
            }
            return true;
        }
    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = findViewById(R.id.tv);

        EventBus.getDefault().addIndex(new EventBusIndex());
        EventBus.getDefault().register(this);
    }
    //普通跳转
    public void sticky(View view) {
        EventBus.getDefault().postSticky(new UserInfo("kangjj-sticky,",31));

    }
    //跳转
    public void jump(View view) {
        startActivity(new Intent(this,Main2Activity.class));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void abc(UserInfo user) {
//        tv.setText(user.toString());
//        Log.e("abc", user.toString());
        Message msg = new Message();
        msg.obj = user;
        msg.what = 163;
        handler.sendMessage(msg);
        Log.e("abc", user.toString());
    }



    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void sticky(UserInfo user){
        Log.e("sticky1", user.toString());
    }

    public void goTicky(View view) {
        startActivity(new Intent(this,TestStickyActivity.class));
    }
}
