package com.banco.cuentas.clienteref.repository;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClienteRefRepository extends JpaRepository<ClienteRef, UUID> {
}
