package com.banco.cuentas.reporte.controller;

import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.reporte.service.ReporteService;
import com.banco.cuentas.shared.fecha.RangoFechas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Estado de cuenta")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping
    @Operation(summary = "Estado de cuenta de un cliente en un rango de fechas (F4)")
    @ApiResponse(responseCode = "200", description = "Estado de cuenta")
    @ApiResponse(responseCode = "400", description = "Parámetro inválido o faltante")
    @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    public ReporteResponse generar(
            @Parameter(description = "Fechas de inicio y fin ISO, ambas incluidas, separadas por coma (máximo 1 año)",
                    example = "2022-02-01,2022-02-10")
            @RequestParam("fecha") RangoFechas rango,
            @Parameter(description = "clienteId del cliente")
            @RequestParam("cliente") UUID clienteId) {
        return reporteService.generar(clienteId, rango);
    }
}
