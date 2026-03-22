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
 * {@link WixToolsetCommandBuilder} implementation for WiX Toolset v3.
 * <p>
 * WiX v3 uses separate executables located in the {@code bin/} subdirectory of the tools archive:
 * {@code candle.exe}, {@code light.exe}, {@code lit.exe}, {@code heat.exe}, etc. Extensions are
 * physical DLL files resolved from Maven {@code wixext} artifacts.
 */
public class WixV3CommandBuilder implements WixToolsetCommandBuilder {

  @Override
  public String getToolSubdirectory() {
    return "bin";
  }

  @Override
  public File resolveToolExecutable(File toolDirectory, String toolName) {
    return new File(toolDirectory, "bin/" + toolName + ".exe");
  }

  @Override
  public void addGeneralOptions(Commandline cl, boolean verbose, Set<String> suppress,
      Set<String> warn) {
    if (!verbose) {
      cl.addArguments(new String[] {"-nologo"});
    }
    if (suppress != null) {
      for (String sup : suppress) {
        cl.addArguments(new String[] {"-s" + sup});
      }
    }
    if (warn != null) {
      for (String w : warn) {
        cl.addArguments(new String[] {"-w" + w});
      }
    }
  }

  @Override
  public void addExtensions(Commandline cl, Set<Artifact> extArtifacts, Set<String> wixExtensions)
      throws MojoExecutionException {
    if (extArtifacts != null) {
      for (Artifact ext : extArtifacts) {
        cl.addArguments(new String[] {"-ext", ext.getFile().getAbsolutePath()});
      }
    }
    // wixExtensions (v4 NuGet-style names) are ignored in v3 mode
  }

  @Override
  public boolean isUnifiedBuild() {
    return false;
  }
}
