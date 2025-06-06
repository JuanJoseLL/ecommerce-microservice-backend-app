pipeline {
    agent any

    parameters {
        choice(
            name: 'MICROSERVICE',
            choices: ['ALL', 'api-gateway', 'service-discovery', 'user-service', 'product-service', 'order-service', 'payment-service', 'shipping-service', 'favourite-service'],
            description: 'Seleccionar microservicio específico o ALL para todos'
        )
        booleanParam(
            name: 'SKIP_SECURITY_SCAN',
            defaultValue: false,
            description: 'Saltar escaneo de seguridad con Trivy'
        )
    }

    environment {
        // Credenciales y configuración existente
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        SONAR_HOST_URL = 'http://sonarqube:9000'
        
        // Configuración de Java
        JAVA_HOME = '/opt/java/openjdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        
        // Configuración de la aplicación
        APP_NAME = 'ecommerce-microservice-backend'
        DOCKER_IMAGE = "${APP_NAME}:${env.BUILD_NUMBER}"

        DOCKERHUB_USERNAME = 'j2loop' 
        DOCKERHUB_CREDENTIALS_ID = 'DOCKERHUB_CREDENTIALS' // El ID de la credencial que creaste en Jenkins
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Obteniendo código fuente...'
                checkout scm
            }
        }

        stage('Compile') {
            steps {
                echo 'Compilando el proyecto...'
                sh './mvnw clean compile'
            }
        }

        stage('Detect Services to Build') {
            steps {
                echo 'Detectando microservicios a construir...'
                script {
                    // Definir microservicios disponibles
                    def microservices = [
                        'api-gateway',
                        'service-discovery', 
                        'user-service',
                        'product-service',
                        'order-service',
                        'payment-service',
                        'shipping-service',
                        'favourite-service'
                    ]
                    
                    def servicesToBuild = []
                    
                    // Detectar cambios (si es commit específico) o construir todos (si es manual)
                    if (env.CHANGE_TARGET) {
                        // Es un PR, detectar cambios
                        def changes = sh(
                            script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD",
                            returnStdout: true
                        ).trim().split('\n')
                        
                        servicesToBuild = microservices.findAll { service ->
                            changes.any { it.startsWith("${service}/") }
                        }
                    } else {
                        // Build manual o push a main, construir servicios específicos
                        def serviceToBuild = params.MICROSERVICE ?: 'ALL'
                        if (serviceToBuild == 'ALL') {
                            servicesToBuild = microservices
                        } else {
                            servicesToBuild = [serviceToBuild]
                        }
                    }
                    
                    if (servicesToBuild.isEmpty()) {
                        echo "ℹ️  No se detectaron cambios en microservicios"
                        servicesToBuild = ['user-service'] // Default para testing
                    }
                    
                    echo "🔨 Microservicios a construir: ${servicesToBuild.join(', ')}"
                    
                    // Guardar lista de servicios para etapas posteriores
                    env.SERVICES_TO_BUILD = servicesToBuild.join(',')
                }
            }
        }

        stage('Package Microservices') {
            steps {
                echo 'Empaquetando microservicios...'
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    
                    for (service in servicesToBuild) {
                        echo "📦 Empaquetando ${service}..."
                        
                        // Opción 1: Si cada microservicio tiene su propio pom.xml
                        if (fileExists("${service}/pom.xml")) {
                            sh """
                                cd ${service}
                                ../mvnw clean package -DskipTests
                                echo "✅ JAR generado para ${service}"
                                ls -la target/*.jar || echo "⚠️  No se encontró JAR en target/"
                            """
                        } else {
                            // Opción 2: Si es un proyecto multi-módulo con un pom padre
                            sh """
                                ./mvnw clean package -pl ${service} -am -DskipTests
                                echo "✅ JAR generado para ${service}"
                                ls -la ${service}/target/*.jar || echo "⚠️  No se encontró JAR en ${service}/target/"
                            """
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                echo 'Construyendo imágenes Docker...'
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    def builtImages = []
                    def localImages = []
                    
                    for (service in servicesToBuild) {
                        if (fileExists("${service}/Dockerfile")) {
                            echo "🐳 Construyendo imagen Docker para ${service}..."
                            
                            // Verificar que el JAR existe antes de construir
                            def jarExists = sh(
                                script: "ls ${service}/target/*.jar 2>/dev/null | wc -l",
                                returnStdout: true
                            ).trim()
                            
                            if (jarExists == "0") {
                                error "❌ No se encontró JAR para ${service}. Verifica que el empaquetado fue exitoso."
                            }
                            
                            sh """
                                cd ${service}
                                # Construir la imagen con tags locales y de Docker Hub
                                docker build -t ${service}:${env.BUILD_NUMBER} .
                                docker tag ${service}:${env.BUILD_NUMBER} ${service}:latest
                                docker tag ${service}:${env.BUILD_NUMBER} ${DOCKERHUB_USERNAME}/${service}:${env.BUILD_NUMBER}
                                docker tag ${service}:${env.BUILD_NUMBER} ${DOCKERHUB_USERNAME}/${service}:latest
                            """
                            
                            // Agregar imagen local para escaneo de seguridad
                            localImages.add("${service}:${env.BUILD_NUMBER}")
                            // Agregar imagen de Docker Hub para push
                            builtImages.add("${DOCKERHUB_USERNAME}/${service}:${env.BUILD_NUMBER}")
                        } else {
                            echo "⚠️  No se encontró Dockerfile en ${service}/"
                        }
                    }
                    
                    // Guardar listas de imágenes para etapas posteriores
                    env.LOCAL_IMAGES = localImages.join(',')
                    env.BUILT_IMAGES = builtImages.join(',')
                    echo "📦 Imágenes locales (para escaneo): ${env.LOCAL_IMAGES}"
                    echo "📦 Imágenes para push: ${env.BUILT_IMAGES}"
                }
            }
        }

        stage('Code Quality and Security Analysis') {
            parallel {
                stage('SonarQube Analysis') {
                    steps {
                        echo 'Ejecutando análisis de SonarQube...'
                        withSonarQubeEnv('SonarQube-Server') {
                            sh """
                                ./mvnw sonar:sonar \
                                -Dsonar.projectKey=ecommerce-microservice-backend \
                                -Dsonar.projectName='Ecommerce Microservice Backend' \
                                -Dsonar.projectVersion=${env.BUILD_NUMBER} \
                                -Dsonar.host.url=${SONAR_HOST_URL} \
                                -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                    }
                }
                
                stage('Container Security Scan') {
                    when {
                        expression { !params.SKIP_SECURITY_SCAN }
                    }
                    steps {
                        echo 'Ejecutando escaneo de seguridad con Trivy (modo standalone)...'
                        script {
                            def imagesToScan = env.LOCAL_IMAGES?.split(',') ?: []
                            
                            if (imagesToScan.size() == 0) {
                                echo "ℹ️  No hay imágenes locales para escanear"
                                return
                            }
                            
                            sh """
                                # Instalar Trivy si no existe
                                if ! command -v trivy &> /dev/null; then
                                    echo "📦 Instalando Trivy..."
                                    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
                                fi
                                
                                echo "✅ Trivy instalado y listo para escaneo local"
                            """
                            
                            def totalCritical = 0
                            def totalHigh = 0
                            def scanResults = []
                            
                            // Escanear cada imagen local construida
                            for (image in imagesToScan) {
                                // Extraer el nombre del servicio desde imagen local (ej: user-service:123)
                                def serviceName = image.split(':')[0]
                                echo "🛡️  Escaneando imagen local ${image}..."
                                
                                sh """
                                    # Escaneo completo en formato JSON
                                    trivy image \
                                        --format json \
                                        --output trivy-${serviceName}-report.json \
                                        ${image}
                                    
                                    # Escaneo resumen para consola
                                    trivy image \
                                        --format table \
                                        --output trivy-${serviceName}-summary.txt \
                                        --severity CRITICAL,HIGH \
                                        ${image}
                                    
                                    echo "📊 Resumen de ${serviceName}:"
                                    cat trivy-${serviceName}-summary.txt || echo "No hay vulnerabilidades críticas/altas"
                                """
                                
                                // Contar vulnerabilidades para este servicio
                                def criticalCount = sh(
                                    script: "cat trivy-${serviceName}-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity==\"CRITICAL\") | .VulnerabilityID' | wc -l",
                                    returnStdout: true
                                ).trim() as Integer
                                
                                def highCount = sh(
                                    script: "cat trivy-${serviceName}-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity==\"HIGH\") | .VulnerabilityID' | wc -l",
                                    returnStdout: true
                                ).trim() as Integer
                                
                                totalCritical += criticalCount
                                totalHigh += highCount
                                
                                scanResults.add([
                                    service: serviceName,
                                    image: image,
                                    critical: criticalCount,
                                    high: highCount
                                ])
                                
                                echo "🔴 ${serviceName} - Críticas: ${criticalCount}, Altas: ${highCount}"
                            }
                            
                            // Generar reporte consolidado
                            sh """
                                echo "TOTAL_CRITICAL_VULNS=${totalCritical}" > trivy-metrics.properties
                                echo "TOTAL_HIGH_VULNS=${totalHigh}" >> trivy-metrics.properties
                                echo "SCANNED_SERVICES=${imagesToScan.size()}" >> trivy-metrics.properties
                                
                                # Crear reporte consolidado HTML
                                cat > trivy-consolidated-report.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Trivy Security Report - Microservices (Local Scan)</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .service { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .critical { color: #d32f2f; font-weight: bold; }
        .high { color: #f57c00; font-weight: bold; }
        .summary { background: #f5f5f5; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <h1>🛡️ Trivy Security Report (Local Images) - Build ${BUILD_NUMBER}</h1>
    <div class="summary">
        <h2>📊 Resumen General</h2>
        <p><strong>Servicios escaneados:</strong> ${imagesToScan.size()}</p>
        <p><strong>Total vulnerabilidades críticas:</strong> <span class="critical">${totalCritical}</span></p>
        <p><strong>Total vulnerabilidades altas:</strong> <span class="high">${totalHigh}</span></p>
        <p><strong>Modo:</strong> Standalone (imágenes locales)</p>
        <p><strong>Fecha:</strong> ${new Date()}</p>
    </div>
EOF
                            """
                            
                            // Agregar detalles de cada servicio al HTML
                            for (result in scanResults) {
                                sh """
cat >> trivy-consolidated-report.html << 'EOF'
    <div class="service">
        <h3>🚀 ${result.service}</h3>
        <p><strong>Imagen local:</strong> ${result.image}</p>
        <p><strong>Vulnerabilidades críticas:</strong> <span class="critical">${result.critical}</span></p>
        <p><strong>Vulnerabilidades altas:</strong> <span class="high">${result.high}</span></p>
        <a href="trivy-${result.service}-report.json" target="_blank">Ver reporte detallado JSON</a>
    </div>
EOF
                                """
                            }
                            
                            sh 'echo "</body></html>" >> trivy-consolidated-report.html'
                            
                            echo "📈 Resumen final del escaneo local:"
                            echo "   - Total servicios: ${imagesToScan.size()}"
                            echo "   - Total vulnerabilidades críticas: ${totalCritical}"
                            echo "   - Total vulnerabilidades altas: ${totalHigh}"
                            
                            // Política de seguridad - fallar si hay vulnerabilidades críticas
                            if (totalCritical > 0) {
                                echo "❌ ADVERTENCIA: Se encontraron ${totalCritical} vulnerabilidades críticas en total"
                                echo "🚫 Considerando fallar el build por política de seguridad..."
                                // Descomentar para fallar el build:
                                // error("Build fallido por vulnerabilidades críticas detectadas")
                            }
                            
                            if (totalHigh > 20) {
                                echo "⚠️  ADVERTENCIA: Se encontraron ${totalHigh} vulnerabilidades altas en total (límite recomendado: 20)"
                                currentBuild.result = 'UNSTABLE'
                            }
                        }
                    }
                    post {
                        always {
                            // Publicar reporte consolidado
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: '.',
                                reportFiles: 'trivy-consolidated-report.html',
                                reportName: 'Trivy Security Report (Local)'
                            ])
                            
                            // Archivar todos los reportes
                            archiveArtifacts artifacts: 'trivy-*-report.json,trivy-*-summary.txt,trivy-metrics.properties,trivy-consolidated-report.html', 
                                           fingerprint: true, 
                                           allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                echo 'Publicando imágenes Docker en Docker Hub...'
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    
                    withCredentials([usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS_ID, usernameVariable: 'DOCKERHUB_USER', passwordVariable: 'DOCKERHUB_PASS')]) {
                        // Login a Docker Hub
                        sh 'echo $DOCKERHUB_PASS | docker login -u $DOCKERHUB_USER --password-stdin'
                        
                        for (service in servicesToBuild) {
                            if (fileExists("${service}/Dockerfile")) {
                                echo "📤 Publicando ${service} en Docker Hub..."
                                
                                sh """
                                    # Push imagen con número de build
                                    docker push ${DOCKERHUB_USERNAME}/${service}:${env.BUILD_NUMBER}
                                    
                                    # Push imagen latest
                                    docker push ${DOCKERHUB_USERNAME}/${service}:latest
                                    
                                    echo "✅ ${service} publicado exitosamente"
                                """
                            }
                        }
                        
                        // Logout de Docker Hub por seguridad
                        sh 'docker logout'
                    }
                    
                    echo "🎉 Todas las imágenes han sido publicadas en Docker Hub"
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo 'Esperando resultado del Quality Gate de SonarQube...'
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Security Policy Check') {
            when {
                expression { !params.SKIP_SECURITY_SCAN }
            }
            steps {
                echo 'Verificando políticas de seguridad...'
                script {
                    // Leer métricas de Trivy
                    if (fileExists('trivy-metrics.properties')) {
                        def props = readProperties file: 'trivy-metrics.properties'
                        def totalCriticalVulns = props.TOTAL_CRITICAL_VULNS as Integer
                        def totalHighVulns = props.TOTAL_HIGH_VULNS as Integer
                        def scannedServices = props.SCANNED_SERVICES as Integer
                        
                        echo "📊 Métricas de seguridad consolidadas:"
                        echo "   - Servicios escaneados: ${scannedServices}"
                        echo "   - Total vulnerabilidades críticas: ${totalCriticalVulns}"
                        echo "   - Total vulnerabilidades altas: ${totalHighVulns}"
                        
                        // Definir políticas (ajusta según tus necesidades)
                        if (totalCriticalVulns > 0) {
                            echo "⚠️  POLÍTICA: Se encontraron ${totalCriticalVulns} vulnerabilidades críticas en ${scannedServices} servicios"
                            // currentBuild.result = 'UNSTABLE'  // Marca como inestable
                            // error("Build fallido por vulnerabilidades críticas") // Falla el build
                        }
                        
                        if (totalHighVulns > 30) {
                            echo "⚠️  POLÍTICA: Demasiadas vulnerabilidades altas (${totalHighVulns} > 30) en todos los servicios"
                            currentBuild.result = 'UNSTABLE'
                        }
                        
                        // Política por promedio de servicios
                        def avgCritical = totalCriticalVulns / scannedServices
                        def avgHigh = totalHighVulns / scannedServices
                        
                        echo "📈 Promedio por servicio:"
                        echo "   - Críticas: ${avgCritical.round(2)}"
                        echo "   - Altas: ${avgHigh.round(2)}"
                        
                        echo "✅ Verificación de políticas completada"
                    } else {
                        echo "ℹ️  No se encontraron métricas de seguridad para evaluar"
                    }
                }
            }
        }
        
        stage('Docker Cleanup') {
            steps {
                echo 'Limpiando recursos Docker...'
                script {
                    def imagesToClean = env.BUILT_IMAGES?.split(',') ?: []
                    
                    if (imagesToClean.size() == 0) {
                        echo "ℹ️  No hay imágenes para limpiar"
                        return
                    }
                    
                    echo "🧹 Limpiando ${imagesToClean.size()} imágenes construidas..."
                    
                    for (image in imagesToClean) {
                        // Extraer el nombre del servicio correctamente
                        def imageParts = image.split(':')[0].split('/')
                        def serviceName = imageParts[-1] // Obtener la última parte (nombre del servicio)
                        echo "🗑️  Limpiando ${image}..."
                        
                        sh """
                            # Remover imagen con tag del build
                            docker rmi ${image} || echo "⚠️  No se pudo remover ${image}"
                            
                            # Remover imagen con tag latest
                            docker rmi ${serviceName}:latest || echo "⚠️  No se pudo remover ${serviceName}:latest"
                        """
                    }
                    
                    // Limpiar imágenes huérfanas y sin usar
                    echo "🧹 Limpiando imágenes sin usar..."
                    sh """
                        # Remover imágenes sin usar (dangling)
                        docker image prune -f || echo "⚠️  No se pudo ejecutar image prune"
                        
                        # Mostrar espacio liberado
                        echo "📊 Estado actual de Docker:"
                        docker system df || echo "⚠️  No se pudo obtener información del sistema Docker"
                    """
                    
                    echo "✅ Limpieza Docker completada"
                }
            }
        }
    }

    post {
        always {
            echo 'Pipeline completado'
            // Limpiar archivos temporales
            sh """
                rm -f trivy-*-report.json trivy-*-summary.txt trivy-metrics.properties || true
            """
        }
        
        success {
            echo '✅ Pipeline completado exitosamente!'
            script {
                // Enviar notificación con métricas de seguridad si existen
                if (fileExists('trivy-metrics.properties')) {
                    def props = readProperties file: 'trivy-metrics.properties'
                    echo "📊 Resumen final de seguridad:"
                    echo "   - Servicios escaneados: ${props.SCANNED_SERVICES ?: 0}"
                    echo "   - Total vulnerabilidades críticas: ${props.TOTAL_CRITICAL_VULNS ?: 0}"
                    echo "   - Total vulnerabilidades altas: ${props.TOTAL_HIGH_VULNS ?: 0}"
                }
            }
        }
        
        failure {
            echo '❌ El pipeline falló'
        }
        
        unstable {
            echo '⚠️  Pipeline completado con advertencias de seguridad'
        }
    }
}