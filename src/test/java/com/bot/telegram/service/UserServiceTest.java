package com.bot.telegram.service;

import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.UserTelegramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserTelegramRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserTelegramRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    void getOrCreateUser_novoUsuario_deveCriarComMetasPadraoEApprovedFalse() {
        when(userRepository.findById(100L)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserTelegram.class))).thenAnswer(inv -> inv.getArgument(0));

        UserTelegram user = userService.getOrCreateUser(100L, "savio", "Sávio");
        assertNotNull(user);
        assertEquals(100L, user.getId());
        assertEquals("savio", user.getUsername());
        assertEquals("Sávio", user.getFirstName());
        assertEquals(2000, user.getTargetCalories());
        assertEquals(150, user.getTargetProtein());
        assertEquals(200, user.getTargetCarbs());
        assertEquals(60, user.getTargetFat());
        assertFalse(user.getApproved());
    }

    @Test
    void getOrCreateUser_usuarioExistente_deveRetornarSemDuplicar() {
        UserTelegram existing = UserTelegram.builder().id(101L).username("user1").firstName("User One").approved(true).build();
        when(userRepository.findById(101L)).thenReturn(Optional.of(existing));

        UserTelegram result = userService.getOrCreateUser(101L, "user1", "User One");
        assertEquals(101L, result.getId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrCreateUser_usernameAlterado_deveAtualizar() {
        UserTelegram existing = UserTelegram.builder().id(102L).username("old_name").firstName("User Two").approved(true).build();
        when(userRepository.findById(102L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserTelegram.class))).thenAnswer(inv -> inv.getArgument(0));

        UserTelegram updatedUser = userService.getOrCreateUser(102L, "new_name", "User Two");
        assertEquals("new_name", updatedUser.getUsername());
    }

    @Test
    void updateGoals_deveAtualizarTodosOsCampos() {
        UserTelegram user = UserTelegram.builder().id(103L).build();
        when(userRepository.save(any(UserTelegram.class))).thenAnswer(inv -> inv.getArgument(0));

        UserTelegram updated = userService.updateGoals(user, 2500, 180, 250, 70);
        assertEquals(2500, updated.getTargetCalories());
        assertEquals(180, updated.getTargetProtein());
        assertEquals(250, updated.getTargetCarbs());
        assertEquals(70, updated.getTargetFat());
    }

    @Test
    void approveUser_deveSetarApprovedTrue() {
        UserTelegram user = UserTelegram.builder().id(104L).approved(false).build();
        when(userRepository.findById(104L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserTelegram.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<UserTelegram> result = userService.approveUser(104L);
        assertTrue(result.isPresent());
        assertTrue(result.get().getApproved());
    }

    @Test
    void revokeUser_deveSetarApprovedFalse() {
        UserTelegram user = UserTelegram.builder().id(105L).approved(true).build();
        when(userRepository.findById(105L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserTelegram.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<UserTelegram> result = userService.revokeUser(105L);
        assertTrue(result.isPresent());
        assertFalse(result.get().getApproved());
    }

    @Test
    void listApprovedUsers_deveRetornarApenasAprovados() {
        UserTelegram u1 = UserTelegram.builder().id(106L).approved(true).build();
        when(userRepository.findByApprovedTrue()).thenReturn(List.of(u1));

        List<UserTelegram> approvedList = userService.listApprovedUsers();
        assertEquals(1, approvedList.size());
    }
}
