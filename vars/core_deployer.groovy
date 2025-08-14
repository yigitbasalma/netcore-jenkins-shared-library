def call(Map config) {

    pipeline {
        agent {label 'docker-node'}

        parameters {
            string(name: 'IMAGE', defaultValue: '', description: '')
            string(name: 'BRANCH', description: '', defaultValue: '')
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
                        config.config_file = config.containsKey('config_file_path') ? config.config_file_path : ".jenkins/buildspec.yaml"
                        config.b_config = readYaml file: config.config_file
                        config.job_base = sh(
                            script: "python3 -c 'print(\"/\".join(\"${JOB_NAME}\".split(\"/\")[:-1]))'",
                            returnStdout: true
                        ).trim()
                        config.job_name = sh(
                            script: "python3 -c 'print(\"${JOB_NAME}\".split(\"/\")[-1])'",
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

                        if ( config.b_config.containsKey("sequentialDeploymentMapping") ) {
                            config.sequential_deployment_mapping = config.b_config.sequentialDeploymentMapping[config.target_branch]
                        }

                        if ( config.containsKey("permit_trigger_branches") ) {
                            config.permit_trigger_branch = config.permit_trigger_branches
                        }

                        // If environment is production, prevent early deployment
//                         if ( config.environment == "production" ) {
//                             def now = new Date()
//                             def currentHour = now.format('HH', TimeZone.getTimeZone('Europe/Istanbul')) as int
//                             def currentMinute = now.format('mm', TimeZone.getTimeZone('Europe/Istanbul')) as int
//
//                             if (currentHour < 23 || (currentHour == 23 && currentMinute < 30)) {
//                                 currentBuild.result = "ABORTED"
//                                 buildDescription("Error: Current time is before 23:30.")
//                                 error("Build aborted: Current time is before 23:30.")
//                             }
//                         }
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
                    script {
                        lib_deployController(
                            config
                        )
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

                script {
                    try {
                        withCredentials([string(credentialsId: 'teams-webhook-url', variable: 'URL_WEBHOOK')]) {
                            office365ConnectorSend webhookUrl: "${URL_WEBHOOK}"
                        }
                    } catch (_) {
                        echo "Teams credential does not exists, skipping."
                    }
                }
            }
            success {
                buildDescription("Container ID: ${env.CONTAINER_IMAGE_ID}")

                script {
                    lib_helper.triggerJob(
                        config
                    )
                }
            }
        }

    }
}
