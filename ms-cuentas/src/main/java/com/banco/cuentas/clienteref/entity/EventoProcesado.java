package com.banco.cuentas.clienteref.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de eventos ya aplicados (patrón idempotent consumer). La PK impide procesar dos veces
 * el mismo evento aunque lleguen en paralelo.
 */
@Entity
@Table(name = "evento_procesado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcesado {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    private EventoProcesado(UUID eventId, Instant procesadoEn) {
        this.eventId = eventId;
        this.procesadoEn = procesadoEn;
    }

    public static EventoProcesado registrar(UUID eventId, Instant procesadoEn) {
        return new EventoProcesado(eventId, procesadoEn);
    }
}
