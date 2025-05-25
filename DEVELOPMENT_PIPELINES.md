# Development Environment Construction Pipelines

## Overview

This document describes the development environment construction pipelines included in the Jenkins pipeline configuration. These pipelines are specifically designed for local development with Docker Desktop and are currently **commented out for testing purposes**.

## Pipeline Structure

### 1. Core Development Environment Pipeline

**Purpose**: Sets up the complete development environment with selected microservices.

**Features**:
- Development-specific infrastructure (H2 database, Redis)
- Core services construction (service-discovery, cloud-config, api-gateway)
- Selected microservices deployment
- Debug configuration with remote debugging capabilities
- Local Kubernetes deployment with Docker Desktop

**Parameters**:
- `DEV_BUILD_TYPE`: Choose between `incremental`, `full-rebuild`, or `services-only`
- `ENABLE_DEBUG`: Enable debug ports for remote debugging (default: true)
- `SETUP_LOCAL_DB`: Configure local H2 database (default: true)
- `SELECTED_SERVICES`: Comma-separated list of services to build

### 2. Service-Specific Development Pipelines

#### User Service Development Pipeline
- **Port**: 8081 (HTTP), 5006 (Debug)
- **Database**: H2 in-memory with user-specific schema
- **Features**:
  - Swagger UI enabled
  - Test data generation
  - User profile management
  - Authentication endpoints

#### Product Service Development Pipeline
- **Port**: 8082 (HTTP), 5007 (Debug)
- **Database**: H2 in-memory with product catalog schema
- **Features**:
  - Product inventory management
  - Category management
  - Image upload simulation
  - Auto-restock capabilities for development

#### Order Service Development Pipeline
- **Port**: 8083 (HTTP), 5008 (Debug)
- **Database**: H2 in-memory with order management schema
- **Features**:
  - Order processing simulation
  - Integration with other services
  - Payment timeout configuration
  - Order status tracking

## Environment Configuration

### Development Infrastructure

```yaml
# Namespace
DEV_NAMESPACE: ecommerce-dev

# Database Configuration
Database Type: H2 (in-memory)
URL: jdbc:h2:mem:ecommerce_dev
Web Console: http://localhost:8081/h2-console

# Redis Configuration
Redis URL: redis://localhost:6379/1
Purpose: Caching and session management

# Docker Registry
Registry: localhost:5000
Images: Tagged as *-service:dev-latest
```

### Debug Configuration

Each service gets a unique debug port starting from 5005:
- service-discovery: 5005
- cloud-config: 5005
- api-gateway: 5005
- user-service: 5006
- product-service: 5007
- order-service: 5008
- payment-service: 5009
- shipping-service: 5010

### Spring Profiles

All services use development-specific profiles:
- `SPRING_PROFILES_ACTIVE=development,local`
- `MAVEN_PROFILES=development,debug`

## How to Use Development Pipelines

### 1. Enabling Development Pipelines

To enable the development environment pipelines, uncomment the desired sections in the Jenkinsfile:

```bash
# For core development environment
# Uncomment the "DEVELOPMENT ENVIRONMENT - CORE SERVICES CONSTRUCTION PIPELINE" section

# For specific service development
# Uncomment individual service pipeline sections as needed
```

### 2. Running Development Build

1. **Full Development Environment**:
   ```
   Jenkins Job: ecommerce-microservices
   Parameters:
   - ENVIRONMENT: development (if parameter exists)
   - DEV_BUILD_TYPE: full-rebuild
   - ENABLE_DEBUG: true
   - SETUP_LOCAL_DB: true
   - SELECTED_SERVICES: user-service,product-service,order-service
   ```

2. **Specific Service Development**:
   ```
   Jenkins Job: ecommerce-microservices
   Parameters:
   - TARGET_SERVICE: user-service (or specific service)
   - INCLUDE_TEST_DATA: true
   - ENABLE_SWAGGER: true
   ```

### 3. Port Forwarding Setup

After deployment, configure port forwarding to access services:

```bash
# User Service
kubectl port-forward service/user-service-service 8081:8080 -n ecommerce-dev &
kubectl port-forward service/user-service-service 5006:5005 -n ecommerce-dev &

# Product Service  
kubectl port-forward service/product-service-service 8082:8080 -n ecommerce-dev &
kubectl port-forward service/product-service-service 5007:5005 -n ecommerce-dev &

# Order Service
kubectl port-forward service/order-service-service 8083:8080 -n ecommerce-dev &
kubectl port-forward service/order-service-service 5008:5005 -n ecommerce-dev &

# H2 Database Console
kubectl port-forward service/h2-service 8090:81 -n ecommerce-dev &

# Redis
kubectl port-forward service/redis-service 6379:6379 -n ecommerce-dev &
```

## Development Features

### 1. Database Management

**H2 Console Access**:
- URL: http://localhost:8090 (after port forwarding)
- JDBC URL: `jdbc:h2:mem:ecommerce_dev`
- Username: `sa`
- Password: `password`

**Test Data**:
- Automatically populated when `INCLUDE_TEST_DATA=true`
- Sample users, products, and orders
- Realistic data for development and testing

### 2. API Documentation

**Swagger UI**:
- User Service: http://localhost:8081/swagger-ui.html
- Product Service: http://localhost:8082/swagger-ui.html
- Order Service: http://localhost:8083/swagger-ui.html

### 3. Debug Configuration

**Remote Debugging**:
- Connect your IDE to the debug ports
- IntelliJ IDEA: Run → Edit Configurations → Remote JVM Debug
- VS Code: Use Java debug configuration
- Eclipse: Debug Configurations → Remote Java Application

**Example Debug Configuration**:
```json
{
    "type": "java",
    "name": "Debug User Service",
    "request": "attach",
    "hostName": "localhost",
    "port": 5006
}
```

### 4. Monitoring and Health Checks

**Actuator Endpoints**:
- Health: http://localhost:8081/actuator/health
- Metrics: http://localhost:8081/actuator/metrics
- Info: http://localhost:8081/actuator/info

## Pipeline Stages

### Core Development Pipeline Stages

1. **Development Environment Initialization**
   - Verify development tools
   - Create development namespace
   - Set up labels and configuration

2. **Development Infrastructure Setup** (Parallel)
   - Setup Local Database (H2)
   - Setup Development Redis

3. **Build Core Services for Development**
   - Build selected services with development profile
   - Generate debug configuration
   - Create development-specific configs

4. **Development Docker Images**
   - Build development-specific Docker images
   - Include debugging tools
   - Configure development environment variables

5. **Deploy to Development Environment**
   - Generate Kubernetes manifests for development
   - Deploy services to ecommerce-dev namespace
   - Configure service discovery

6. **Development Environment Validation**
   - Wait for services to be ready
   - Validate deployments
   - Generate port forwarding commands

### Service-Specific Pipeline Stages

1. **Service Development Build**
   - Build with development profile
   - Generate service-specific configuration
   - Create test data if enabled

2. **Service Docker Development**
   - Build development Docker image
   - Include debugging capabilities
   - Configure service-specific environment

## Troubleshooting

### Common Issues

1. **Services Not Starting**:
   ```bash
   # Check pod status
   kubectl get pods -n ecommerce-dev
   
   # Check logs
   kubectl logs -f deployment/user-service-dev -n ecommerce-dev
   ```

2. **Port Forwarding Issues**:
   ```bash
   # Kill existing port forwards
   pkill -f "kubectl port-forward"
   
   # Restart port forwarding
   kubectl port-forward service/user-service-service 8081:8080 -n ecommerce-dev
   ```

3. **Database Connection Issues**:
   ```bash
   # Check H2 deployment
   kubectl get deployment h2-database -n ecommerce-dev
   
   # Restart H2 if needed
   kubectl rollout restart deployment/h2-database -n ecommerce-dev
   ```

### Debug Information

The pipeline generates debug information on failure:
- Kubernetes resource status
- Pod descriptions
- Docker image list
- Deployment logs

## Best Practices

### 1. Service Selection
- Start with core services (user-service, product-service)
- Add additional services as needed
- Consider dependencies between services

### 2. Resource Management
- Development containers use limited resources
- Clean up unused images regularly
- Monitor namespace resource usage

### 3. Development Workflow
1. Enable specific service pipeline
2. Make code changes
3. Run development build
4. Test using port forwarding
5. Debug using remote debugging
6. Iterate and improve

## Security Considerations

### Development Environment
- Uses in-memory databases (data not persisted)
- Debug ports exposed only locally
- No authentication for development endpoints
- H2 console enabled for easy database access

### Production Differences
- Production uses persistent databases
- Debug ports disabled
- Full authentication and authorization
- Monitoring and logging enabled
- Security scanning and compliance checks

## Next Steps

1. **Uncomment Desired Pipelines**: Enable the development pipelines you need
2. **Configure IDE**: Set up remote debugging in your development environment
3. **Test Services**: Use Swagger UI and direct API calls for testing
4. **Monitor Performance**: Use actuator endpoints for monitoring
5. **Iterate Development**: Make changes and rebuild as needed

---

*This document is part of the comprehensive Jenkins CI/CD pipeline for e-commerce microservices development.*
