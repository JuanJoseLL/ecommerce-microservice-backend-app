#!/bin/bash

# Network Connectivity Validation Script
# ======================================
# This script validates the network connectivity between Jenkins container 
# and Kubernetes services using the new host.docker.internal configuration

echo "🔍 Jenkins-Kubernetes Network Connectivity Validation"
echo "====================================================="
echo

# Function to test endpoint connectivity
test_endpoint() {
    local endpoint=$1
    local description=$2
    
    echo -n "Testing $description... "
    
    if curl -f -s --max-time 10 "$endpoint" >/dev/null 2>&1; then
        echo "✅ OK"
        return 0
    else
        echo "❌ FAILED"
        return 1
    fi
}

# Test basic connectivity
echo "📡 Basic Connectivity Tests"
echo "-----------------------------"

test_endpoint "http://host.docker.internal/actuator/health" "API Gateway Health Check"
test_endpoint "http://host.docker.internal/api/products" "Products Endpoint"
test_endpoint "http://host.docker.internal/api/users" "Users Endpoint"

echo

# Test Kubernetes services status
echo "🚀 Kubernetes Services Status"
echo "------------------------------"

if command -v kubectl >/dev/null 2>&1; then
    echo "Checking Kubernetes services in ecommerce-app namespace..."
    
    if kubectl get namespace ecommerce-app >/dev/null 2>&1; then
        echo "✅ Namespace 'ecommerce-app' exists"
        
        echo
        echo "Services status:"
        kubectl get svc -n ecommerce-app 2>/dev/null || echo "❌ No services found or kubectl access denied"
        
        echo
        echo "Pods status:"
        kubectl get pods -n ecommerce-app 2>/dev/null || echo "❌ No pods found or kubectl access denied"
        
        echo
        echo "Ingress status:"
        kubectl get ingress -n ecommerce-app 2>/dev/null || echo "ℹ️ No ingress found"
        
    else
        echo "❌ Namespace 'ecommerce-app' not found"
    fi
else
    echo "⚠️ kubectl not found - cannot check Kubernetes status"
fi

echo

# Test performance test dependencies
echo "🐍 Performance Test Dependencies"
echo "---------------------------------"

if command -v python3 >/dev/null 2>&1; then
    echo -n "Testing Python 3... "
    echo "✅ Available"
    
    echo -n "Testing Locust... "
    if python3 -c "import locust" 2>/dev/null; then
        echo "✅ Available"
    else
        echo "❌ Not installed"
        echo "   Run: pip3 install locust"
    fi
    
    echo -n "Testing Requests... "
    if python3 -c "import requests" 2>/dev/null; then
        echo "✅ Available"
    else
        echo "❌ Not installed" 
        echo "   Run: pip3 install requests"
    fi
    
    echo -n "Testing ConfigParser... "
    if python3 -c "import configparser" 2>/dev/null; then
        echo "✅ Available"
    else
        echo "❌ Not installed"
        echo "   Run: pip3 install configparser"
    fi
    
else
    echo "❌ Python 3 not found"
fi

echo

# Test performance test execution
echo "🎯 Performance Test Quick Validation"
echo "------------------------------------"

if [ -f "performance-tests/performance_test_suite.py" ]; then
    echo "✅ Performance test suite found"
    
    cd performance-tests 2>/dev/null || {
        echo "❌ Cannot access performance-tests directory"
        exit 1
    }
    
    echo "Testing configuration file..."
    if [ -f "performance_config.ini" ]; then
        echo "✅ Configuration file found"
        echo "Default host: $(grep "^host" performance_config.ini | cut -d'=' -f2 | xargs)"
    else
        echo "❌ Configuration file not found"
    fi
    
    echo
    echo "Testing dry-run execution..."
    if python3 performance_test_suite.py --help >/dev/null 2>&1; then
        echo "✅ Performance test suite can be executed"
    else
        echo "❌ Performance test suite execution failed"
    fi
    
else
    echo "❌ Performance test suite not found"
fi

echo
echo "🏁 Validation Complete"
echo "======================"
echo
echo "Next steps:"
echo "1. If any connectivity tests failed, check Kubernetes deployment"
echo "2. If dependencies are missing, install them with pip3"
echo "3. Run a full Jenkins pipeline to test integration"
echo
echo "To run performance tests manually:"
echo "cd performance-tests"
echo "python3 performance_test_suite.py --host http://host.docker.internal --users 5 --duration 30"
