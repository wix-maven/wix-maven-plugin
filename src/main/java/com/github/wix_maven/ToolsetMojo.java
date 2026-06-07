package com.github.wix_maven;

/*
 * #%L WiX Toolset (Windows Installer XML) Maven Plugin %% Copyright (C) 2013 - 2014 GregDomjan
 * NetIQ %% Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License. #L%
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * TODO: support for tools -
 * 
 * WixCop static analysis WixCop will identify and optionally fix source code issues
 * 
 * Lux/Nit to perform unit testing of Custom actions
 * 
 * melt/dark - wxs generation from inputs - msi/msm
 */
/**
 * Goal to initialize the workspace with wix toolset.
 */
@Mojo(name = "toolset", defaultPhase = LifecyclePhase.INITIALIZE)
public class ToolsetMojo extends AbstractWixMojo {

  @Parameter(property = "wix.nugetSource", defaultValue = "https://api.nuget.org/v3/index.json")
  private String nugetSource;

  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info(getClass().getName() + " skipped");
      return;
    }

    unpackFileBasedResources();
    installWixV4Extensions();
    getLog().debug("WiX Toolset ready for work");
  }

  private void installWixV4Extensions() throws MojoExecutionException {
    if (getWixVersion() != WixToolsetVersion.V4_PLUS) {
      return;
    }

    if (wixExtensions == null || wixExtensions.isEmpty()) {
      getLog().debug("No configured WiX v4+ extensions to install.");
      return;
    }

    File wixExe = getCommandBuilder().resolveToolExecutable(toolDirectory, "light");
    if (!wixExe.isFile()) {
      throw new MojoExecutionException(
          "Unable to locate wix executable for extension installation: " + wixExe.getAbsolutePath());
    }

    File nugetWorkDirectory = prepareNugetWorkDirectory();
    String toolsetVersion = resolveToolsetVersion();

    for (String extension : wixExtensions) {
      String extensionRef = withToolsetVersion(extension, toolsetVersion);
      installWixV4Extension(wixExe, extensionRef, nugetWorkDirectory);
    }
  }

  private void installWixV4Extension(File wixExe, String extensionRef, File nugetWorkDirectory)
      throws MojoExecutionException {
    String extensionId = extensionRef;
    int slash = extensionRef.indexOf('/');
    if (slash > 0) {
      extensionId = extensionRef.substring(0, slash);
    }

    // Clear stale/damaged global cache entry before installing the pinned version.
    int removeCode =
        runWixExtensionCommand(wixExe, nugetWorkDirectory, new String[] {"extension", "remove",
            "-g", extensionId}, "Removing WiX extension: ", false);
    if (removeCode != 0) {
      getLog().debug(
          "WiX extension remove returned " + removeCode + " for " + extensionId
              + "; continuing with install");
    }

    int addCode =
        runWixExtensionCommand(wixExe, nugetWorkDirectory, new String[] {"extension", "add", "-g",
            extensionRef}, "Installing WiX extension: ", true);
    if (addCode != 0) {
      throw new MojoExecutionException("Failed to install WiX extension '" + extensionRef
          + "', return code " + addCode);
    }
  }

  private int runWixExtensionCommand(File wixExe, File nugetWorkDirectory, String[] args,
      String actionLogPrefix, boolean logInfo) throws MojoExecutionException {
    Commandline cl = new Commandline();
    cl.setExecutable(wixExe.getAbsolutePath());
    cl.setWorkingDirectory(nugetWorkDirectory);
    cl.addEnvironment("NUGET_PACKAGES", new File(nugetWorkDirectory, "packages").getAbsolutePath());
    cl.addArguments(args);

    String target = args[args.length - 1];
    if (logInfo && verbose) {
      getLog().info(actionLogPrefix + target);
      getLog().info(cl.toString());
    } else {
      getLog().debug(cl.toString());
    }

    try {
      return CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {
        public void consumeLine(final String line) {
          if (line.contains("error")) {
            getLog().error(line);
          } else if (line.contains("warning")) {
            getLog().warn(line);
          } else if (verbose) {
            getLog().info(line);
          } else {
            getLog().debug(line);
          }
        }
      }, new StreamConsumer() {
        public void consumeLine(final String line) {
          getLog().error(line);
        }
      });
    } catch (CommandLineException e) {
      throw new MojoExecutionException("Failed to execute WiX extension command for '" + target
          + "'", e);
    }
  }

  private String resolveToolsetVersion() throws MojoExecutionException {
    Artifact[] tools = findToolsArtifacts();
    for (Artifact artifact : tools) {
      if (toolsPluginArtifactId.equals(artifact.getArtifactId())) {
        return artifact.getVersion();
      }
    }
    return tools.length > 0 ? tools[0].getVersion() : null;
  }

  private String withToolsetVersion(String extensionRef, String toolsetVersion) {
    if (extensionRef == null || extensionRef.contains("/") || toolsetVersion == null
        || toolsetVersion.trim().isEmpty()) {
      return extensionRef;
    }
    return extensionRef + "/" + toolsetVersion;
  }

  private File prepareNugetWorkDirectory() throws MojoExecutionException {
    File base = new File(project.getBuild().getDirectory(), "wix-extension-cache");
    if (!base.exists() && !base.mkdirs()) {
      throw new MojoExecutionException("Unable to create wix extension cache directory: "
          + base.getAbsolutePath());
    }

    File configFile = new File(base, "NuGet.Config");
    String source =
        (nugetSource == null || nugetSource.trim().isEmpty()) ? "https://api.nuget.org/v3/index.json"
            : nugetSource.trim();
    String config =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<configuration>\n"
            + "  <packageSources>\n" + "    <clear />\n" + "    <add key=\"wix-default\" value=\""
            + source + "\" />\n" + "  </packageSources>\n" + "</configuration>\n";

    FileWriter writer = null;
    try {
      writer = new FileWriter(configFile);
      writer.write(config);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to write isolated NuGet config: "
          + configFile.getAbsolutePath(), e);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          getLog().warn("Failed closing NuGet config file: " + configFile.getAbsolutePath(), e);
        }
      }
    }

    return base;
  }

}
