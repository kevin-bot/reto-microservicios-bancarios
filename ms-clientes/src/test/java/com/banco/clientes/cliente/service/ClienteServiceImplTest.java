package com.banco.clientes.cliente.service;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.entity.Cliente;
import com.banco.clientes.cliente.entity.Genero;
import com.banco.clientes.cliente.event.ClienteEventoDominio;
import com.banco.clientes.cliente.event.TipoEventoCliente;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import com.banco.clientes.cliente.mapper.ClienteMapper;
import com.banco.clientes.cliente.repository.ClienteRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
