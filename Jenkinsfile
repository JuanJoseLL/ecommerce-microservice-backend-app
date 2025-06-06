pipeline {
    agent any

    environment {
        // El ID de las credenciales que mencionaste (TEXT credential)
        SONAR_TOKEN = credentials('SONAR_TOKEN') // Asegúrate que este ID exista en Jenkins
        SONAR_HOST_URL = 'http://sonarqube:9000'    // Ajusta la URL de tu SonarQube
        JAVA_HOME = '/opt/java/openjdk'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
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

        stage('SonarQube Analysis') {
            steps {
                echo 'Ejecutando análisis de SonarQube...'
                withSonarQubeEnv('SonarQube-Server') { // Ajusta el nombre según tu configuración en Jenkins
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

        stage('Quality Gate') {
            steps {
                echo 'Esperando resultado del Quality Gate...'
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }

    post {
        always {
            echo 'Pipeline completado'
            // 👇 THIS IS THE CORRECTED BLOCK
           
        }
        success {
            echo 'Análisis de SonarQube completado exitosamente!'
        }
        failure {
            echo 'El análisis de SonarQube falló'
        }
    }
}