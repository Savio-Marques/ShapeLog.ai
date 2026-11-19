package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;

@Component
public class MealHandler {

    private static final Logger log = LoggerFactory.getLogger(MealHandler.class);
    private final MealService mealService;
    private final ReportService reportService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;

    public MealHandler(MealService mealService, ReportService reportService,
                       MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this.mealService = mealService;
        this.reportService = reportService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    public void registrarRefeicao(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, "Analisando refeição... ⏳");
        try {
            Meal meal = mealService.registerMeal(user, text, audioBytes, userMessageId);
            LocalDate mealDate = meal.getCreatedAt() != null ? meal.getCreatedAt().toLocalDate() : LocalDate.now();
            DailyReportDto dailyReport = reportService.getReportForDate(user, mealDate);
            String formattedText = messageFormatter.formatMealRegistered(meal, dailyReport, mealDate);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
            if (botMsg != null) {
                mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Erro ao registrar refeição para chatId={}", chatId, e);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar a refeição.");
        }
    }

    public void atualizarRefeicaoEditada(UserTelegram user, Long mealId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, "Atualizando refeição... ⏳");
        try {
            Meal meal = mealService.updateMeal(mealId, text, audioBytes);
            LocalDate mealDate = meal.getCreatedAt() != null ? meal.getCreatedAt().toLocalDate() : LocalDate.now();
            DailyReportDto dailyReport = reportService.getReportForDate(user, mealDate);
            String formattedText = messageFormatter.formatMealRegistered(meal, dailyReport, mealDate);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            if (meal.getBotMessageId() != null) {
                sender.editarMensagemBot(chatId, meal.getBotMessageId(),
                        formattedText,
                        keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
                sender.enviarMensagem(chatId, "✅ Refeição atualizada com sucesso!");
            } else {
                Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
                if (botMsg != null) {
                    mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar refeição id={} para chatId={}", mealId, chatId, e);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar a refeição.");
        }
    }

    public void processarExclusaoRefeicao(Long mealId, int botMessageId, long chatId, BotActionSender sender) {
        if (mealService.existeMeal(mealId)) {
            mealService.deleteMeal(mealId);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Refeição excluída com sucesso\\!", null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta refeição já foi removida\\.", null);
        }
    }
}
