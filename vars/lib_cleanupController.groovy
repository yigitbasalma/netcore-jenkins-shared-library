def call(Map config) {
    sh """
    rm -rf ${WORKSPACE}/* && \
    docker rmi \$(docker images -f "dangling=true" -q)
    """
}