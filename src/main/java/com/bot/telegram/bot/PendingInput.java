package com.bot.telegram.bot;

public record PendingInput(
        String token,
        String text,
        byte[] audioBytes,
        Integer userMessageId,
        long createdAt
) {
    public boolean isVoice() {
        return audioBytes != null && audioBytes.length > 0;
    }
}
