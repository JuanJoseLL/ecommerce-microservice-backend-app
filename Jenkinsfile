// Script para Jenkins Pipeline (pegar en la UI)
pipeline {
    agent any

    environment {
        AZURE_CREDENTIALS_ID = 'AZURE_CREDENTIALS_ID'     // <<< ¡ASEGÚRATE DE USAR EL ID CORRECTO DE TU CREDENCIAL "Azure Service Principal"!
        ACR_NAME             = 'micsvcwsacr'
        AKS_CLUSTER_NAME     = 'micsvc-ws-aks'
        AKS_RESOURCE_GROUP   = 'jenkins-get-started-rg'
        K8S_TARGET_NAMESPACE = 'ecommerce-app'
        K8S_MANIFESTS_ROOT   = 'k8s'
        DOCKERFILE_DIR_ROOT  = '.'
    }

    parameters {
        string(name: 'BUILD_TAG', defaultValue: "${env.BUILD_ID}", description: 'Tag para la imagen Docker.')
    }

    stages {
        stage('Checkout Code & Verify Workspace') { // Renombrado para más claridad
            steps {
                // Este paso explícito de checkout asegura que el código se obtiene.
                // Asume que la configuración SCM está correctamente definida en la UI del job.
                // Si tu repo es privado y necesita credenciales, asegúrate de que estén configuradas en el SCM del job.
                checkout scm
                script {
                    echo "Workspace después del checkout explícito:"
                    sh 'ls -la'
                    // Verifica si un archivo clave existe, por ejemplo, el pom.xml de un microservicio
                    if (fileExists("${env.DOCKERFILE_DIR_ROOT}/api-gateway/pom.xml")) {
                        echo "api-gateway/pom.xml encontrado. El checkout parece haber funcionado."
                    } else {
                        error "ERROR CRÍTICO: api-gateway/pom.xml NO encontrado. El checkout del código SCM falló o el workspace está incorrecto."
                    }
                }
            }
        }

        stage('Initialize & Login Azure') {
            steps {
                script {
                    echo "Usando BUILD_TAG: ${params.BUILD_TAG}"
                    // CORRECCIÓN: Se quitan subscriptionId y tenantId de aquí.
                    // El plugin Azure Credentials los tomará de la configuración de la credencial en Jenkins.
                    withCredentials([azureServicePrincipal(credentialsId: env.AZURE_CREDENTIALS_ID)]) {
                        sh "az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID"
                        sh "az acr login --name ${env.ACR_NAME}"
                        sh "az aks get-credentials --resource-group ${env.AKS_RESOURCE_GROUP} --name ${env.AKS_CLUSTER_NAME} --overwrite-existing"
                    }
                }
            }
        }

        stage('Apply Common Kubernetes Manifests') {
            steps {
                script {
                    def namespaceFile = "${env.K8S_MANIFESTS_ROOT}/namespace.yaml"
                    def commonConfigFile = "${env.K8S_MANIFESTS_ROOT}/common-config.yaml"

                    if (fileExists(namespaceFile)) {
                        echo "Aplicando Namespace: ${namespaceFile}"
                        sh "kubectl apply -f ${namespaceFile}"
                    } else {
                        echo "ADVERTENCIA: Archivo de Namespace ${namespaceFile} no encontrado. Asegúrate de que el namespace '${env.K8S_TARGET_NAMESPACE}' exista o se cree."
                    }

                    if (fileExists(commonConfigFile)) {
                        echo "Aplicando Common ConfigMap: ${commonConfigFile} al namespace ${env.K8S_TARGET_NAMESPACE}"
                        sh "kubectl apply -f ${commonConfigFile} -n ${env.K8S_TARGET_NAMESPACE}"
                    } else {
                        echo "ADVERTENCIA: Archivo de ConfigMap común ${commonConfigFile} no encontrado."
                    }
                }
            }
        }

        stage('Deploy Core Infrastructure Services') {
            steps {
                script {
                    echo "Deploying Service Discovery (Eureka)..."
                    // Deploy service-discovery (build from source)
                    def sdConfig = [name: 'service-discovery', k8sDeploymentName: 'service-discovery']
                    deployMicroservice(sdConfig, params.BUILD_TAG, true) // true for buildFromSource

                    echo "Deploying Zipkin..."
                    // Deploy Zipkin (pre-built image)
                    def zipkinDeploymentFile = "${env.K8S_MANIFESTS_ROOT}/zipkin/deployment.yaml"
                    def zipkinServiceFile = "${env.K8S_MANIFESTS_ROOT}/zipkin/service.yaml"

                    if (fileExists(zipkinDeploymentFile)) {
                        echo "Applying Deployment ${zipkinDeploymentFile} for Zipkin in namespace ${env.K8S_TARGET_NAMESPACE}"
                        sh "kubectl apply -f ${zipkinDeploymentFile} -n ${env.K8S_TARGET_NAMESPACE}"
                        if (fileExists(zipkinServiceFile)) {
                            echo "Applying Service ${zipkinServiceFile} for Zipkin in namespace ${env.K8S_TARGET_NAMESPACE}"
                            sh "kubectl apply -f ${zipkinServiceFile} -n ${env.K8S_TARGET_NAMESPACE}"
                        }
                        echo "Verifying rollout status for deployment/zipkin in namespace ${env.K8S_TARGET_NAMESPACE}"
                        sh "kubectl rollout status deployment/zipkin -n ${env.K8S_TARGET_NAMESPACE} --timeout=5m"
                    } else {
                        echo "ADVERTENCIA: Archivo de Deployment ${zipkinDeploymentFile} no encontrado para Zipkin."
                    }
                }
            }
        }

        stage('Build, Push & Deploy Configuration Server') {
            steps {
                script {
                    echo "Deploying Cloud Config Server..."
                    def ccConfig = [name: 'cloud-config', k8sDeploymentName: 'cloud-config']
                    deployMicroservice(ccConfig, params.BUILD_TAG, true) // true for buildFromSource
                }
            }
        }
        
        stage('Build, Push & Deploy Application Microservices') {
            steps {
                script {
                    // Service Discovery and Cloud Config are now deployed in earlier dedicated stages
                    def microservicesToDeploy = [
                        [name: 'api-gateway',         k8sDeploymentName: 'api-gateway',         hasIngress: true],
                        [name: 'proxy-client',        k8sDeploymentName: 'proxy-client'],
                        [name: 'user-service',        k8sDeploymentName: 'user-service'],
                        [name: 'product-service',     k8sDeploymentName: 'product-service'],
                        [name: 'order-service',       k8sDeploymentName: 'order-service'],
                        [name: 'payment-service',     k8sDeploymentName: 'payment-service'],
                        [name: 'favourite-service',   k8sDeploymentName: 'favourite-service'],
                        [name: 'shipping-service',    k8sDeploymentName: 'shipping-service'],
                    ]

                    microservicesToDeploy.each { msConfig ->
                        deployMicroservice(msConfig, params.BUILD_TAG, true) // true for buildFromSource
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Pipeline finalizado. Limpiando archivos temporales..."
                sh "rm -f processed-*-deployment.yaml"
            }
        }
        success {
            script {
                echo "Pipeline ejecutado con éxito."
            }
        }
        failure {
            script {
                echo "Pipeline falló. Revisa los logs."
            }
        }
    }
}

// Helper function to deploy a microservice
// buildFromSource: boolean, true if it needs mvn build and docker push, false if using pre-built image (like zipkin)
def deployMicroservice(Map msConfig, String buildTag, boolean buildFromSource) {
    def msName = msConfig.name
    def k8sDepName = msConfig.k8sDeploymentName ?: msName // Fallback to msName if k8sDeploymentName not provided
    def imageNameBase = msConfig.imageNameOnACR ?: msName
    def imageNameInACR = "${env.ACR_NAME}.azurecr.io/${imageNameBase}"
    def fullImageNameWithTag = "${imageNameInACR}:${buildTag}"
    def dockerfileContextPath = "${env.DOCKERFILE_DIR_ROOT}/${msName}"

    if (buildFromSource) {
        stage("Maven Build: ${msName}") {
            echo "Construyendo ${msName} con Maven..."
            dir(dockerfileContextPath) {
                sh "./mvnw clean package -DskipTests"
            }
        }

        stage("Docker Build & Push: ${msName}") {
            echo "Construyendo imagen Docker ${fullImageNameWithTag} desde el contexto ${dockerfileContextPath}"
            dir(dockerfileContextPath) {
                docker.build(fullImageNameWithTag, ".")
            }
            echo "Pusheando imagen ${fullImageNameWithTag} a ${env.ACR_NAME}"
            docker.image(fullImageNameWithTag).push()
        }
    }

    stage("Kubernetes Deploy: ${msName}") {
        def deploymentFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/deployment.yaml"
        def serviceFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/service.yaml"
        def ingressFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/ingress.yaml"
        def processedDeploymentFile = "processed-${msName}-deployment.yaml" // Stays in workspace root

        if (fileExists(deploymentFile)) {
            def manifestContent = readFile(deploymentFile)
            // Ensure the placeholder matches exactly what's in your YAML files.
            // It's common for the placeholder tag to be something generic.
            def placeholderInManifest = "image: ${imageNameInACR}:PLACEHOLDER_TAG" 
            // If Zipkin or other pre-built images are handled by this function AND they have a deployment.yaml,
            // this replacement logic needs to be conditional or the placeholder needs to exist in their yaml.
            // For Zipkin, we're applying its manifest directly in its own stage, so this part is for source-built services.
            def updatedManifestContent = manifestContent.replace(placeholderInManifest, "image: ${fullImageNameWithTag}")
            writeFile(file: processedDeploymentFile, text: updatedManifestContent)

            echo "Aplicando Deployment ${processedDeploymentFile} para ${msName} en namespace ${env.K8S_TARGET_NAMESPACE}"
            sh "kubectl apply -f ${processedDeploymentFile} -n ${env.K8S_TARGET_NAMESPACE}"
            
            if (fileExists(serviceFile)) {
                echo "Aplicando Service ${serviceFile} para ${msName} en namespace ${env.K8S_TARGET_NAMESPACE}"
                sh "kubectl apply -f ${serviceFile} -n ${env.K8S_TARGET_NAMESPACE}"
            }

            echo "Verificando estado del rollout para deployment/${k8sDepName} en namespace ${env.K8S_TARGET_NAMESPACE}"
            sh "kubectl rollout status deployment/${k8sDepName} -n ${env.K8S_TARGET_NAMESPACE} --timeout=5m"

            if (msConfig.hasIngress && fileExists(ingressFile)) {
                echo "Aplicando Ingress ${ingressFile} para ${msName} en namespace ${env.K8S_TARGET_NAMESPACE}"
                sh "kubectl apply -f ${ingressFile} -n ${env.K8S_TARGET_NAMESPACE}"
            }
        } else {
            echo "ADVERTENCIA: Archivo de Deployment ${deploymentFile} no encontrado para ${msName}. Saltando despliegue de Kubernetes."
        }
    }
}
