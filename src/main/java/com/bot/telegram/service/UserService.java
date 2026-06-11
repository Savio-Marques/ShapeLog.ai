package com.bot.telegram.service;

import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.UserTelegramRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserTelegramRepository userRepository;

    public UserService(UserTelegramRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserTelegram getOrCreateUser(Long id, String username, String firstName) {
        return userRepository.findById(id).orElseGet(() -> {
            UserTelegram newUser = UserTelegram.builder()
                    .id(id)
                    .username(username)
                    .firstName(firstName)
                    .registeredAt(LocalDateTime.now())
                    .targetCalories(2000)
                    .targetProtein(150)
                    .targetCarbs(200)
                    .targetFat(60)
                    .build();
            return userRepository.save(newUser);
        });
    }

    public UserTelegram updateGoals(UserTelegram user, int calories, int protein, int carbs, int fat) {
        user.setTargetCalories(calories);
        user.setTargetProtein(protein);
        user.setTargetCarbs(carbs);
        user.setTargetFat(fat);
        return userRepository.save(user);
    }
}
