package com.bot.telegram.bot;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface BotActionSender {
    void enviarMensagem(long chatId, String texto);
    Message enviarMensagemRetornando(long chatId, String texto);
    void deletarMensagem(long chatId, int messageId);
    Message enviarMensagemMarkdown(long chatId, String texto);
    Message enviarMensagemMarkdown(long chatId, String texto, InlineKeyboardMarkup keyboard);
    void editarMensagemBot(long chatId, int messageId, String novoTexto, InlineKeyboardMarkup keyboard);
    default void editarTecladoBot(long chatId, int messageId, InlineKeyboardMarkup keyboard) {}
    default void responderToast(String texto) {}
    String resolverMensagemDeErro(Exception e);
}
