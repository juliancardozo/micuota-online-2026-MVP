package com.micuota.mvp.domain;

/** Como se cobro una operacion. MERCADOPAGO se concilia solo; el resto lo marca el profesional. */
public enum PaidMethod {
    MERCADOPAGO,
    TRANSFER,
    CASH,
    GIRO,
    OTHER
}
