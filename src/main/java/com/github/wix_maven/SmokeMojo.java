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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/***
 * Smoke to perform 'unit' testing of msi/msp Smoke runs ICE similar to light, this goal allows a
 * seperate execution from the linker step. Optionally translate into unit test report output
 */
@Mojo(name = "smoke", requiresProject = true, defaultPhase = LifecyclePhase.TEST)
public class SmokeMojo extends AbstractPackageable {

  /**
   * Skip running of smoke goal.
   */
  @Parameter(property = "wix.skipTests", defaultValue = "false")
  protected boolean skipTests;

  /**
   * Where to store the results as an xml report. TODO: gather ICE issues into XML report...
   */
  @Parameter(property = "wix.reportDirectory",
      defaultValue = "${project.build.directory}/wix-reports")
  protected File reportDirectory;

  /**
   * Where to store the validation log file.
   */
  @Parameter(property = "wix.validationLogFile",
      defaultValue = "${project.build.directory}/wix-log/validation.txt")
  protected File validationLogFile;

  /**
   * Run smoke on installers created for all cultures. When false only runs against the base
   * culture.
   */
  @Parameter(property = "wix.validateAllCultures", defaultValue = "false")
  protected boolean validateAllCultures;

  /**
   * Set this to "true" to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite
   * convenient on occasion.
   */
  @Parameter(property = "wix.test.failure.ignore", defaultValue = "false")
  protected boolean testFailureIgnore;

  public void multiSmoke(File smokeTool) throws MojoExecutionException, MojoFailureException {
    defaultLocale();

    ArrayList<String> files = new ArrayList<String>(2);

    for (String arch : getPlatforms()) {
      if (validateAllCultures) {
        for (String culture : culturespecs()) {
          File archOutputFile = getOutput(arch, culture, outputExtension());
          files.add(archOutputFile.getPath());
        }
      } else {
        String culture = baseCulturespec();
        File archOutputFile = getOutput(arch, culture, outputExtension());
        files.add(archOutputFile.getPath());
      }
    }

    getLog().info(" -- Smoke testing : " + files.toString());

    Commandline cl = new Commandline();

    cl.setExecutable(smokeTool.getAbsolutePath());
    cl.setWorkingDirectory(relativeBase);
    addToolsetGeneralOptions(cl);
    addWixExtensions(cl);
    cl.addArguments(files.toArray(new String[0]));

    smoke(cl);
  }

  protected void smoke(Commandline cl) throws MojoExecutionException {
    try {
      if (verbose) {
        getLog().info(cl.toString());
      } else {
        getLog().debug(cl.toString());
      }

      if (!validationLogFile.exists()) {
        validationLogFile.getParentFile().mkdirs();
        validationLogFile.createNewFile();
      }
      FileWriter fw = new FileWriter(validationLogFile.getAbsoluteFile());
      final BufferedWriter bw = new BufferedWriter(fw);

      // TODO: maybe should report or do something with return value.
      int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

        public void consumeLine(final String line) {
          if (verbose) {
            if (line.contains(" : error ")) {
              getLog().error(line);
            } else if (line.contains(" : warning ")) { // TODO: option to write warning to log only
                                                       // as often many warning.
              getLog().warn(line);
            } else if (line.contains("usage: ")) {
              getLog().warn(line);
            } else if (verbose) {
              getLog().info(line);
            } else {
              getLog().debug(line);
            }
          }
          try {
            bw.write(line);
            bw.newLine();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }

      }, new StreamConsumer() {

        public void consumeLine(final String line) {
          getLog().error(line);

          try {
            bw.write(line);
            bw.newLine();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }

      });

      bw.close();
      if (!verbose) {
        getLog().info("Smoke log " + validationLogFile.getAbsoluteFile());
      }
      if (returnValue != 0) {
        throw new MojoExecutionException("Problem executing smoke, return code " + returnValue);
      }
    } catch (CommandLineException e) {
      throw new MojoExecutionException("Problem executing smoke", e);
    } catch (IOException e) {
      throw new MojoExecutionException("Problem recording smoke execution", e);
    }
  }

  public void execute() throws MojoExecutionException, MojoFailureException {

    if (skip || skipTests) {
      if (verbose)
        getLog().info(
            getClass().getName() + " skipped due to skip " + skip + " or skipTests " + skipTests);
      return;
    }

    if (!(PACK_INSTALL.equalsIgnoreCase(getPackaging()) || PACK_PATCH
        .equalsIgnoreCase(getPackaging())))
      throw new MojoFailureException("Can only smoke test .msi or .msp");

    if (VALIDATE_SUPPRESS.equalsIgnoreCase(validate) || VALIDATE_LINK.equalsIgnoreCase(validate)) {
      if (verbose)
        getLog().info(getClass().getName() + " skipped due to validate=" + validate);
      return;
    }

    if (reportDirectory != null)
      reportDirectory.mkdirs();

    File smokeTool = new File(toolDirectory, "/bin/smoke.exe");
    if (!smokeTool.exists())
      throw new MojoExecutionException("Smoke tool doesn't exist " + smokeTool.getAbsolutePath());

    try {
      multiSmoke(smokeTool);
    } catch (MojoExecutionException ex) {
      if (testFailureIgnore)
        getLog().warn(ex.getMessage());
      else
        throw ex;
    } catch (MojoFailureException ex) {
      if (testFailureIgnore)
        getLog().warn(ex.getMessage());
      else
        throw ex;
    }
  }

  /**
   * 
   * @return
   */
  private String outputExtension() {
    return getPackaging();
  }

}
