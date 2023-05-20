def call(Map config) {
    if ( config.b_config.controllers.postbuildController ) {
        def informations = [
            b_status: currentBuild.currentResult.toLowerCase(),
            b_id: currentBuild.number,
            b_name: currentBuild.fullProjectName,
            b_link: BUILD_URL
        ]

        // Firstly, run always tagged jobs if exists any
        if ( config.b_config.postBuild.containsKey("always") ) {
            config.b_config.postBuild.always.each { it ->
                "${it.key}"(it.value, informations)
            }

            config.b_config.postBuild.remove("always")
        }

        // Run job named current status if exits any
        if ( config.b_config.postBuild[informations.b_status] ) {
            config.b_config.postBuild[informations.b_status].each { it ->
                "${it.key}"(it.value, informations)
            }
        }
    }
}

def triggerDependedProjects(List dependencies, Map informations) {
    for ( i = 0; i < dependencies.size(); i++ ) {
        build job: "${dependencies[i]}", propagate: false, wait: false
    }
}

def sendEmail(List environments, Map informations) {
    def body = environments[0].message
    def subject = environments[0].subject
    informations.each { it ->
        body = body.replace("\$${it.key}", "${it.value}")
        subject = subject.replace("\$${it.key}", "${it.value}")
    }
    
    emailext(
        to: "${environments[0].addresses.join(",")}",
        body: "${body}",
        mimeType: "text/html",
        subject: "${subject}"
    )
}

def sendDiscordMessage(List environments, Map informations) {
    def description = environments[0].description
    def footer = environments[0].footer
    informations.each { it ->
        description = description.replace("\$${it.key}", "${it.value}")
        footer = footer.replace("\$${it.key}", "${it.value}")
    }

    discordSend description: description, 
                footer: footer, 
                link: env.BUILD_URL, 
                result: currentBuild.currentResult, 
                title: JOB_NAME, 
                webhookURL: ""
}