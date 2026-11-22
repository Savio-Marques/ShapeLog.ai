package com.bot.telegram.bot;

public enum UserState {

    AWAITING_MEAL,
    AWAITING_WORKOUT_TITLE,
    AWAITING_EXERCISE,
    AWAITING_EDIT_MEAL,
    AWAITING_EDIT_EXERCISE,
    AWAITING_GOAL;

    public String serialize() {
        return this.name();
    }

    public String withId(long id) {
        return this.name() + ":" + id;
    }

    public String withPayload(String payload) {
        return this.name() + ":" + payload;
    }

    public static String withPromptMessageId(String serializedState, Integer promptMessageId) {
        if (serializedState == null || promptMessageId == null) {
            return serializedState;
        }
        return serializedState + ":" + promptMessageId;
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

    public static Integer extractPromptMessageId(String raw) {
        if (raw == null || !raw.contains(":")) return null;
        UserState state = from(raw);
        if (state == null) return null;
        String[] parts = raw.split(":");
        int expectedIdx = switch (state) {
            case AWAITING_MEAL, AWAITING_WORKOUT_TITLE, AWAITING_EXERCISE, AWAITING_GOAL -> 1;
            case AWAITING_EDIT_MEAL -> 2;
            case AWAITING_EDIT_EXERCISE -> 4;
        };
        if (parts.length <= expectedIdx) {
            return null;
        }
        try {
            return Integer.parseInt(parts[expectedIdx]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
