def call(Map config) {
    sh "${config.b_config.project.builderVersion} restore --no-cache ${config.b_config.project.solutionFilePath}"

    config.b_config.projects.each { it ->
        def buildArgs = []
        def mode = "build"

        if ( it.containsKey("mode") ) {
            mode = it.mode
        }

        if ( config.b_config.containsKey("buildArgs") ) {
            buildArgs.addAll(config.b_config.buildArgs)
        }

        if ( it.containsKey("buildArgs") ) {
            buildArgs.addAll(it.buildArgs)
        }

        if ( it.containsKey("restore") && it.restore ) {
            def restoreArgs = []

            if ( it.containsKey("restoreArgs") ) {
                restoreArgs.addAll(it.restoreArgs)
            }

            sh """
            ${config.b_config.project.builderVersion} restore --no-cache \
                ${restoreArgs.unique().join(" ")}
            """
        }

        sh """
        ${config.b_config.project.builderVersion} ${mode} -c Release --no-restore \
            -o ${config.b_config.project.solutionFilePath}${it.path}/out \
            ${buildArgs.unique().join(" ")} \
            /p:Version="${config.project_full_version}" \
            ${config.b_config.project.solutionFilePath}${it.path}
        """
    }

    if ( config.b_config.controllers.codeQualityTestController ) {
        withSonarQubeEnv(config.sonarqube_env_name) {
            sh """
            dotnet ${config.sonarqube_home}/SonarScanner.MSBuild.dll end
            """
        }
    }
}
