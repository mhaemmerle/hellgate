package com.hellgate.client.model;

public class ClientFoo implements java.io.Serializable {
    private int id;
    private int userId;
    private ClientBar bar;

    public ClientFoo() {}

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

    public ClientBar getBar() {
        return this.bar;
    }

    public void setBar(ClientBar bar) {
        this.bar = bar;
    }
}
