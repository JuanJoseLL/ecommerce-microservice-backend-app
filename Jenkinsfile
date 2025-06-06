pipeline {
    agent any
    
    tools {
        maven 'Maven-3.8.4' // Ajusta el nombre según tu configuración de Maven en Jenkins
        jdk 'JDK-11' // Ajusta según tu configuración de JDK en Jenkins
    }
    
    environment {
        // El ID de las credenciales que mencionaste (TEXT credential)
        SONAR_TOKEN = credentials('SONAR_TOKEN') // Ajusta el ID según como lo nombraste
        SONAR_HOST_URL = 'http://localhost:9000' // Ajusta la URL de tu SonarQube
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
                sh 'mvn clean compile'
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                echo 'Ejecutando análisis de SonarQube...'
                withSonarQubeEnv('SonarQube-Server') { // Ajusta el nombre según tu configuración en Jenkins
                    sh """
                        mvn sonar:sonar \
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
            cleanWs() // Limpia el workspace
        }
        success {
            echo 'Análisis de SonarQube completado exitosamente!'
        }
        failure {
            echo 'El análisis de SonarQube falló'
        }
    }
}