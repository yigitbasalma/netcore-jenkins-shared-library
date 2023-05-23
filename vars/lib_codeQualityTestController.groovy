def call(Map config) {
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
