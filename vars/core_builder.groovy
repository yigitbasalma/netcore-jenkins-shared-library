def call(Map config) {

    def triggerRef = config.containsKey('trigger_ref') ? config.trigger_ref : '$.push.changes[0].old.name'
    def triggerRegexpFilter = config.containsKey('trigger_regexp_filter') ? config.trigger_regexp_filter : '^(development|uat)'

    if ( config.containsKey("github_hook") && config.github_hook ) {
        properties([pipelineTriggers([githubPush()])])
    }

    pipeline {
        agent {label 'docker-node'}

        options {
            timeout(time: 45, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))
            disableConcurrentBuilds()
        }

        parameters {
            string(name: 'BRANCH', description: 'Branch to build', defaultValue: '')
        }

        triggers {
            GenericTrigger(
                genericVariables: [
                    [key: 'REF', value: triggerRef],
                ],
                 causeString: 'Triggered by Remote Event',
                 token: 'bitbucket_' + config.sonar_qube_project_key,
                 printContributedVariables: false,
                 printPostContent: false,
                 silentResponse: false,
                 shouldNotFlattern: false,

                 regexpFilterText: '$REF',
                 regexpFilterExpression: triggerRegexpFilter
            )
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

                        // Configure commit ID for project
                        commitID = sh(
                            script: """
                            git log --pretty=format:"%h" | head -1
                            """,
                            returnStdout: true
                        ).trim()
                        randomContainerID = sh(
                            script: "python3 -c 'import uuid; print(str(uuid.uuid4()).split(\"-\")[0])'",
                            returnStdout: true
                        ).trim()

                         // config.b_config.imageTag = commitID + "R" + randomContainerID
                        config.b_config.imageTag = commitID
                        config.b_config.imageLatestTag = "latest"
                        config.b_config.originalCommitID = commitID

                        // config.commitID = commitID + "R" + randomContainerID
                        config.commitID = commitID

                        writeFile file: 'result.txt', text: config.b_config.imageTag
                        archiveArtifacts artifacts: 'result.txt'

                        if ( config.containsKey("overrideImageTag" && config.overrideImageTag ) ) {
                            newTagPart = sh(
                                script: "python3 -c 'import datetime; print(datetime.datetime.now().strftime(\"%Y%m%d%H%M%S\"))'",
                                returnStdout: true
                            ).trim()

                            config.b_config.imageTag = newTagPart + commitID
                            config.b_config.imageLatestTag = newTagPart + "latest"
                        }

                        if ( config.scope == "branch" && !config.permit_trigger_branch.contains(config.target_branch) ) {
                            config.b_config.imageLatestTag = "${config.target_branch.replace('/', '-')}-latest"
                        }

                        if ( config.b_config.containsKey("sequentialDeploymentMapping") ) {
                            config.sequential_deployment_mapping = config.b_config.sequentialDeploymentMapping[config.target_branch]
                        }

                        if ( config.b_config.containsKey("triggerDeploy") ) {
                            config.b_config.deploy.each { dp ->
                                if ( dp.type in ["nativeK8s"] ) {
                                    if ( dp.namespaceSelector.startsWith(".*") ) {
                                        currentBuild.result = "ABORTED"
                                        error("You cannot use wildcard namespace pattern when build branch.")
                                    }
                                }
                            }

                            config.permit_trigger_branch = [config.target_branch]
                        }
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
                script {
                    def publisher = LastChanges.getLastChangesPublisher "PREVIOUS_REVISION", "SIDE", "LINE", true, true, "", "", "", "", ""
                    publisher.publishLastChanges()
                    def htmlDiff = publisher.getHtmlDiff()
                    writeFile file: 'build-diff.html', text: htmlDiff

                    lib_helper.triggerJob(
                        config
                    )
                }
            }
        }

    }
}
