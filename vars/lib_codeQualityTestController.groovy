def call(Map config) {
    withSonarQubeEnv(config.sonarqube_env_name) {
        sh """
        ${config.sonarqube_home}/SonarScanner.MSBuild.dll begin \
            /k:${config.sonar_qube_project_key} \
            /v:${config.project_full_version} \
            /n:${config.sonar_qube_project_key} \
            /d:sonar.links.ci=${BUILD_URL} \
            ${config.b_config.project.solutionFilePath}
        """
    }
}

def finisfScan() {
    withSonarQubeEnv(config.sonarqube_env_name) {
        sh """
        ${config.sonarqube_home}/SonarScanner.MSBuild.dll end
        """
    }

    timeout(time: 1, unit: 'HOURS') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}
