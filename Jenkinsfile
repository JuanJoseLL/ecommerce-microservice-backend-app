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

        stage('Build, Push & Deploy Microservices') {
            steps {
                script {
                    def microservicesToDeploy = [
                        [name: 'cloud-config',        k8sDeploymentName: 'cloud-config'],
                        [name: 'service-discovery',   k8sDeploymentName: 'service-discovery'],
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
                        def msName = msConfig.name
                        def dockerfileContextPath = "${env.DOCKERFILE_DIR_ROOT}/${msName}"

                        stage("Maven Build: ${msName}") {
                            echo "Construyendo ${msName} con Maven..."
                            dir(dockerfileContextPath) {
                                sh "./mvnw clean package -DskipTests"
                            }
                        }

                        stage("Docker Build & Push: ${msName}") {
                            def imageTag = params.BUILD_TAG
                            def imageNameBase = msConfig.imageNameOnACR ?: msName
                            def imageNameInACR = "${env.ACR_NAME}.azurecr.io/${imageNameBase}"
                            def fullImageNameWithTag = "${imageNameInACR}:${imageTag}"
                            def dockerFileContextPath = "${env.DOCKERFILE_DIR_ROOT}/${msName}"

                            echo "Construyendo imagen Docker ${fullImageNameWithTag} desde el contexto ${dockerFileContextPath}"
                            dir(dockerFileContextPath) {
                                docker.build(fullImageNameWithTag, ".")
                            }
                            echo "Pusheando imagen ${fullImageNameWithTag} a ${env.ACR_NAME}"
                            docker.image(fullImageNameWithTag).push()
                        }

                        stage("Kubernetes Deploy: ${msName}") {
                            def imageTag = params.BUILD_TAG
                            def imageNameBase = msConfig.imageNameOnACR ?: msName
                            def imageNameInACR = "${env.ACR_NAME}.azurecr.io/${imageNameBase}"
                            def fullImageNameWithTag = "${imageNameInACR}:${imageTag}"

                            def deploymentFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/deployment.yaml"
                            def serviceFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/service.yaml"
                            def ingressFile = "${env.K8S_MANIFESTS_ROOT}/${msName}/ingress.yaml"
                            def processedDeploymentFile = "processed-${msName}-deployment.yaml"

                            if (fileExists(deploymentFile)) {
                                def manifestContent = readFile(deploymentFile)
                                def placeholderInManifest = "image: ${imageNameInACR}:PLACEHOLDER_TAG"
                                def updatedManifestContent = manifestContent.replace(placeholderInManifest, "image: ${fullImageNameWithTag}")
                                writeFile(file: processedDeploymentFile, text: updatedManifestContent)

                                echo "Aplicando Deployment ${processedDeploymentFile} para ${msName} en namespace ${env.K8S_TARGET_NAMESPACE}"
                                sh "kubectl apply -f ${processedDeploymentFile} -n ${env.K8S_TARGET_NAMESPACE}"

                                if (fileExists(serviceFile)) {
                                    echo "Aplicando Service ${serviceFile} para ${msName} en namespace ${env.K8S_TARGET_NAMESPACE}"
                                    sh "kubectl apply -f ${serviceFile} -n ${env.K8S_TARGET_NAMESPACE}"
                                }

                                def k8sDepName = msConfig.k8sDeploymentName ?: msName
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
