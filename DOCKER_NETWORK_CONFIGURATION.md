# Docker Network Configuration Update

## 🔧 Network Configuration Changes

### Problem Identified
When Jenkins runs inside a Docker container (not part of the Kubernetes cluster), using `localhost` for API calls fails because:
- `localhost` inside the Jenkins container refers to the container itself
- The Kubernetes services are running on the Docker host
- Network isolation prevents direct access to host services

### Solution Implemented
Changed all network references from `localhost` to `host.docker.internal` to enable proper communication between:
- Jenkins container (where pipeline runs)
- Kubernetes services (running on Docker Desktop)

## 📝 Files Updated

### 1. Jenkinsfile
**Location**: `/Jenkinsfile`
**Changes**:
- `runPerformanceTests()` function connectivity validation
- Performance test execution host parameter
- Debug information URLs

```groovy
# Before
curl -f -s http://localhost/actuator/health

# After  
curl -f -s http://host.docker.internal/actuator/health
```

### 2. Performance Configuration
**Location**: `/performance-tests/performance_config.ini`
**Changes**:
- Default host configuration

```ini
# Before
host = http://localhost

# After
host = http://host.docker.internal
```

### 3. Performance Test Suite
**Location**: `/performance-tests/performance_test_suite.py`
**Changes**:
- Default host parameter in class constructor
- Command line argument default value

```python
# Before
def __init__(self, host: str = "http://localhost"):

# After
def __init__(self, host: str = "http://host.docker.internal"):
```

### 4. Debug Scripts
**Location**: `/performance-tests/debug_order_request.py`
**Changes**:
- API endpoint URLs for order creation tests

```python
# Before
url = "http://localhost/api/orders"

# After
url = "http://host.docker.internal/api/orders"
```

## 🌐 Network Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  Jenkins        │    │  Docker Desktop  │    │  Kubernetes     │
│  Container      │────│  Host            │────│  Services       │
│                 │    │                  │    │                 │
│  Pipeline       │    │  host.docker.    │    │  - api-gateway  │
│  Performance    │────│  internal        │────│  - user-service │
│  Tests          │    │                  │    │  - product-svc  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## 🔍 Verification Steps

### 1. Test Network Connectivity
```bash
# From Jenkins container
curl -f -s http://host.docker.internal/actuator/health

# Expected: HTTP 200 response from API Gateway
```

### 2. Verify Service Endpoints
```bash
# Products endpoint
curl -f -s http://host.docker.internal/api/products/1

# Users endpoint  
curl -f -s http://host.docker.internal/api/users
```

### 3. Performance Test Execution
```bash
cd performance-tests
python3 performance_test_suite.py --host http://host.docker.internal --users 5 --duration 30
```

## 📊 Environment Compatibility

| Environment | Jenkins Location | Target Host | Status |
|-------------|------------------|-------------|--------|
| **Local Dev** | Host Machine | `http://localhost` | ✅ Compatible |
| **Docker Jenkins** | Docker Container | `http://host.docker.internal` | ✅ **Current Setup** |
| **Kubernetes Jenkins** | K8s Pod | Service DNS names | 🔄 Future consideration |

## 🚨 Important Notes

1. **Docker Desktop Required**: `host.docker.internal` is a Docker Desktop specific hostname
2. **Linux Docker**: On Linux, you might need to use `host.docker.internal` or configure `--add-host`
3. **Port Mapping**: Ensure Kubernetes services are properly exposed via Ingress or NodePort
4. **Firewall**: Check that Docker host firewall allows connections from containers

## ✅ Validation Checklist

- [x] Jenkinsfile updated with correct host URLs
- [x] Performance test configuration updated
- [x] Default values in Python scripts updated
- [x] Debug scripts updated
- [x] Documentation updated
- [ ] Test pipeline execution with new configuration
- [ ] Verify HTML report generation
- [ ] Validate performance metrics collection

## 🔧 Troubleshooting

### Common Issues

#### Connection Refused
```bash
curl: (7) Failed to connect to host.docker.internal port 80: Connection refused
```
**Solutions**:
1. Verify Kubernetes services are running: `kubectl get svc -n ecommerce-app`
2. Check Ingress configuration: `kubectl get ingress -n ecommerce-app`
3. Verify port forwarding: `kubectl port-forward svc/api-gateway 80:80 -n ecommerce-app`

#### DNS Resolution Failed
```bash
curl: (6) Could not resolve host: host.docker.internal
```
**Solutions**:
1. Ensure running on Docker Desktop
2. For Linux Docker: Add `--add-host host.docker.internal:host-gateway` to container run command
3. Alternative: Use container IP directly

---

**Status**: ✅ **NETWORK CONFIGURATION COMPLETED**  
**Next Steps**: Test pipeline execution and verify connectivity
