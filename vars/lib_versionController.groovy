def call(Map config) {
    // Configure project version if not configured
    if ( ! config.project_version ) {
        def full_version

        if ( params.BRANCH ==~ /[pP]roduction/  ) {
            full_version = sh(
                script: "python3 -c 'import sys,yaml,os; from datetime import datetime; b=int(os.environ['BUILD_NUMBER']); t=datetime.now(); major=yaml.load(open('config.yaml'),Loader=yaml.CLoader)['project']['version']; print(f\"{major}.{b//100}.{b%100}.{t.year-2000}{t.strftime('%j')}\")",
                returnStdout: true
            ).trim()
        } else {
            full_version = sh(
                script: "cat ${config.config_file} | python3 -c 'import sys, yaml; print(yaml.load(sys.stdin, Loader=yaml.CLoader)[\"project\"][\"version\"])'",
                returnStdout: true
            ).trim()
        }
        config.project_full_version = full_version
    }
}