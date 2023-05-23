def call(Map config) {

    pipeline {
        agent any

        options {
            buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))
            disableConcurrentBuilds()
        }

        environment {
            SCANNER_HOME = tool 'SonarScanner for MSBuild'
        }

        parameters {
            string(name: 'BRANCH', description: 'Branch to build', defaultValue: '')
        }

        stages {
            stage("Configure Init") {
                steps {
                    script {
                        lib_helper.configureInit(
                            config
                        )
                    }
                }
            }

            stage("Checkout Project Code") {
                steps {
                    checkout scm: [
                        $class: "GitSCM",
                        branches: [[name: "refs/heads/${config.target_branch}"]],
                        submoduleCfg: [],
                        userRemoteConfigs: [
                            config.scm_global_config
                        ]
                    ]
                }
            }

            stage("Read Project Config") {
                steps {
                    script {
                        // Create config file variable
                        config.config_file = ".jenkins/buildspec.yaml"
                        config.b_config = readYaml file: config.config_file
                        config.job_base = sh(
                            script: "python3 -c 'print(\"${JENKINS_HOME}/jobs/%s\" % \"/jobs/\".join(\"${JOB_NAME}\".split(\"/\")))'",
                            returnStdout: true
                        ).trim()

                        // Configure commit ID for project
                        commitID = sh(
                            script: """
                            git log --pretty=format:"%h" | head -1
                            """,
                            returnStdout: true
                        ).trim()

                        // Define variable for container build
                        config.b_config.imageTag = commitID
                        config.b_config.imageLatestTag = "latest"

                        config.commitID = commitID
                    }
                }
            }

            stage("Change Version Number") {
                when {
                    expression {
                        return config.b_config.controllers.versionNumberController
                    }
                }
                steps {
                    script {
                        lib_versionController(
                            config
                        )
                    }
                }
            }

            stage("Run Unit tests") {
                when {
                    expression {
                        return config.b_config.controllers.unitTestController && 
                            config.b_config.controllers.buildController
                    }
                }
                steps {
                    script {
                        lib_unitTestController(
                            config
                        )
                    }
                }
            }

            stage("Run SonarQube Code Quality") {
                when {
                    expression {
                        return config.b_config.controllers.codeQualityTestController &&
                            config.b_config.controllers.buildController
                    }
                }
                steps {
                    script {
                        lib_codeQualityTestController(
                            config
                        )
                    }
                }
            }

            stage("Build Project") {
                when {
                    expression {
                        return config.b_config.controllers.buildController
                    }
                }
                steps {
                    script {
                        lib_buildController(
                            config
                        )
                    }
                }
            }

            stage("Publish Artifact") {
                when {
                    expression {
                        return config.b_config.controllers.publishController && 
                            config.b_config.controllers.buildController
                    }
                }
                steps {
                    script {
                        lib_publishController(
                            config
                        )
                    }
                }
            }

            stage("Build and Publish as a Container") {
                when {
                    expression {
                        return config.b_config.controllers.containerController
                    }
                }
                steps {
                    script {
                        lib_containerController(
                            config
                        )
                    }
                }
            }

            stage("Security Scan For Container") {
                when {
                    expression {
                        return config.scan_container_image
                    }
                }
                steps {
                    sh """
                    echo ${config.containerImages.join("\n")} > anchore_images
                    """
                    anchore name: "anchore_images", bailOnFail: false
                }
            }

        }

        post {
            always {
                // Take necessary actions
                script {
                    // Cleanup
                    lib_cleanupController(
                        config
                    )

                    lib_postbuildController(
                        config
                    )
                }
            }
            success {
                script {
                    def publisher = LastChanges.getLastChangesPublisher "PREVIOUS_REVISION", "SIDE", "LINE", true, true, "", "", "", "", ""
                    publisher.publishLastChanges()
                    def htmlDiff = publisher.getHtmlDiff()
                    writeFile file: 'build-diff.html', text: htmlDiff
                }
            }
        }

    }
}
