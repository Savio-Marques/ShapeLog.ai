package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.MealService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
public class MealHandler {

    private static final Logger log = LoggerFactory.getLogger(MealHandler.class);
    private final MealService mealService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;

    public MealHandler(MealService mealService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this.mealService = mealService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    public void registrarRefeicao(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Analisando refeição... ");
            Meal meal = mealService.registerMeal(user, text, audioBytes, userMessageId);
            String formattedText = messageFormatter.formatMealRegistered(meal);
            Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesRefeicao(meal.getId()));
            if (botMsg != null) {
                mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Erro ao registrar refeição para chatId={}", chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar a refeição.");
        }
    }

    public void atualizarRefeicaoEditada(Long mealId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Atualizando refeição... ");
            Meal meal = mealService.updateMeal(mealId, text, audioBytes);
            if (meal.getBotMessageId() != null) {
                sender.editarMensagemBot(chatId, meal.getBotMessageId(),
                        messageFormatter.formatMealRegistered(meal),
                        keyboardFactory.criarBotoesRefeicao(meal.getId()));
                sender.enviarMensagem(chatId, "✅ Refeição atualizada com sucesso!");
            } else {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatMealRegistered(meal), keyboardFactory.criarBotoesRefeicao(meal.getId()));
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar refeição id={} para chatId={}", mealId, chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar a refeição.");
        }
    }

    public void processarExclusaoRefeicao(Long mealId, int botMessageId, long chatId, BotActionSender sender) {
        if (mealService.existeMeal(mealId)) {
            mealService.deleteMeal(mealId);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Refeição excluída com sucesso!", null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta refeição já foi removida.", null);
        }
    }
}
