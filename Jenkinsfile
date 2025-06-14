@Library('jenkins-microservices-library@main') _

pipeline {
    agent any
    
    parameters {
        choice(
            name: 'MICROSERVICE',
            choices: pipelineConfig.getMicroservices() + ['ALL'],
            description: 'Seleccionar microservicio específico o ALL para todos'
        )
        booleanParam(
            name: 'SKIP_SECURITY_SCAN',
            defaultValue: false,
            description: 'Saltar escaneo de seguridad con Trivy'
        )
    }
    
    environment {
        // Credenciales y configuración
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        SONAR_HOST_URL = 'http://sonarqube:9000'
        
        // Configuración de Java
        JAVA_HOME = '/opt/java/openjdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        
        // Configuración de la aplicación
        APP_NAME = 'ecommerce-microservice-backend'
        
        // Docker Hub
        DOCKERHUB_USERNAME = 'j2loop'
        DOCKERHUB_CREDENTIALS_ID = 'DOCKERHUB_CREDENTIALS'
        
        // GitHub
        GITHUB_TOKEN = credentials('GITHUB_TOKEN')
        
        // Notificaciones
        EMAIL_RECIPIENTS = 'juanjolo1204lo@gmail.com'
        
        // Variables dinámicas
        SEMANTIC_VERSION = ''
        IS_PRODUCTION_DEPLOY = 'false'
        SERVICES_TO_BUILD = ''
        LOCAL_IMAGES = ''
        BUILT_IMAGES = ''
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
                script {
                    // Compilar excluyendo proxy-client temporalmente debido a problemas de compatibilidad Java/Lombok
                    echo "🔧 Compilando proyecto (excluyendo proxy-client temporalmente)..."
                    sh """
                        echo "📦 Compilando proyecto principal..."
                        ./mvnw clean compile -pl '!proxy-client' -T 2C
                        echo "✅ Compilación completada exitosamente"
                    """
                }
            }
        }
        
        stage('Calculate Version') {
            steps {
                script {
                    env.SEMANTIC_VERSION = versioningStages.calculateSemanticVersion()
                }
            }
        }
        
        stage('Detect Services') {
            steps {
                script {
                    def servicesToBuild = buildStages.detectServicesToBuild(params)
                    env.SERVICES_TO_BUILD = servicesToBuild.join(',')
                }
            }
        }
        
        stage('Tests') {
            steps {
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    testStages.runAllTests(servicesToBuild)
                }
            }
            post {
                always {
                    script {
                        testStages.generateTestSummaryReport(
                            env.BUILD_NUMBER, 
                            env.BRANCH_NAME, 
                            env.SERVICES_TO_BUILD
                        )
                    }
                }
            }
        }
        
        stage('Package') {
            steps {
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    buildStages.packageMicroservices(servicesToBuild)
                }
            }
        }
        
        stage('Build Docker') {
            steps {
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    def images = buildStages.buildDockerImages(servicesToBuild, env.DOCKERHUB_USERNAME)
                    env.LOCAL_IMAGES = images.local.join(',')
                    env.BUILT_IMAGES = images.built.join(',')
                }
            }
        }
        
        stage('Security & Quality') {
            steps {
                script {
                    def localImages = env.LOCAL_IMAGES?.split(',') ?: []
                    securityStages.runAllSecurityScans(localImages, params.SKIP_SECURITY_SCAN)
                }
            }
        }
        
        stage('Push Images') {
            steps {
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    deploymentStages.pushDockerImages(servicesToBuild, env.DOCKERHUB_USERNAME)
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                script {
                    securityStages.waitForQualityGate()
                }
            }
        }
        
        stage('Security Policy Check') {
            when {
                expression { !params.SKIP_SECURITY_SCAN }
            }
            steps {
                script {
                    securityStages.checkSecurityPolicy()
                }
            }
        }
        
        stage('Production Approval') {
            when {
                expression { env.IS_PRODUCTION_DEPLOY == 'true' }
            }
            steps {
                script {
                    def servicesToDeploy = env.SERVICES_TO_BUILD.split(',')
                    deploymentStages.requestProductionApproval(servicesToDeploy, env.SEMANTIC_VERSION)
                }
            }
        }
        
        stage('GitHub Release') {
            when {
                anyOf {
                    branch 'master'
                    branch 'main'
                }
            }
            steps {
                script {
                    versioningStages.createGitHubRelease(env.SEMANTIC_VERSION, env.SERVICES_TO_BUILD)
                }
            }
        }
        
        stage('Load Testing') {
            steps {
                script {
                    def servicesToBuild = env.SERVICES_TO_BUILD.split(',')
                    runLoadTests(servicesToBuild)
                }
            }
            post {
                always {
                    publishLoadTestResults()
                }
            }
        }
    }
    
    post {
        always {
            script {
                def buildResult = currentBuild.result ?: 'SUCCESS'
                def servicesToBuild = env.SERVICES_TO_BUILD ?: 'N/A'
                def semanticVersion = env.SEMANTIC_VERSION ?: 'N/A'
                
                echo "📋 Build Summary:"
                echo "   📊 Status: ${buildResult}"
                echo "   🔧 Services: ${servicesToBuild}"
                echo "   🏷️ Version: ${semanticVersion}"
                echo "   📧 Notifications would be sent to: ${env.EMAIL_RECIPIENTS}"
                
                // Simple email notification using basic Jenkins step
                emailext (
                    subject: "[${buildResult}] ${env.APP_NAME} - Build #${env.BUILD_NUMBER}",
                    body: """
                        <h2>Build ${buildResult}</h2>
                        <p><strong>Project:</strong> ${env.APP_NAME}</p>
                        <p><strong>Build Number:</strong> ${env.BUILD_NUMBER}</p>
                        <p><strong>Branch:</strong> ${env.BRANCH_NAME}</p>
                        <p><strong>Services Built:</strong> ${servicesToBuild}</p>
                        <p><strong>Version:</strong> ${semanticVersion}</p>
                        <p><strong>Build URL:</strong> <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
                    """,
                    to: env.EMAIL_RECIPIENTS,
                    mimeType: 'text/html'
                )
            }
        }
        cleanup {
            cleanWs()
        }
    }
}

// Funciones auxiliares para pruebas
def runUnitTests(List services) {
    echo "Ejecutando pruebas unitarias para servicios: ${services.join(', ')}"
    
    services.each { service ->
        dir(service) {
            sh """
                echo "🧪 Ejecutando pruebas unitarias para ${service}..."
                ./mvnw test -Dtest.profile=unit
                echo "✅ Pruebas unitarias completadas para ${service}"
            """
        }
    }
    
    publishUnitTestResults(services)
}

def runIntegrationTests(List services) {
    echo "Ejecutando pruebas de integración para servicios: ${services.join(', ')}"
    
    services.each { service ->
        dir(service) {
            sh """
                echo "🔗 Ejecutando pruebas de integración para ${service}..."
                ./mvnw verify -Dtest.profile=integration
                echo "✅ Pruebas de integración completadas para ${service}"
            """
        }
    }
    
    publishIntegrationTestResults(services)
}

def runLoadTests(List services) {
    echo "Ejecutando pruebas de carga para servicios: ${services.join(', ')}"
    
    sh """
        cd locust
        echo "🐛 Preparando entorno Locust..."
        
        # Instalar dependencias de Python si no existen
        if ! command -v python3 &> /dev/null; then
            echo "📦 Instalando Python3..."
            apt-get update && apt-get install -y python3 python3-pip
        fi
        
        # Instalar dependencias de Locust
        pip3 install -r requirements.txt
        
        # Configurar URL base para las pruebas
        export LOCUST_HOST=\${LOCUST_HOST:-http://localhost:8080}
        
        echo "🚀 Iniciando pruebas de carga..."
        
        # Ejecutar Locust en modo headless
        locust -f locustfile.py \
            --headless \
            --users 10 \
            --spawn-rate 2 \
            --run-time 2m \
            --host \$LOCUST_HOST \
            --html locust-report.html \
            --csv locust-stats
        
        echo "✅ Pruebas de carga completadas"
        
        # Mostrar resumen de resultados
        if [ -f "locust-stats_stats.csv" ]; then
            echo "📊 Resumen de pruebas de carga:"
            head -5 locust-stats_stats.csv
        fi
    """
}

def publishUnitTestResults(List services) {
    services.each { service ->
        if (fileExists("${service}/target/surefire-reports/*.xml")) {
            junit "${service}/target/surefire-reports/*.xml"
        }
    }
}

def publishIntegrationTestResults(List services) {
    services.each { service ->
        if (fileExists("${service}/target/failsafe-reports/*.xml")) {
            junit "${service}/target/failsafe-reports/*.xml"
        }
    }
}

def publishLoadTestResults() {
    if (fileExists("locust/locust-report.html")) {
        publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'locust',
            reportFiles: 'locust-report.html',
            reportName: 'Locust Load Test Report'
        ])
    }
    
    archiveArtifacts artifacts: 'locust/locust-stats*.csv,locust/locust-report.html', 
                   fingerprint: true, 
                   allowEmptyArchive: true
}

def generateTestSummaryReport(String buildNumber, String branchName, String servicesToBuild) {
    def reportContent = libraryResource('templates/test-summary.html')
    
    // Reemplazar placeholders
    reportContent = reportContent.replace('${BUILD_NUMBER}', buildNumber)
    reportContent = reportContent.replace('${BRANCH_NAME}', branchName)
    reportContent = reportContent.replace('${BUILD_DATE}', new Date().toString())
    reportContent = reportContent.replace('${SERVICES_TO_BUILD}', servicesToBuild)
    
    writeFile file: 'test-summary-report.html', text: reportContent
    
    publishHTML([
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: '.',
        reportFiles: 'test-summary-report.html',
        reportName: 'Test Summary Report'
    ])
}

        