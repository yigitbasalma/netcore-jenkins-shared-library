def call(Map config) {

    pipeline {
        agent {label 'docker-node'}

        parameters {
            string(name: 'IMAGE', defaultValue: '', description: '')
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
                        commitID = sh(
                            script: """
                            git log --pretty=format:"%h" | head -1
                            """,
                            returnStdout: true
                        ).trim()

                        // Configure image from params
                        if ( params.containsKey("IMAGE") && params.IMAGE != "" ) {
                            config.image = params.IMAGE
                        } else {
                            currentBuild.result = "ABORTED"
                            buildDescription("Error: You have to set IMAGE_ID parameter for branch deployment.")
                            error("You have to set 'IMAGE' parameter.")
                        }

                        // Set container id global
                        env.CONTAINER_IMAGE_ID = config.image

                        config.b_config.imageTag = commitID
                        config.b_config.imageLatestTag = "latest"
                        config.commitID = commitID
                    }
                }
            }

            stage("Deploy New Code") {
                when {
                    expression {
                        return config.b_config.controllers.deployController
                    }
                }
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: config.argocd_credentials_id, keyFileVariable: 'keyfile')]) {
                        script {
                            lib_deployController(
                                config,
                                keyfile
                            )
                        }
                    }
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
                buildDescription("Container ID: ${env.CONTAINER_IMAGE_ID}")

                script {
                    lib_helper.triggerJob()
                }
            }
        }

    }
}
