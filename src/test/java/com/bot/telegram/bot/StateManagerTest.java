package com.bot.telegram.bot;

import com.bot.telegram.model.PendingInputEntity;
import com.bot.telegram.model.UserStateEntity;
import com.bot.telegram.repository.PendingInputRepository;
import com.bot.telegram.repository.UserStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        assertEquals("AWAITING_MEAL", stateManager.getState(123L));
    }

    @Test
    void putAndConsumePendingInput_tokenValido_deveRetornarUmaUnicaVez() {
        byte[] audio = new byte[]{10, 20, 30};
        String token = stateManager.putPendingInput(123L, null, audio, 55);

        assertNotNull(token);
        assertEquals(12, token.length());

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
    void putPendingInput_multiplasEntradasMesmoChat_devemCoexistirEIsolarPorChat() {
        String firstToken = stateManager.putPendingInput(123L, "pão com ovo", null, 1);
        String secondToken = stateManager.putPendingInput(123L, "arroz e frango", null, 2);
        String audioToken = stateManager.putPendingInput(123L, null, new byte[]{7, 8}, 3);

        // Outro usuário não consegue consumir token alheio
        assertNull(stateManager.consumePendingInput(999L, firstToken));

        // Todas as entradas do próprio chat continuam válidas na ordem que o usuário clicar
        PendingInput second = stateManager.consumePendingInput(123L, secondToken);
        PendingInput first = stateManager.consumePendingInput(123L, firstToken);
        PendingInput audio = stateManager.consumePendingInput(123L, audioToken);

        assertNotNull(second);
        assertEquals("arroz e frango", second.text());
        assertNotNull(first);
        assertEquals("pão com ovo", first.text());
        assertNotNull(audio);
        assertTrue(audio.isVoice());
    }

    @Test
    @SuppressWarnings("unchecked")
    void limparEstadosExpirados_deveLimparPendingInputsExpirados() throws Exception {
        String token = stateManager.putPendingInput(123L, "antigo", null, 1);
        Field field = StateManager.class.getDeclaredField("pendingInputs");
        field.setAccessible(true);
        Map<String, StateManager.ScopedPendingInput> map =
                (Map<String, StateManager.ScopedPendingInput>) field.get(stateManager);
        long expiredTs = System.currentTimeMillis() - (16 * 60 * 1000L);
        map.put(token, new StateManager.ScopedPendingInput(123L, new PendingInput(token, "antigo", null, 1, expiredTs)));

        stateManager.limparEstadosExpirados();
        assertNull(stateManager.consumePendingInput(123L, token));
    }

    @Test
    void comRepositorios_devePersistirEstadoEEntradaTextoNoBanco() {
        UserStateRepository stateRepo = mock(UserStateRepository.class);
        PendingInputRepository pendingRepo = mock(PendingInputRepository.class);
        StateManager persistentManager = new StateManager(stateRepo, pendingRepo);

        persistentManager.putState(500L, "AWAITING_MEAL");
        verify(stateRepo).save(argThat(e -> e.getChatId().equals(500L) && "AWAITING_MEAL".equals(e.getState())));

        when(stateRepo.findById(500L)).thenReturn(Optional.of(
                UserStateEntity.builder().chatId(500L).state("AWAITING_MEAL").expiresAt(Instant.now().plusSeconds(60)).build()
        ));
        assertEquals("AWAITING_MEAL", persistentManager.getState(500L));

        String token = persistentManager.putPendingInput(500L, "2 ovos", null, 33);
        verify(pendingRepo).save(argThat(p -> p.getChatId().equals(500L) && token.equals(p.getToken()) && "2 ovos".equals(p.getTextContent())));

        PendingInputEntity entity = PendingInputEntity.builder()
                .chatId(500L)
                .token(token)
                .textContent("2 ovos")
                .userMessageId(33)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(pendingRepo.findByTokenAndChatId(token, 500L)).thenReturn(Optional.of(entity));

        PendingInput consumed = persistentManager.consumePendingInput(500L, token);
        assertNotNull(consumed);
        assertEquals("2 ovos", consumed.text());
        assertEquals(33, consumed.userMessageId());
        verify(pendingRepo).delete(entity);
    }

    @Test
    void comRepositorios_limparEstadosExpirados_deveDeletarNoBanco() {
        UserStateRepository stateRepo = mock(UserStateRepository.class);
        PendingInputRepository pendingRepo = mock(PendingInputRepository.class);
        StateManager persistentManager = new StateManager(stateRepo, pendingRepo);

        persistentManager.limparEstadosExpirados();
        verify(stateRepo).deleteByExpiresAtBefore(any(Instant.class));
        verify(pendingRepo).deleteByExpiresAtBefore(any(Instant.class));
    }
}
