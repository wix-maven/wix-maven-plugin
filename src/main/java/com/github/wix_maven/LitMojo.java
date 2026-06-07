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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.compiler.util.scan.*;
import org.codehaus.plexus.compiler.util.scan.mapping.*;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Goal which executes WiX lit to create a .wixlib file.
 */
@Mojo(name = "lit", requiresProject = true, defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class LitMojo extends AbstractLinker {

  /**
   * Bind files into wixlib library file. (-bf option)
   * 
   * @parameter expression="${wix.bindFiles.lib}" default-value="true"
   */
  @Parameter(property = "wix.bindFiles.lib", defaultValue = "true")
  private boolean bindFiles;

  @Override
  @SuppressWarnings("unchecked")
  protected void multilink(File toolDirectory) throws MojoExecutionException {

    File linkTool = getCommandBuilder().resolveToolExecutable(toolDirectory, "lit");
    if (!linkTool.exists())
      throw new MojoExecutionException("lit tool doesn't exist " + linkTool.getAbsolutePath());

    if (getCommandBuilder().isUnifiedBuild()) {
      multilinkV4(linkTool);
      return;
    }

    for (String arch : getPlatforms()) {
      try {
        File archOutputFile = getOutput(arch, null, getPackageOutputExtension());

        getLog().info(" -- Linking : " + archOutputFile.getPath());

        SourceInclusionScanner scanner =
            new SimpleSourceInclusionScanner(getIncludes(), getExcludes());
        scanner
            .addSourceMapping(new SingleTargetSourceMapping(".wixobj", archOutputFile.getName()));
        Set<File> objects =
            scanner.getIncludedSources(getArchIntDirectory(arch, null), archOutputFile);

        List<File> locales = null;
        if (wxlInputDirectory.exists()) {
          scanner = new SimpleSourceInclusionScanner(getLocaleIncludes(), getLocaleExcludes());
          scanner.addSourceMapping(new SingleTargetSourceMapping(".wxl", archOutputFile.getName()));
          // The order of -loc is currently (wix 3.7) important due to an issue with UI element
          locales =
              asSortedList(scanner.getIncludedSources(wxlInputDirectory,
                  archOutputFile.getParentFile()));
          // not locale specific because lit doesn't do that
          fileSourceRoots.add(wxlInputDirectory.getAbsolutePath());
        }

        if (!objects.isEmpty()) { // || !locales.isEmpty() must have at least one wxs?
          Set<String> files = new HashSet<String>();
          for (Iterator<File> i = objects.iterator(); i.hasNext();) {
            files.add(getRelative(i.next()));
          }

          Commandline cl = new Commandline();

          cl.setExecutable(linkTool.getAbsolutePath());
          cl.setWorkingDirectory(relativeBase);
          addToolsetGeneralOptions(cl);

          if (bindFiles)
            cl.addArguments(new String[] {"-bf"});

          cl.addArguments(new String[] {"-out", archOutputFile.getAbsolutePath()});

          addOptions(cl, fileSourceRoots);
          addWixExtensions(cl);
          if (locales != null) {
            for (Iterator<File> i = locales.iterator(); i.hasNext();) {
              cl.addArguments(new String[] {"-loc", getRelative(i.next())});
            }
          }
          addOtherOptions(cl);

          cl.addArguments(files.toArray(new String[0]));

          if (!archOutputFile.getParentFile().exists())
            archOutputFile.getParentFile().mkdirs();

          link(cl);

          // project.getBuild().setFinalName(archOutputFile.getAbsolutePath() );
        }

      } catch (InclusionScanException e) {
        throw new MojoExecutionException("XSD: scanning for updated files failed", e);
      }

    }
  }

  private void multilinkV4(File wixExe) throws MojoExecutionException {
    Set<String> wxsSources = new HashSet<String>();
    try {
      Set<String> wxsIncludes = new HashSet<String>();
      wxsIncludes.add("**/*.wxs");
      SourceInclusionScanner scanner =
          new SimpleSourceInclusionScanner(wxsIncludes, new HashSet<String>());
      File dummyTarget = new File(intDirectory, "dummy.wixlib");
      scanner.addSourceMapping(new SingleTargetSourceMapping(".wxs", dummyTarget.getName()));
      if (wxsInputDirectory.exists()) {
        for (Object o : scanner.getIncludedSources(wxsInputDirectory, intDirectory)) {
          File f = (File) o;
          wxsSources.add(getRelative(f));
        }
      }
      if (wxsGeneratedDirectory.exists()) {
        for (Object o : scanner.getIncludedSources(wxsGeneratedDirectory, intDirectory)) {
          File f = (File) o;
          wxsSources.add(getRelative(f));
        }
      }
    } catch (InclusionScanException e) {
      throw new MojoExecutionException("Scanning for .wxs source files failed", e);
    }

    if (wxsSources.isEmpty()) {
      getLog().info("No .wxs sources found, skipping wix build");
      return;
    }

    for (String arch : getPlatforms()) {
      File archOutputFile = getOutput(arch, null, getPackageOutputExtension());
      getLog().info(" -- Building wixlib (v4): " + archOutputFile.getPath());

      Commandline cl = new Commandline();
      cl.setExecutable(wixExe.getAbsolutePath());
      cl.setWorkingDirectory(relativeBase);
      cl.addArguments(new String[] {"build"});
      addToolsetGeneralOptions(cl);
      cl.addArguments(new String[] {"-arch", arch, "-outputType", "Library", "-o",
          archOutputFile.getAbsolutePath()});

      addWixExtensions(cl);
      addOtherOptions(cl);

      // WiX v4 does not search the WXS file directory by default; add bindpaths
      if (wxsInputDirectory != null && wxsInputDirectory.exists()) {
        cl.addArguments(new String[] {"-b", wxsInputDirectory.getAbsolutePath()});
      }
      if (wxsGeneratedDirectory != null && wxsGeneratedDirectory.exists()) {
        cl.addArguments(new String[] {"-b", wxsGeneratedDirectory.getAbsolutePath()});
      }

      cl.addArguments(wxsSources.toArray(new String[0]));

      if (!archOutputFile.getParentFile().exists())
        archOutputFile.getParentFile().mkdirs();

      link(cl);
    }
  }

}
