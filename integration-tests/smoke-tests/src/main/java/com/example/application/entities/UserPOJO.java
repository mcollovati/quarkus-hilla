package com.example.application.entities;

public class UserPOJO {

    private String name;
    private String surname;

    public UserPOJO(String name, String surname) {
        this.name = name;
        this.surname = surname;
    }

    public String name() {
        return name;
    }

    public String surname() {
        return surname;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }
}
