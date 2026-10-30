package com.banco.clientes.shared.exception;

import com.banco.clientes.cliente.exception.ClienteNoEncontradoException;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único punto de traducción de excepciones a respuestas HTTP con formato RFC 7807 (ProblemDetail).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TITULO_DATOS_INVALIDOS = "Datos inválidos";
    private static final String TITULO_PARAMETRO_INVALIDO = "Parámetro inválido";
    private static final String TITULO_IDENTIFICACION_DUPLICADA = "Identificación duplicada";
    private static final String RESTRICCION_IDENTIFICACION = "uk_persona_identificacion";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errores.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(datosInvalidos(errores));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(resultado -> errores.putIfAbsent(
                resultado.getMethodParameter().getParameterName(),
                resultado.getResolvableErrors().getFirst().getDefaultMessage()));
        return ResponseEntity.badRequest().body(datosInvalidos(errores));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Solicitud mal formada",
                "El cuerpo de la solicitud no es legible o contiene campos no permitidos"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(@NonNull TypeMismatchException ex,
                                                        @NonNull HttpHeaders headers,
                                                        @NonNull HttpStatusCode status,
                                                        @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, TITULO_PARAMETRO_INVALIDO,
                "El parámetro '" + ex.getPropertyName() + "' tiene un valor inválido"));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail manejarOrdenInvalido(PropertyReferenceException ex) {
        return problema(HttpStatus.BAD_REQUEST, TITULO_PARAMETRO_INVALIDO,
                "No se puede ordenar por '" + ex.getPropertyName() + "'");
    }

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail manejarValidacionDominio(DomainValidationException ex) {
        return problema(HttpStatus.BAD_REQUEST, TITULO_DATOS_INVALIDOS, ex.getMessage());
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ProblemDetail manejarClienteNoEncontrado(ClienteNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Cliente no encontrado", ex.getMessage());
    }

    @ExceptionHandler(IdentificacionDuplicadaException.class)
    public ProblemDetail manejarIdentificacionDuplicada(IdentificacionDuplicadaException ex) {
        return problema(HttpStatus.CONFLICT, TITULO_IDENTIFICACION_DUPLICADA, ex.getMessage());
    }

    /**
     * Cubre la carrera entre dos altas simultáneas con la misma identificación: ambas pasan la
     * verificación previa y la base de datos rechaza la segunda. Cualquier otra violación de
     * integridad es un error inesperado.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException ex) {
        if (violaRestriccion(ex, RESTRICCION_IDENTIFICACION)) {
            return problema(HttpStatus.CONFLICT, TITULO_IDENTIFICACION_DUPLICADA,
                    "Ya existe un cliente con la identificación indicada");
        }
        return errorInterno(ex);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex) {
        return errorInterno(ex);
    }

    private ProblemDetail errorInterno(Exception ex) {
        log.error("Error inesperado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intente nuevamente más tarde");
    }

    private static ProblemDetail datosInvalidos(Map<String, String> errores) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, TITULO_DATOS_INVALIDOS,
                "La solicitud contiene campos inválidos");
        problema.setProperty("errores", errores);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }

    private static boolean violaRestriccion(Throwable ex, String restriccion) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacion
                    && restriccion.equalsIgnoreCase(violacion.getConstraintName())) {
                return true;
            }
        }
        return ex.getMessage() != null && ex.getMessage().contains(restriccion);
    }
}
