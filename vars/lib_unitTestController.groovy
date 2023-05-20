def call(Map config) {
    def unit_test_result_file_name = "coverage.cobertura.xml"

    sh """
    mvn cobertura:cobertura \
     -Dcobertura.report.format=xml \
     -DoutputDirectory='${WORKSPACE}/'
    """

    // Available serving engines
    def servingEngines = [
        "coberturaAdapter",
        "istanbulCoberturaAdapter",
        "jacocoAdapter",
        "opencoverAdapter"
    ]

    // Report serving strategy
    if ( ! config.b_config.unitTests ) {
        config.b_config.unitTests = [
            resultFile: unit_test_result_file_name,
            adapter: "coberturaAdapter",
            mergeToOneReport: false,
            unhealthyThreshold: 0.0,
            unstableThreshold: 0.0
        ]
    }

    if ( servingEngines.contains(config.b_config.unitTests.adapter) ) {
        publishCoverage adapters: [
            "${config.b_config.unitTests.adapter}"(
                path: "**/${config.b_config.unitTests.resultFile}",
                mergeToOneReport: config.b_config.unitTests.mergeToOneReport,
                thresholds: [
                    [
                        thresholdTarget: "Function",
                        unhealthyThreshold: config.b_config.unitTests.unhealthyThreshold,
                        unstableThreshold: config.b_config.unitTests.unstableThreshold
                    ]
                ]
            )
        ]
    } else {
        if ( config.b_config.unitTests.adapter == "junit" ) {
            // sh "find . -name '${config.b_config.unitTests.resultFile}' -exec touch {} \\;"
            junit "**/${config.b_config.unitTests.resultFile}"
        }
    }
}