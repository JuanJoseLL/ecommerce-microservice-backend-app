# Pipeline de Jenkins - E-commerce Microservices

## Descripción General

Este pipeline de Jenkins implementa un flujo completo de CI/CD para los microservicios de e-commerce, incluyendo construcción, pruebas y despliegue en Kubernetes usando Docker Desktop.

## Estructura del Pipeline

### Parámetros de Configuración

- **ENVIRONMENT**: Selección entre 'stage' y 'master'
- **BUILD_TAG**: Tag para las imágenes Docker (por defecto: BUILD_ID)
- **SKIP_TESTS**: Omitir todas las pruebas (solo para emergencias)
- **GENERATE_RELEASE_NOTES**: Generar notas de release automáticamente

### Etapas del Pipeline

#### 1. Checkout & Workspace Verification
- Verifica la estructura del workspace
- Confirma la existencia de archivos clave (pom.xml de cada microservicio)
- Valida la integridad del código fuente

#### 2. Initialize Docker & Kubernetes
- Configura el contexto de Kubernetes (Docker Desktop)
- Verifica la conectividad con el cluster
- Crea el namespace si no existe

#### 3. Unit Tests
**Microservicios Incluidos:**
- user-service
- order-service
- payment-service
- shipping-service
- proxy-client

**Pruebas Ejecutadas:**
- `UserServiceImplTest` - Validación de lógica de creación de usuarios
- `OrderServiceImplTest` - Validación de cálculo y creación de órdenes
- `PaymentServiceImplTest` - Validación de procesamiento de pagos
- `OrderItemServiceImplTest` - Validación de cálculos de envío
- `ProductControllerTest` - Validación de controladores proxy

#### 4. Integration Tests
**Ubicación:** proxy-client
**Pruebas Ejecutadas:**
- `UserServiceIntegrationTest` - Comunicación proxy-client <-> user-service
- `ProductServiceIntegrationTest` - Comunicación proxy-client <-> product-service
- `OrderServiceIntegrationTest` - Comunicación proxy-client <-> order-service
- `PaymentServiceIntegrationTest` - Comunicación proxy-client <-> payment-service
- `ShippingServiceIntegrationTest` - Comunicación proxy-client <-> shipping-service

#### 5. End-to-End Tests
**Ubicación:** proxy-client
**Flujos Validados:**
- `UserRegistrationEndToEndTest` - Registro completo de usuario
- `ProductViewEndToEndTest` - Visualización de detalles de producto
- `OrderCreationEndToEndTest` - Creación simplificada de orden
- `PaymentProcessingEndToEndTest` - Procesamiento de pago
- `ShippingInitiationEndToEndTest` - Inicio de envío

#### 6. Build & Package
- Construcción Maven de todos los microservicios
- Validación de JARs generados
- Preparación para contenerización

#### 7. Docker Build & Push
- Construcción de imágenes Docker para cada microservicio
- Push al registry local (localhost:5000)
- Etiquetado con BUILD_TAG

#### 8. Deploy Infrastructure Services
- Aplicación de manifiestos comunes (namespace, configmaps)
- Despliegue de Zipkin (tracing distribuido)
- Despliegue de Service Discovery (Eureka)
- Despliegue de Cloud Config Server

#### 9. Deploy Application Services
- Despliegue de microservicios de aplicación
- Actualización de manifiestos con nuevas imágenes
- Verificación de rollout exitoso

#### 10. System Validation Tests (Solo Master)
- Verificación de estado de pods
- Smoke tests básicos
- Validación de conectividad

#### 11. Generate Release Notes
- Generación automática de notas de release
- Información de build y despliegue
- Resumen de cambios y commits
- Archivo en Markdown

## Configuración para Docker Desktop

### Variables de Entorno
```groovy
DOCKER_REGISTRY = 'localhost:5000'
K8S_CONTEXT = 'docker-desktop'
K8S_TARGET_NAMESPACE = 'ecommerce-app'
```

### Requisitos Previos
1. Docker Desktop con Kubernetes habilitado
2. kubectl configurado para Docker Desktop
3. Registry local opcional (localhost:5000)

## Funciones Helper

### buildAndPushDockerImage()
- Construye imágenes Docker para microservicios
- Maneja push al registry con fallback a local

### deployMicroservice()
- Despliega microservicios en Kubernetes
- Actualiza manifiestos con nuevas imágenes
- Maneja servicios e ingress

### runSmokeTests()
- Ejecuta pruebas básicas de conectividad
- Verifica estado de servicios críticos

### generateReleaseNotes()
- Genera documentación automática de release
- Incluye información de Git y estado de pruebas

## Pruebas de Rendimiento (Futuras)

### Estado Actual
Las pruebas de rendimiento están **temporalmente deshabilitadas** para permitir la implementación inicial del pipeline.

### Ubicación en Código
- Stage: Comentado en líneas ~375-400
- Función: `runPerformanceTests()` comentada

### Implementación Futura
Cuando se implementen las pruebas de rendimiento, incluirán:

1. **Carga en Listado de Productos**
   - Endpoint: `/api/products`
   - Carga: Múltiples usuarios concurrentes
   - Métricas: Tiempo de respuesta, throughput

2. **Rendimiento en Creación de Órdenes**
   - Endpoint: `/api/orders`
   - Escenario: Pico de carga en creación
   - Métricas: Órdenes por segundo, latencia

3. **Capacidad de User Service**
   - Endpoints: `/api/users`
   - Escenario: Registros concurrentes
   - Métricas: Tiempo de respuesta bajo carga

### Herramientas Sugeridas
- JMeter para pruebas de carga HTTP
- Gatling para pruebas de rendimiento avanzadas
- Script personalizado `performance-tests.sh` (ya disponible)

## Ejecución del Pipeline

### Para Stage Environment
```bash
# En Jenkins UI
- ENVIRONMENT: stage
- BUILD_TAG: (automático)
- SKIP_TESTS: false
- GENERATE_RELEASE_NOTES: true
```

### Para Master Environment
```bash
# En Jenkins UI
- ENVIRONMENT: master
- BUILD_TAG: (automático o específico)
- SKIP_TESTS: false
- GENERATE_RELEASE_NOTES: true
```

## Monitoreo y Debugging

### Verificación Post-Despliegue
```bash
kubectl get pods -n ecommerce-app
kubectl get services -n ecommerce-app
kubectl logs -f deployment/api-gateway -n ecommerce-app
```

### Zipkin Tracing
- Acceso a través del servicio zipkin en el namespace
- Port-forward: `kubectl port-forward service/zipkin 9411:9411 -n ecommerce-app`
- URL: http://localhost:9411

### Logs de Jenkins
- Artifacts de pruebas archivados automáticamente
- Release notes generadas y almacenadas
- Logs detallados de cada etapa

## Próximos Pasos

1. **Implementar Pruebas de Rendimiento**
   - Descomentar stage de Performance Tests
   - Implementar herramientas de carga
   - Configurar métricas y umbrales

2. **Mejoras de Monitoreo**
   - Integración con Prometheus/Grafana
   - Alertas automáticas
   - Dashboards de métricas

3. **Notificaciones**
   - Slack/Teams para resultados de pipeline
   - Email para fallos críticos
   - Webhooks para integraciones

4. **Seguridad**
   - Escaneo de vulnerabilidades en imágenes
   - Validación de dependencias
   - Políticas de seguridad en Kubernetes

## Configuración Local de Jenkins

### Ejecución Manual (Recomendada para Desarrollo Local)

Este pipeline está optimizado para Jenkins corriendo en un contenedor Docker con Docker Desktop. Los triggers automáticos están **DESHABILITADOS** intencionalmente por las siguientes razones:

1. **GitHub Webhooks**: No funcionan con localhost - GitHub no puede enviar webhooks a tu máquina local
2. **SCM Polling**: Consume recursos innecesariamente en entorno de desarrollo
3. **Cron Jobs**: No son necesarios para desarrollo local donde prefieres control manual

### Cómo Ejecutar el Pipeline

1. **Accede a Jenkins**: `http://localhost:8080` (o el puerto configurado)
2. **Ve al proyecto**: Encuentra tu pipeline en la lista
3. **Click en "Build with Parameters"**
4. **Configura los parámetros**:
   - Environment: `stage` (para pruebas) o `master` (para validación completa)
   - Build Tag: Deja el valor por defecto o personaliza
   - Skip Tests: `false` (recomendado para ejecutar todas las pruebas)
   - Generate Release Notes: `true` (para documentación automática)
5. **Click "Build"**

### Prerequisites Locales

- Docker Desktop corriendo con Kubernetes habilitado
- Jenkins corriendo en contenedor con acceso a Docker socket
- Registry local opcional en `localhost:5000`
- Kubectl configurado con contexto `docker-desktop`
