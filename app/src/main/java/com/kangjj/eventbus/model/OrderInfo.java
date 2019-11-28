package com.kangjj.eventbus.model;

/**
 * 定义事件
 */
public class OrderInfo {

    private String name;
    private int id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public OrderInfo(String name, int id) {
        this.name = name;
        this.id = id;
    }

    @Override
    public String toString() {
        return "OrderInfo{" +
                "name='" + name + '\'' +
                ", id=" + id +
                '}';
    }
}

