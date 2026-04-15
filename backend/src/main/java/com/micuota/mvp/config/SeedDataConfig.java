package com.micuota.mvp.config;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, TeacherProfileRepository teacherProfileRepository) {
        return args -> {
            if (userRepository.findByEmail("teacher@micuota.online").isPresent()) {
                return;
            }

            User teacherUser = new User();
            teacherUser.setEmail("teacher@micuota.online");
            teacherUser.setPasswordHash("{noop}demo");
            teacherUser.setFullName("Profe Demo");
            teacherUser.setRole(UserRole.TEACHER);
            teacherUser = userRepository.save(teacherUser);

            TeacherProfile teacherProfile = new TeacherProfile();
            teacherProfile.setUser(teacherUser);
            teacherProfile.setDisplayName("Profe Demo");
            teacherProfile.setMpAccessToken("TEST-MP-TOKEN");
            teacherProfile.setPrometeoApiKey("TEST-PROMETEO-KEY");
            teacherProfile.setWooCommerceApiKey("TEST-WC-KEY");
            teacherProfileRepository.save(teacherProfile);
        };
    }
}
