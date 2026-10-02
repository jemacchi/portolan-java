# Distribution

Each tagged version can be consumed as a GitHub release or from GitHub
Packages. Maven Central is the preferred long-term public repository because it
does not require GitHub credentials from consumers.

## Release artifacts

Create a tag from `main`:

```bash
git tag v0.1.1
git push origin v0.1.1
```

The release workflow removes the `-SNAPSHOT` suffix for that build. It runs all
tests and creates these GitHub release assets:

```text
portolan-java-0.1.1.jar
portolan-java-0.1.1-sources.jar
portolan-java-0.1.1-javadoc.jar
portolan-java-0.1.1.pom
SHA256SUMS
```

The base version in `pom.xml` must match the tag. Set it to the next snapshot
before you create another release.

## Use GitHub Packages

Each version tag publishes `org.portolan:portolan-java` with the release
version. Set the repository variable `PUBLISH_GITHUB_PACKAGES` to `false` only
when a release must skip the package repository.

GitHub Packages requires a personal access token with `read:packages`, even for
a public package. Put the token in `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USER</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Then add the repository and dependency shown in [Examples](examples.md).

## Publish to Maven Central

Maven Central needs human-owned publishing credentials and a verified
namespace. Complete these steps before enabling Central publication:

1. Sign in to the Central Portal with the publishing account.
2. Register a namespace that the project controls.
3. Decide whether to keep `org.portolan` or change the Maven `groupId`.
4. Generate a Central Portal user token.
5. Generate a GPG key for release signatures.
6. Add the required credentials to GitHub Actions secrets.

The namespace decision matters because Maven coordinates are a public API.
Changing the `groupId` after publication creates a different artifact.

Central also requires signed binary, source, Javadoc, and POM artifacts. The
release profile already builds the unsigned artifact set. Central publishing
should be enabled only after the namespace and signing identity are settled.
