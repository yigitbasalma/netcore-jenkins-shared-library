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

def finisfScan() {
    withSonarQubeEnv(config.sonarqube_env_name) {
        sh """
        dotnet ${config.sonarqube_home}/SonarScanner.MSBuild.dll end
        """
    }

    timeout(time: 1, unit: 'HOURS') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}
