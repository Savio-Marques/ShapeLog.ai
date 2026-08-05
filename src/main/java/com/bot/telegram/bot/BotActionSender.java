package com.bot.telegram.bot;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface BotActionSender {
    void enviarMensagem(long chatId, String texto);
    Message enviarMensagemRetornando(long chatId, String texto);
    void deletarMensagem(long chatId, int messageId);
    Message enviarMensagemMarkdown(long chatId, String texto);
    Message enviarMensagemMarkdown(long chatId, String texto, InlineKeyboardMarkup keyboard);
    void editarMensagemBot(long chatId, int messageId, String novoTexto, InlineKeyboardMarkup keyboard);
    String resolverMensagemDeErro(Exception e);
}
