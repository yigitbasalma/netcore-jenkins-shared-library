def call(Map config) {
    sh """
    rm -rf ${WORKSPACE}/* && \
    docker rmi \$(docker images -f "dangling=true" -q) 2> /dev/null || true
    """

    if ( config.docker_volume_prune ) {
        sh """
        docker system prune --volumes -f
        """
    }
}