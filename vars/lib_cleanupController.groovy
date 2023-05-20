def call(Map config) {
    sh """
    rm -rf ${WORKSPACE}/*
    """
}