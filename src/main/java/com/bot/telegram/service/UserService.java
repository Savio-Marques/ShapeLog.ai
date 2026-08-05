package com.bot.telegram.service;

import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.UserTelegramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserTelegramRepository userRepository;

    public UserService(UserTelegramRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserTelegram getOrCreateUser(Long id, String username, String firstName) {
        return userRepository.findById(id).map(existingUser -> {
            boolean changed = false;
            if ((username != null && !username.equals(existingUser.getUsername())) || (username == null && existingUser.getUsername() != null)) {
                existingUser.setUsername(username);
                changed = true;
            }
            if ((firstName != null && !firstName.equals(existingUser.getFirstName())) || (firstName == null && existingUser.getFirstName() != null)) {
                existingUser.setFirstName(firstName);
                changed = true;
            }
            if (existingUser.getApproved() == null) {
                existingUser.setApproved(false);
                changed = true;
            }
            if (changed) {
                return userRepository.save(existingUser);
            }
            return existingUser;
        }).orElseGet(() -> {
            UserTelegram newUser = UserTelegram.builder()
                    .id(id)
                    .username(username)
                    .firstName(firstName)
                    .registeredAt(LocalDateTime.now())
                    .targetCalories(2000)
                    .targetProtein(150)
                    .targetCarbs(200)
                    .targetFat(60)
                    .approved(false)
                    .build();
            return userRepository.save(newUser);
        });
    }

    @Transactional
    public UserTelegram updateGoals(UserTelegram user, int calories, int protein, int carbs, int fat) {
        user.setTargetCalories(calories);
        user.setTargetProtein(protein);
        user.setTargetCarbs(carbs);
        user.setTargetFat(fat);
        return userRepository.save(user);
    }

    @Transactional
    public Optional<UserTelegram> approveUser(Long userId) {
        return userRepository.findById(userId).map(user -> {
            user.setApproved(true);
            return userRepository.save(user);
        });
    }

    @Transactional
    public Optional<UserTelegram> revokeUser(Long userId) {
        return userRepository.findById(userId).map(user -> {
            user.setApproved(false);
            return userRepository.save(user);
        });
    }

    public List<UserTelegram> listApprovedUsers() {
        return userRepository.findByApprovedTrue();
    }
}
