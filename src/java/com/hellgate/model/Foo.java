package com.hellgate.model;

public class Foo implements java.io.Serializable {
    private int id;
    private int userId;
    private Bar bar;

    public Foo() {}

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return this.userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Bar getBar() {
        return this.bar;
    }

    public void setBar(Bar bar) {
        this.bar = bar;
    }
}
