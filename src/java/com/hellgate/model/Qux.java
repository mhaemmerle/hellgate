package com.hellgate.model;

public class Qux implements java.io.Serializable {
    private int id;
    private String message;

    public Qux() {}

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
