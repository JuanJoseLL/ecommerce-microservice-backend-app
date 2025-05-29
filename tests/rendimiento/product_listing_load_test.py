#!/usr/bin/env python3
"""
Prueba de Rendimiento: Carga en Listado de Productos
=================================================

Esta prueba de rendimiento simula múltiples usuarios concurrentes navegando 
por la lista de productos en el sistema de e-commerce.

Flujo de la prueba:
Cliente -> API Gateway -> Proxy Client -> Product Service

Endpoints bajo prueba:
- GET /api/products (listar todos los productos)
- GET /api/products/{id} (obtener producto específico)

Métricas clave:
- Tiempo de respuesta promedio
- Throughput (peticiones por segundo)
- Tasa de errores bajo carga
- Percentiles de latencia (P50, P95, P99)

Uso:
    # Prueba básica
    locust -f product_listing_load_test.py --host=http://localhost

    # Prueba con configuración específica
    locust -f product_listing_load_test.py --host=http://localhost --users=50 --spawn-rate=5 --run-time=300s

Configuración para Kubernetes:
- API Gateway expuesto vía Ingress en puerto 80
- Los servicios corren en la red Kubernetes interna
"""

import random
import time
from locust import HttpUser, task, between
from typing import Dict, Any


class ProductListingUser(HttpUser):
    """
    Simulación de un usuario navegando por productos.
    
    Comportamiento del usuario:
    1. Lista productos disponibles (más frecuente)
    2. Ve detalles de productos específicos
    3. Busca productos por categoría (si está disponible)
    """
    
    # Tiempo de espera entre tareas (simula tiempo de lectura/navegación del usuario)
    wait_time = between(1, 3)
    
    def on_start(self):
        """Configuración inicial del usuario al comenzar la prueba"""
        self.product_ids = []
        self.category_ids = []
        
        # Obtener lista inicial de productos para usar en pruebas posteriores
        self._fetch_initial_data()
    
    def _fetch_initial_data(self):
        """Obtiene datos iniciales para usar en las pruebas"""
        try:
            # Obtener lista de productos para tener IDs reales para pruebas
            response = self.client.get("/api/products", name="GET /api/products (setup)")
            if response.status_code == 200:
                try:
                    products_data = response.json()
                    # Manejar diferentes estructuras de respuesta
                    if isinstance(products_data, dict) and 'collection' in products_data:
                        products = products_data['collection']
                    elif isinstance(products_data, list):
                        products = products_data
                    else:
                        products = []
                    
                    # Extraer IDs de productos
                    self.product_ids = [str(product.get('productId', product.get('id', i+1))) 
                                      for i, product in enumerate(products[:20])]  # Tomar primeros 20
                    
                    # Extraer IDs de categorías si están disponibles
                    self.category_ids = list(set([str(product.get('categoryDto', {}).get('categoryId', 
                                                                 product.get('category', {}).get('categoryId', 1)))
                                                for product in products[:10]]))  # Tomar primeras 10 categorías únicas
                    
                except Exception as e:
                    # Si hay error parseando JSON, usar IDs por defecto
                    print(f"Error parsing products JSON: {e}")
                    self._use_default_ids()
            else:
                self._use_default_ids()
                
        except Exception as e:
            print(f"Error fetching initial data: {e}")
            self._use_default_ids()
    
    def _use_default_ids(self):
        """Usar IDs por defecto si no se pueden obtener datos reales"""
        self.product_ids = [str(i) for i in range(1, 5)]   # IDs 1-4 (basado en datos reales)
        self.category_ids = [str(i) for i in range(1, 4)]   # IDs 1-3

    @task(5)
    def list_all_products(self):
        """
        Tarea más frecuente: Listar todos los productos
        Peso: 5 (50% del tiempo)
        """
        with self.client.get("/api/products", 
                           catch_response=True,
                           name="GET /api/products") as response:
            if response.status_code == 200:
                response.success()
                # Medir tiempo de respuesta
                if response.elapsed.total_seconds() > 2.0:  # Threshold de 2 segundos
                    response.failure(f"Response too slow: {response.elapsed.total_seconds():.2f}s")
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(3)
    def get_product_details(self):
        """
        Tarea frecuente: Obtener detalles de un producto específico
        Peso: 3 (30% del tiempo)
        """
        if not self.product_ids:
            return
            
        product_id = random.choice(self.product_ids)
        endpoint = f"/api/products/{product_id}"
        
        with self.client.get(endpoint,
                           catch_response=True,
                           name="GET /api/products/{id}") as response:
            if response.status_code == 200:
                try:
                    product_data = response.json()
                    # Verificar que la respuesta contiene datos esperados
                    if product_data and ('productId' in product_data or 'id' in product_data):
                        response.success()
                    else:
                        response.failure("Empty or invalid product data")
                except Exception as e:
                    response.failure(f"Invalid JSON response: {e}")
            elif response.status_code == 404:
                # 404 es aceptable para algunos IDs que pueden no existir
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(2)
    def browse_categories(self):
        """
        Tarea moderada: Navegar por categorías
        Peso: 2 (20% del tiempo)
        """
        if not self.category_ids:
            return
            
        category_id = random.choice(self.category_ids)
        endpoint = f"/api/categories/{category_id}"
        
        with self.client.get(endpoint,
                           catch_response=True,
                           name="GET /api/categories/{id}") as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 404:
                # 404 es aceptable para categorías que pueden no existir
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(1)
    def random_product_sequence(self):
        """
        Tarea menos frecuente: Simular secuencia de navegación realista
        Peso: 1 (10% del tiempo)
        """
        # Simular un usuario que:
        # 1. Lista productos
        # 2. Ve detalles de 2-3 productos
        # 3. Puede ver una categoría
        
        # Paso 1: Listar productos
        self.client.get("/api/products", name="GET /api/products (sequence)")
        time.sleep(random.uniform(0.5, 1.5))  # Tiempo de lectura
        
        # Paso 2: Ver detalles de productos
        products_to_view = min(random.randint(2, 3), len(self.product_ids))
        for _ in range(products_to_view):
            if self.product_ids:
                product_id = random.choice(self.product_ids)
                self.client.get(f"/api/products/{product_id}", 
                              name="GET /api/products/{id} (sequence)")
                time.sleep(random.uniform(1.0, 2.0))  # Tiempo de lectura del producto
        
        # Paso 3: Ocasionalmente ver una categoría
        if random.random() < 0.4 and self.category_ids:  # 40% de probabilidad
            category_id = random.choice(self.category_ids)
            self.client.get(f"/api/categories/{category_id}",
                          name="GET /api/categories/{id} (sequence)")


class ProductLoadTestUser(HttpUser):
    """
    Usuario específico para pruebas de carga intensiva.
    Enfocado únicamente en productos para máximo stress.
    """
    
    wait_time = between(0.1, 0.5)  # Menor tiempo de espera para mayor carga
    
    def on_start(self):
        # IDs fijos para pruebas de carga rápidas
        self.product_ids = [str(i) for i in range(1, 5)]  # IDs 1-4 (basado en datos reales)

    @task(1)
    def rapid_product_access(self):
        """Acceso rápido y continuo a productos"""
        product_id = random.choice(self.product_ids)
        
        # Alternear entre listado y detalles
        if random.random() < 0.6:  # 60% listado, 40% detalles
            self.client.get("/api/products", name="GET /api/products (load)")
        else:
            self.client.get(f"/api/products/{product_id}", name="GET /api/products/{id} (load)")


# Configuración por defecto para diferentes tipos de prueba
if __name__ == "__main__":
    print("Prueba de Rendimiento - Listado de Productos")
    print("===========================================")
    print()
    print("Para ejecutar esta prueba:")
    print()
    print("1. Prueba exploratoria (baja carga):")
    print("   locust -f product_listing_load_test.py --host=http://localhost --users=10 --spawn-rate=2")
    print()
    print("2. Prueba de carga normal:")
    print("   locust -f product_listing_load_test.py --host=http://localhost --users=50 --spawn-rate=5 --run-time=300s")
    print()
    print("3. Prueba de stress (alta carga):")
    print("   locust -f product_listing_load_test.py --host=http://localhost --users=100 --spawn-rate=10 --run-time=600s")
    print()
    print("4. Acceder a la interfaz web de Locust:")
    print("   locust -f product_listing_load_test.py --host=http://localhost")
    print("   Luego abrir: http://localhost:8089")
