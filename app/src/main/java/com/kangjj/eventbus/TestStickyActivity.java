package com.kangjj.eventbus;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kangjj.eventbus.annotation.Subscribe;
import com.kangjj.eventbus.library.EventBus;
import com.kangjj.eventbus.model.OrderInfo;


/**
 * @Description:
 * @Author: jj.kang
 * @Email: jj.kang@zkteco.com
 * @ProjectName: 3.1.1_eventbus_used_demo
 * @Package: com.netease.eventbus.demo
 * @CreateDate: 2019/11/28 16:37
 */
public class TestStickyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_sticky);

    }

    // 订阅方法
    @Subscribe
    public void event(OrderInfo info) {
        Log.e("event >>> ", info.toString());
    }

    // 测试优先级
    @Subscribe(sticky = true)
    public void event2(OrderInfo info) {
        Log.e("event >>> sticky", info.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    public void close(View view) {
        finish();
    }
    //多次调用和移除粘性事件时，post会执行多次粘性事件订阅方法（非粘性正常）
    OrderInfo orderInfo = new OrderInfo("商品1",1);
    public void postSticky(View view) {
        // Bug重现第1步
        EventBus.getDefault().postSticky(orderInfo);
    }

    public void registerSticky(View view) {
        // Bug重现第2步
        EventBus.getDefault().register(this);
        EventBus.getDefault().removeStickyEvent(OrderInfo.class);
//        EventBus.getDefault().removeStickyEvent(postSticky);
    }

    public void post(View view) {
        // Bug重现第3步
        EventBus.getDefault().post(orderInfo);
    }
}
