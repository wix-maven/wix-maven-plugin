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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.compiler.util.scan.*;
import org.codehaus.plexus.compiler.util.scan.mapping.*;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Goal which executes WiX light to create a .msi file.
 * 
 * The following project dependency inclusion patterns apply<br>
 * Dependent Wixlib project 'Foo' with possible output redefined as 'bar' adds to commandline <br>
 * ${narunpack}\Foo-version\Bar.wixlib
 */
@Mojo(name = "light", requiresProject = true, defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class LightMojo extends AbstractLinker {

  /**
   * Re use cabinet files across multiple linkages. (-reusecab)
   */
  @Parameter(property = "wix.reuseCab", defaultValue = "false")
  private boolean reuseCabs;

  /**
   * Bind files, only useful wixout format
   */
  @Parameter(property = "wix.bindFiles.msi", defaultValue = "false")
  private boolean bindFiles;

  private void addLocaleOptions(Commandline cl, String culture) {
    // TODO: culture might be a list of primary and fallback cultures
    if (culture != null)
      cl.addArguments(new String[] {"-cultures:" + culture});
  }

  private void addReuseCabOptions(Commandline cl, String arch) {
    // TODO: culture might be a list of primary and fallback cultures
    if (reuseCabs) {
      File resolvedCabCacheDirectory = new File(cabCacheDirectory, arch); // TODO: provide pattern
                                                                          // replace
      cl.addArguments(new String[] {"-reusecab", "-cc",
          resolvedCabCacheDirectory.getAbsolutePath() + "\\"});
      if (!resolvedCabCacheDirectory.exists())
        resolvedCabCacheDirectory.mkdirs();
    }
  }

  protected void addValidationOptions(Commandline cl) throws MojoExecutionException {
    if (VALIDATE_SUPPRESS.equalsIgnoreCase(validate) || VALIDATE_UNIT.equalsIgnoreCase(validate)) {
      cl.addArguments(new String[] {"-sval"});
    }
  }

  private String outputExtension() {
    if (PACK_PATCH.equalsIgnoreCase(getPackaging())) { // final msp output is from pyro
      return "wixmsp";
    }
    // msi/msm extension - actual build differences are in xml
    return getPackageOutputExtension();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void multilink(File toolDirectory) throws MojoExecutionException {

    File linkTool = getCommandBuilder().resolveToolExecutable(toolDirectory, "light");
    if (!linkTool.exists())
      throw new MojoExecutionException("Light tool doesn't exist " + linkTool.getAbsolutePath());

    if (getCommandBuilder().isUnifiedBuild()) {
      multilinkV4(linkTool);
      return;
    }

    defaultLocale();

    Set<Artifact> wixDependencies = getWixDependencySets();

    for (Iterator<Artifact> i = wixDependencies.iterator(); i.hasNext();) {
      Artifact libGroup = i.next();
      if (!libGroup.hasClassifier()) {
        getLog().debug("Attempting to unpack resources for " + libGroup.toString());
        unpackResource(libGroup);
      }
    }

    for (String arch : getPlatforms()) {
      for (String culture : culturespecs()) {

        final File intermediateFolder = getArchIntDirectory(arch, culture);
        if (!intermediateFolder.exists())
          throw new MojoExecutionException("No source for linking? Intermediate dir doesn't exist "
              + intermediateFolder.getAbsolutePath());

        File archOutputFile = getOutput(arch, culture, outputExtension());

        getLog().info(" -- Linking : " + archOutputFile.getPath());
        try {
          // we are using source scanning to find all the files for the build - because all should
          // be listed we don't check for just newer
          // ones.
          // TODO: add check to see if the msi is out of date compared to input files of all kinds.
          SourceInclusionScanner scanner =
              new SimpleSourceInclusionScanner(getIncludes(), getExcludes());
          scanner.addSourceMapping(new SingleTargetSourceMapping(".wixobj", archOutputFile
              .getName()));
          scanner.addSourceMapping(new SingleTargetSourceMapping(".wixlib", archOutputFile
              .getName()));
          Set<File> objects = scanner.getIncludedSources(intermediateFolder, archOutputFile);
          // **/{arch}/*.wixlib
          // **/{arch}/*.wixobj


          Set<String> allSourceRoots = new LinkedHashSet<String>(fileSourceRoots); // we need this
                                                                                   // to keep order
                                                                                   // for more
                                                                                   // specific
                                                                                   // locale
          List<File> locales = null; // coming first
          if (wxlInputDirectory.exists()) {
            // culture might be a list of primary and fallback cultures
            // include all the wxl files and the -culture option will sort them out.
            // include the files from only the primary culture and the nuetral.
            scanner = new SimpleSourceInclusionScanner(getLocaleIncludes(), getLocaleExcludes());
            scanner
                .addSourceMapping(new SingleTargetSourceMapping(".wxl", archOutputFile.getName()));
            // The order of -loc is currently (wix 3.7) important due to an issue with UI element
            locales = asSortedList(scanner.getIncludedSources(wxlInputDirectory, archOutputFile));

            addBinderOption(wxlInputDirectory, culture, allSourceRoots);
          }
          if (unpackDirectory.exists()) {
            allSourceRoots.add(unpackDirectory.getAbsolutePath());
          }

          Set<String> objectFiles = new HashSet<String>();
          if (!objects.isEmpty()) {
            for (Iterator<File> i = objects.iterator(); i.hasNext();) {
              objectFiles.add(getRelative(i.next()));
            }
          }
          for (Iterator<Artifact> i = wixDependencies.iterator(); i.hasNext();) {
            Artifact libGroup = i.next();
            getLog().debug(libGroup.toString());
            if (PACK_LIB.equalsIgnoreCase(libGroup.getType())) {
              // try unpack resources
              addResource(libGroup, culture, allSourceRoots);

              Set<Artifact> depArtifacts = getRelatedArtifacts(libGroup, arch, culture);
              for (Iterator<Artifact> j = depArtifacts.iterator(); j.hasNext();) {
                Artifact lib = j.next();
                objectFiles.add(getRelative(lib.getFile()));
              }
            }
          }

          if (!objectFiles.isEmpty()) {
            Commandline cl = new Commandline();

            cl.setExecutable(linkTool.getAbsolutePath());
            cl.setWorkingDirectory(relativeBase);// wxsInputDirectory
            addToolsetGeneralOptions(cl);

            if (bindFiles)
              cl.addArguments(new String[] {"-bf"});

            cl.addArguments(new String[] {"-out", archOutputFile.getAbsolutePath()});

            addOptions(cl, allSourceRoots);
            addValidationOptions(cl);
            addLocaleOptions(cl, culture);

            if (locales != null) {
              for (Iterator<File> i = locales.iterator(); i.hasNext();) {
                cl.addArguments(new String[] {"-loc", getRelative(i.next())});
              }
            }

            addWixExtensions(cl);
            addOtherOptions(cl);
            addReuseCabOptions(cl, arch);

            cl.addArguments(objectFiles.toArray(new String[0]));

            if (!archOutputFile.getParentFile().exists())
              archOutputFile.getParentFile().mkdirs();
            link(cl);
            // projectHelper.attachArtifact(project, packaging, classifier, archOutputFile);
          }

        } catch (InclusionScanException e) {
          throw new MojoExecutionException("Scanning for updated files failed", e);
        }

      }
    }
  }

  /**
   * Add neutral and optional culture specific binder folders
   * 
   * @param baseFolder
   * @param culture
   * @param allSourceRoots
   */
  private void addBinderOption(File baseFolder, String culture, Set<String> allSourceRoots) {
    if (culture != null) {
      // TODO: might be that all cultures should be added
      // TODO: might be that LanguageID should be added also
      File cultureWxlInputDirectory = new File(baseFolder, getPrimaryCulture(culture));
      if (cultureWxlInputDirectory.exists())
        allSourceRoots.add(cultureWxlInputDirectory.getAbsolutePath());
    }
    // order is important, prefer files in culture specific over same in neutral
    allSourceRoots.add(baseFolder.getAbsolutePath());
  }

  /**
   * Add resources attached from dependencies
   * 
   * @param libGroup
   * @param culture
   * @param allSourceRoots
   */
  private void addResource(Artifact libGroup, String culture, Set<String> allSourceRoots) {
    File resUnpackDirectory = wixUnpackDirectory(libGroup);

    File neutralFolder = new File(resUnpackDirectory, "wix-locale");
    if (neutralFolder.exists()) {
      addBinderOption(neutralFolder, culture, allSourceRoots);
    } else {
      getLog().debug(
          String.format("Warning: %1$s:%2$s resources not unpacked", libGroup.getGroupId(),
              libGroup.getArtifactId()));
    }
  }

  private void unpackResource(Artifact libGroup) {
    // TODO: support compile if( libGroup.getFile().isFile() )
    zipUnArchiver.setSourceFile(libGroup.getFile());
    File resUnpackDirectory = wixUnpackDirectory(libGroup);
    // zipUnArchiver.extract(subfolder, resUnpackDirectory);

    if (!resUnpackDirectory.exists())
      resUnpackDirectory.mkdirs();

    zipUnArchiver.setDestDirectory(resUnpackDirectory);
    IncludeExcludeFileSelector[] selectors =
        new IncludeExcludeFileSelector[] {new IncludeExcludeFileSelector()};
    selectors[0].setIncludes("wix-locale/**,cabs/**".split(","));
    zipUnArchiver.setFileSelectors(selectors);
    zipUnArchiver.extract();
  }

  /**
   * WiX v4+ unified build path. Invokes {@code wix.exe build <sources> -o <output>}. Sources are
   * .wxs files scanned from {@code wxsInputDirectory} and {@code wxsGeneratedDirectory}; there are
   * no intermediate .wixobj files.
   */
  @SuppressWarnings("unchecked")
  private void multilinkV4(File wixExe) throws MojoExecutionException {
    defaultLocale();

    Set<String> wxsSources = new HashSet<String>();
    try {
      Set<String> wxsIncludes = new HashSet<String>();
      wxsIncludes.add("**/*.wxs");
      SourceInclusionScanner scanner =
          new SimpleSourceInclusionScanner(wxsIncludes, new HashSet<String>());
      File dummyTarget = new File(intDirectory, "dummy.msi");
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
      for (String culture : culturespecs()) {
        File archOutputFile = getOutput(arch, culture, outputExtension());
        if (!archOutputFile.getParentFile().exists())
          archOutputFile.getParentFile().mkdirs();

        getLog().info(" -- Building (v4): " + archOutputFile.getPath());

        Commandline cl = new Commandline();
        cl.setExecutable(wixExe.getAbsolutePath());
        cl.setWorkingDirectory(relativeBase);
        cl.addArguments(new String[] {"build"});

        addToolsetGeneralOptions(cl);
        cl.addArguments(new String[] {"-arch", arch});
        if (culture != null) {
          cl.addArguments(new String[] {"-culture", culture});
        }
        cl.addArguments(new String[] {"-outputType", outputTypeForPackaging()});
        cl.addArguments(new String[] {"-o", archOutputFile.getAbsolutePath()});

        addWixExtensions(cl);
        addOtherOptions(cl);

        addUnifiedResponseOptions(cl);

        addUnifiedBindPaths(cl);
        if (wxsGeneratedDirectory != null && wxsGeneratedDirectory.exists()) {
          cl.addArguments(new String[] {"-b", wxsGeneratedDirectory.getAbsolutePath()});
        }

        try {
          List<File> locales = null; // coming first
          if (wxlInputDirectory.exists()) {
            getLog().info(">>> wxlInputDirectory found!");
            // culture might be a list of primary and fallback cultures
            // include all the wxl files and the -culture option will sort them out.
            // include the files from only the primary culture and the nuetral.
            SourceInclusionScanner scanner =
                new SimpleSourceInclusionScanner(getLocaleIncludes(), getLocaleExcludes());
            scanner
                .addSourceMapping(new SingleTargetSourceMapping(".wxl", archOutputFile.getName()));
            // The order of -loc is currently (wix 3.7) important due to an issue with UI element
            locales = asSortedList(scanner.getIncludedSources(wxlInputDirectory, archOutputFile));

            // addBinderOption(wxlInputDirectory, culture, allSourceRoots);
          }

          if (locales != null) {
            for (Iterator<File> i = locales.iterator(); i.hasNext();) {
              cl.addArguments(new String[] {"-loc", getRelative(i.next())});
            }
          }
        } catch (InclusionScanException e) {
          throw new MojoExecutionException("Scanning for updated files failed", e);
        }

        cl.addArguments(wxsSources.toArray(new String[0]));
        link(cl);
      }
    }
  }

  private void addUnifiedBindPaths(Commandline cl) {
    Set<String> allSourceRoots = new LinkedHashSet<String>(fileSourceRoots);

    List<File> roots = new ArrayList<File>();
    roots.add(wxsInputDirectory);
    // wxlInputDirectory = src/main/wix-locale: payload files (exes, dlls) live here
    roots.add(wxlInputDirectory);
    roots.add(resourceDirectory);
    roots.add(narUnpackDirectory);
    roots.add(unpackDirectory);
    roots.add(localRepository == null ? null : new File(localRepository.getBasedir()));

    for (File root : roots) {
      if (root != null && root.exists()) {
        allSourceRoots.add(root.getAbsolutePath());
      }
    }

    for (String root : allSourceRoots) {
      cl.addArguments(new String[] {"-b", root});
    }
  }

  private void addUnifiedResponseOptions(Commandline cl) throws MojoExecutionException {
    File candleResponseFile = new File(intDirectory, CandleMojo.RESPONSE_FILE_NAME);
    if (!candleResponseFile.exists()) {
      return;
    }

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(candleResponseFile));
      String line;
      while ((line = reader.readLine()) != null) {
        String arg = line.trim();
        if (arg.isEmpty()) {
          continue;
        }
        if (arg.startsWith("\"") && arg.endsWith("\"") && arg.length() >= 2) {
          arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.startsWith("-d") && arg.length() > 2) {
          cl.addArguments(new String[] {"-d", arg.substring(2)});
          continue;
        }
        if (arg.startsWith("-I") && arg.length() > 2) {
          cl.addArguments(new String[] {"-i", arg.substring(2)});
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to read response file for unified build "
          + candleResponseFile.getAbsolutePath(), e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          getLog().warn("Failed to close response file " + candleResponseFile.getAbsolutePath(), e);
        }
      }
    }
  }

  private String outputTypeForPackaging() {
    if (PACK_BUNDLE.equalsIgnoreCase(getPackaging())) {
      return "Bundle";
    }
    if (PACK_MERGE.equalsIgnoreCase(getPackaging())) {
      return "Module";
    }
    return "Package";
  }
}
