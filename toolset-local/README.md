# Local WiX Maven Artifacts

This reactor creates local Maven artifacts for WiX toolsets used by `wix-maven-plugin`:

- `org.wixtoolset.maven:wix-toolset:3.14.1`
- `org.wixtoolset.maven:wix-bootstrap:3.14.1`
- `org.wixtoolset.maven:wix-toolset4:4.0.6`
- `org.wixtoolset.maven:wix-toolset5:5.0.2`
- `org.wixtoolset.maven:wix-toolset6:6.0.2`

## Build and install locally

Run from the repository root:

```bash
mvn -f toolset-local/pom.xml clean install
```

This downloads the upstream release archives and installs the repackaged JAR artifacts into your local Maven repository.

## Notes

- WiX v4+ modules use `dotnet tool install --tool-path target/classes wix --version X.Y.Z`.
  This packages `wix.exe` (launcher shim) and the `.store/` DLL tree directly into the JAR.
  Requires .NET 6 SDK or later on the build machine.
- WiX v3 modules download `wix314-binaries.zip` from GitHub and unzip the executables
  into `bin/` so the plugin's `zipUnArchiver.extract("bin", toolDirectory)` finds them.
- The `--ignore-failed-sources` flag suppresses failures from unreachable NuGet feeds;
  `--add-source https://api.nuget.org/v3/index.json` ensures NuGet.org is always consulted.
