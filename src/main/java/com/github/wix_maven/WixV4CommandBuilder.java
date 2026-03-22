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
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * {@link WixToolsetCommandBuilder} implementation for WiX Toolset v4 and later.
 * <p>
 * WiX v4+ uses a single unified {@code wix.exe} CLI located at the root of the tools archive.
 * Operations that were separate executables in v3 (candle, light, lit, heat, …) are now subcommands
 * of {@code wix.exe}. Extensions are NuGet-package-id strings passed as {@code -ext <name>}.
 * <p>
 * Compile and link steps are unified into a single {@code wix build} invocation; the
 * {@link CandleMojo} becomes a no-op preparation step in v4 mode and {@link LightMojo}/
 * {@link LitMojo} issue the combined {@code wix build} command.
 */
public class WixV4CommandBuilder implements WixToolsetCommandBuilder {

  /** v4 maps all tool names to {@code wix.exe}. */
  private static final String WIX_EXE = "wix.exe";

  @Override
  public String getToolSubdirectory() {
    return "";
  }

  @Override
  public File resolveToolExecutable(File toolDirectory, String toolName) {
    // In WiX v4, heat is provided by the separate WixToolset.Heat NuGet package as heat.exe.
    // It is NOT a subcommand of wix.exe (unlike candle/light/lit which became 'wix build').
    if ("heat".equalsIgnoreCase(toolName)) {
      final String HEAT_EXE = "heat.exe";
      File direct = new File(toolDirectory, HEAT_EXE);
      if (direct.isFile()) {
        return direct;
      }
      File nested = findExecutableRecursively(toolDirectory, HEAT_EXE);
      return nested != null ? nested : direct;
    }

    // All other v4 operations are subcommands of wix.exe.
    File direct = new File(toolDirectory, WIX_EXE);
    if (direct.isFile()) {
      return direct;
    }

    File nested = findExecutableRecursively(toolDirectory, WIX_EXE);
    return nested != null ? nested : direct;
  }

  private File findExecutableRecursively(File directory, String executableName) {
    if (directory == null || !directory.exists()) {
      return null;
    }

    File[] children = directory.listFiles();
    if (children == null) {
      return null;
    }

    for (File child : children) {
      if (child.isDirectory()) {
        File nested = findExecutableRecursively(child, executableName);
        if (nested != null) {
          return nested;
        }
      } else if (executableName.equalsIgnoreCase(child.getName())) {
        return child;
      }
    }

    return null;
  }

  @Override
  public void addGeneralOptions(Commandline cl, boolean verbose, Set<String> suppress,
      Set<String> warn) {
    // v4 unified CLI does not use -nologo; verbose flag controls output differently
    if (suppress != null) {
      for (String sup : suppress) {
        // v3 values use "w<N>" format (e.g. "w1076"); v4 -sw takes a number directly
        String supVal = sup.matches("(?i)w\\d+") ? sup.substring(1) : sup;
        cl.addArguments(new String[] {"-sw" + supVal});
      }
    }
    if (warn != null) {
      for (String w : warn) {
        cl.addArguments(new String[] {"-wx" + w});
      }
    }
  }

  @Override
  public void addExtensions(Commandline cl, Set<Artifact> extArtifacts, Set<String> wixExtensions)
      throws MojoExecutionException {
    if (wixExtensions != null) {
      for (String ext : wixExtensions) {
        cl.addArguments(new String[] {"-ext", ext});
      }
    }
    // extArtifacts (v3 DLL paths) are ignored in v4 mode — log a warning if non-empty
  }

  @Override
  public boolean isUnifiedBuild() {
    return true;
  }
}
