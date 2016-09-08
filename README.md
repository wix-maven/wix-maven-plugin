wix-maven-plugin
================

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.wix-maven/wix-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.wix-maven/wix-maven-plugin)

A maven plugin to provide lifecycle of a Windows Installer build using WiX.
A work in progress, Sorry no doc yet.
Attempts some dependencies references for wix build types and also NAR.

Provides lifecycles for 
 * msi
 * msp
 * wixlib
 * bundle

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

Major TODO:
 * doc & site
 * expanded integration tests
 * patch goal completion
 * appropriate meta added in prepare-package, reading the meta from dependencies to reduce 'searching' behaviour for unknown cultures
 * lifecycle for msm 
 * goal for repacking transforms/cabs into msi with correct language id

