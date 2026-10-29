package com.banco.clientes.cliente.entity;

import com.banco.clientes.cliente.exception.ClienteEliminadoException;
import com.banco.clientes.shared.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Entidad de dominio Cliente")
class ClienteTest {

    private static final String HASH = "$2a$10$hashDePrueba";

    private static Cliente clienteValido() {
        return Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                "Otavalo sn y principal", "098254785", HASH, true);
    }

    @Nested
    @DisplayName("Creación")
    class Creacion {

        @Test
        @DisplayName("Con datos válidos genera un clienteId y queda activo y no eliminado")
        void crear_cuandoDatosValidos_debeGenerarClienteIdYQuedarActivo() {
            // Given / When
            Cliente cliente = clienteValido();

            // Then
            assertThat(cliente.getClienteId()).isNotNull();
            assertThat(cliente.isEstado()).isTrue();
            assertThat(cliente.estaEliminado()).isFalse();
            assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
            assertThat(cliente.getContrasena()).isEqualTo(HASH);
        }

        @Test
        @DisplayName("Cada cliente creado recibe un clienteId distinto")
        void crear_cuandoSeCreanDosClientes_debeAsignarClienteIdDistintos() {
            // Given / When
            Cliente primero = clienteValido();
            Cliente segundo = clienteValido();

            // Then
            assertThat(primero.getClienteId()).isNotEqualTo(segundo.getClienteId());
        }

        @Test
        @DisplayName("Con estado false queda inactivo")
        void crear_cuandoEstadoFalse_debeQuedarInactivo() {
            // Given / When
            Cliente cliente = Cliente.crear("Juan Osorio", Genero.MASCULINO, 40, "1798765432",
                    "13 junio y Equinoccial", "098874587", HASH, false);

            // Then
            assertThat(cliente.isEstado()).isFalse();
        }

        @ParameterizedTest(name = "edad {0}")
        @ValueSource(ints = {17, 121, 0, -1})
        @DisplayName("Rechaza edades fuera del rango 18–120")
        void crear_cuandoEdadFueraDeRango_debeLanzarDomainValidationException(int edad) {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, edad, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("edad");
        }

        @ParameterizedTest(name = "edad {0}")
        @ValueSource(ints = {18, 120})
        @DisplayName("Acepta los límites del rango de edad")
        void crear_cuandoEdadEnLimites_debeCrearCliente(int edad) {
            Cliente cliente = Cliente.crear("Jose Lema", Genero.MASCULINO, edad, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true);

            assertThat(cliente.getEdad()).isEqualTo(edad);
        }

        @Test
        @DisplayName("Rechaza edad nula")
        void crear_cuandoEdadNula_debeLanzarDomainValidationException() {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, null, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("edad");
        }

        @ParameterizedTest(name = "identificación \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"ab-1", "1234", "123456789012345678901", "17 1234567"})
        @DisplayName("Rechaza identificaciones que no son alfanuméricas de 5 a 20 caracteres")
        void crear_cuandoIdentificacionInvalida_debeLanzarDomainValidationException(String identificacion) {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, 35, identificacion,
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("identificación");
        }

        @ParameterizedTest(name = "teléfono \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"123456", "09825478a", "1234567890123456"})
        @DisplayName("Rechaza teléfonos que no tienen solo dígitos (7–15)")
        void crear_cuandoTelefonoInvalido_debeLanzarDomainValidationException(String telefono) {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                    "Otavalo sn y principal", telefono, HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("teléfono");
        }

        @ParameterizedTest(name = "nombre \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Rechaza nombre vacío")
        void crear_cuandoNombreVacio_debeLanzarDomainValidationException(String nombre) {
            assertThatThrownBy(() -> Cliente.crear(nombre, Genero.MASCULINO, 35, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("nombre");
        }

        @Test
        @DisplayName("Rechaza nombre de más de 100 caracteres")
        void crear_cuandoNombreExcede100Caracteres_debeLanzarDomainValidationException() {
            String nombreLargo = "a".repeat(101);

            assertThatThrownBy(() -> Cliente.crear(nombreLargo, Genero.MASCULINO, 35, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("nombre");
        }

        @ParameterizedTest(name = "dirección \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Rechaza dirección vacía")
        void crear_cuandoDireccionVacia_debeLanzarDomainValidationException(String direccion) {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                    direccion, "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("dirección");
        }

        @Test
        @DisplayName("Rechaza dirección de más de 200 caracteres")
        void crear_cuandoDireccionExcede200Caracteres_debeLanzarDomainValidationException() {
            String direccionLarga = "a".repeat(201);

            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                    direccionLarga, "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("dirección");
        }

        @ParameterizedTest(name = "hash \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Rechaza hash de contraseña vacío")
        void crear_cuandoHashContrasenaVacio_debeLanzarDomainValidationException(String hash) {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", Genero.MASCULINO, 35, "1712345678",
                    "Otavalo sn y principal", "098254785", hash, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("contraseña");
        }

        @Test
        @DisplayName("Rechaza género nulo")
        void crear_cuandoGeneroNulo_debeLanzarDomainValidationException() {
            assertThatThrownBy(() -> Cliente.crear("Jose Lema", null, 35, "1712345678",
                    "Otavalo sn y principal", "098254785", HASH, true))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("género");
        }
    }

    @Nested
    @DisplayName("Estado y eliminación")
    class EstadoYEliminacion {

        @Test
        @DisplayName("desactivar deja el cliente inactivo y activar lo reactiva")
        void desactivarYActivar_cuandoClienteNoEliminado_debenCambiarEstado() {
            // Given
            Cliente cliente = clienteValido();

            // When
            cliente.desactivar();

            // Then
            assertThat(cliente.isEstado()).isFalse();

            // When
            cliente.activar();

            // Then
            assertThat(cliente.isEstado()).isTrue();
        }

        @Test
        @DisplayName("eliminar marca el cliente como eliminado e inactivo")
        void eliminar_cuandoClienteActivo_debeQuedarEliminadoEInactivo() {
            // Given
            Cliente cliente = clienteValido();

            // When
            cliente.eliminar();

            // Then
            assertThat(cliente.estaEliminado()).isTrue();
            assertThat(cliente.isEstado()).isFalse();
        }

        @Test
        @DisplayName("No se puede eliminar dos veces")
        void eliminar_cuandoYaEliminado_debeLanzarClienteEliminadoException() {
            // Given
            Cliente cliente = clienteValido();
            cliente.eliminar();

            // When / Then
            assertThatThrownBy(cliente::eliminar).isInstanceOf(ClienteEliminadoException.class);
        }

        @Test
        @DisplayName("No se puede activar un cliente eliminado")
        void activar_cuandoClienteEliminado_debeLanzarClienteEliminadoException() {
            // Given
            Cliente cliente = clienteValido();
            cliente.eliminar();

            // When / Then
            assertThatThrownBy(cliente::activar).isInstanceOf(ClienteEliminadoException.class);
        }

        @Test
        @DisplayName("No se puede desactivar un cliente eliminado")
        void desactivar_cuandoClienteEliminado_debeLanzarClienteEliminadoException() {
            // Given
            Cliente cliente = clienteValido();
            cliente.eliminar();

            // When / Then
            assertThatThrownBy(cliente::desactivar).isInstanceOf(ClienteEliminadoException.class);
        }
    }

    @Nested
    @DisplayName("Actualización de datos")
    class Actualizacion {

        @Test
        @DisplayName("cambiarContrasena reemplaza el hash")
        void cambiarContrasena_cuandoHashValido_debeReemplazarHash() {
            // Given
            Cliente cliente = clienteValido();

            // When
            cliente.cambiarContrasena("$2a$10$nuevoHash");

            // Then
            assertThat(cliente.getContrasena()).isEqualTo("$2a$10$nuevoHash");
        }

        @Test
        @DisplayName("cambiarContrasena rechaza un hash vacío")
        void cambiarContrasena_cuandoHashVacio_debeLanzarDomainValidationException() {
            // Given
            Cliente cliente = clienteValido();

            // When / Then
            assertThatThrownBy(() -> cliente.cambiarContrasena(" "))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("contraseña");
            assertThat(cliente.getContrasena()).isEqualTo(HASH);
        }

        @Test
        @DisplayName("actualizarDatosPersonales aplica los nuevos valores")
        void actualizarDatosPersonales_cuandoDatosValidos_debeAplicarCambios() {
            // Given
            Cliente cliente = clienteValido();

            // When
            cliente.actualizarDatosPersonales("Jose Antonio Lema", Genero.OTRO, 36, "ABC12345");

            // Then
            assertThat(cliente.getNombre()).isEqualTo("Jose Antonio Lema");
            assertThat(cliente.getGenero()).isEqualTo(Genero.OTRO);
            assertThat(cliente.getEdad()).isEqualTo(36);
            assertThat(cliente.getIdentificacion()).isEqualTo("ABC12345");
        }

        @Test
        @DisplayName("actualizarDatosPersonales valida y no aplica cambios parciales si un dato es inválido")
        void actualizarDatosPersonales_cuandoEdadInvalida_debeLanzarExcepcionSinModificar() {
            // Given
            Cliente cliente = clienteValido();

            // When / Then
            assertThatThrownBy(() -> cliente.actualizarDatosPersonales("Otro Nombre", Genero.OTRO, 17, "ABC12345"))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("edad");
            assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
            assertThat(cliente.getEdad()).isEqualTo(35);
        }

        @Test
        @DisplayName("actualizarContacto aplica la nueva dirección y teléfono")
        void actualizarContacto_cuandoDatosValidos_debeAplicarCambios() {
            // Given
            Cliente cliente = clienteValido();

            // When
            cliente.actualizarContacto("Amazonas y NNUU", "097548965");

            // Then
            assertThat(cliente.getDireccion()).isEqualTo("Amazonas y NNUU");
            assertThat(cliente.getTelefono()).isEqualTo("097548965");
        }

        @Test
        @DisplayName("actualizarContacto rechaza un teléfono inválido")
        void actualizarContacto_cuandoTelefonoInvalido_debeLanzarDomainValidationException() {
            // Given
            Cliente cliente = clienteValido();

            // When / Then
            assertThatThrownBy(() -> cliente.actualizarContacto("Amazonas y NNUU", "abc"))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("teléfono");
        }
    }
}
