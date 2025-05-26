# Pruebas de Rendimiento - E-commerce Microservices

Este directorio contiene pruebas de rendimiento completas para el sistema de microservicios de e-commerce usando Locust.

## 📋 Pruebas Incluidas

### 1. 🛍️ Prueba de Carga en Listado de Productos
**Archivo:** `product_listing_load_test.py`
- **Objetivo:** Evaluar el rendimiento del product-service vía proxy-client
- **Endpoints:** `GET /api/products`, `GET /api/products/{id}`, `GET /api/categories/{id}`
- **Escenarios:** Listado de productos, consulta de detalles, navegación por categorías

### 2. 📦 Prueba de Rendimiento en Creación de Órdenes  
**Archivo:** `order_creation_load_test.py`
- **Objetivo:** Evaluar el rendimiento del order-service bajo picos de carga
- **Endpoints:** `POST /api/orders`, `GET /api/orders/{id}`, `GET /api/orders`
- **Escenarios:** Creación de órdenes, consulta de estado, listado de órdenes

### 3. 👤 Prueba de Capacidad de Respuesta del User Service
**Archivo:** `user_service_load_test.py`  
- **Objetivo:** Evaluar el rendimiento del user-service en registros y consultas
- **Endpoints:** `POST /api/users`, `GET /api/users/{id}`, `PUT /api/users/{id}`
- **Escenarios:** Registro de usuarios, consulta de perfiles, actualización de datos

## 🚀 Configuración y Ejecución

### Prerrequisitos

1. **Python 3.7+** instalado
2. **Locust** instalado:
   ```bash
   pip install locust
   ```
3. **Sistema de microservicios ejecutándose** en Docker Desktop
4. **API Gateway** accesible en `http://localhost:8080`

### Ejecución Rápida

```bash
# Navegar al directorio de pruebas
cd performance-tests

# Ejecutar suite completa con configuración por defecto
python performance_test_suite.py --all

# Ejecutar prueba específica
python performance_test_suite.py --test products
python performance_test_suite.py --test orders
python performance_test_suite.py --test users
```

### Configuraciones Predefinidas

```bash
# Prueba exploratoria (baja carga)
python performance_test_suite.py --all --users 5 --spawn-rate 1 --duration 60

# Prueba de carga normal 
python performance_test_suite.py --all --users 25 --spawn-rate 3 --duration 300

# Prueba de stress (alta carga)
python performance_test_suite.py --all --users 50 --spawn-rate 5 --duration 600

# Prueba de pico extremo
python performance_test_suite.py --all --users 100 --spawn-rate 10 --duration 300
```

### Ejecución con Interfaz Web

```bash
# Iniciar Locust con interfaz web
locust -f product_listing_load_test.py --host=http://localhost:8080

# Abrir navegador en: http://localhost:8089
# Configurar usuarios y duración desde la interfaz
```

## 📊 Métricas y Reportes

### Métricas Clave Monitoreadas

- **Tiempo de Respuesta:** P50, P95, P99
- **Throughput:** Requests por segundo
- **Tasa de Errores:** Porcentaje de fallos
- **Concurrencia:** Usuarios simultáneos
- **Latencia:** Tiempo de primera respuesta

### Archivos de Salida

```
performance_results/
├── {test}_report_{timestamp}.html     # Reporte HTML detallado
├── {test}_stats_{timestamp}.csv       # Estadísticas CSV
├── {test}_results_{timestamp}.json    # Resultados JSON
└── summary_report_{timestamp}.json    # Reporte resumen
```

### Thresholds de Rendimiento

| Endpoint | Tiempo Máximo | Throughput Mínimo |
|----------|---------------|-------------------|
| `GET /api/products` | 2.0s | 50 RPS |
| `GET /api/products/{id}` | 1.0s | 100 RPS |
| `POST /api/orders` | 3.0s | 20 RPS |
| `POST /api/users` | 2.0s | 30 RPS |
| `GET /api/users/{id}` | 1.0s | 75 RPS |

## 🔧 Configuración Avanzada

### Variables de Entorno

```bash
# Host del sistema bajo prueba
export PERF_TEST_HOST=http://localhost:8080

# Directorio de resultados
export PERF_RESULTS_DIR=./performance_results

# Nivel de logging
export PERF_LOG_LEVEL=INFO
```

### Configuración Personalizada

Editar `performance_config.ini` para ajustar:
- Thresholds de rendimiento
- Configuraciones por servicio
- Parámetros de monitoreo
- Formatos de reporte

## 🏗️ Arquitectura de Pruebas

```
Cliente de Prueba (Locust)
    ↓
API Gateway (puerto 8080)
    ↓
Proxy Client (puerto 8700)
    ↓
[Product Service | User Service | Order Service]
    ↓
[Payment Service | Shipping Service] (según el flujo)
```

## 📈 Patrones de Carga Simulados

### Patrón de Usuario Realista
1. **Navegación de Productos** (50% del tiempo)
   - Listado de productos
   - Consulta de detalles
   - Navegación por categorías

2. **Creación de Órdenes** (30% del tiempo)
   - Creación de orden
   - Consulta de estado
   - Verificación de detalles

3. **Gestión de Usuarios** (20% del tiempo)
   - Registro de usuarios
   - Consulta de perfil
   - Actualización de datos

### Patrón de Carga de Stress
- Generación rápida de usuarios
- Tiempo mínimo entre requests
- Enfoque en endpoints críticos
- Simulación de picos de tráfico

## 🐛 Troubleshooting

### Problemas Comunes

**Error: Connection refused**
```bash
# Verificar que el API Gateway esté ejecutándose
docker ps | grep api-gateway

# Verificar conectividad
curl http://localhost:8080/actuator/health
```

**Error: No tests were executed**
```bash
# Verificar que los archivos de prueba estén en el directorio correcto
ls -la *.py

# Ejecutar con modo verbose
locust -f product_listing_load_test.py --host=http://localhost:8080 -v
```

**Error: High failure rate**
```bash
# Revisar logs de los servicios
docker logs {container_name}

# Reducir la carga de prueba
python performance_test_suite.py --test products --users 5 --spawn-rate 1
```

### Logs y Debugging

```bash
# Ejecutar con logging detallado
python performance_test_suite.py --test products --users 10 --duration 60 --verbose

# Revisar archivos de resultados
cat performance_results/products_results_*.json | jq '.stderr'

# Monitorear recursos del sistema
htop
docker stats
```

## 📚 Interpretación de Resultados

### Métricas Críticas

1. **Response Time P95 < 2s:** 95% de las requests responden en menos de 2 segundos
2. **Error Rate < 5%:** Menos del 5% de requests fallan
3. **Throughput > Threshold:** Supera el throughput mínimo esperado
4. **Resource Usage < 80%:** CPU y memoria se mantienen bajo 80%

### Indicadores de Problemas

- ⚠️ **Tiempo de respuesta creciente:** Posible saturación
- ❌ **Alta tasa de errores:** Problemas de configuración o capacidad  
- 🐌 **Throughput decreciente:** Cuellos de botella en el sistema
- 💥 **Fallos de conexión:** Problemas de red o servicios caídos

## 🔄 Integración con CI/CD

### Jenkins Pipeline

```groovy
stage('Performance Tests') {
    steps {
        script {
            sh '''
                cd performance-tests
                python performance_test_suite.py --all --users 25 --duration 180
            '''
        }
    }
    post {
        always {
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'performance-tests/performance_results',
                reportFiles: '*.html',
                reportName: 'Performance Test Report'
            ])
        }
    }
}
```

### GitHub Actions

```yaml
- name: Run Performance Tests
  run: |
    cd performance-tests
    python performance_test_suite.py --all --users 20 --duration 120
    
- name: Upload Performance Reports
  uses: actions/upload-artifact@v3
  with:
    name: performance-reports
    path: performance-tests/performance_results/
```

## 📞 Soporte

Para dudas o problemas con las pruebas de rendimiento:

1. Revisar la documentación del sistema
2. Verificar logs de Docker containers  
3. Consultar archivos de configuración
4. Revisar thresholds de rendimiento

---

**Autor:** Sistema de Pruebas E-commerce  
**Versión:** 1.0  
**Última actualización:** $(date)  
