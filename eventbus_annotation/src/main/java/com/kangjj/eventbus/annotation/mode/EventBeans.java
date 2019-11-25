package com.kangjj.eventbus.annotation.mode;

public class EventBeans implements SubscriberInfo {
    private final Class subscriberClass;
    private final SubscriberMethod[] methodInfos;

    public EventBeans(Class<?> subscriberClass, SubscriberMethod[] methodInfos) {
        this.subscriberClass = subscriberClass;
        this.methodInfos = methodInfos;
    }

    @Override
    public Class<?> getSubscriberClass() {
        return subscriberClass;
    }

    @Override
    public SubscriberMethod[] getSubscriberMethod() {
        return methodInfos;
    }
}
