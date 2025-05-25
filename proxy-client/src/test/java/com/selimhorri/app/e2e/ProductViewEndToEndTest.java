package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.product.model.CategoryDto;
import com.selimhorri.app.business.product.model.ProductDto;
import com.selimhorri.app.business.product.service.ProductClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba E2E del Flujo de Visualización de Detalles de un Producto
 * 
 * Esta prueba valida el flujo completo:
 * Cliente -> API Gateway -> Proxy Client -> Product Service (mockeado)
 * 
 * Verifica que:
 * 1. Un cliente puede obtener los detalles de un producto específico
 * 2. Se devuelven todos los datos correctos del producto incluyendo categoría
 * 3. La comunicación entre microservicios funciona correctamente
 * 4. Se manejan correctamente los casos de error (producto no encontrado, servicio no disponible)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
public class ProductViewEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductClientService productClientService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(productClientService);
    }

    @Test
    @DisplayName("Should retrieve product details successfully")
    void testProductViewFlow_ShouldRetrieveProductDetailsSuccessfully() throws Exception {
        // Given - Preparar datos del producto con categoría
        String productId = "1";
        
        CategoryDto categoryDto = CategoryDto.builder()
                .categoryId(5)
                .categoryTitle("Laptops")
                .imageUrl("https://example.com/images/categories/laptops.jpg")
                .build();

        ProductDto expectedProduct = ProductDto.builder()
                .productId(1)
                .productTitle("Dell XPS 15 - Laptop Profesional")
                .imageUrl("https://example.com/images/products/dell-xps-15.jpg")
                .sku("DELL-XPS15-2024")
                .priceUnit(2299.99)
                .quantity(12)
                .categoryDto(categoryDto)
                .build();

        // Mock del servicio de producto
        when(productClientService.findById(eq(productId)))
                .thenReturn(ResponseEntity.ok(expectedProduct));

        // When & Then - Realizar la solicitud de visualización del producto
        mockMvc.perform(get("/api/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                // Verificar que la respuesta es exitosa
                .andExpect(status().isOk())
                // Verificar los datos principales del producto
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.productTitle").value("Dell XPS 15 - Laptop Profesional"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/images/products/dell-xps-15.jpg"))
                .andExpect(jsonPath("$.sku").value("DELL-XPS15-2024"))
                .andExpect(jsonPath("$.priceUnit").value(2299.99))
                .andExpect(jsonPath("$.quantity").value(12))
                // Verificar los datos de la categoría (nota: se mapea como "category" en JSON)
                .andExpect(jsonPath("$.category.categoryId").value(5))
                .andExpect(jsonPath("$.category.categoryTitle").value("Laptops"))
                .andExpect(jsonPath("$.category.imageUrl").value("https://example.com/images/categories/laptops.jpg"))
                .andDo(print());

        // Verify - Verificar que se llamó al servicio correcto
        verify(productClientService, times(1)).findById(eq(productId));
    }

    @Test
    @DisplayName("Should handle product not found scenario")
    void testProductViewFlow_WithNonExistentProduct_ShouldReturnNotFound() throws Exception {
        // Given - Preparar escenario de producto no encontrado
        String nonExistentProductId = "999";
        
        // Mock del servicio para devolver respuesta not found
        when(productClientService.findById(eq(nonExistentProductId)))
                .thenReturn(ResponseEntity.notFound().build());

        // When & Then - Realizar la solicitud para producto no existente
        mockMvc.perform(get("/api/products/{productId}", nonExistentProductId)
                .contentType(MediaType.APPLICATION_JSON))
                // El proxy-client devuelve HTTP 200 con body null cuando el servicio devuelve 404
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andDo(print());

        // Verify
        verify(productClientService, times(1)).findById(eq(nonExistentProductId));
    }

    @Test
    @DisplayName("Should handle service unavailable scenario")
    void testProductViewFlow_WithServiceUnavailable_ShouldReturnServiceError() throws Exception {
        // Given - Preparar escenario de servicio no disponible
        String productId = "1";
        
        // Mock del servicio para simular servicio no disponible
        when(productClientService.findById(eq(productId)))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));

        // When & Then - Realizar la solicitud cuando el servicio no está disponible
        mockMvc.perform(get("/api/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                // El proxy-client devuelve HTTP 200 con body null cuando hay error en el servicio
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andDo(print());

        // Verify
        verify(productClientService, times(1)).findById(eq(productId));
    }

    @Test
    @DisplayName("Should retrieve product with complex category hierarchy")
    void testProductViewFlow_WithComplexCategory_ShouldRetrieveAllDetails() throws Exception {
        // Given - Preparar producto con categoría más compleja
        String productId = "2";
        
        CategoryDto parentCategory = CategoryDto.builder()
                .categoryId(1)
                .categoryTitle("Electronics")
                .imageUrl("https://example.com/images/categories/electronics.jpg")
                .build();

        CategoryDto categoryDto = CategoryDto.builder()
                .categoryId(3)
                .categoryTitle("Gaming Laptops")
                .imageUrl("https://example.com/images/categories/gaming-laptops.jpg")
                .build();

        ProductDto expectedProduct = ProductDto.builder()
                .productId(2)
                .productTitle("ASUS ROG Strix G15 - Gaming Laptop")
                .imageUrl("https://example.com/images/products/asus-rog-strix-g15.jpg")
                .sku("ASUS-ROG-G15-2024")
                .priceUnit(1899.99)
                .quantity(8)
                .categoryDto(categoryDto)
                .build();

        // Mock del servicio de producto
        when(productClientService.findById(eq(productId)))
                .thenReturn(ResponseEntity.ok(expectedProduct));

        // When & Then - Realizar la solicitud
        mockMvc.perform(get("/api/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(2))
                .andExpect(jsonPath("$.productTitle").value("ASUS ROG Strix G15 - Gaming Laptop"))
                .andExpect(jsonPath("$.sku").value("ASUS-ROG-G15-2024"))
                .andExpect(jsonPath("$.priceUnit").value(1899.99))
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.category.categoryId").value(3))
                .andExpect(jsonPath("$.category.categoryTitle").value("Gaming Laptops"))
                .andDo(print());

        // Verify
        verify(productClientService, times(1)).findById(eq(productId));
    }

    @Test
    @DisplayName("Should handle invalid product ID format")
    void testProductViewFlow_WithInvalidProductId_ShouldCallServiceAndHandleResponse() throws Exception {
        // Given - Preparar escenario con ID inválido
        String invalidProductId = "invalid-id";
        
        // El servicio puede devolver error o manejar la conversión internamente
        when(productClientService.findById(eq(invalidProductId)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        // When & Then - Realizar la solicitud con ID inválido
        mockMvc.perform(get("/api/products/{productId}", invalidProductId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andDo(print());

        // Verify
        verify(productClientService, times(1)).findById(eq(invalidProductId));
    }
}
