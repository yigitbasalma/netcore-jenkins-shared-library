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

    if ( env.REF ) {
        config.target_branch = env.REF
    }

    buildName "${config.target_branch} - ${env.BUILD_NUMBER}"

    // SonarQube settings
    config.sonarqube_env_name = "sonarqube01"
    config.sonarqube_home = tool config.sonarqube_env_name

    // Repo settings
    sh "git config --global --add safe.directory '${WORKSPACE}'"

    // Sequential deployment mapping
    config.sequential_deployment_mapping = [
        "1_Build": "2_DeployToTest",
        "1_build": "2_deploy_to_test"
    ]

    config.permit_trigger_branch = [
        "development",
        "dev",
        "uat",
        "devops"
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
    try {
        if ( config.sequential_deployment_mapping.containsKey(config.job_name) && config.permit_trigger_branch.contains(config.target_branch) ) {
            next_job_name = config.sequential_deployment_mapping[config.job_name]
            build job: "${config.job_base}/${next_job_name}", propagate: false, wait: false, parameters: [string(name: 'IMAGE', value: config.b_config.imageTag)]
        }
    } catch (Exception e) {
        echo "No job found for trigger."
    }
}