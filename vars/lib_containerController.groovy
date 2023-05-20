def call(Map config) {
    // Check object existence
    if ( ! config.b_config.containerConfig ) {
        currentBuild.result = "ABORTED"
        error("You have to set 'containerConfig' in your yaml file.")
    }

    // Locals
    def containerImages = []

    // Define constraints
    def builds = [:]
    def container_repository = "${config.container_artifact_repo_address}/${config.container_repo}"

    buildDescription("Container ID: ${config.b_config.imageTag}")

    config.b_config.containerConfig.each { it ->
        def extraParams = []
        def buildArgs = []

        // Environments
        def repoName = it.name.replace("_", "-").toLowerCase()
        def dockerFilePath = it.dockerFilePath.replace("_", "-")

        containerImages.add("${container_repository}/${repoName}:${config.b_config.imageTag} ${dockerFilePath}")

        builds["${repoName}"] = {
            timeout(time: 45, unit: "MINUTES") {
                stage("Building ${repoName}") {
                    script {
                        try {
                            sh """
                            docker build --rm  \
                                -t ${container_repository}/${repoName.toLowerCase()}:${config.b_config.imageTag} \
                                -t ${container_repository}/${repoName.toLowerCase()}:${config.b_config.imageLatestTag} \
                                ${extraParams.unique().join(" ")} \
                                ${buildArgs.unique().join(" ")} \
                                -f ${dockerFilePath} \
                                ${it.contextPath}
                            """
                        } catch (Exception e) {
                            currentBuild.result = "ABORTED"
                            error("Error occurred when building container images. Image Name: ${it.name}")
                        }
                    }
                }
            }
        }
    }

    parallel builds

    config.b_config.containerConfig.each { it ->
        // Environments
        def repoName = it.name.replace("\$Identifier", config.container_repo).replace("_", "-")

        withCredentials([[$class:"UsernamePasswordMultiBinding", credentialsId: "nexus-deployuser", usernameVariable: "USERNAME", passwordVariable: "PASSWORD"]]) {
            sh """
            docker login --username $USERNAME --password $PASSWORD ${container_repository}
                docker push  ${container_repository}/${repoName.toLowerCase()}:${config.b_config.imageLatestTag} && \
                docker push  ${container_repository}/${repoName.toLowerCase()}:${config.b_config.imageTag}
            """
        }
    }

    config.containerImages = containerImages
}
