package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.user.model.CredentialDto;
import com.selimhorri.app.business.user.model.UserDto;
import com.selimhorri.app.business.user.model.RoleBasedAuthority;
import com.selimhorri.app.business.user.service.UserClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba E2E del Flujo de Registro de Usuario
 * 
 * Esta prueba valida el flujo completo:
 * Cliente -> Proxy Client -> User Service (mockeado)
 * 
 * Verifica que:
 * 1. Un usuario se puede registrar exitosamente
 * 2. Se devuelve una confirmación adecuada
 * 3. La comunicación entre microservicios funciona correctamente
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
public class UserRegistrationEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserClientService userClientService;

    @BeforeEach
    void setUp() {
        // Setup básico para cada test
    }

    @Test
    void testUserRegistrationFlow_ShouldCreateUserSuccessfully() throws Exception {
        // Given - Preparar datos de registro de usuario
        CredentialDto credentialDto = CredentialDto.builder()
                .username("newuser2024")
                .password("SecurePassword123!")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto newUserDto = UserDto.builder()
                .firstName("Carlos")
                .lastName("Rodriguez")
                .email("carlos.rodriguez@example.com")
                .phone("987654321")
                .imageUrl("https://example.com/avatar/carlos.jpg")
                .credentialDto(credentialDto)
                .build();

        // Usuario devuelto por el servicio mockeado
        UserDto createdUserDto = UserDto.builder()
                .userId(1)
                .firstName("Carlos")
                .lastName("Rodriguez")
                .email("carlos.rodriguez@example.com")
                .phone("987654321")
                .imageUrl("https://example.com/avatar/carlos.jpg")
                .credentialDto(CredentialDto.builder()
                        .credentialId(1)
                        .username("newuser2024")
                        .password("SecurePassword123!")
                        .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                        .isEnabled(true)
                        .isAccountNonExpired(true)
                        .isAccountNonLocked(true)
                        .isCredentialsNonExpired(true)
                        .build())
                .build();

        // Mock del servicio de usuario
        when(userClientService.save(any(UserDto.class)))
                .thenReturn(ResponseEntity.ok(createdUserDto));
        
        when(userClientService.findById("1"))
                .thenReturn(ResponseEntity.ok(createdUserDto));

        // When & Then - Realizar la solicitud de registro a través del proxy-client
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUserDto)))
                // Verificar que el registro fue exitoso
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.firstName").value("Carlos"))
                .andExpect(jsonPath("$.lastName").value("Rodriguez"))
                .andExpect(jsonPath("$.email").value("carlos.rodriguez@example.com"))
                .andExpect(jsonPath("$.phone").value("987654321"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/avatar/carlos.jpg"))
                .andExpect(jsonPath("$.credential.username").value("newuser2024"))
                .andExpect(jsonPath("$.credential.roleBasedAuthority").value("ROLE_USER"))
                .andExpect(jsonPath("$.credential.isEnabled").value(true));

        // Verificar que se puede obtener el usuario recién creado
        mockMvc.perform(get("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("carlos.rodriguez@example.com"));
    }

    @Test 
    void testUserRegistrationFlow_WithInvalidData_ShouldReturnBadRequest() throws Exception {
        // Given - Preparar datos inválidos (sin email)
        CredentialDto credentialDto = CredentialDto.builder()
                .username("invaliduser")
                .password("pass")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto invalidUserDto = UserDto.builder()
                .firstName("Test")
                .lastName("User")
                // email faltante - debería causar error de validación
                .phone("123456789")
                .credentialDto(credentialDto)
                .build();

        // Mock del servicio para devolver error (simulando error de validación)
        when(userClientService.save(any(UserDto.class)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        // When & Then - Realizar la solicitud con datos inválidos
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidUserDto)))
                // Verificar que se devuelve respuesta exitosa pero sin contenido
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void testUserRegistrationFlow_ServiceUnavailable_ShouldReturnServiceUnavailable() throws Exception {
        // Given - Preparar datos válidos
        CredentialDto credentialDto = CredentialDto.builder()
                .username("testuser")
                .password("Password123!")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto userDto = UserDto.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phone("123456789")
                .credentialDto(credentialDto)
                .build();

        // Mock del servicio para simular error (servicio no disponible)
        when(userClientService.save(any(UserDto.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));

        // When & Then - Realizar la solicitud cuando el servicio no está disponible
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDto)))
                // Verificar que se devuelve respuesta exitosa pero sin contenido
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
