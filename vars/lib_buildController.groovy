def call(Map config) {
    sh "${config.b_config.project.builderVersion} restore --no-cache ${config.b_config.project.solutionFilePath}"

    config.b_config.projects.each { it ->
        def buildArgs = []

        if ( config.b_config.containsKey("buildArgs") ) {
            buildArgs.addAll(config.b_config.buildArgs)
        }

        if ( it.containsKey("buildArgs") ) {
            buildArgs.addAll(it.buildArgs)
        }

        sh """
        ${config.b_config.project.builderVersion} build -c Release --no-restore \
            -o ${config.b_config.project.solutionFilePath}${it.path}/out \
            ${buildArgs.unique().join(" ")} \
            /p:Version="${config.project_full_version}" \
            ${config.b_config.project.solutionFilePath}${it.path}
        """
    }
}
