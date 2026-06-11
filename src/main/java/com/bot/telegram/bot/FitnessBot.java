package com.bot.telegram.bot;

import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.stereotype.Component;

@Component
public class FitnessBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (update.getMessage().hasText()) {
                String mensagemRecebida = update.getMessage().getText();
                enviarMensagem(chatId, "Texto recebido: " + mensagemRecebida);
            } else if (update.getMessage().hasVoice()) {
                Voice voice = update.getMessage().getVoice();
                enviarMensagem(chatId, "Baixando áudio...");
                byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                if (audioBytes != null) {
                    enviarMensagem(chatId, "Áudio baixado com sucesso! Tamanho: " + audioBytes.length + " bytes.");
                } else {
                    enviarMensagem(chatId, "Falha ao baixar o áudio.");
                }
            }
        }
    }

    private byte[] obterBytesDoAudio(String fileId) {
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File fileTelegram = execute(getFile);
            
            java.io.File fileLocal = downloadFile(fileTelegram);
            byte[] bytes = java.nio.file.Files.readAllBytes(fileLocal.toPath());
            
            fileLocal.delete();
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void enviarMensagem(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}