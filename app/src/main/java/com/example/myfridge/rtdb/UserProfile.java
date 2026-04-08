package com.example.myfridge.rtdb;

import androidx.annotation.Keep;

/**
 * POJO mapped to the {@code /users/<uid>} node in Firebase RTDB.
 * <p>
 * Stores a user's display name, preferred measurement units and the number of
 * days before expiry at which the notification window opens.
 * </p>
 * <p>
 * Both constructors are kept via {@code @Keep} to prevent ProGuard/R8 from
 * stripping the no-arg constructor required by Firebase's automatic
 * deserialisation.
 * </p>
 */
@Keep
public class UserProfile {
    /** User's display name (stored in RTDB, may differ from the Auth display name). */
    public String name;
    /** Measurement unit system: {@code "metric"} or {@code "imperial"}. */
    public String units;
    /** Number of days before expiry at which alerts should be triggered (1–30). */
    public int daysBeforeExpireChoice;

    /**
     * No-arg constructor required for Firebase automatic POJO deserialisation.
     */
    @Keep
    public UserProfile() {}

    /**
     * Creates a fully-initialised user profile.
     *
     * @param name                  the user's display name
     * @param units                 measurement unit system
     * @param daysBeforeExpireChoice expiry-alert window in days
     */
    public UserProfile(String name, String units, int daysBeforeExpireChoice) {
        this.name = name;
        this.units = units;
        this.daysBeforeExpireChoice = daysBeforeExpireChoice;
    }
}

