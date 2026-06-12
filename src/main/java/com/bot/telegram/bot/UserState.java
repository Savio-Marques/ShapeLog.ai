package com.bot.telegram.bot;

public enum UserState {

    AWAITING_MEAL,
    AWAITING_WORKOUT_TITLE,
    AWAITING_EXERCISE,
    AWAITING_EDIT_MEAL,
    AWAITING_EDIT_WORKOUT,
    AWAITING_EDIT_EXERCISE;

    public String serialize() {
        return this.name();
    }

    public String withId(long id) {
        return this.name() + ":" + id;
    }

    public String withPayload(String payload) {
        return this.name() + ":" + payload;
    }

    public static UserState from(String raw) {
        if (raw == null) return null;
        String base = raw.contains(":") ? raw.split(":")[0] : raw;
        try {
            return UserState.valueOf(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    public static Long extractId(String raw) {
        if (raw == null || !raw.contains(":")) return null;
        try {
            return Long.parseLong(raw.split(":")[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    public static String extractPayload(String raw) {
        if (raw == null || !raw.contains(":")) return null;
        return raw.substring(raw.indexOf(":") + 1);
    }
}
