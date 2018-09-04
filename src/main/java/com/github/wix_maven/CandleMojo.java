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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;


/**
 * Goal which executes WiX candle to create a .wixobj file.
 * 
 * The following project dependency inclusion patterns apply<br>
 * Dependent NAR project 'Foo' with possible nar output redefined as 'bar' <br>
 * <ul>
 * <li>-dFoo.TargetDir=Foo-version\</li>
 * <li>-dFoo.TargetExt=.wixlib</li>
 * <li>-dFoo.TargetFileName=bar.type</li>
 * <li>-dFoo.TargetName=bar</li>
 * </ul>
 */
@Mojo(name = "candle", requiresProject = true, defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class CandleMojo extends AbstractCompilerMojo {
  /**
   * The name of the response file.
   */
  public static final String RESPONSE_FILE_NAME = "responseFile.txt";
  /**
   * Definitions (pre) Compilation (-d option)
   */
  @Parameter
  private Set<String> definitions = new HashSet<String>();
  private Set<String> definitionsArch = new HashSet<String>();

  /**
   * Include paths (-I option)
   */
  @Parameter
  private String[] includePaths;

  /**
   * The granularity in milliseconds of the last modification date for testing whether a source
   * needs re-compilation
   */
  @Parameter(property = "wix.staleMillis", defaultValue = "1000", required = true)
  protected int staleMillis;

  /**
   * Set this value if you wish to have a single timestamp file to track changes rather than cxx,hxx
   * comparison.<BR>
   * The time-stamp file for the processed xsd files.
   */
  @Parameter(property = "wix.timestampFile")
  protected String timestampFile = null;

  /**
   * The directory to store the time-stamp file for the processed aid files. <BR>
   * Defaults to outputDirectory. <BR>
   * Only used with xsdTimestampFile being set.
   */
  @Parameter(property = "wix.timestampDirectory",
      defaultValue = "${project.build.directory}/mapping/cpp")
  protected File timestampDirectory;

  /**
   * The set of files/patterns to include Defaults to "**\/*.wxs"
   */
  @Parameter
  private Set<String> includes = new HashSet<String>();

  /**
   * A set of exclusion filters.
   */
  @Parameter
  private Set<String> excludes = new HashSet<String>();

  /**
   * A set of response files with configuration options.
   */
  private Set<String> responseFiles = new HashSet<String>();

  /**
   * Properties catch all in case we missed some configuration. Passed directly to candle
   */
  @Parameter
  private Properties candleProperties;

  public final Set<String> getIncludes() {
    if (includes.isEmpty()) {
      includes.add("**/*.wxs");
    }
    return includes;
  }

  public final Set<String> getExcludes() {
    return excludes;
  }

  protected void addOtherOptions(Commandline cl) {
    if (candleProperties != null && !candleProperties.isEmpty()) {
      ArrayList<String> result = new ArrayList<String>();

      for (Enumeration<Object> keys = candleProperties.keys(); keys.hasMoreElements();) {
        String key = (String) keys.nextElement();
        if (key.startsWith("x--"))
          key = key.substring(2);
        result.add(key);
        String value = candleProperties.getProperty(key);
        if (null != value) {
          result.add(value);
        }
      }

      cl.addArguments(result.toArray(new String[0]));
    }
  }

  private void addOptions(Commandline cl, String arch, String culture) {
    // note: cl tool will add quotes if necessary - adding \" in an arg will break it.

    if (definitionsArch != null) {
      for (String def : definitionsArch) {
        cl.addArguments(new String[] {"-d" + def});
      }
    }

    // TODO: shorten commandline, use relative paths where possible
    cl.addArguments(new String[] {"-dConfiguration=Release"});
    String intOutDir = getArchIntDirectory(arch, culture).getAbsolutePath() + "\\";
    cl.addArguments(new String[] {"-out", intOutDir,
        "-dOutDir=" + intDirectory.getAbsolutePath() + "\\"}); // VS OutDir doesn't include arch
    cl.addArguments(new String[] {"-arch", arch, "-dPlatform=" + arch});
    cl.addArguments(new String[] {"-dProjectDir=" + project.getBasedir().getAbsolutePath() + "\\"});
    // -dProjectExt=.wixproj -dProjectFileName=Baz.wixproj
    // -dProjectName=Baz
    // -dProjectPath=C:\Baz\SecureLogin-32.wixproj
    cl.addArguments(new String[] {"-dProjectId=" + project.getArtifactId(),
        "-dProjectName=" + project.getName()});

    // -dTargetDir=C:\Baz\target\Release\
    // -dTargetExt=.msi
    // -dTargetFileName=Bez.msi
    // -dTargetName=Bez
    // -dTargetPath=C:\Baz\target\Release\Bez.msi

    // TODO: "Solution" values, or should we change this for maven build to artifacts...
    // if( project.getParent() != null ){ // direct parent, or top most parent, or aggregate
    // parent?... not a great match to VS Solution.
    // cl.addArguments(new String[] { "-dSolutionName=" + project.getParent().getArtifactId() });
    // -dSolutionDir=\assemblies
    // -dSolutionExt=.sln
    // -dSolutionFileName=Assemblies.sln
    // -dSolutionName=Assemblies
    // -dSolutionPath=\assemblies\Assemblies.sln
    // }

    if (responseFiles != null) {
      for (String file : responseFiles) {
        cl.addArguments(new String[] {"@" + file});
      }
    }
  }

  private static String join(Iterable<? extends CharSequence> elements) {
    final StringBuilder sb = new StringBuilder();
    // Each file to new line
    // All files quoted for handling of spaces
    if (elements != null && elements.iterator().hasNext()) {
      Iterator<? extends CharSequence> iter = elements.iterator();
      sb.append("\n\"");
      sb.append(iter.next());
      sb.append("\"");
      while (iter.hasNext()) {
        sb.append("\n\"");
        sb.append(iter.next());
        sb.append("\"");
      }
    }
    return sb.toString();
  }

  protected void compile(File responseFile, String arch, String culture)
      throws MojoExecutionException {

    Commandline cl = new Commandline();

    cl.setExecutable(new File(toolDirectory, "bin/candle.exe").getAbsolutePath());
    cl.setWorkingDirectory(relativeBase);
    addToolsetGeneralOptions(cl);

    addWixExtensions(cl);
    addOptions(cl, arch, culture);
    addOtherOptions(cl);
    cl.addArguments(new String[] {"@" + responseFile.getAbsolutePath()});

    try {
      if (verbose) {
        getLog().info(cl.toString());
      } else {
        getLog().debug(cl.toString());
      }

      // TODO: maybe should report or do something with return value.
      int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

        public void consumeLine(final String line) {
          if (line.contains(") : error ")) {
            getLog().error(line);
          } else if (line.contains(") : warning ")) {
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

      if (returnValue != 0) {
        throw new MojoExecutionException("Problem executing compiler, return code " + returnValue
            + "\nFailed execution of " + cl.toString());
      }
    } catch (CommandLineException e) {
      throw new MojoExecutionException("Problem executing compiler candle \nFailed execution of "
          + cl.toString(), e);
    }
  }

  @Override
  protected void addDefinition(String def) {
    definitions.add(def);
  }

  protected void createCommonResponseFile() throws MojoExecutionException {
    final StringBuilder sb = new StringBuilder();

    if (includePaths != null) {
      for (String incPath : includePaths) {
        sb.append("\n\"-I");
        sb.append(incPath); // quoted for spaces, assuming it won't include " as they are invalid in
                            // file name
        sb.append("\"");
      }
    }
    if (definitions != null) {
      for (String def : definitions) {
        sb.append("\n\"-d");
        sb.append(StringUtils.escape(def, new char[] {'\"'}, '\\'));
        sb.append("\"");
      }
    }

    final File file = new File(intDirectory, RESPONSE_FILE_NAME);
    createResponseFile(file, sb.toString());
    responseFiles.add(file.getAbsolutePath());
  }

  @SuppressWarnings("unchecked")
  public void execute() throws MojoExecutionException {

    if (skip) {
      getLog().info(getClass().getName() + " skipped");
      return;
    }

    if (!wxsInputDirectory.exists()) {
      return;
    }

    addWixDefines();
    addNARDefines();
    addJARDefines();
    addNPANDAYDefines();
    addHarvestDefines();

    createCommonResponseFile();

    for (String arch : getPlatforms()) {
      definitionsArch.clear();
      addNARArchDefines(arch);

      if (!compilePerLocale || PACK_LIB.equalsIgnoreCase(packaging)) {
        multiCompile(arch, null);
      } else {
        for (String culture : culturespecs()) {

          multiCompile(arch, culture);
        }
      }
    }
  }

  private void multiCompile(String arch, String culture) throws MojoExecutionException {
    File intDir = getArchIntDirectory(arch, culture);
    intDir.mkdirs();

    // and intel... and...
    try {

      // TODO: there is a limitation here - if you change config options in pom, then we don't check
      // to see if the file is older than the new config...
      SourceInclusionScanner scanner =
          new StaleSourceScanner(staleMillis, getIncludes(), getExcludes());
      if (timestampFile != null && timestampDirectory != null) {
        getLog().debug("Using timestamp file tracking for sources");
        // if( !xsdTimestampDirectory.exists() ||
        // xsdTimestampFile.isEmpty() ) tracking isn't going to work,
        // always rebuild - warning?

        scanner.addSourceMapping(new SingleTargetSourceMapping(".wxs", timestampFile));
      } else {
        Set<String> fileExts = new HashSet<String>();
        fileExts.add(".wixobj");
        timestampDirectory = intDir;
        scanner.addSourceMapping(new SuffixMapping(".wxs", fileExts));
      }

      if (!timestampDirectory.exists())
        timestampDirectory.mkdirs();

      Set<File> wixSources = scanner.getIncludedSources(wxsInputDirectory, timestampDirectory);
      if (wxsGeneratedDirectory.exists())
        wixSources.addAll(scanner.getIncludedSources(wxsGeneratedDirectory, timestampDirectory));

      if (wixSources.isEmpty()) {
        getLog().info("All objects appear up to date");
      } else {
        Set<String> files = new HashSet<String>();
        for (Iterator<File> i = wixSources.iterator(); i.hasNext();) {
          files.add(getRelative(i.next()));
        }


        final String argsText = join(files);
        final File responseFile = new File(intDir, RESPONSE_FILE_NAME);
        createResponseFile(responseFile, argsText);

        compile(responseFile, arch, culture);

        if (timestampFile != null && timestampDirectory != null) {
          File timeStamp = new File(timestampDirectory, timestampFile);
          if (!timeStamp.exists())
            try {
              timeStamp.createNewFile();
            } catch (IOException e) {
              getLog().warn("XSD: Unable to touch timestamp file");
            }
          else if (!timeStamp.setLastModified(System.currentTimeMillis()))
            getLog().warn("XSD: Unable to touch timestamp file");
        }
      }

      // project.addCompileSourceRoot(
      // outputDirectory.getAbsolutePath() );
      // updateProject( );
    } catch (InclusionScanException e) {
      throw new MojoExecutionException("XSD: scanning for updated files failed", e);
    }
  }

  private void addNARArchDefines(String arch) {
    if ("x86".equals(arch)) {
      definitionsArch.add("IsWin64=no");
      definitionsArch.add("narDir.dll=x86-Windows-msvc-shared/lib/x86-Windows-msvc/shared");
      definitionsArch.add("narDir.exe=x86-Windows-msvc-executable/bin/x86-Windows-msvc");
    } else {
      definitionsArch.add("IsWin64=yes");
      definitionsArch.add("narDir.dll=amd64-Windows-msvc-shared/lib/amd64-Windows-msvc/shared");
      definitionsArch.add("narDir.exe=amd64-Windows-msvc-executable/bin/amd64-Windows-msvc");
    }
  }

  private void createResponseFile(final File file, final String args) throws MojoExecutionException {
    FileOutputStream fop = null;
    try {
      // if file doesn't exists, then create it
      if (!file.exists()) {
        file.getParentFile().mkdirs();
        file.createNewFile();
      }
      fop = new FileOutputStream(file);
      // get the content in bytes
      byte[] contentInBytes = args.getBytes();
      fop.write(contentInBytes);
      fop.flush();
      fop.close();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to create a response file: " + ex.getMessage(), ex);
    } finally {
      try {
        if (fop != null) {
          fop.close();
        }
      } catch (IOException ex) {
        getLog().debug("Failed to close fop: " + ex.getMessage());
      }
    }
  }
}
