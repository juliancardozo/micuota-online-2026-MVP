package com.micuota.mvp.service;

/** Se intento mover plata real con la plataforma en modo prueba. El mensaje es para el profesional. */
public class TestModeViolationException extends IllegalArgumentException {

    public TestModeViolationException(String message) {
        super(message);
    }
}
