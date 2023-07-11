def call(Map config) {
    try {
        sh "${config.b_config.project.builderVersion} restore --no-cache ${config.b_config.project.solutionFilePath}"
    } catch (Exception e) {
        sh "${config.b_config.project.builderVersion} restore --no-cache ${config.b_config.project.solutionFilePath}/${config.b_config.project[0].path}"
    }

    config.b_config.projects.each { it ->
        def buildArgs = []
        def restoreArgs = []
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
            if ( it.containsKey("restoreArgs") ) {
                restoreArgs.addAll(it.restoreArgs)
            }
        }

        dir("${config.b_config.project.solutionFilePath}${it.path}") {
            if ( config.b_config.controllers.codeQualityTestController ) {
                withSonarQubeEnv(config.sonarqube_env_name) {
                    sh """
                    dotnet ${config.sonarqube_home}/SonarScanner.MSBuild.dll begin \
                        /key:${config.sonar_qube_project_key} \
                        /version:${config.project_full_version} \
                        /name:${config.sonar_qube_project_key} \
                        /d:sonar.links.ci=${BUILD_URL}
                    """
                }
            }

            sh """
            ${config.b_config.project.builderVersion} restore --no-cache \
                ${restoreArgs.unique().join(" ")}
            """
        }

        dir("${config.b_config.project.solutionFilePath}${it.path}") {
            sh """
            ${config.b_config.project.builderVersion} ${mode} -c Release --no-restore \
                -o out \
                ${buildArgs.unique().join(" ")} \
                /p:Version="${config.project_full_version}"
            """

            if ( config.b_config.controllers.codeQualityTestController ) {
                withSonarQubeEnv(config.sonarqube_env_name) {
                    sh """
                    dotnet ${config.sonarqube_home}/SonarScanner.MSBuild.dll end
                    """
                }
            }
        }
    }
}
