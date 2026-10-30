package com.banco.cuentas.clienteref.repository;

import com.banco.cuentas.clienteref.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {
}
