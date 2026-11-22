package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ReportHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportHandler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ReportService reportService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;
    private final ZoneId appZoneId;

    public ReportHandler(ReportService reportService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this(reportService, messageFormatter, keyboardFactory, DEFAULT_ZONE);
    }

    @Autowired
    public ReportHandler(ReportService reportService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory, ZoneId appZoneId) {
        this.reportService = reportService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public void gerarRelatorio(UserTelegram user, String arg, long chatId, BotActionSender sender) {
        try {
            LocalDate date = LocalDate.now(appZoneId);
            if (arg != null && !arg.isEmpty()) {
                if ("ontem".equalsIgnoreCase(arg)) {
                    date = date.minusDays(1);
                } else {
                    date = parseDate(arg);
                    if (date == null) {
                        sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidDateFormat());
                        return;
                    }
                }
            }
            abrirRelatorioPorData(user, date, chatId, sender);
        } catch (Exception e) {
            log.error("Erro ao gerar relatório para chatId={}", chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Ocorreu um erro ao gerar o relatório."));
        }
    }

    public void abrirRelatorioPorData(UserTelegram user, LocalDate date, long chatId, BotActionSender sender) {
        try {
            DailyReportDto report = reportService.getReportForDate(user, date);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatDailyReport(report, date), keyboardFactory.criarBotoesRelatorio(report, date));
        } catch (Exception e) {
            log.error("Erro ao abrir relatório de {} para chatId={}", date, chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Ocorreu um erro ao gerar o relatório."));
        }
    }

    public void navegarRelatorio(UserTelegram user, LocalDate date, int botMessageId, long chatId, BotActionSender sender) {
        editarRelatorio(user, date, botMessageId, chatId, sender, false, "Ocorreu um erro ao navegar no relatório.");
    }

    public void abrirModoGerenciar(UserTelegram user, LocalDate date, int botMessageId, long chatId, BotActionSender sender) {
        editarRelatorio(user, date, botMessageId, chatId, sender, true, "Ocorreu um erro ao abrir o gerenciamento.");
    }

    public void voltarModoVisualizacao(UserTelegram user, LocalDate date, int botMessageId, long chatId, BotActionSender sender) {
        navegarRelatorio(user, date, botMessageId, chatId, sender);
    }

    public void atualizarRelatorioAposExclusao(UserTelegram user, LocalDate date, int botMessageId, long chatId, BotActionSender sender) {
        try {
            DailyReportDto report = reportService.getReportForDate(user, date);
            boolean hasItems = (report.getMeals() != null && !report.getMeals().isEmpty())
                    || (report.getWorkouts() != null && !report.getWorkouts().isEmpty());
            InlineKeyboardMarkup markup = hasItems
                    ? keyboardFactory.criarBotoesGerenciarRelatorio(report, date)
                    : keyboardFactory.criarBotoesRelatorio(report, date);
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatDailyReport(report, date), markup);
        } catch (Exception e) {
            log.error("Erro ao atualizar relatório após exclusão para chatId={}", chatId, e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning("Item excluído, mas ocorreu um erro ao recarregar o relatório."));
        }
    }

    private void editarRelatorio(UserTelegram user, LocalDate date, int botMessageId, long chatId, BotActionSender sender, boolean manageMode, String fallbackError) {
        try {
            DailyReportDto report = reportService.getReportForDate(user, date);
            InlineKeyboardMarkup markup = manageMode
                    ? keyboardFactory.criarBotoesGerenciarRelatorio(report, date)
                    : keyboardFactory.criarBotoesRelatorio(report, date);
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatDailyReport(report, date), markup);
        } catch (Exception e) {
            log.error("Erro ao atualizar relatório de {} para chatId={}", date, chatId, e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(fallbackError));
        }
    }

    private LocalDate parseDate(String input) {
        try {
            if (input.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return LocalDate.parse(input, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else if (input.matches("\\d{2}/\\d{2}")) {
                String fullDate = input + "/" + LocalDate.now(appZoneId).getYear();
                return LocalDate.parse(fullDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
