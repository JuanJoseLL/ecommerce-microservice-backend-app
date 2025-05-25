package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba E2E del Flujo de Registro de Usuario
 * 
 * Esta prueba valida el flujo completo:
 * Cliente -> API Gateway -> Proxy Client -> User Service
 * 
 * Verifica que:
 * 1. Un usuario se puede registrar exitosamente
 * 2. Se devuelve una confirmación adecuada
 * 3. Los datos se persisten correctamente
 * 4. La comunicación entre microservicios funciona correctamente
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class UserRegistrationEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> userDatabase = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("user_service_db")
            .withUsername("test")
            .withPassword("test")
            .withNetwork(network)
            .withNetworkAliases("user-db");

    @Container
    static GenericContainer<?> userService = new GenericContainer<>("openjdk:11-jre-slim")
            .withNetwork(network)
            .withNetworkAliases("user-service")
            .withExposedPorts(8081)
            .dependsOn(userDatabase)
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8081));

    @Container
    static GenericContainer<?> apiGateway = new GenericContainer<>("openjdk:11-jre-slim")
            .withNetwork(network)
            .withNetworkAliases("api-gateway")
            .withExposedPorts(8080)
            .dependsOn(userService)
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8080));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configurar propiedades para conectar con los contenedores
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("user-service.url", () -> "http://user-service:8081");
        registry.add("api-gateway.url", () -> "http://api-gateway:8080");
        
        // Configurar la base de datos para el user-service
        registry.add("spring.datasource.url", userDatabase::getJdbcUrl);
        registry.add("spring.datasource.username", userDatabase::getUsername);
        registry.add("spring.datasource.password", userDatabase::getPassword);
    }

    private String baseUrl;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Mock JWT token para autenticación (si es necesario)
        headers.set("Authorization", "Bearer mock-jwt-token");
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

        HttpEntity<UserDto> request = new HttpEntity<>(newUserDto, headers);

        // When - Realizar la solicitud de registro a través del proxy-client
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                baseUrl + "/api/users",
                request,
                UserDto.class
        );

        // Then - Verificar que el registro fue exitoso
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        UserDto createdUser = response.getBody();
        
        // Verificar que se asignó un ID al usuario
        assertThat(createdUser.getUserId()).isNotNull();
        assertThat(createdUser.getUserId()).isPositive();
        
        // Verificar que los datos del usuario son correctos
        assertThat(createdUser.getFirstName()).isEqualTo("Carlos");
        assertThat(createdUser.getLastName()).isEqualTo("Rodriguez");
        assertThat(createdUser.getEmail()).isEqualTo("carlos.rodriguez@example.com");
        assertThat(createdUser.getPhone()).isEqualTo("987654321");
        assertThat(createdUser.getImageUrl()).isEqualTo("https://example.com/avatar/carlos.jpg");
        
        // Verificar que las credenciales fueron procesadas correctamente
        assertThat(createdUser.getCredentialDto()).isNotNull();
        assertThat(createdUser.getCredentialDto().getUsername()).isEqualTo("newuser2024");
        assertThat(createdUser.getCredentialDto().getRoleBasedAuthority()).isEqualTo(RoleBasedAuthority.ROLE_USER);
        assertThat(createdUser.getCredentialDto().getIsEnabled()).isTrue();
        
        // Verificar que se puede obtener el usuario recién creado
        ResponseEntity<UserDto> getResponse = restTemplate.exchange(
                baseUrl + "/api/users/" + createdUser.getUserId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserDto.class
        );
        
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getUserId()).isEqualTo(createdUser.getUserId());
        assertThat(getResponse.getBody().getEmail()).isEqualTo("carlos.rodriguez@example.com");
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

        HttpEntity<UserDto> request = new HttpEntity<>(invalidUserDto, headers);

        // When - Realizar la solicitud con datos inválidos
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/users",
                request,
                String.class
        );

        // Then - Verificar que se devuelve un error apropiado
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void testUserRegistrationFlow_DuplicateUsername_ShouldReturnConflict() throws Exception {
        // Given - Crear un usuario primero
        CredentialDto credentialDto1 = CredentialDto.builder()
                .username("duplicateuser")
                .password("Password123!")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto firstUser = UserDto.builder()
                .firstName("First")
                .lastName("User")
                .email("first@example.com")
                .phone("111111111")
                .credentialDto(credentialDto1)
                .build();

        // Crear el primer usuario
        HttpEntity<UserDto> firstRequest = new HttpEntity<>(firstUser, headers);
        ResponseEntity<UserDto> firstResponse = restTemplate.postForEntity(
                baseUrl + "/api/users",
                firstRequest,
                UserDto.class
        );
        
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Given - Intentar crear otro usuario con el mismo username
        CredentialDto credentialDto2 = CredentialDto.builder()
                .username("duplicateuser") // mismo username
                .password("AnotherPassword123!")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto secondUser = UserDto.builder()
                .firstName("Second")
                .lastName("User")
                .email("second@example.com")
                .phone("222222222")
                .credentialDto(credentialDto2)
                .build();

        HttpEntity<UserDto> secondRequest = new HttpEntity<>(secondUser, headers);

        // When - Intentar crear el segundo usuario con username duplicado
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                baseUrl + "/api/users",
                secondRequest,
                String.class
        );

        // Then - Verificar que se devuelve un error de conflicto
        assertThat(secondResponse.getStatusCode()).isIn(HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
    }
}
