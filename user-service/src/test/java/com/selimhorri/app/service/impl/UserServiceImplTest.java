package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.selimhorri.app.domain.Credential;
import com.selimhorri.app.domain.RoleBasedAuthority;
import com.selimhorri.app.domain.User;
import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void testSaveUser_ShouldProcessInputCorrectlyAndReturnExpectedResult() {
        // Given - Datos de entrada
        CredentialDto credentialDto = CredentialDto.builder()
                .username("testuser")
                .password("password123")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        UserDto inputUserDto = UserDto.builder()
                .firstName("Juan")
                .lastName("Pérez")
                .email("juan.perez@example.com")
                .phone("123456789")
                .imageUrl("http://example.com/image.jpg")
                .credentialDto(credentialDto)
                .build();

        // Simular la entidad User que se guardará
        Credential savedCredential = Credential.builder()
                .credentialId(1)
                .username("testuser")
                .password("password123")
                .roleBasedAuthority(RoleBasedAuthority.ROLE_USER)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .build();

        User savedUser = User.builder()
                .userId(1)
                .firstName("Juan")
                .lastName("Pérez")
                .email("juan.perez@example.com")
                .phone("123456789")
                .imageUrl("http://example.com/image.jpg")
                .credential(savedCredential)
                .build();

        // When - Configurar mock para devolver el usuario guardado
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Ejecutar el método bajo prueba
        UserDto result = userService.save(inputUserDto);

        // Then - Verificaciones
        assertNotNull(result, "El resultado no debería ser null");
        assertEquals(1, result.getUserId(), "El ID del usuario debería ser 1");
        assertEquals("Juan", result.getFirstName(), "El nombre debería ser Juan");
        assertEquals("Pérez", result.getLastName(), "El apellido debería ser Pérez");
        assertEquals("juan.perez@example.com", result.getEmail(), "El email debería coincidir");
        assertEquals("123456789", result.getPhone(), "El teléfono debería coincidir");
        assertEquals("http://example.com/image.jpg", result.getImageUrl(), "La URL de imagen debería coincidir");

        // Verificar credenciales
        assertNotNull(result.getCredentialDto(), "Las credenciales no deberían ser null");
        assertEquals(1, result.getCredentialDto().getCredentialId(), "El ID de credencial debería ser 1");
        assertEquals("testuser", result.getCredentialDto().getUsername(), "El username debería coincidir");
        assertEquals("password123", result.getCredentialDto().getPassword(), "La contraseña debería coincidir");
        assertEquals(RoleBasedAuthority.ROLE_USER, result.getCredentialDto().getRoleBasedAuthority(), "El rol debería ser ROLE_USER");
        assertTrue(result.getCredentialDto().getIsEnabled(), "La cuenta debería estar habilitada");
        assertTrue(result.getCredentialDto().getIsAccountNonExpired(), "La cuenta no debería estar expirada");
        assertTrue(result.getCredentialDto().getIsAccountNonLocked(), "La cuenta no debería estar bloqueada");
        assertTrue(result.getCredentialDto().getIsCredentialsNonExpired(), "Las credenciales no deberían estar expiradas");

        // Verificar que el repository fue llamado una vez
        verify(userRepository, times(1)).save(any(User.class));
    }
}
