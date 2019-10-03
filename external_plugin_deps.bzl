load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "easymock",
        artifact = "org.easymock:easymock:3.1",
        sha1 = "3e127311a86fc2e8f550ef8ee4abe094bbcf7e7e",
    )
    maven_jar(
        name = "cglib",
        artifact = "cglib:cglib-nodep:3.2.6",
        sha1 = "92bf48723d277d6efd1150b2f7e9e1e92cb56caf",
    )
