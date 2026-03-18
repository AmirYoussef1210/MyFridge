package com.example.myfridge.rtdb;

import androidx.annotation.Keep;

@Keep
public class UserProfile {
    public String name;
    public String units;
    public int daysBeforeExpireChoice;

    // Required for Firebase deserialization
    @Keep
    public UserProfile() {}

    public UserProfile(String name, String units, int daysBeforeExpireChoice) {
        this.name = name;
        this.units = units;
        this.daysBeforeExpireChoice = daysBeforeExpireChoice;
    }
}

