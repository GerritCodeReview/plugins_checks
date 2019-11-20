load("//tools/maven:package.bzl", "maven_package")
load("//plugins/checks:version.bzl", "CHECKS_PLUGIN_VERSION")

MAVEN_REPOSITORY = "sonatype-nexus-staging"

URL = "https://oss.sonatype.org/content/repositories/snapshots" if CHECKS_PLUGIN_VERSION.endswith("-SNAPSHOT") else "https://oss.sonatype.org/service/local/staging/deploy/maven2"

maven_package(
    src = {
        "checks": "//plugins/checks:checks-rest-api-client-sources_deploy.jar",
    },
    doc = {
        "checks": "//plugins/checks:checks-rest-api-client-javadoc",
    },
    jar = {
        "checks": "//plugins/checks:checks-rest-api-client_deploy.jar",
    },
    repository = "sonatype-nexus-staging",
    url = URL,
    version = CHECKS_PLUGIN_VERSION,
)
