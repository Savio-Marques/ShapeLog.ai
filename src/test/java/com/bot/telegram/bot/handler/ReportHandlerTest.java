package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportHandlerTest {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private ReportService reportService;
    private MessageFormatter messageFormatter;
    private InlineKeyboardFactory keyboardFactory;
    private BotActionSender sender;
    private ReportHandler reportHandler;
    private UserTelegram user;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        messageFormatter = new MessageFormatter(mock(IWorkoutService.class), APP_ZONE);
        keyboardFactory = new InlineKeyboardFactory(APP_ZONE);
        sender = mock(BotActionSender.class);
        reportHandler = new ReportHandler(reportService, messageFormatter, keyboardFactory, APP_ZONE);

        user = UserTelegram.builder()
                .id(100L)
                .firstName("Sávio")
                .build();
    }

    @Test
    void gerarRelatorio_dataFuturaExplicita_deveRejeitarSemConsultarBanco() {
        reportHandler.gerarRelatorio(user, "31/12/2030", 100L, sender);

        verify(sender).enviarMensagemMarkdown(eq(100L), contains("Data futura não permitida"));
        verifyNoInteractions(reportService);
    }

    @Test
    void gerarRelatorio_ddMmQueCairiaNoFuturoEsteAno_deveAjustarParaAnoAnterior() {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate futureThisYear = today.plusDays(5);
        String ddMm = futureThisYear.format(DateTimeFormatter.ofPattern("dd/MM"));
        LocalDate expectedPastYear = LocalDate.parse(
                ddMm + "/" + today.getYear(), DateTimeFormatter.ofPattern("dd/MM/yyyy")
        ).minusYears(1);

        DailyReportDto report = DailyReportDto.builder().user(user).meals(List.of()).workouts(List.of()).build();
        when(reportService.getReportForDate(user, expectedPastYear)).thenReturn(report);

        reportHandler.gerarRelatorio(user, ddMm, 100L, sender);

        verify(reportService).getReportForDate(user, expectedPastYear);
        verify(sender).enviarMensagemMarkdown(eq(100L), contains("Relatório"), any(InlineKeyboardMarkup.class));
    }
}
