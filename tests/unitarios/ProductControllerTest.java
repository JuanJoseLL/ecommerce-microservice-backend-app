package com.selimhorri.app.business.product.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.selimhorri.app.business.product.model.CategoryDto;
import com.selimhorri.app.business.product.model.ProductDto;
import com.selimhorri.app.business.product.service.ProductClientService;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductClientService productClientService;

    @InjectMocks
    private ProductController productController;

    @Test
    void testFindById_ShouldReturnProductDtoWhenClientServiceReturnsValidResponse() {
        // Given - Simular respuesta del servicio product-service vía Feign client
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

        // Simular respuesta del Feign client (proxy hacia product-service)
        ResponseEntity<ProductDto> feignResponse = new ResponseEntity<>(expectedProductDto, HttpStatus.OK);

        // Configurar mock del ProductClientService
        when(productClientService.findById(productId)).thenReturn(feignResponse);

        // When - Ejecutar el método del controller (proxy-client)
        ResponseEntity<ProductDto> result = productController.findById(productId);

        // Then - Verificar que el proxy controller retorna la respuesta correcta
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        
        ProductDto resultProductDto = result.getBody();
        assertEquals(1, resultProductDto.getProductId());
        assertEquals("Laptop Gaming", resultProductDto.getProductTitle());
        assertEquals("http://example.com/laptop.jpg", resultProductDto.getImageUrl());
        assertEquals("LAP001", resultProductDto.getSku());
        assertEquals(1299.99, resultProductDto.getPriceUnit());
        assertEquals(5, resultProductDto.getQuantity());
        
        // Verificar CategoryDto anidado
        assertNotNull(resultProductDto.getCategoryDto());
        assertEquals(10, resultProductDto.getCategoryDto().getCategoryId());
        assertEquals("Electronics", resultProductDto.getCategoryDto().getCategoryTitle());
        assertEquals("http://example.com/category.jpg", resultProductDto.getCategoryDto().getImageUrl());

        // Verificar que el Feign client fue llamado correctamente
        verify(productClientService).findById(productId);
    }
}
