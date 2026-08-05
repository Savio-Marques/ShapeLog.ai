package com.bot.telegram.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
