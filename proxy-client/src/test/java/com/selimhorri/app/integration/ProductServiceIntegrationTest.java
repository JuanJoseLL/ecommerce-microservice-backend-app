package com.selimhorri.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.selimhorri.app.business.product.model.CategoryDto;
import com.selimhorri.app.business.product.model.ProductDto;
import com.selimhorri.app.business.product.service.ProductClientService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class ProductServiceIntegrationTest {

    @MockBean
    private ProductClientService productClientService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetProductById_ShouldCallProductServiceAndReturnProductDto() throws Exception {
        // Given - Prepare test data
        String productId = "1";
        
        CategoryDto categoryDto = CategoryDto.builder()
                .categoryId(10)
                .categoryTitle("Electronics")
                .imageUrl("http://example.com/category.jpg")
                .build();

        ProductDto expectedProductDto = ProductDto.builder()
                .productId(1)
                .productTitle("Laptop Gaming")
                .imageUrl("http://example.com/laptop.jpg")
                .sku("LAP001")
                .priceUnit(1299.99)
                .quantity(5)
                .categoryDto(categoryDto)
                .build();

        // Mock the Feign client response
        ResponseEntity<ProductDto> feignResponse = new ResponseEntity<>(expectedProductDto, HttpStatus.OK);
        when(productClientService.findById(eq(productId))).thenReturn(feignResponse);

        // When & Then - Execute request and verify response
        mockMvc.perform(get("/api/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.productTitle").value("Laptop Gaming"))
                .andExpect(jsonPath("$.imageUrl").value("http://example.com/laptop.jpg"))
                .andExpect(jsonPath("$.sku").value("LAP001"))
                .andExpect(jsonPath("$.priceUnit").value(1299.99))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.category.categoryId").value(10))
                .andExpect(jsonPath("$.category.categoryTitle").value("Electronics"))
                .andExpect(jsonPath("$.category.imageUrl").value("http://example.com/category.jpg"));

        // Verify that the Feign client was called correctly
        verify(productClientService).findById(productId);
    }
}
