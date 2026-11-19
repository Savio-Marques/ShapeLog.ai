package com.bot.telegram.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StateManagerTest {

    private StateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new StateManager();
    }

    @Test
    void putState_deveArmazenarEstado() {
        stateManager.putState(123L, "AWAITING_MEAL");
        assertEquals("AWAITING_MEAL", stateManager.getState(123L));
    }

    @Test
    void removeState_deveRemoverEstado() {
        stateManager.putState(123L, "AWAITING_MEAL");
        stateManager.removeState(123L);
        assertNull(stateManager.getState(123L));
    }

    @Test
    void getState_estadoInexistente_deveRetornarNull() {
        assertNull(stateManager.getState(999L));
    }

    @Test
    void limparEstadosExpirados_deveRemoverApenasExpirados() {
        stateManager.putState(123L, "AWAITING_MEAL");
        stateManager.limparEstadosExpirados();
        // O estado recente não deve ser limpo
        assertEquals("AWAITING_MEAL", stateManager.getState(123L));
    }

    @Test
    void putAndConsumePendingInput_tokenValido_deveRetornarUmaUnicaVez() {
        byte[] audio = new byte[]{10, 20, 30};
        String token = stateManager.putPendingInput(123L, null, audio, 55);

        assertNotNull(token);
        assertEquals(8, token.length());

        PendingInput first = stateManager.consumePendingInput(123L, token);
        assertNotNull(first);
        assertEquals(token, first.token());
        assertArrayEquals(audio, first.audioBytes());
        assertEquals(55, first.userMessageId());
        assertTrue(first.isVoice());

        PendingInput second = stateManager.consumePendingInput(123L, token);
        assertNull(second);
    }

    @Test
    void consumePendingInput_tokenInvalido_naoDeveConsumirEntradaAtual() {
        String token = stateManager.putPendingInput(123L, "2 ovos", null, 10);

        assertNull(stateManager.consumePendingInput(123L, "errado00"));
        PendingInput valid = stateManager.consumePendingInput(123L, token);
        assertNotNull(valid);
        assertEquals("2 ovos", valid.text());
        assertFalse(valid.isVoice());
    }

    @Test
    void putPendingInput_novaEntradaDeveSubstituirAnterior() {
        String oldToken = stateManager.putPendingInput(123L, "pão", null, 1);
        String newToken = stateManager.putPendingInput(123L, "arroz", null, 2);

        assertNull(stateManager.consumePendingInput(123L, oldToken));
        PendingInput latest = stateManager.consumePendingInput(123L, newToken);
        assertNotNull(latest);
        assertEquals("arroz", latest.text());
    }

    @Test
    @SuppressWarnings("unchecked")
    void limparEstadosExpirados_deveLimparPendingInputsExpirados() throws Exception {
        String token = stateManager.putPendingInput(123L, "antigo", null, 1);
        Field field = StateManager.class.getDeclaredField("pendingInputs");
        field.setAccessible(true);
        Map<Long, PendingInput> map = (Map<Long, PendingInput>) field.get(stateManager);
        long expiredTs = System.currentTimeMillis() - (16 * 60 * 1000L);
        map.put(123L, new PendingInput(token, "antigo", null, 1, expiredTs));

        stateManager.limparEstadosExpirados();
        assertNull(stateManager.consumePendingInput(123L, token));
    }
}
