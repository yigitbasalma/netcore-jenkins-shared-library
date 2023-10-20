def call(Map config) {
    container_repository = "${config.container_artifact_repo_address}"

    if ( config.container_repo != "" ) {
        container_repository = "${config.container_artifact_repo_address}/${config.container_repo}"
    }

    if ( config.scope == "branch" && params.IMAGE == "" ) {
        currentBuild.result = "ABORTED"
        error("You have to set IMAGE_ID parameter for branch deployment.")
    }

    config.b_config.deploy.each { it ->
        "${it.type}"(config, config.image, it, container_repository)
    }
}

def argocd(Map config, String image, Map r_config, String containerRepository) {
    path = "${r_config.path.replace('/{environment}', '')}/{environment}"

    if ( config.scope == "branch" ) {
        path = "${r_config.path}/branch/${config.target_branch}"
    }

    withCredentials([sshUserPrivateKey(credentialsId: config.argocd_credentials_id, keyFileVariable: 'sshKeyFile')]) {
        // Change image version on argocd repo and push
        sh "chmod 600 ${sshKeyFile}"
        sh """
        ${config.script_base}/argocd/argocd.py --image "${containerRepository}/${r_config.name}:${image}" -r ${r_config.repo} --application-path ${path} --environment ${config.environment} --key-file "${sshKeyFile}"
        """
    }

    // check auto sync status for environment
    if ( config.b_config.containsKey("argocd")
        && config.b_config.argocd.containsKey(config.environment)
        && config.b_config.argocd[config.environment].autoSync) {

        withCredentials([string(credentialsId: config.b_config.argocd[config.environment].tokenID, variable: 'TOKEN')]) {
            sh """#!/bin/bash
            argocd app sync ${path.split('/')[1]} \
                --force \
                --insecure \
                --grpc-web \
                --server ${config.b_config.argocd[config.environment].url} \
                --auth-token $TOKEN || if grep "Running";then true; fi
            """
        }
    }
}

def nativeK8s(Map config, String image, Map r_config, String containerRepository) {
    namespaceSelector = r_config.namespaceSelector

    if ( params.containsKey("TARGETS") && params.TARGETS != "" ) {
        _targets = params.TARGETS
        namespaceSelector = "\(${_targets}\)-namespace"
    }

    sh """
    ${config.script_base}/native_k8s/deploy.py \
        --kubeconfig /opt/k8s-admin-configs/${config.environment}-config \
        --namespace-selector ${namespaceSelector} \
        --deployment-selector ${r_config.appNameSelector} \
        --image-id ${containerRepository}/${config.b_config.project.name}:${image} \
        --per-namespace ${r_config.deployThread}
    """
}

def nativeDocker(Map config, String image, Map r_config, String containerRepository) {
    def dockerArgs = []

    if ( r_config.containsKey("port") ) {
        dockerArgs.push("-p ${r_config.port}")
    }

    if ( r_config.containsKey("env") ) {
        r_config.env.each { key, val ->
            dockerArgs.push("-e '${key}=${val}'")
        }
    }

    sshagent(credentials: [config.remoteHostCredentialID]) {
      sh """
      ssh -o StrictHostKeyChecking=no -p ${config.remoteHostSSHPort} ${config.remoteUser}@${config.remoteHost} << EOF
docker rm -f ${r_config.name} 2> /dev/null
docker run -d --name ${r_config.name} ${dockerArgs.unique().join(" ")} ${containerRepository}/${config.b_config.project.name}:${image}
EOF
      """
    }
}