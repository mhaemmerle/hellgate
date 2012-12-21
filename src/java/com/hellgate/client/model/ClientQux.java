package com.hellgate.client.model;

public class ClientQux implements java.io.Serializable {
    private int id;
    private String message;

    public ClientQux() {}

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
