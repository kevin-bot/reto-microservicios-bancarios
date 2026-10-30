package com.banco.clientes.cliente.service;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.entity.Cliente;
import com.banco.clientes.cliente.entity.Genero;
import com.banco.clientes.cliente.event.ClienteEventoDominio;
import com.banco.clientes.cliente.event.TipoEventoCliente;
import com.banco.clientes.cliente.exception.ClienteNoEncontradoException;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import com.banco.clientes.cliente.mapper.ClienteMapper;
import com.banco.clientes.cliente.repository.ClienteRepository;
import com.banco.clientes.shared.dto.PaginaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de clientes")
class ClienteServiceImplTest {

    private static final String HASH = "$2a$10$hashGenerado";

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ClienteMapper clienteMapper = Mappers.getMapper(ClienteMapper.class);

    @InjectMocks
    private ClienteServiceImpl clienteService;

    private static ClienteCreateRequest solicitudJoseLema(Boolean estado) {
        return new ClienteCreateRequest("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                "Otavalo sn y principal", "098254785", "1234", estado);
    }

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("Guarda el cliente con la contraseña hasheada y publica CLIENTE_CREADO")
        void crear_cuandoDatosValidos_debeGuardarConHashYPublicarEvento() {
            // Given
            when(clienteRepository.existsByIdentificacion("1712345678")).thenReturn(false);
            when(passwordEncoder.encode("1234")).thenReturn(HASH);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            // When
            ClienteResponse respuesta = clienteService.crear(solicitudJoseLema(true));

            // Then
            ArgumentCaptor<Cliente> clienteGuardado = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(clienteGuardado.capture());
            assertThat(clienteGuardado.getValue().getContrasena()).isEqualTo(HASH).isNotEqualTo("1234");

            ArgumentCaptor<ClienteEventoDominio> evento = ArgumentCaptor.forClass(ClienteEventoDominio.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().evento().tipo()).isEqualTo(TipoEventoCliente.CLIENTE_CREADO);
            assertThat(evento.getValue().evento().clienteId()).isEqualTo(respuesta.clienteId());

            assertThat(respuesta.nombre()).isEqualTo("Jose Lema");
            assertThat(respuesta.estado()).isTrue();
        }

        @Test
        @DisplayName("Si no se envía estado, el cliente queda activo")
        void crear_cuandoEstadoNulo_debeCrearClienteActivo() {
            // Given
            when(clienteRepository.existsByIdentificacion("1712345678")).thenReturn(false);
            when(passwordEncoder.encode("1234")).thenReturn(HASH);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            // When
            ClienteResponse respuesta = clienteService.crear(solicitudJoseLema(null));

            // Then
            assertThat(respuesta.estado()).isTrue();
        }

        @Test
        @DisplayName("Rechaza una identificación ya registrada sin guardar ni publicar")
        void crear_cuandoIdentificacionDuplicada_debeLanzarExcepcionSinGuardarNiPublicar() {
            // Given
            when(clienteRepository.existsByIdentificacion("1712345678")).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> clienteService.crear(solicitudJoseLema(true)))
                    .isInstanceOf(IdentificacionDuplicadaException.class)
                    .hasMessageContaining("1712345678");
            verify(clienteRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        @DisplayName("Devuelve el cliente cuando existe y no está eliminado")
        void obtener_cuandoClienteExiste_debeDevolverRespuesta() {
            // Given
            Cliente cliente = clienteExistente();
            when(clienteRepository.findByClienteIdAndEliminadoFalse(cliente.getClienteId()))
                    .thenReturn(Optional.of(cliente));

            // When
            ClienteResponse respuesta = clienteService.obtener(cliente.getClienteId());

            // Then
            assertThat(respuesta.clienteId()).isEqualTo(cliente.getClienteId());
            assertThat(respuesta.identificacion()).isEqualTo("1712345678");
        }

        @Test
        @DisplayName("Lanza ClienteNoEncontradoException si no existe o está eliminado")
        void obtener_cuandoClienteNoExiste_debeLanzarClienteNoEncontrado() {
            // Given
            UUID clienteId = UUID.randomUUID();
            when(clienteRepository.findByClienteIdAndEliminadoFalse(clienteId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> clienteService.obtener(clienteId))
                    .isInstanceOf(ClienteNoEncontradoException.class)
                    .hasMessageContaining(clienteId.toString());
        }
    }

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("Consulta con filtros y devuelve una página de respuestas")
        @SuppressWarnings("unchecked")
        void listar_cuandoHayClientes_debeDevolverPaginaResponse() {
            // Given
            PageRequest pagina = PageRequest.of(0, 20);
            Cliente cliente = clienteExistente();
            when(clienteRepository.findAll(any(Specification.class), eq(pagina)))
                    .thenReturn(new PageImpl<>(List.of(cliente), pagina, 1));

            // When
            PaginaResponse<ClienteResponse> resultado = clienteService.listar(true, "1712345678", pagina);

            // Then
            assertThat(resultado.contenido()).extracting(ClienteResponse::clienteId)
                    .containsExactly(cliente.getClienteId());
            assertThat(resultado.pagina()).isZero();
            assertThat(resultado.tamano()).isEqualTo(20);
            assertThat(resultado.totalElementos()).isEqualTo(1);
            assertThat(resultado.totalPaginas()).isEqualTo(1);
        }
    }

    private static Cliente clienteExistente() {
        return Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                "Otavalo sn y principal", "098254785", HASH, true);
    }
}
