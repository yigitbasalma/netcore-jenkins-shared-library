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
                    withCredentials([sshUserPrivateKey(credentialsId: config.scm_credentials_id, keyFileVariable: 'keyfile')]) {
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
                    if ( config.environment == "production" ) {
                        withCredentials([usernamePassword(credentialsId: 'jira-automation-auth', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            sh """
                            # 629ef1b2451c00006964feb9 => Korcan Özsuer
                            python3 ${config.script_base}/jira/main.py \
                             --container_id ${env.CONTAINER_IMAGE_ID} \
                             --summary "${config.b_config.project.name} Project" \
                             --server https://trzipco.atlassian.net \
                             --user $USERNAME \
                             --apikey $PASSWORD \
                             --project_key PRODDEP \
                             --assignee 629ef1b2451c00006964feb9
                            """
                        }
                    }
                }
            }
        }

    }
}
