#!/usr/bin/env python3
"""
Prueba de Rendimiento: Capacidad de Respuesta del User Service
=============================================================

Esta prueba de rendimiento evalúa la capacidad de respuesta del user-service
bajo carga, simulando registros de usuarios concurrentes y consultas de perfiles.

Flujo de la prueba:
Cliente -> API Gateway -> Proxy Client -> User Service

Endpoints bajo prueba:
- POST /api/users (registro de nuevos usuarios)
- GET /api/users/{id} (consultar perfil de usuario)
- GET /api/users (listar usuarios)
- PUT /api/users/{id} (actualizar perfil de usuario)

Métricas clave:
- Tiempo de respuesta para operaciones de usuario bajo carga
- Throughput de registros de usuarios por segundo
- Latencia de consultas de perfil
- Tasa de errores en registros y consultas

Uso:
    # Prueba básica
    locust -f user_service_load_test.py --host=http://localhost:8080

    # Prueba de registros concurrentes
    locust -f user_service_load_test.py --host=http://localhost:8080 --users=25 --spawn-rate=3 --run-time=300s
"""

import random
import time
import json
import string
from datetime import datetime, timedelta
from locust import HttpUser, task, between
from typing import Dict, Any, List


class UserServiceUser(HttpUser):
    """
    Simulación de operaciones de usuarios en el sistema.
    
    Comportamiento simulado:
    1. Registro de nuevos usuarios
    2. Consulta de perfiles de usuarios
    3. Actualización de datos de perfil
    4. Listado de usuarios (operaciones administrativas)
    """
    
    wait_time = between(1, 4)  # Tiempo entre operaciones de usuario
    
    def on_start(self):
        """Configuración inicial"""
        self.registered_users = []
        self.user_counter = random.randint(1000, 9999)
        self.session_user_id = None
        
        # Simular algunos usuarios existentes para consultas
        self.existing_user_ids = [str(i) for i in range(1, 51)]
        
        # Configurar headers comunes
        self.client.headers.update({
            "Content-Type": "application/json",
            "User-Agent": "LoadTest-UserService/1.0"
        })
    
    def _generate_random_string(self, length: int = 8) -> str:
        """Genera una cadena aleatoria"""
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))
    
    def _generate_user_data(self) -> Dict[str, Any]:
        """Genera datos de usuario realistas para registro"""
        self.user_counter += 1
        random_suffix = self._generate_random_string(6)
        
        user_data = {
            "firstName": random.choice([
                "Juan", "María", "Carlos", "Ana", "Luis", "Carmen", "José", "Laura",
                "Miguel", "Elena", "David", "Sara", "Pedro", "Isabel", "Jorge", "Lucía"
            ]),
            "lastName": random.choice([
                "García", "Rodríguez", "González", "Fernández", "López", "Martínez",
                "Sánchez", "Pérez", "Gómez", "Martín", "Jiménez", "Ruiz", "Hernández"
            ]),
            "imageUrl": f"https://example.com/avatar/{self.user_counter}.jpg",
            "email": f"user_{self.user_counter}_{random_suffix}@example.com",
            "phone": f"+1-555-{random.randint(100, 999)}-{random.randint(1000, 9999)}",
            "credentialDto": {
                "username": f"user_{self.user_counter}_{random_suffix}",
                "password": f"Password123_{random_suffix}",
                "role": "USER"  # Rol por defecto
            }
        }
        
        return user_data
    
    def _generate_update_data(self) -> Dict[str, Any]:
        """Genera datos para actualización de usuario"""
        return {
            "firstName": random.choice(["Updated_" + name for name in 
                                     ["Juan", "María", "Carlos", "Ana", "Luis"]]),
            "phone": f"+1-555-{random.randint(800, 999)}-{random.randint(5000, 9999)}",
            "imageUrl": f"https://example.com/updated_avatar/{random.randint(1000, 9999)}.jpg"
        }

    @task(3)
    def register_new_user(self):
        """
        Tarea frecuente: Registrar un nuevo usuario
        Peso: 3 (30% del tiempo)
        """
        user_data = self._generate_user_data()
        
        with self.client.post("/api/users",
                            json=user_data,
                            catch_response=True,
                            name="POST /api/users") as response:
            
            if response.status_code in [200, 201]:
                try:
                    created_user = response.json()
                    # Verificar que el usuario fue creado correctamente
                    if created_user and ('userId' in created_user or 'id' in created_user):
                        user_id = created_user.get('userId', created_user.get('id'))
                        self.registered_users.append(str(user_id))
                        
                        # Si es el primer usuario registrado, usarlo como sesión
                        if not self.session_user_id:
                            self.session_user_id = str(user_id)
                        
                        response.success()
                        
                        # Medir tiempo de respuesta crítico para registros
                        if response.elapsed.total_seconds() > 2.0:  # Threshold de 2 segundos
                            response.failure(f"User registration too slow: {response.elapsed.total_seconds():.2f}s")
                    else:
                        response.failure("User created but invalid response structure")
                        
                except Exception as e:
                    response.failure(f"Invalid JSON response: {e}")
            elif response.status_code == 400:
                response.failure(f"Bad request (possible duplicate): {response.text[:200]}")
            elif response.status_code == 409:
                # Conflicto (usuario duplicado) es aceptable en pruebas de carga
                response.success()
            elif response.status_code == 500:
                response.failure(f"Server error: {response.text[:200]}")
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(4)
    def get_user_profile(self):
        """
        Tarea más frecuente: Consultar perfil de usuario
        Peso: 4 (40% del tiempo)
        """
        # Usar usuarios registrados o existentes
        available_users = self.registered_users + self.existing_user_ids
        if not available_users:
            return
        
        user_id = random.choice(available_users)
        endpoint = f"/api/users/{user_id}"
        
        with self.client.get(endpoint,
                           catch_response=True,
                           name="GET /api/users/{id}") as response:
            
            if response.status_code == 200:
                try:
                    user_data = response.json()
                    if user_data and ('userId' in user_data or 'id' in user_data):
                        response.success()
                        
                        # Verificar tiempo de respuesta para consultas
                        if response.elapsed.total_seconds() > 1.0:  # Threshold de 1 segundo
                            response.failure(f"User query too slow: {response.elapsed.total_seconds():.2f}s")
                    else:
                        response.failure("Empty or invalid user data")
                except Exception as e:
                    response.failure(f"Invalid JSON response: {e}")
            elif response.status_code == 404:
                # 404 es aceptable para algunos IDs que pueden no existir
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(2)
    def list_users(self):
        """
        Tarea moderada: Listar usuarios (operación administrativa)
        Peso: 2 (20% del tiempo)
        """
        with self.client.get("/api/users",
                           catch_response=True,
                           name="GET /api/users") as response:
            
            if response.status_code == 200:
                try:
                    users_data = response.json()
                    # Manejar diferentes estructuras de respuesta
                    if isinstance(users_data, dict) and 'collection' in users_data:
                        users = users_data['collection']
                    elif isinstance(users_data, list):
                        users = users_data
                    else:
                        users = []
                    
                    response.success()
                    
                    # Verificar tiempo de respuesta para listados
                    if response.elapsed.total_seconds() > 1.5:  # Threshold de 1.5 segundos
                        response.failure(f"User listing too slow: {response.elapsed.total_seconds():.2f}s")
                        
                except Exception as e:
                    response.failure(f"Invalid JSON response: {e}")
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(1)
    def update_user_profile(self):
        """
        Tarea menos frecuente: Actualizar perfil de usuario
        Peso: 1 (10% del tiempo)
        """
        # Solo actualizar usuarios que hemos registrado en esta sesión
        if not self.registered_users:
            return
        
        user_id = random.choice(self.registered_users)
        update_data = self._generate_update_data()
        endpoint = f"/api/users/{user_id}"
        
        with self.client.put(endpoint,
                           json=update_data,
                           catch_response=True,
                           name="PUT /api/users/{id}") as response:
            
            if response.status_code in [200, 204]:
                response.success()
                
                # Verificar tiempo de respuesta para actualizaciones
                if response.elapsed.total_seconds() > 2.0:  # Threshold de 2 segundos
                    response.failure(f"User update too slow: {response.elapsed.total_seconds():.2f}s")
            elif response.status_code == 404:
                response.failure(f"User {user_id} not found for update")
            elif response.status_code == 400:
                response.failure(f"Bad request: {response.text[:200]}")
            else:
                response.failure(f"HTTP {response.status_code}: {response.text[:100]}")

    @task(1)
    def user_lifecycle_simulation(self):
        """
        Tarea especial: Simular ciclo de vida completo de usuario
        Peso: 1 (10% del tiempo)
        """
        # Simular flujo completo:
        # 1. Registrar usuario
        # 2. Consultar perfil inmediatamente
        # 3. Actualizar algún dato
        # 4. Consultar perfil actualizado
        
        # Paso 1: Registrar usuario
        user_data = self._generate_user_data()
        register_response = self.client.post("/api/users",
                                           json=user_data,
                                           name="POST /api/users (lifecycle)")
        
        if register_response.status_code in [200, 201]:
            try:
                created_user = register_response.json()
                user_id = created_user.get('userId', created_user.get('id'))
                
                if user_id:
                    # Paso 2: Consulta inmediata
                    time.sleep(random.uniform(0.3, 0.8))
                    self.client.get(f"/api/users/{user_id}",
                                  name="GET /api/users/{id} (lifecycle-check)")
                    
                    # Paso 3: Actualizar datos
                    time.sleep(random.uniform(1.0, 2.0))
                    update_data = self._generate_update_data()
                    self.client.put(f"/api/users/{user_id}",
                                  json=update_data,
                                  name="PUT /api/users/{id} (lifecycle-update)")
                    
                    # Paso 4: Verificar actualización
                    time.sleep(random.uniform(0.5, 1.0))
                    self.client.get(f"/api/users/{user_id}",
                                  name="GET /api/users/{id} (lifecycle-verify)")
                    
            except Exception as e:
                print(f"Error in user lifecycle simulation: {e}")


class HighVolumeRegistrationUser(HttpUser):
    """
    Usuario específico para pruebas de alto volumen de registros.
    Enfocado únicamente en crear usuarios para máximo stress en user-service.
    """
    
    wait_time = between(0.2, 1.0)  # Menor tiempo de espera para más carga
    
    def on_start(self):
        self.registration_counter = random.randint(50000, 99999)
    
    def _generate_simple_user(self) -> Dict[str, Any]:
        """Genera datos de usuario simples para registro rápido"""
        self.registration_counter += 1
        
        return {
            "firstName": f"TestUser",
            "lastName": f"#{self.registration_counter}",
            "email": f"test_{self.registration_counter}@loadtest.com",
            "phone": f"+1-999-{random.randint(100, 999)}-{random.randint(1000, 9999)}",
            "credentialDto": {
                "username": f"test_{self.registration_counter}",
                "password": "TestPassword123",
                "role": "USER"
            }
        }
    
    @task(1)
    def rapid_user_registration(self):
        """Registro rápido y continuo de usuarios"""
        user_data = self._generate_simple_user()
        
        self.client.post("/api/users",
                        json=user_data,
                        name="POST /api/users (high-volume)")


# Configuración por defecto
if __name__ == "__main__":
    print("Prueba de Rendimiento - User Service")
    print("====================================")
    print()
    print("Para ejecutar esta prueba:")
    print()
    print("1. Prueba exploratoria (baja carga):")
    print("   locust -f user_service_load_test.py --host=http://localhost:8080 --users=5 --spawn-rate=1")
    print()
    print("2. Prueba de registros concurrentes:")
    print("   locust -f user_service_load_test.py --host=http://localhost:8080 --users=25 --spawn-rate=3 --run-time=300s")
    print()
    print("3. Prueba de alto volumen (stress):")
    print("   locust -f user_service_load_test.py --host=http://localhost:8080 --users=50 --spawn-rate=5 --run-time=600s")
    print()
    print("4. Acceder a la interfaz web de Locust:")
    print("   locust -f user_service_load_test.py --host=http://localhost:8080")
    print("   Luego abrir: http://localhost:8089")
