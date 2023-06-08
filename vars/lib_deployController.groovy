def call(Map config, String sshKeyFile) {
    // SSH key file permission
    sh "chmod 600 ${sshKeyFile}"
    container_repository = "${config.container_artifact_repo_address}/${config.container_repo}"

    if ( config.scope == "branch" && params.IMAGE == "" ) {
        currentBuild.result = "ABORTED"
        error("You have to set IMAGE_ID parameter for branch deployment.")
    }

    config.b_config.deploy.each { it ->
        // Replacing {environment} definition in path for backward compatibility
        path = "${it.path.replace('/{environment}', '')}/{environment}"

        if ( config.scope == "branch" ) {
            path = "${it.path}/branch/${config.target_branch}"
        }

        "${it.type}"(config, config.image, it, path, sshKeyFile, container_repository)
    }
}

def argocd(Map config, String image, Map r_config, String path, String sshKeyFile, String containerRepository) {
    // Change image version on argocd repo and push
    sh """
    ${config.script_base}/argocd/argocd.py --image "${containerRepository}/${r_config.name}:${image}" -r ${r_config.repo} --application-path ${path} --environment ${config.environment} --key-file "${sshKeyFile}"
    """

    // check auto sync status for environment
    if ( config.b_config.containsKey("argocd")
        && config.b_config.argocd.containsKey(config.environment)
        && config.b_config.argocd[config.environment].autoSync) {

        withCredentials([string(credentialsId: config.b_config.argocd[config.environment].tokenID, variable: 'TOKEN')]) {
            sh """
            argocd app sync ${path.split('/')[1]} --force --insecure --grpc-web --server ${config.b_config.argocd[config.environment].url} --auth-token $TOKEN
            """
        }
    }
}

def nativeK8s(Map config, String image, Map r_config, String path, String sshKeyFile, String containerRepository) {
    sh """
    ${config.script_base}/native_k8s/argocd.py \
        --kubeconfig /opt/k8s-admin-configs/${config.environment}-config \
        --namespace-selector ${r_config.namespaceSelector} \
        --deployment-selector ${r_config.appNameSelector} \
        --image ${image} \
        --per-namespace ${r_config.deployThread}
    """
}