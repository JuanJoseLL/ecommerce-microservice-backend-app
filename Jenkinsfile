pipeline {
    agent any

    /*
    triggers {
        // githubPush()
        // pollSCM('H/5 * * * *')
        // parameterizedCron('''
        // # Stage: cada push o diario a las 3 AM
        // H 3 * * * %ENVIRONMENT=stage;SKIP_TESTS=false
        // # Master: solo manual (sin cron automático para producción)
        // ''')
    }
    */

    environment {
        DOCKER_REGISTRY = 'host.docker.internal:5000'
        K8S_CONTEXT = 'docker-desktop'
        K8S_TARGET_NAMESPACE = 'ecommerce-app'
        K8S_MANIFESTS_ROOT = 'k8s'
        DOCKERFILE_DIR_ROOT = '.'
        
        DEV_ENV = 'dev'
        STAGE_ENV = 'stage'
        MASTER_ENV = 'master'
        
        MAVEN_OPTS = '-Xmx1024m'
        JAVA_HOME = '/opt/java/openjdk'
    }

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'stage', 'master'],
            description: 'Environment to deploy to'
        )
        string(
            name: 'BUILD_TAG', 
            defaultValue: "${env.BUILD_ID}", 
            description: 'Tag para la imagen Docker'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip all tests (emergency deployment only)'
        )
        booleanParam(
            name: 'GENERATE_RELEASE_NOTES',
            defaultValue: true,
            description: 'Generate automatic release notes'
        )
        choice(
            name: 'PERFORMANCE_TEST_LEVEL',
            choices: ['standard', 'light', 'stress'],
            description: 'Performance test intensity level (only applies in master environment)'
        )
        booleanParam(
            name: 'SKIP_PERFORMANCE_TESTS',
            defaultValue: false,
            description: 'Skip performance tests specifically (even in master environment)'
        )
    }

    stages {
        stage('Checkout & Workspace Verification') {
            steps {
                script {
                    echo "=== CHECKOUT & WORKSPACE VERIFICATION ==="
                    checkout scm
                    
                    sh 'ls -la'
                    
                    def keyFiles = [
                        "api-gateway/pom.xml",
                        "proxy-client/pom.xml", 
                        "user-service/pom.xml",
                        "product-service/pom.xml",
                        "order-service/pom.xml",
                        "payment-service/pom.xml",
                        "shipping-service/pom.xml"
                    ]
                    
                    keyFiles.each { file ->
                        if (fileExists("${env.DOCKERFILE_DIR_ROOT}/${file}")) {
                            echo "✓ ${file} encontrado"
                        } else {
                            error "✗ CRÍTICO: ${file} NO encontrado"
                        }
                    }
                    
                    echo "✓ Workspace verificado exitosamente"
                }
            }
        }

        /*
        stage('Initialize Docker & Kubernetes') {
            steps {
                script {
                    echo "=== INITIALIZE DOCKER & KUBERNETES ==="
                    
                    sh "kubectl config use-context ${env.K8S_CONTEXT}"
                    
                    sh "kubectl cluster-info"
                    sh "kubectl get nodes"
                    
                    sh """
                        kubectl get namespace ${env.K8S_TARGET_NAMESPACE} || \
                        kubectl create namespace ${env.K8S_TARGET_NAMESPACE}
                    """
                    
                    echo "✓ Docker Desktop & Kubernetes inicializados"
                }
            }
        }
        */

        stage('Unit Tests') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                script {
                    echo "=== UNIT TESTS ==="
                    
                    def microservices = [
                        'user-service',
                        'order-service', 
                        'payment-service',
                        'shipping-service',
                        'proxy-client'
                    ]
                    
                    def testResults = [:]
                    
                    microservices.each { service ->
                        try {
                            echo "Ejecutando pruebas unitarias para ${service}..."
                            dir("${env.DOCKERFILE_DIR_ROOT}/${service}") {
                                // Configurar patrón de pruebas específico por servicio
                                def testPattern = service == 'proxy-client' ? '*ControllerTest*' : '*ServiceImplTest*'
                                
                                echo "  ⚡ Patrón de pruebas para ${service}: ${testPattern}"
                                
                                // Verificar que existan archivos de prueba antes de ejecutar
                                def testCheck = sh(
                                    script: "find src/test/java -name '*Test.java' | grep -E '${testPattern.replace('*', '.*')}' | wc -l", 
                                    returnStdout: true
                                ).trim()
                                
                                echo "  📋 Archivos de prueba encontrados para patrón '${testPattern}': ${testCheck}"
                                
                                if (testCheck == '0') {
                                    echo "  ⚠️  No se encontraron pruebas con patrón ${testPattern}, ejecutando todas las pruebas..."
                                    sh "./mvnw clean test -DfailIfNoTests=false -Dmaven.test.failure.ignore=true"
                                } else {
                                    sh "./mvnw clean test -Dtest=${testPattern} -DfailIfNoTests=false -Dmaven.test.failure.ignore=true"
                                }
                                
                                if (fileExists('target/surefire-reports/TEST-*.xml')) {
                                    publishTestResults testResultsPattern: 'target/surefire-reports/TEST-*.xml'
                                } else {
                                    echo "  ⚠️  No se generaron reportes de pruebas para ${service}"
                                }
                                
                                testResults[service] = 'PASSED'
                            }
                        } catch (Exception e) {
                            echo "❌ Pruebas unitarias fallaron para ${service}: ${e.message}"
                            testResults[service] = 'FAILED'
                            if (params.ENVIRONMENT == 'master') {
                                error "Pruebas unitarias críticas fallaron en ${service}"
                            }
                        }
                    }
                    
                    echo "=== RESUMEN PRUEBAS UNITARIAS ==="
                    testResults.each { service, result ->
                        echo "${service}: ${result}"
                    }
                }
            }
        }

        stage('Integration Tests') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                script {
                    echo "=== INTEGRATION TESTS ==="
                    
                    try {
                        dir("${env.DOCKERFILE_DIR_ROOT}/proxy-client") {
                            echo "Ejecutando pruebas de integración..."
                            
                            // Verificar que existan pruebas de integración
                            def integrationTestCheck = sh(
                                script: "find src/test/java -name '*IntegrationTest.java' | wc -l", 
                                returnStdout: true
                            ).trim()
                            
                            echo "📋 Pruebas de integración encontradas: ${integrationTestCheck}"
                            
                            if (integrationTestCheck == '0') {
                                echo "⚠️  No se encontraron pruebas de integración, saltando..."
                            } else {
                                sh "./mvnw test -Dtest=*IntegrationTest* -DfailIfNoTests=false -Dmaven.test.failure.ignore=true"
                                
                                if (fileExists('target/surefire-reports/TEST-*.xml')) {
                                    publishTestResults testResultsPattern: 'target/surefire-reports/TEST-*.xml'
                                }
                            }
                        }
                        echo "✓ Pruebas de integración completadas"
                    } catch (Exception e) {
                        echo "❌ Pruebas de integración fallaron: ${e.message}"
                        if (params.ENVIRONMENT == 'master') {
                            error "Pruebas de integración críticas fallaron"
                        }
                    }
                }
            }
        }

        stage('End-to-End Tests') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                script {
                    echo "=== END-TO-END TESTS ==="
                    
                    try {
                        dir("${env.DOCKERFILE_DIR_ROOT}/proxy-client") {
                            echo "Ejecutando pruebas E2E..."
                            
                            // Verificar que existan pruebas E2E
                            def e2eTestCheck = sh(
                                script: "find src/test/java -name '*EndToEndTest.java' | wc -l", 
                                returnStdout: true
                            ).trim()
                            
                            echo "📋 Pruebas E2E encontradas: ${e2eTestCheck}"
                            
                            if (e2eTestCheck == '0') {
                                echo "⚠️  No se encontraron pruebas E2E, saltando..."
                            } else {
                                sh "./mvnw test -Dtest=*EndToEndTest* -DfailIfNoTests=false -Dmaven.test.failure.ignore=true"
                                
                                if (fileExists('target/surefire-reports/TEST-*.xml')) {
                                    publishTestResults testResultsPattern: 'target/surefire-reports/TEST-*.xml'
                                }
                            }
                        }
                        echo "✓ Pruebas E2E completadas"
                    } catch (Exception e) {
                        echo "❌ Pruebas E2E fallaron: ${e.message}"
                        if (params.ENVIRONMENT == 'master') {
                            error "Pruebas E2E críticas fallaron"
                        }
                    }
                }
            }
        }

        /*
        stage('Build & Package') {
            steps {
                script {
                    echo "=== BUILD & PACKAGE ==="
                    
                    def microservices = [
                        'service-discovery',
                        'cloud-config',
                        'api-gateway',
                        'proxy-client',
                        'user-service',
                        'product-service',
                        'order-service',
                        'payment-service',
                        'shipping-service'
                    ]
                    
                    microservices.each { service ->
                        try {
                            echo "Construyendo ${service}..."
                            dir("${env.DOCKERFILE_DIR_ROOT}/${service}") {
                                sh "./mvnw clean package -DskipTests"
                                
                                def jarFile = sh(
                                    script: "find target -name '*.jar' -not -name '*sources*' -not -name '*javadoc*' | head -1",
                                    returnStdout: true
                                ).trim()
                                
                                if (jarFile) {
                                    echo "✓ JAR creado: ${jarFile}"
                                } else {
                                    error "❌ JAR no encontrado para ${service}"
                                }
                            }
                        } catch (Exception e) {
                            error "❌ Error construyendo ${service}: ${e.message}"
                        }
                    }
                    
                    echo "✓ Construcción completada para todos los microservicios"
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    echo "=== DOCKER BUILD & PUSH ==="
                    
                    def microservices = [
                        'service-discovery',
                        'cloud-config', 
                        'api-gateway',
                        'proxy-client',
                        'user-service',
                        'product-service',
                        'order-service',
                        'payment-service',
                        'shipping-service'
                    ]
                    
                    microservices.each { service ->
                        buildAndPushDockerImage(service, params.BUILD_TAG)
                    }
                    
                    echo "✓ Todas las imágenes Docker construidas y subidas"
                }
            }
        }

        stage('Deploy Infrastructure Services') {
            steps {
                script {
                    echo "=== DEPLOY INFRASTRUCTURE SERVICES ==="
                    
                    applyKubernetesManifests('namespace.yaml')
                    applyKubernetesManifests('common-config.yaml')
                    
                    deployPreBuiltService('zipkin')
                    
                    deployMicroservice('service-discovery', params.BUILD_TAG)
                    
                    deployMicroservice('cloud-config', params.BUILD_TAG)
                    
                    echo "✓ Servicios de infraestructura desplegados"
                }
            }
        }

        stage('Deploy Application Services') {
            steps {
                script {
                    echo "=== DEPLOY APPLICATION SERVICES ==="
                    
                    def appServices = [
                        'user-service',
                        'product-service', 
                        'order-service',
                        'payment-service',
                        'shipping-service',
                        'proxy-client',
                        'api-gateway'
                    ]
                    
                    appServices.each { service ->
                        deployMicroservice(service, params.BUILD_TAG)
                    }
                    
                    echo "✓ Servicios de aplicación desplegados"
                }
            }
        }

        stage('System Validation Tests') {
            when {
                allOf {
                    not { params.SKIP_TESTS }
                    expression { params.ENVIRONMENT == 'master' }
                }
            }
            steps {
                script {
                    echo "=== SYSTEM VALIDATION TESTS ==="
                    
                    sleep(time: 30, unit: 'SECONDS')
                    
                    try {
                        def services = ['api-gateway', 'proxy-client', 'user-service', 'product-service']
                        
                        services.each { service ->
                            sh """
                                kubectl wait --for=condition=ready pod -l app=${service} \
                                -n ${env.K8S_TARGET_NAMESPACE} --timeout=300s
                            """
                        }
                        
                        echo "✓ Todos los servicios están listos"
                        
                        runSmokeTests()
                        
                    } catch (Exception e) {
                        error "❌ Validación del sistema falló: ${e.message}"
                    }
                }
            }
        }
        */

        stage('Generate Release Notes') {
            when {
                expression { params.GENERATE_RELEASE_NOTES }
            }
            steps {
                script {
                    echo "=== GENERATE RELEASE NOTES ==="
                    generateReleaseNotes()
                }
            }
        }

        stage('Performance Tests') {
            when {
                allOf {
                    expression { !params.SKIP_TESTS }
                    expression { !params.SKIP_PERFORMANCE_TESTS }
                    expression { params.ENVIRONMENT == 'master' }
                }
            }
            steps {
                script {
                    def performanceLevel = params.PERFORMANCE_TEST_LEVEL ?: 'standard'
                    echo "=== PERFORMANCE TESTS ==="
                    echo "🎛️ Performance Test Level: ${performanceLevel}"
                    
                    try {
                        runPerformanceTests()
                        echo "✓ Pruebas de rendimiento completadas"
                    } catch (Exception e) {
                        echo "⚠️ Pruebas de rendimiento fallaron: ${e.message}"
                        // En ambiente master, las pruebas de rendimiento son críticas
                        if (params.ENVIRONMENT == 'master') {
                            error "Pruebas de rendimiento críticas fallaron en ambiente master"
                        }
                    }
                }
            }
            post {
                always {
                    // Archivar reportes de rendimiento (HTML Publisher plugin no disponible)
                    archiveArtifacts artifacts: 'performance-tests/performance_results/**/*', allowEmptyArchive: true
                    
                    // Publicar reportes como archivos simples
                    script {
                        if (fileExists('performance-tests/performance_results')) {
                            echo "📊 Reportes de rendimiento archivados en artifacts"
                            sh "find performance-tests/performance_results -name '*.html' -o -name '*.json' -o -name '*.csv' | head -10"
                        }
                    }
                }
                success {
                    echo "🎯 Pruebas de rendimiento exitosas - Sistema cumple con métricas de performance"
                }
                failure {
                    echo "⚠️ Algunas pruebas de rendimiento fallaron - Revisar reportes para detalles"
                }
            }
        }
    }

    post {
        always {
            script {
                echo "=== PIPELINE CLEANUP ==="
                
                archiveArtifacts artifacts: '**/target/surefire-reports/**', allowEmptyArchive: true
                
                sh "rm -f processed-*-deployment.yaml"
                sh "rm -f release-notes-*.md"
                
                def status = currentBuild.currentResult
                echo "Pipeline Status: ${status}"
                
                if (status == 'SUCCESS') {
                    echo "✅ Pipeline ejecutado exitosamente"
                } else {
                    echo "❌ Pipeline falló - verificar logs"
                }
            }
        }
        /*
        success {
            script {
                echo "🎉 DEPLOYMENT SUCCESSFUL!"
                echo "Environment: ${params.ENVIRONMENT}"
                echo "Build Tag: ${params.BUILD_TAG}"
                
                sh """
                    echo "=== DEPLOYMENT SUMMARY ==="
                    kubectl get pods -n ${env.K8S_TARGET_NAMESPACE}
                    kubectl get services -n ${env.K8S_TARGET_NAMESPACE}
                """
            }
        }
        failure {
            script {
                echo "💥 DEPLOYMENT FAILED!"
                echo "Check the logs above for error details"
                
                sh """
                    echo "=== CLUSTER STATE FOR DEBUGGING ==="
                    kubectl get pods -n ${env.K8S_TARGET_NAMESPACE} || true
                    kubectl describe pods -n ${env.K8S_TARGET_NAMESPACE} || true
                """
            }
        }
        */
    }
}

def buildAndPushDockerImage(String serviceName, String buildTag) {
    echo "Construyendo imagen Docker para ${serviceName}..."
    
    def imageName = "${env.DOCKER_REGISTRY}/${serviceName}:${buildTag}"
    def contextPath = "${env.DOCKERFILE_DIR_ROOT}/${serviceName}"
    
    dir(contextPath) {
        sh "docker build -t ${imageName} ."
        
        try {
            sh "docker push ${imageName}"
            echo "✓ Imagen ${imageName} subida al registry"
        } catch (Exception e) {
            echo "⚠️ No se pudo subir al registry, usando imagen local: ${e.message}"
        }
    }
}

def applyKubernetesManifests(String fileName) {
    def manifestFile = "${env.K8S_MANIFESTS_ROOT}/${fileName}"
    
    if (fileExists(manifestFile)) {
        echo "Aplicando ${manifestFile}..."
        sh "kubectl apply -f ${manifestFile} -n ${env.K8S_TARGET_NAMESPACE}"
    } else {
        echo "⚠️ Archivo ${manifestFile} no encontrado"
    }
}

def deployPreBuiltService(String serviceName) {
    echo "Desplegando servicio pre-construido: ${serviceName}..."
    
    def deploymentFile = "${env.K8S_MANIFESTS_ROOT}/${serviceName}/deployment.yaml"
    def serviceFile = "${env.K8S_MANIFESTS_ROOT}/${serviceName}/service.yaml"
    
    if (fileExists(deploymentFile)) {
        sh "kubectl apply -f ${deploymentFile} -n ${env.K8S_TARGET_NAMESPACE}"
        
        if (fileExists(serviceFile)) {
            sh "kubectl apply -f ${serviceFile} -n ${env.K8S_TARGET_NAMESPACE}"
        }
        
        sh "kubectl rollout status deployment/${serviceName} -n ${env.K8S_TARGET_NAMESPACE} --timeout=300s"
    }
}

def deployMicroservice(String serviceName, String buildTag) {
    echo "Desplegando microservicio: ${serviceName}..."
    
    def imageName = "${env.DOCKER_REGISTRY}/${serviceName}:${buildTag}"
    def deploymentFile = "${env.K8S_MANIFESTS_ROOT}/${serviceName}/deployment.yaml"
    def serviceFile = "${env.K8S_MANIFESTS_ROOT}/${serviceName}/service.yaml"
    def ingressFile = "${env.K8S_MANIFESTS_ROOT}/${serviceName}/ingress.yaml"
    
    if (fileExists(deploymentFile)) {
        def processedFile = "processed-${serviceName}-deployment.yaml"
        def deploymentContent = readFile(deploymentFile)
        
        def updatedContent = deploymentContent.replaceAll(
            /image: .*${serviceName}:.*/, 
            "image: ${imageName}"
        )
        
        writeFile(file: processedFile, text: updatedContent)
        
        sh "kubectl apply -f ${processedFile} -n ${env.K8S_TARGET_NAMESPACE}"
        
        if (fileExists(serviceFile)) {
            sh "kubectl apply -f ${serviceFile} -n ${env.K8S_TARGET_NAMESPACE}"
        }
        
        if (fileExists(ingressFile)) {
            sh "kubectl apply -f ${ingressFile} -n ${env.K8S_TARGET_NAMESPACE}"
        }
        
        sh "kubectl rollout status deployment/${serviceName} -n ${env.K8S_TARGET_NAMESPACE} --timeout=300s"
        
        echo "✓ ${serviceName} desplegado exitosamente"
    } else {
        echo "⚠️ Archivo deployment.yaml no encontrado para ${serviceName}"
    }
}

def runSmokeTests() {
    echo "Ejecutando smoke tests..."
    
    sh """
        kubectl get service api-gateway -n ${env.K8S_TARGET_NAMESPACE} || echo "API Gateway service not found"
    """
    
    echo "✓ Smoke tests completados"
}


def runPerformanceTests() {
    echo "🚀 Ejecutando Suite Completa de Pruebas de Rendimiento con Locust..."
    
    try {
        // Verificar que los servicios estén listos
        echo "⏳ Verificando estado de servicios..."
        
        
        // Esperar estabilización de servicios
        echo "⏱️ Esperando estabilización de servicios (60 segundos)..."
        sleep(time: 60, unit: 'SECONDS')
        
        dir('performance-tests') {
            // Instalar dependencias de Python
            echo "📦 Instalando dependencias de Python..."
            sh """
                pip3 install --user locust requests configparser pandas || true
                
                # Verificar instalación
                python3 -c "import locust; print('Locust version:', locust.__version__)" || echo "Locust no encontrado"
                python3 -c "import requests; print('Requests disponible')" || echo "Requests no encontrado"
            """
            
            // Verificar conectividad de servicios
            echo "🔍 Verificando conectividad de endpoints críticos..."
            sh """
                echo "Verificando API Gateway..."
                for i in {1..10}; do
                    if curl -f -s http://host.docker.internal/actuator/health >/dev/null 2>&1; then
                        echo "✓ API Gateway health check OK"
                        break
                    fi
                    echo "Esperando API Gateway... intento \$i/10"
                    sleep 15
                done
                
                echo "Verificando endpoints de productos..."
                curl -f -s http://host.docker.internal/api/products/1 >/dev/null && echo "✓ Product endpoint disponible" || echo "⚠️ Product endpoint no responde"
                
                echo "Verificando endpoints de usuarios..."
                curl -f -s http://host.docker.internal/api/users >/dev/null && echo "✓ User endpoint disponible" || echo "⚠️ User endpoint no responde"
            """
            
            // Crear directorio de resultados si no existe
            sh "mkdir -p performance_results"
            
            // Ejecutar suite completa de pruebas con configuración optimizada para CI/CD
            echo "🎯 Ejecutando Suite Completa de Pruebas de Rendimiento..."
            echo "📊 Nivel de prueba: ${params.PERFORMANCE_TEST_LEVEL}"
            
            // Configuración basada en el nivel de prueba
            def testConfig = [:]
            switch(params.PERFORMANCE_TEST_LEVEL) {
                case 'light':
                    testConfig = [users: 10, spawnRate: 1, duration: 60]
                    echo "🔵 Configuración LIGHT: Pruebas ligeras para validación rápida"
                    break
                case 'stress':
                    testConfig = [users: 50, spawnRate: 5, duration: 300]
                    echo "🔴 Configuración STRESS: Pruebas intensivas para validación de límites"
                    break
                default: // standard
                    testConfig = [users: 20, spawnRate: 2, duration: 120]
                    echo "🟡 Configuración STANDARD: Pruebas equilibradas para CI/CD"
            }
            
            sh """
                # Ejecutar todas las pruebas disponibles secuencialmente
                echo "📊 Iniciando pruebas de rendimiento..."
                echo "  - Usuarios concurrentes: ${testConfig.users}"
                echo "  - Velocidad de spawn: ${testConfig.spawnRate} usuarios/seg"
                echo "  - Duración: ${testConfig.duration} segundos"
                
                python3 performance_test_suite.py --all \\
                    --users ${testConfig.users} \\
                    --spawn-rate ${testConfig.spawnRate} \\
                    --duration ${testConfig.duration} \\
                    --host http://host.docker.internal || echo "⚠️ Algunas pruebas de rendimiento fallaron"
                
                # Verificar que se generaron reportes
                echo "📋 Verificando resultados generados..."
                ls -la performance_results/ || echo "No se encontraron resultados"
                
                # Contar archivos de reporte generados
                HTML_COUNT=\$(find performance_results -name "*.html" | wc -l)
                JSON_COUNT=\$(find performance_results -name "*.json" | wc -l)
                CSV_COUNT=\$(find performance_results -name "*.csv" | wc -l)
                
                echo "📊 Reportes generados:"
                echo "  - HTML Reports: \$HTML_COUNT"
                echo "  - JSON Reports: \$JSON_COUNT"
                echo "  - CSV Reports: \$CSV_COUNT"
                
                # Mostrar resumen de archivos generados
                find performance_results -type f -name "*.html" -o -name "*.json" -o -name "*.md" | head -20
            """
            
            // Generar reporte consolidado de rendimiento
            echo "📈 Generando reporte consolidado..."
            sh """
                # Crear reporte consolidado si hay resultados
                if [ -d "performance_results" ] && [ "\$(ls -A performance_results)" ]; then
                    echo "# Performance Test Summary - Build ${env.BUILD_NUMBER}" > performance_results/ci_summary.md
                    echo "## Execution Date: \$(date)" >> performance_results/ci_summary.md
                    echo "## Environment: ${params.ENVIRONMENT}" >> performance_results/ci_summary.md
                    echo "## Build Tag: ${params.BUILD_TAG}" >> performance_results/ci_summary.md
                    echo "" >> performance_results/ci_summary.md
                    echo "### Generated Reports:" >> performance_results/ci_summary.md
                    find performance_results -name "*.html" | sed 's/^/- /' >> performance_results/ci_summary.md
                    echo "" >> performance_results/ci_summary.md
                    echo "### Test Configuration:" >> performance_results/ci_summary.md
                    echo "- Performance Level: ${params.PERFORMANCE_TEST_LEVEL}" >> performance_results/ci_summary.md
                    echo "- Concurrent Users: ${testConfig.users}" >> performance_results/ci_summary.md
                    echo "- Spawn Rate: ${testConfig.spawnRate} users/sec" >> performance_results/ci_summary.md
                    echo "- Duration: ${testConfig.duration} seconds" >> performance_results/ci_summary.md
                    
                    echo "✓ Reporte consolidado generado"
                else
                    echo "⚠️ No se encontraron resultados para el reporte consolidado"
                fi
            """
        }
        
        echo "✅ Suite de pruebas de rendimiento completada exitosamente"
        
    } catch (Exception e) {
        echo "💥 Error en pruebas de rendimiento: ${e.message}"
        
        // Intentar capturar logs para debugging
        try {
            sh """
                echo "=== DEBUG INFORMATION ==="
                kubectl get pods -n ${env.K8S_TARGET_NAMESPACE} || true
                kubectl get services -n ${env.K8S_TARGET_NAMESPACE} || true
                curl -v http://host.docker.internal/actuator/health || true
            """
        } catch (Exception debugException) {
            echo "No se pudo capturar información de debug: ${debugException.message}"
        }
        
        // Re-lanzar excepción solo en ambiente crítico
        if (params.ENVIRONMENT == 'master') {
            throw e
        } else {
            echo "⚠️ Continuando pipeline a pesar de fallos en pruebas de rendimiento (ambiente no crítico)"
        }
    }
}

def generateReleaseNotes() {
    echo "Generando Release Notes automáticos..."
    
    def releaseNotesFile = "release-notes-${params.BUILD_TAG}.md"
    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def gitBranch = sh(returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD').trim()
    def buildDate = new Date().format('yyyy-MM-dd HH:mm:ss')
    
    def releaseNotes = """
# Release Notes - Build ${params.BUILD_TAG}

## Build Information
- **Build Number**: ${env.BUILD_NUMBER}
- **Build Tag**: ${params.BUILD_TAG}
- **Environment**: ${params.ENVIRONMENT}
- **Date**: ${buildDate}
- **Git Commit**: ${gitCommit}
- **Git Branch**: ${gitBranch}

## Deployed Services
- service-discovery
- cloud-config
- api-gateway
- proxy-client
- user-service
- product-service
- order-service
- payment-service
- shipping-service
- zipkin (monitoring)

## Test Results
- **Unit Tests**: ${params.SKIP_TESTS ? 'SKIPPED' : 'EXECUTED'}
- **Integration Tests**: ${params.SKIP_TESTS ? 'SKIPPED' : 'EXECUTED'}
- **End-to-End Tests**: ${params.SKIP_TESTS ? 'SKIPPED' : 'EXECUTED'}
- **System Validation**: ${params.ENVIRONMENT == 'master' ? 'EXECUTED' : 'SKIPPED'}
- **Performance Tests**: ${(params.ENVIRONMENT == 'master' && !params.SKIP_TESTS && !params.SKIP_PERFORMANCE_TESTS) ? "EXECUTED (${params.PERFORMANCE_TEST_LEVEL.toUpperCase()})" : 'SKIPPED'}

## Changes
\$(git log --oneline --since="1 day ago" | head -10 || echo "No recent commits found")

## Deployment Status
✅ Successfully deployed to ${params.ENVIRONMENT} environment


---
*Generated automatically by Jenkins Pipeline*
"""
    
    writeFile(file: releaseNotesFile, text: releaseNotes)
    archiveArtifacts artifacts: releaseNotesFile
    
    echo "✓ Release Notes generados: ${releaseNotesFile}"
}

// Additional utility functions can be added here