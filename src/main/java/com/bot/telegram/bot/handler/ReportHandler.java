package com.bot.telegram.bot.handler;
import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReportHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportHandler.class);
    private final ReportService reportService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;

    public ReportHandler(ReportService reportService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this.reportService = reportService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    public void gerarRelatorio(UserTelegram user, String arg, long chatId, BotActionSender sender) {
        try {
            java.time.LocalDate date = java.time.LocalDate.now();
            if (!arg.isEmpty()) {
                if ("ontem".equalsIgnoreCase(arg)) {
                    date = date.minusDays(1);
                } else {
                    date = parseDate(arg);
                    if (date == null) {
                        sender.enviarMensagem(chatId, "⚠️ Formato de data inválido! Use /relatorio, /relatorio ontem, ou /relatorio DD/MM/AAAA.");
                        return;
                    }
                }
            }
            DailyReportDto report = reportService.getReportForDate(user, date);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatDailyReport(report, date), keyboardFactory.criarBotoesRelatorio(report));
        } catch (Exception e) {
            log.error("Erro ao gerar relatório para chatId={}", chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao gerar o relatório.");
        }
    }

    private java.time.LocalDate parseDate(String input) {
        try {
            if (input.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return java.time.LocalDate.parse(input, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else if (input.matches("\\d{2}/\\d{2}")) {
                String fullDate = input + "/" + java.time.LocalDate.now().getYear();
                return java.time.LocalDate.parse(fullDate, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
        }
        return null;
    }
}
