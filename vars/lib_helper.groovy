def configureInit(Map config) {
    // Define constraints
    config.script_base = "/scripts"
    config.scope = "global"

    // Configure repository settings
    config.scm_global_config = [url: config.scm_address]
    if ( config.scm_security ) {
        config.scm_global_config.credentialsId = "${config.scm_credentials_id}"
    }

    // Configure branch from params
    if ( params.containsKey("BRANCH") && params.BRANCH != "" ) {
        config.target_branch = params.BRANCH
        config.scope = "branch"
    }

    buildName "${config.target_branch} - ${env.BUILD_NUMBER}"

    // SonarQube settings
    config.sonarqube_env_name = "sonarqube01"
    config.sonarqube_home = tool config.sonarqube_env_name

    // Repo settings
    sh "git config --global --add safe.directory '${WORKSPACE}'"

    // Sequential deployment mapping
    config.sequential_deployment_mapping = [
        "1_Build": "2_DeployToTest"
    ]
}

def configureBranchDeployment(Map config, String sshKeyFile) {
    // SSH key file permission
    sh "chmod 600 ${sshKeyFile}"

    config.b_config.deploy.each { it ->
        String yml = writeYaml returnText: true, data: it.deploy
        sh """
        ${config.script_base}/branch-controller/controller.py -r ${it.repo} --deploy-config "${yml}" --application-path ${it.path.replace('/{environment}', '')}/branch --branch ${config.target_branch} --key-file "${sshKeyFile}"
        """
    }
}

def triggerJob(Map config) {
    if ( ! config.sequential_deployment_mapping.containsKey(config.job_name) ) {
        return
    }

    next_job_name = config.sequential_deployment_mapping[config.job_name]
    build job: "${config.job_base}/${next_job_name}", propagate: false, wait: false, parameters: [string(name: 'IMAGE', value: config.b_config.imageTag)]
}