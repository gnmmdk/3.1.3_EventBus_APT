package com.kangjj.eventbus.annotation;

import com.kangjj.eventbus.annotation.mode.SubscriberInfo;

/**
 * 所有事件订阅方法，生成索引接口
 * apt 生成的类需要实现本接口
 */
public interface SubscriberInfoIndex {

    SubscriberInfo getSubscriberInfo(Class<?> subscriberClass);
}
