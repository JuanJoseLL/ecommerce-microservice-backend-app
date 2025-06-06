pipeline {
    agent any

    environment {
        // Credenciales y configuración existente
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        SONAR_HOST_URL = 'http://sonarqube:9000'
        
        // Configuración de Trivy
        TRIVY_SERVER_URL = 'http://trivy-server:4954'
        
        // Configuración de Java
        JAVA_HOME = '/opt/java/openjdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        
        // Configuración de la aplicación
        APP_NAME = 'ecommerce-microservice-backend'
        DOCKER_IMAGE = "${APP_NAME}:${env.BUILD_NUMBER}"
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

        stage('Build Docker Image') {
            steps {
                echo 'Construyendo imagen Docker...'
                script {
                    sh """
                        # Asegurar que tenemos un Dockerfile
                        if [ ! -f Dockerfile ]; then
                            echo "⚠️  No se encontró Dockerfile, creando uno básico..."
                            cat > Dockerfile << 'EOF'
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
                        fi
                        
                        # Construir imagen
                        docker build -t ${DOCKER_IMAGE} .
                        docker tag ${DOCKER_IMAGE} ${APP_NAME}:latest
                    """
                }
            }
        }

        stage('Code Quality Analysis') {
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
                    steps {
                        echo 'Ejecutando escaneo de seguridad con Trivy...'
                        script {
                            sh """
                                # Instalar Trivy client si no existe
                                if ! command -v trivy &> /dev/null; then
                                    echo "📦 Instalando Trivy client..."
                                    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
                                fi
                                
                                # Verificar conectividad con Trivy server
                                echo "🔍 Verificando conexión con Trivy server..."
                                until wget -q --spider ${TRIVY_SERVER_URL}/healthz; do
                                    echo "⏳ Esperando a que Trivy server esté listo..."
                                    sleep 5
                                done
                                echo "✅ Trivy server está listo!"
                                
                                # Ejecutar escaneo de vulnerabilidades
                                echo "🛡️  Iniciando escaneo de seguridad..."
                                
                                # Escaneo completo en formato JSON
                                trivy client \
                                    --server ${TRIVY_SERVER_URL} \
                                    --format json \
                                    --output trivy-report.json \
                                    ${DOCKER_IMAGE}
                                
                                # Escaneo solo vulnerabilidades críticas y altas para decisión de pipeline
                                trivy client \
                                    --server ${TRIVY_SERVER_URL} \
                                    --format table \
                                    --output trivy-summary.txt \
                                    --severity CRITICAL,HIGH \
                                    ${DOCKER_IMAGE}
                                
                                # Mostrar resumen en consola
                                echo "📊 Resumen de vulnerabilidades:"
                                cat trivy-summary.txt
                                
                                # Contar vulnerabilidades críticas
                                CRITICAL_COUNT=\$(cat trivy-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity=="CRITICAL") | .VulnerabilityID' | wc -l)
                                HIGH_COUNT=\$(cat trivy-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity=="HIGH") | .VulnerabilityID' | wc -l)
                                
                                echo "🔴 Vulnerabilidades CRÍTICAS encontradas: \$CRITICAL_COUNT"
                                echo "🟠 Vulnerabilidades ALTAS encontradas: \$HIGH_COUNT"
                                
                                # Crear archivo de métricas para Jenkins
                                echo "CRITICAL_VULNS=\$CRITICAL_COUNT" > trivy-metrics.properties
                                echo "HIGH_VULNS=\$HIGH_COUNT" >> trivy-metrics.properties
                                
                                # Política de seguridad (ajusta según tus necesidades)
                                if [ \$CRITICAL_COUNT -gt 0 ]; then
                                    echo "❌ ADVERTENCIA: Se encontraron \$CRITICAL_COUNT vulnerabilidades críticas"
                                    echo "🔒 Considera revisar estas vulnerabilidades antes del despliegue"
                                    # Descomenta la siguiente línea para fallar el build con vulnerabilidades críticas
                                    # exit 1
                                fi
                                
                                if [ \$HIGH_COUNT -gt 10 ]; then
                                    echo "⚠️  ADVERTENCIA: Se encontraron \$HIGH_COUNT vulnerabilidades altas (límite recomendado: 10)"
                                fi
                                
                                echo "✅ Escaneo de seguridad completado"
                            """
                        }
                    }
                    post {
                        always {
                            // Publicar reporte HTML de Trivy
                            script {
                                // Generar reporte HTML si existe el archivo JSON
                                sh '''
                                    if [ -f trivy-report.json ]; then
                                        echo "📄 Generando reporte HTML..."
                                        # Usar template HTML simple
                                        trivy client \
                                            --server ${TRIVY_SERVER_URL} \
                                            --format template \
                                            --template '@contrib/html.tpl' \
                                            --output trivy-report.html \
                                            ${DOCKER_IMAGE} || echo "No se pudo generar HTML, usando JSON"
                                    fi
                                '''
                            }
                            
                            // Publicar reportes
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: '.',
                                reportFiles: 'trivy-report.html',
                                reportName: 'Trivy Security Report'
                            ])
                            
                            // Archivar reportes para análisis posterior
                            archiveArtifacts artifacts: 'trivy-*.json,trivy-*.txt,trivy-metrics.properties', 
                                           fingerprint: true, 
                                           allowEmptyArchive: true
                        }
                    }
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
            steps {
                echo 'Verificando políticas de seguridad...'
                script {
                    // Leer métricas de Trivy
                    def props = readProperties file: 'trivy-metrics.properties'
                    def criticalVulns = props.CRITICAL_VULNS as Integer
                    def highVulns = props.HIGH_VULNS as Integer
                    
                    echo "📊 Métricas de seguridad:"
                    echo "   - Vulnerabilidades críticas: ${criticalVulns}"
                    echo "   - Vulnerabilidades altas: ${highVulns}"
                    
                    // Definir políticas (ajusta según tus necesidades)
                    if (criticalVulns > 0) {
                        echo "⚠️  POLÍTICA: Se encontraron vulnerabilidades críticas"
                        // currentBuild.result = 'UNSTABLE'  // Marca como inestable
                        // error("Build fallido por vulnerabilidades críticas") // Falla el build
                    }
                    
                    if (highVulns > 15) {
                        echo "⚠️  POLÍTICA: Demasiadas vulnerabilidades altas (${highVulns} > 15)"
                        currentBuild.result = 'UNSTABLE'
                    }
                    
                    echo "✅ Verificación de políticas completada"
                }
            }
        }
        
        stage('Package') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                echo 'Empaquetando aplicación...'
                sh './mvnw clean package -DskipTests'
            }
        }
    }

    post {
        always {
            echo 'Pipeline completado'
            
            // Limpiar imágenes Docker locales para ahorrar espacio
            sh """
                docker rmi ${DOCKER_IMAGE} || true
                docker rmi ${APP_NAME}:latest || true
                docker image prune -f || true
            """
        }
        
        success {
            echo '✅ Pipeline completado exitosamente!'
            script {
                // Enviar notificación con métricas de seguridad si existen
                if (fileExists('trivy-metrics.properties')) {
                    def props = readProperties file: 'trivy-metrics.properties'
                    echo "📊 Resumen final de seguridad:"
                    echo "   - Vulnerabilidades críticas: ${props.CRITICAL_VULNS}"
                    echo "   - Vulnerabilidades altas: ${props.HIGH_VULNS}"
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