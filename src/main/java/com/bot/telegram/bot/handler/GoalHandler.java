package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.GeminiService;
import com.bot.telegram.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
public class GoalHandler {

    private static final Logger log = LoggerFactory.getLogger(GoalHandler.class);
    private static final int DEFAULT_CALORIES = 2000;
    private static final int DEFAULT_PROTEIN = 150;
    private static final int DEFAULT_CARBS = 200;
    private static final int DEFAULT_FAT = 60;

    private final UserService userService;
    private final GeminiService geminiService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;

    public GoalHandler(UserService userService, GeminiService geminiService,
                       MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this.userService = userService;
        this.geminiService = geminiService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    public void mostrarMetasAtuais(UserTelegram user, long chatId, BotActionSender sender) {
        sender.enviarMensagemMarkdown(chatId, messageFormatter.formatCurrentGoals(user), keyboardFactory.criarBotaoCancelarEstado());
    }

    public void salvarMetasDiretas(UserTelegram user, int cal, int prot, int carb, int fat, long chatId, BotActionSender sender) {
        if (cal <= 0 || prot <= 0 || carb <= 0 || fat <= 0) {
            sender.enviarMensagem(chatId, "⚠️ Os valores das metas devem ser números maiores que zero!\n\n💡 Exemplo: /meta 2000 150 200 60");
            return;
        }
        userService.updateGoals(user, cal, prot, carb, fat);
        sender.enviarMensagemMarkdown(chatId, messageFormatter.formatGoalsUpdated(cal, prot, carb, fat));
    }

    public void sugerirMetasPorIA(UserTelegram user, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, "Calculando metas... ⏳");
        try {
            int curCal = (user != null && user.getTargetCalories() != null && user.getTargetCalories() > 0) ? user.getTargetCalories() : DEFAULT_CALORIES;
            int curProt = (user != null && user.getTargetProtein() != null && user.getTargetProtein() > 0) ? user.getTargetProtein() : DEFAULT_PROTEIN;
            int curCarb = (user != null && user.getTargetCarbs() != null && user.getTargetCarbs() > 0) ? user.getTargetCarbs() : DEFAULT_CARBS;
            int curFat = (user != null && user.getTargetFat() != null && user.getTargetFat() > 0) ? user.getTargetFat() : DEFAULT_FAT;

            GoalDto dto = geminiService.parseGoal(text, audioBytes, curCal, curProt, curCarb, curFat);
            if (dto == null || dto.getCalories() == null || dto.getProtein() == null
                    || dto.getCarbs() == null || dto.getFat() == null
                    || dto.getCalories() <= 0 || dto.getProtein() <= 0
                    || dto.getCarbs() <= 0 || dto.getFat() <= 0) {
                throw new IllegalArgumentException("Não foi possível calcular metas válidas. Informe seu peso, objetivo ou os valores desejados!");
            }

            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatGoalSuggestion(dto), keyboardFactory.criarBotoesConfirmacaoMeta(dto));
        } catch (Exception e) {
            log.error("Erro ao calcular sugestão de metas para chatId={}", chatId, e);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Não foi possível calcular as metas no momento. Tente informar os 4 números (ex: /meta 2000 150 200 60).");
        }
    }

    public void confirmarMetasSugeridas(UserTelegram user, int cal, int prot, int carb, int fat, int botMessageId, long chatId, BotActionSender sender) {
        if (cal <= 0 || prot <= 0 || carb <= 0 || fat <= 0) {
            sender.enviarMensagem(chatId, "⚠️ Valores de metas inválidos.");
            return;
        }
        userService.updateGoals(user, cal, prot, carb, fat);
        sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatGoalsUpdated(cal, prot, carb, fat), null);
    }
}
