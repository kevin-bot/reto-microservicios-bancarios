package com.banco.cuentas.shared.exception;

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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único punto de traducción de excepciones a respuestas HTTP con formato RFC 7807 (ProblemDetail).
 * Las excepciones de negocio traen su propio título, así que agregar una nueva no exige tocar
 * este manejador.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TITULO_DATOS_INVALIDOS = "Datos inválidos";
    private static final String TITULO_PARAMETRO_INVALIDO = "Parámetro inválido";
    private static final String SALDO_NO_DISPONIBLE = "Saldo no disponible";
    private static final String RESTRICCION_NUMERO_CUENTA = "uk_cuenta_numero";
    private static final String RESTRICCION_SALDO = "ck_cuenta_saldo";

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
        String parametro = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : ex.getPropertyName();
        String motivo = causaRaiz(ex) instanceof IllegalArgumentException causa && causa.getMessage() != null
                ? ": " + causa.getMessage() : "";
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, TITULO_PARAMETRO_INVALIDO,
                "El parámetro '" + parametro + "' tiene un valor inválido" + motivo));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(@NonNull MissingServletRequestParameterException ex,
                                                                          @NonNull HttpHeaders headers,
                                                                          @NonNull HttpStatusCode status,
                                                                          @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, TITULO_PARAMETRO_INVALIDO,
                "El parámetro '" + ex.getParameterName() + "' es obligatorio"));
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

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, ex.getTitulo(), ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail manejarConflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, ex.getTitulo(), ex.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, ex.getTitulo(), ex.getMessage());
    }

    /**
     * Última línea de defensa: las restricciones de la base de datos protegen las mismas reglas
     * que el dominio (por ejemplo, ante una carrera entre dos altas con el mismo número).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException ex) {
        if (violaRestriccion(ex, RESTRICCION_NUMERO_CUENTA)) {
            return problema(HttpStatus.CONFLICT, "Número de cuenta duplicado",
                    "Ya existe una cuenta con el número indicado");
        }
        if (violaRestriccion(ex, RESTRICCION_SALDO)) {
            return problema(HttpStatus.UNPROCESSABLE_ENTITY, SALDO_NO_DISPONIBLE, SALDO_NO_DISPONIBLE);
        }
        return errorInterno(ex);
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

    private static Throwable causaRaiz(Throwable ex) {
        Throwable causa = ex;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa;
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
