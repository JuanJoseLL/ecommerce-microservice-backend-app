package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.selimhorri.app.domain.Category;
import com.selimhorri.app.domain.Product;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.exception.wrapper.ProductNotFoundException;
import com.selimhorri.app.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void testFindById_ShouldReturnProductWhenExists() {
        // Given
        Integer productId = 1;
        
        Category category = Category.builder()
                .categoryId(1)
                .categoryTitle("Electronics")
                .imageUrl("http://example.com/category.jpg")
                .build();
        
        Product product = Product.builder()
                .productId(1)
                .productTitle("Laptop")
                .imageUrl("http://example.com/laptop.jpg")
                .sku("LAP001")
                .priceUnit(999.99)
                .quantity(10)
                .category(category)
                .build();

        // When
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        
        ProductDto result = productService.findById(productId);

        // Then
        assertNotNull(result, "El resultado no debería ser null");
        assertEquals(1, result.getProductId(), "El ID del producto debería ser 1");
        assertEquals("Laptop", result.getProductTitle(), "El título del producto debería ser Laptop");
        assertEquals("http://example.com/laptop.jpg", result.getImageUrl(), "La URL de imagen debería coincidir");
        assertEquals("LAP001", result.getSku(), "El SKU debería coincidir");
        assertEquals(999.99, result.getPriceUnit(), "El precio debería coincidir");
        assertEquals(10, result.getQuantity(), "La cantidad debería coincidir");
        
        // Verificar categoría
        assertNotNull(result.getCategoryDto(), "La categoría no debería ser null");
        assertEquals(1, result.getCategoryDto().getCategoryId(), "El ID de categoría debería ser 1");
        assertEquals("Electronics", result.getCategoryDto().getCategoryTitle(), "El título de categoría debería coincidir");
        assertEquals("http://example.com/category.jpg", result.getCategoryDto().getImageUrl(), "La URL de imagen de categoría debería coincidir");

        // Verificar que el repository fue llamado una vez
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    void testFindById_ShouldThrowExceptionWhenProductNotExists() {
        // Given
        Integer nonExistentProductId = 999;

        // When
        when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

        // Then
        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.findById(nonExistentProductId),
                "Debería lanzar ProductNotFoundException cuando el producto no existe"
        );

        assertEquals("Product with id: 999 not found", exception.getMessage(), "El mensaje de excepción debería ser correcto");

        // Verificar que el repository fue llamado una vez
        verify(productRepository, times(1)).findById(nonExistentProductId);
    }
}
