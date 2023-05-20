def call(Map config) {
    // Configure project version if not configured
    if ( ! config.project_version ) {
        def full_version = sh(
            script: "cat ${config.config_file} | python3 -c 'import sys, yaml; print(\".\".join(yaml.load(sys.stdin, Loader=yaml.CLoader)[\"project\"][\"version\"]))'",
            returnStdout: true
        ).trim()
        config.project_full_version = full_version
    }
}