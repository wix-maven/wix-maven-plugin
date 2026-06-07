wix-maven-plugin
================

[![Maven Central](https://img.shields.io/maven-central/v/com.github.wix-maven/wix-maven-plugin)](https://search.maven.org/artifact/com.github.wix-maven/wix-maven-plugin)
[![Master Build status](https://ci.appveyor.com/api/projects/status/0f5noojdp0dhh8xu/branch/master?svg=true)](https://ci.appveyor.com/project/GregDomjan/wix-maven-plugin/branch/master)
[![AppVeyor Build status](https://ci.appveyor.com/api/projects/status/0f5noojdp0dhh8xu?svg=true)](https://ci.appveyor.com/project/GregDomjan/wix-maven-plugin)

A maven plugin to provide lifecycle of a Windows Installer build using WiX.
A work in progress, Sorry no doc yet.
Attempts some dependencies references for wix build types and also NAR.

Provides lifecycles for 
 * msi
 * msp
 * wixlib
 * bundle
 * msix (WiX v4+)

Goals
 * wix:toolset
 * wix:unpack-dependencies
 * wix:candle
 * wix:lit
 * wix:light
 * wix:transform
 * wix:patch
 * wix:detach-bundle
 * wix:attach-bundle
 * wix:inscribe
 * wix:prepare-package
 * wix:package
 * resources:resources
 * jar:jar
 * install:install
 * deploy:deploy

WiX v4+ configuration

Set the tool artifact to a v4+ package and use string-based extension names.

```xml
<plugin>
	<groupId>com.github.wix-maven</groupId>
	<artifactId>wix-maven-plugin</artifactId>
	<extensions>true</extensions>
	<configuration>
		<toolsPluginArtifactId>wix-toolset4</toolsPluginArtifactId>
		<wixExtensions>
			<ext>WixToolset.UI.wixext</ext>
			<ext>WixToolset.Util.wixext</ext>
		</wixExtensions>
		<platforms>
			<arch>x64</arch>
			<arch>arm64</arch>
		</platforms>
	</configuration>
</plugin>
```

Notes
 * `msix` packaging requires WiX v4+.
 * `arm64` platform requires WiX v4+.
 * WiX v3 Maven `wixext` dependencies remain supported for v3 builds.
 * `mvn verify -Prun-its-v4` requires a resolvable `org.wixtoolset.maven:wix-toolset4` artifact in your configured Maven repositories.
 * Local toolset artifacts for WiX 3.14.1, 4.0.6, 5.0.2, and 6.0.2 can be installed via `mvn -f toolset-local/pom.xml clean install`.

Major TODO:
 * doc & site
 * expanded integration tests
 * patch goal completion
 * appropriate meta added in prepare-package, reading the meta from dependencies to reduce 'searching' behaviour for unknown cultures
 * lifecycle for msm 
 * goal for repacking transforms/cabs into msi with correct language id

