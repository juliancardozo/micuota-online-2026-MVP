package com.micuota.mvp.web;

import com.micuota.mvp.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/platform")
@Tag(name = "Plataforma", description = "Estado publico de la plataforma")
public class PublicPlatformController {

    private final PlatformSettingsService platformSettingsService;

    public PublicPlatformController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping
    @Operation(summary = "Modo de la plataforma", description = "testMode=true: modo prueba, ningun cobro mueve plata real.")
    public Map<String, Boolean> platform() {
        return Map.of("testMode", !platformSettingsService.isLive());
    }
}
