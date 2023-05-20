def call(Map config) {
    config.b_config.projects.each { it ->
        if ( it.containsKey("push") && it.push ) {
            withCredentials([[$class:"UsernamePasswordMultiBinding", credentialsId: it.id, usernameVariable: "USERNAME", passwordVariable: "PASSWORD"]]) {
                sh """
                find ${WORKSPACE}/${it.path} -name "*.jar" -exec \
                    curl -v -u ${USERNAME}:${PASSWORD} \
                    -X POST "${config.artifact_repo_address}/service/rest/v1/components?repository=${it.repo}" \
                    -F "maven2.asset1=@{}" \
                    -F maven2.asset1.extension=jar \
                    -F maven2.generate-pom=true
                """
            }
        }
    }
}