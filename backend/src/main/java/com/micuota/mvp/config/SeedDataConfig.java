package com.micuota.mvp.config;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedUsers(
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        TenantRepository tenantRepository
    ) {
        return args -> {
            if (userRepository.findByEmail("teacher@micuota.online").isPresent()) {
                return;
            }

            Tenant tenant = tenantRepository.findBySlug("demo-academia")
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setName("Demo Academia");
                    t.setSlug("demo-academia");
                    t.setCreatedAt(OffsetDateTime.now());
                    return tenantRepository.save(t);
                });

            User teacherUser = new User();
            teacherUser.setEmail("teacher@micuota.online");
            teacherUser.setPasswordHash("demo");
            teacherUser.setFullName("Profe Demo");
            teacherUser.setRole(UserRole.TEACHER);
            teacherUser.setTenant(tenant);
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
