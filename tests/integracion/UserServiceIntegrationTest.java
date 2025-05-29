package com.selimhorri.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Corrected import for MockMvc auto-configuration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
// MockMvcBuilders is no longer needed if MockMvc is autowired and auto-configured
// import org.springframework.test.web.servlet.setup.MockMvcBuilders; // Can be removed
// WebApplicationContext might not be directly needed if MockMvc is fully auto-configured
// import org.springframework.web.context.WebApplicationContext; // Can be removed if not used elsewhere

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.user.model.UserDto;
import com.selimhorri.app.business.user.service.UserClientService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
// Corrected annotation to auto-configure MockMvc
@AutoConfigureMockMvc
public class UserServiceIntegrationTest {

    @MockBean
    private UserClientService userClientService;

    // WebApplicationContext can be removed if not used for anything other than MockMvc setup
    // @Autowired
    // private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired // MockMvc instance will be provided by @AutoConfigureMockMvc
    private MockMvc mockMvc;

    @Test
    public void testCreateUser_ShouldCallUserServiceAndReturnUserDto() throws Exception {
        // Setup MockMvc - This line is no longer needed with @AutoConfigureMockMvc
        // mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Given - Prepare test data
        UserDto inputUserDto = UserDto.builder()
                .firstName("Juan")
                .lastName("Perez")
                .email("juan@example.com")
                .phone("123456789")
                .build();

        UserDto expectedUserDto = UserDto.builder()
                .userId(1)
                .firstName("Juan")
                .lastName("Perez")
                .email("juan@example.com")
                .phone("123456789")
                .build();

        // Mock the Feign client response
        when(userClientService.save(any(UserDto.class)))
                .thenReturn(ResponseEntity.ok(expectedUserDto));

        // When - Perform the POST request to proxy-client
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputUserDto)))
                // Then - Verify response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.lastName").value("Perez"))
                .andExpect(jsonPath("$.email").value("juan@example.com"))
                .andExpect(jsonPath("$.phone").value("123456789"));

        // Verify that the Feign client was called with correct parameters
        verify(userClientService).save(any(UserDto.class));
    }
}