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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class MealHandler {

    private static final Logger log = LoggerFactory.getLogger(MealHandler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final MealService mealService;
    private final ReportService reportService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;
    private final ZoneId appZoneId;

    public MealHandler(MealService mealService, ReportService reportService,
                       MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this(mealService, reportService, messageFormatter, keyboardFactory, DEFAULT_ZONE);
    }

    @Autowired
    public MealHandler(MealService mealService, ReportService reportService,
                       MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory,
                       ZoneId appZoneId) {
        this.mealService = mealService;
        this.reportService = reportService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public void registrarRefeicao(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, messageFormatter.formatLoading("Analisando refeição"));
        try {
            Meal meal = mealService.registerMeal(user, text, audioBytes, userMessageId);
            LocalDate mealDate = meal.getCreatedAt() != null ? meal.getCreatedAt().atZone(appZoneId).toLocalDate() : LocalDate.now(appZoneId);
            DailyReportDto dailyReport = reportService.getReportForDate(user, mealDate);
            String formattedText = messageFormatter.formatMealRegistered(meal, dailyReport, mealDate);
            deleteTemp(sender, chatId, tempMsg);
            Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
            if (botMsg != null) {
                mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Erro ao registrar refeição para chatId={}", chatId, e);
            deleteTemp(sender, chatId, tempMsg);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar a refeição."));
        }
    }

    public void atualizarRefeicaoEditada(UserTelegram user, Long mealId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, messageFormatter.formatLoading("Atualizando refeição"));
        try {
            Meal meal = mealService.updateMeal(mealId, text, audioBytes);
            LocalDate mealDate = meal.getCreatedAt() != null ? meal.getCreatedAt().atZone(appZoneId).toLocalDate() : LocalDate.now(appZoneId);
            DailyReportDto dailyReport = reportService.getReportForDate(user, mealDate);
            deleteTemp(sender, chatId, tempMsg);
            if (meal.getBotMessageId() != null) {
                String editedCard = messageFormatter.formatMealRegistered(meal, dailyReport, mealDate, true);
                sender.editarMensagemBot(chatId, meal.getBotMessageId(),
                        editedCard,
                        keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatItemUpdated("Refeição", true),
                        keyboardFactory.criarBotaoVerRelatorio(mealDate));
            } else {
                String formattedText = messageFormatter.formatMealRegistered(meal, dailyReport, mealDate);
                Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesRefeicao(meal.getId(), mealDate));
                if (botMsg != null) {
                    mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar refeição id={} para chatId={}", mealId, chatId, e);
            deleteTemp(sender, chatId, tempMsg);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Erro ao atualizar a refeição."));
        }
    }

    public void processarExclusaoRefeicao(Long mealId, int botMessageId, long chatId, BotActionSender sender) {
        if (mealService.existeMeal(mealId)) {
            mealService.deleteMeal(mealId);
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatItemDeleted("Refeição", true), null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatAlreadyRemoved("Esta refeição", true), null);
        }
    }

    private void deleteTemp(BotActionSender sender, long chatId, Message tempMsg) {
        if (tempMsg != null) {
            sender.deletarMensagem(chatId, tempMsg.getMessageId());
        }
    }
}
