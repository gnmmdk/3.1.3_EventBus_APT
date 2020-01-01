package com.kangjj.eventbus.library;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kangjj.eventbus.annotation.SubscriberInfoIndex;
import com.kangjj.eventbus.annotation.mode.SubscriberInfo;
import com.kangjj.eventbus.annotation.mode.SubscriberMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ArrayList的底层是数组，查询和修改直接根据索引可以很快找到对应的元素（替换）
 * 而增加和删除就涉及到数组元素的移动，所以比较慢
 *
 * CopyOnWriteArrayList实现了List接口（读写分离）
 * Vector是增删改查方法都加了synchronized，保证同步，但是每个方法执行的时候都要去获得锁，性能就会大大下降
 * 而CopyOnWriteArrayList只是在增删改上加锁，但是读不加孙，在读性能上九号与Vector
 * CopyOnWriteArrayList支持读多写少的并发情况
 */
public class EventBus {
    //volatile修饰的变量不允许线程内部缓存和重排序，即直接修改内存
    private static volatile EventBus defaultInstance;
    //索引接口
    private SubscriberInfoIndex subscriberInfoIndex;
    // 订阅者类型集合，比如：订阅者MainActivity订阅了哪些EventBean，或者解除订阅的缓存。
    // todo key：订阅者MainActivity.class，value：EventBean集合
    private Map<Object,List<Class<?>>> typesBySubscriber;
    // todo 方法缓存：key：订阅者MainActivity.class，value：订阅方法集合
    private static final Map<Class,List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    //todo EventBean缓存，key:UserInfo.class,value:订阅者（可以是多个Activity）中所有订阅的方法集合
    private Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionByEventType;
    //todo  粘性事件缓存，key：UserInfo.class，value：UserInfo
    private final Map<Class<?>,Object> stickyEvents;
    // 发送（子线程） - 订阅（主线程）
    private Handler handler;
    // 发送（主线程） - 订阅（子线程）
    private ExecutorService executorService;
    private EventBus(){
        typesBySubscriber = new HashMap<>();
        subscriptionByEventType = new HashMap<>();
        stickyEvents = new HashMap<>();
        handler = new Handler(Looper.getMainLooper());
        executorService = Executors.newCachedThreadPool();
    }

    public static EventBus getDefault(){
        if(defaultInstance != null){
            synchronized (EventBus.class){
                if(defaultInstance!=null){
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }
    //todo  将EventBusIndex对象添加进来。
    public void addIndex(SubscriberInfoIndex index){
        this.subscriberInfoIndex = index;
    }

    //todo A 注册/订阅事件，参考EventBus.java138行
    public void register(Object subscriber) {
        //获取MainActivity.class
        Class<?> subscriberClass = subscriber.getClass();
        //todo A.1 寻找（MainActivity.class)订阅方法集合
        List<SubscriberMethod> subscriberMethods = findSubscriberMethods(subscriberClass);
        synchronized (this){
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscriber(subscriber,subscriberMethod);
            }
        }
    }

    /**
     * //todo 寻找（MainActivity.class）订阅方法集合，参考SubscriberMethodFinder.java 55行
     *
     * @param subscriberClass
     * @return
     */
    private List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //todo METHOD_CACHE：注册类中的所有方法 订阅者MainActivity.class，value：订阅方法集合
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        //找到缓存，直接返回
        if(subscriberMethods!=null){
            return subscriberMethods;
        }
        //todo A.1.1 缓存中没有找不到，从APT生成的类文件中寻找
        subscriberMethods = findUsingInfo(subscriberClass);
        if(subscriberClass!=null){
            //todo A.1.4 放入缓存中
            METHOD_CACHE.put(subscriberClass,subscriberMethods);
        }
        return subscriberMethods;
    }

    /**
     *  从APT生成的类文件中寻找订阅方法集合，参考SubscriberMethodFinder.java 64行
     * @param subscriberClass
     * @return
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // todo A.1.2 app在运行时寻找索引，报错了则说明没有初始化索引方法
        if(subscriberInfoIndex == null){
            throw new RuntimeException("未添加索引方法：addIndex()");
        }
        //todo A.1.3 找到了返回List<SubscriberMethod> 接口持有实现类的引用
        SubscriberInfo info = subscriberInfoIndex.getSubscriberInfo(subscriberClass);
        if(info!=null) {
            return Arrays.asList(info.getSubscriberMethod());
        }
        return null;
    }

    /**
     * todo B 遍历中……并开始订阅，参考EventBus.java 149行
     * @param subscriber MainActivity
     * @param subscriberMethod Subscrible注解的方法。
     */
    private void subscriber(Object subscriber, SubscriberMethod subscriberMethod) {
        //todo B.1 获取订阅方法参数类型，如：UserInfo.class
        Class<?> eventType = subscriberMethod.getEventType();
        //todo B.2 临时对象存储
        Subscription subscription = new Subscription(subscriber,subscriberMethod);
        //todo B.3 读取EventBean缓存 不存在则创建     EventBean缓存，key:UserInfo.class,value:订阅者（可以是多个Activity）中所有订阅的方法集合
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionByEventType.get(eventType);
        if(subscriptions == null){
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionByEventType.put(eventType,subscriptions);
        }else{
            if(subscriptions.contains(subscription)){//todo 判断列表中是否包含Subscription 最后会调用到内部的equals方法
                Log.e("netease >>> ", subscriber.getClass() + "重复注册粘性事件！");//原Eventbus这里是直接抛出异常。这里我们是再次执行黏性事件。优化的地方
                //todo  解决bug：解决粘性事件订阅方法无法第2次消费
                // 执行多次黏性事件，但不添加到集合，避免订阅方法多次执行
                sticky(subscriberMethod,eventType,subscription);
                return;
            }
        }
        //todo B.4 订阅方法优先级处理。第一次进来肯定是0，参考EventBus.java 163行
        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            //如果满足任一条件则进入循环（第一次i=size=0）
            //第2次，size不为0，新加入的订阅方法匹配集合中所有订阅方法的优先级
            if(i==size || subscriberMethod.getPriority() > subscriptions.get(i).subscriberMethod.getPriority()){
                //如果新加入的订阅方法优先级大于集合中某订阅方法优先级，则插队到它之前以为
                if(!subscriptions.contains(subscription)){
                    subscriptions.add(i,subscription);
                }
                // 优化：插队成功就跳出（找到了加入集合点）
                break;
            }
        }
        //todo B.5 订阅者类型集合，比如：订阅者MainActivity订阅了哪些EventBean，或者解除订阅的缓存
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if(subscribedEvents == null){
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber,subscribedEvents);
        }
        // 注意：subscribe()方法在遍历过程中，所以一直在添加
        subscribedEvents.add(eventType);
        //todo B.6 发送粘性事件
        sticky(subscriberMethod,eventType,subscription);
    }

    /**
     * 抽取原因：可执行多次黏性事件，而不会出现闪退，参考EventBus.java 158行
     * @param subscriberMethod
     * @param eventType
     * @param subscription
     */
    private void sticky(SubscriberMethod subscriberMethod, Class<?> eventType, Subscription subscription) {
        //黏性事件触发：注册时间就激活方法，因为整个源码只有此处遍历了。
        //最佳切入点原因：1.黏性事件的订阅方法加入了缓存。2.注册时只有黏性事件直接激活方法（隔离非黏性事件）
        //新增开关方法弊端：黏性事件未在缓存中，无法触发订阅方法。且有可能多次执行post（）方法 ？ TODO
        if(subscriberMethod.isSticky()){ // 参考EventBus.java 178行
            //源码中做了继承关系处理了，也说明了迭代效率和更改数据结构方便查找，这里就省略了（真实项目极少）TODO ？
            Object stickyEvent = stickyEvents.get(eventType);
            if(stickyEvent!=null){
                postToSubscription(subscription,stickyEvent);
            }
        }
    }

    /**
     * 发布订阅方法的线程模式
     * @param subscription
     * @param event
     */
    private void postToSubscription(final Subscription subscription,final Object event) {
        switch (subscription.subscriberMethod.getThreadMode()) {
            case POSTING:                    // 订阅、发布在同一线程
                invokeSubscriber(subscription,event);
                break;
            case MAIN:
                if(Looper.myLooper() == Looper.getMainLooper()){
                    invokeSubscriber(subscription,event);
                }else{
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription,event);
                        }
                    });
                }
                break;
            case ASYNC:
                if(Looper.myLooper()==Looper.getMainLooper()){
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription,event);
                        }
                    });
                }else{
                    invokeSubscriber(subscription,event);
                }
                break;
            default:
                throw new IllegalStateException("未知线程模式！" + subscription.subscriberMethod.getThreadMode());
        }
    }

    // 发送消息 / 事件
    public void post(Object event) {
        // 此处两个参数，简化了源码，参考EventBus.java 252 - 265 - 384 - 400行
        postSingleEventForEventType(event, event.getClass());
    }

    // 为EventBean事件类型发布单个事件（遍历），EventBus核心：参数类型必须一致！！！
    private void postSingleEventForEventType(Object event, Class<?> eventClass) {
        // 从EventBean缓存中，获取所有订阅者和订阅方法
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            // 同步锁，保证并发安全
            subscriptions = subscriptionByEventType.get(eventClass);
        }
        // 判空，健壮性代码
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                // 遍历，寻找发送方指定的EventBean，匹配的订阅方法的EventBean
                postToSubscription(subscription, event);
            }
        }
    }

    /**
     *  执行订阅方法（被注解方法自动执行）参考EventBus.java 505行
     * @param subscription
     * @param event
     */
    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            // 无论3.0之前还是之后。最后一步终究逃不过反射！
            subscription.subscriberMethod.getMethod().invoke(subscription.subscriber,event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 发送粘性事件，最终还是调用了post方法，参考EventBus.java 301行
    public void postSticky(Object event) {
        // 同步锁保证并发安全（小项目可忽略此处）
        synchronized (stickyEvents) {
            // 加入粘性事件缓存集合
            stickyEvents.put(event.getClass(), event);
        }
        // todo 解决bug：发布粘性事件：触发了所有同类型订阅方法（粘性和非粘性）
        // 巨坑！！！源码这么写我也不知道什么意图。恶心的后果：只要参数匹配，粘性/非粘性订阅方法全部执行
        // post(event);
    }

    // 获取指定类型的粘性事件，参考EventBus.java 314行
    public <T> T getStickyEvent(Class<T> eventType) {
        // 同步锁保证并发安全（小项目可忽略此处）
        synchronized (stickyEvents) {
            // cast方法做转换类型时安全措施（简化stickyEvents.get(eventType)）
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    public static void clearCaches(){
        METHOD_CACHE.clear();
    }
}
