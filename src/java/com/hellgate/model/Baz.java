package com.hellgate.model;

public class Baz implements java.io.Serializable {
    private int id;
    private Qux qux;
    // private Qux[] quxes;
 
    public Baz() {}

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Qux getQux() {
        return this.qux;
    }

    public void setQux(Qux qux) {
        this.qux = qux;
    }

    // public Qux[] getQuxes() {
    //     return this.quxes;
    // }

    // public void setQuxes(Qux[] quxes) {
    //     this.quxes = quxes;
    // }
}
