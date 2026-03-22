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
 * Strategy interface that encapsulates WiX toolset version-specific CLI construction.
 * <p>
 * Each Mojo delegates to the appropriate implementation via
 * {@link AbstractWixMojo#getCommandBuilder()}.
 * 
 * @see WixV3CommandBuilder
 * @see WixV4CommandBuilder
 */
public interface WixToolsetCommandBuilder {

  /**
   * The subdirectory inside the unpacked tools archive that contains the WiX executables.
   * <ul>
   * <li>v3 → {@code "bin"}</li>
   * <li>v4 → {@code ""} (root of the archive)</li>
   * </ul>
   * 
   * @return tool subdirectory relative to the unpacked tool root.
   */
  String getToolSubdirectory();

  /**
   * Resolve the absolute path to a named WiX tool executable.
   * 
   * @param toolDirectory the root directory where WiX tools were unpacked
   * @param toolName logical name (e.g. {@code "candle"}, {@code "light"}, {@code "heat"})
   * @return the {@link File} pointing at the executable; existence is not guaranteed
   */
  File resolveToolExecutable(File toolDirectory, String toolName);

  /**
   * Append general toolset options to the command line.
   * <ul>
   * <li>v3: {@code -nologo}, {@code -s<N>}, {@code -w<N>}</li>
   * <li>v4: equivalent flags on the unified CLI where supported</li>
   * </ul>
   * 
   * @param cl the command line to augment
   * @param verbose when {@code true} the logo/banner is shown; suppress {@code -nologo} if so
   * @param suppress set of suppression tokens (may be {@code null})
   * @param warn set of warning-as-error tokens (may be {@code null})
   */
  void addGeneralOptions(Commandline cl, boolean verbose, Set<String> suppress, Set<String> warn);

  /**
   * Append WiX extension references to the command line.
   * <ul>
   * <li>v3: {@code -ext <path-to-dll>} — resolved from Maven {@code wixext} artifacts</li>
   * <li>v4: {@code -ext <NuGet-package-id>} — resolved from {@code wixExtensions} strings</li>
   * </ul>
   * 
   * @param cl the command line to augment
   * @param extArtifacts Maven {@code wixext} artifacts (used by v3; may be empty in v4 mode)
   * @param wixExtensions v4-style NuGet extension names (used by v4; ignored in v3 mode)
   * @throws MojoExecutionException if artifact resolution fails
   */
  void addExtensions(Commandline cl, Set<Artifact> extArtifacts, Set<String> wixExtensions)
      throws MojoExecutionException;

  /**
   * Whether this toolset version uses a unified compile+link command (v4) rather than separate
   * {@code candle} and {@code light} invocations (v3).
   * 
   * @return true when unified build mode is used.
   */
  boolean isUnifiedBuild();
}
