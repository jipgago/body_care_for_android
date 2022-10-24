package com.example.body_care;

public class Exercise{
    String name;
    int imageResID;

    public Exercise(String name, int imageResID){
        this.name = name;
        this.imageResID = imageResID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImageResID() {
        return imageResID;
    }

    public void setImageResID(int imageResID) {
        this.imageResID = imageResID;
    }
}