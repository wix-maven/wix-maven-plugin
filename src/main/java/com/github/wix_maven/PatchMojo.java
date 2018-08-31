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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Reference - transform between different versions for patch (note same format must be used for all
 * files - current 3.6) torch.exe -p -xi Error\Product.wixpdb Fixed\Product.wixpdb -out Patch.wixmst
 * torch.exe -p -xo Error\Product.msi Fixed\Product.msi -out Patch.wixmst
 * 
 * pyro.exe Patch.wixmsp -out Patch.msp -t Sample Patch.wixmst
 */
/**
 * Goal which executes WiX torch & pyro to create msp files.
 */
@Mojo(name = "patch", requiresProject = true, defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class PatchMojo extends AbstractTorchMojo {
  // TODO: might be good to make baseline + baseArt + patchedArt an object and make a list of them
  // to allow multiple changes into 1 patch
  /**
   * ArtifactItem to use as base. (ArtifactItem contains groupId, artifactId, version, type,
   * classifier) See <a href="./usage.html">Usage</a> for details.
   */
  @Parameter(required = true)
  private ArtifactItem baseArtifactItem;

  /**
   * ArtifactItem to use as patch. (ArtifactItem contains groupId, artifactId, version, type,
   * classifier) See <a href="./usage.html">Usage</a> for details.
   */
  @Parameter(required = true)
  private ArtifactItem patchedArtifactItem;

  /**
   * Baseline id... needs to match the baseline in the patch file, why then is it needed...I don't
   * get how this works... Can we just read this from the input xml?
   */
  @Parameter(property = "wix.baseline", required = true)
  protected String baseline;

  /**
   * Re use cabinet files across multiple linkages. (-reusecab)
   */
  @Parameter(property = "wix.reuseCab", defaultValue = "false")
  private boolean reuseCabs;

  /**
   * Properties catch all in case we missed some configuration. Passed directly to pyro
   */
  @Parameter
  private Properties patchProperties;

  /**
   * Project builder -- builds a model from a pom.xml
   */
  @Component
  protected MavenProjectBuilder mavenProjectBuilder;

  public PatchMojo() {
    // TODO Auto-generated constructor stub
  }

  @Override
  protected void addValidationOptions(Commandline cl) {
    cl.addArguments(new String[] {"-t", "patch"});

    if ("wixpdb".equals(patchedArtifactItem.getType()))
      cl.addArguments(new String[] {"-xi"});
    else
      cl.addArguments(new String[] {"-xo"});
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

  protected void addOtherOptions(Commandline cl) {
    if (patchProperties != null && !patchProperties.isEmpty()) {
      ArrayList<String> result = new ArrayList<String>();

      for (Enumeration<Object> keys = patchProperties.keys(); keys.hasMoreElements();) {
        String key = (String) keys.nextElement();
        if (key.startsWith("x--"))
          key = key.substring(2);
        result.add(key);
        String value = patchProperties.getProperty(key);
        if (null != value) {
          result.add(value);
        }
      }

      cl.addArguments(result.toArray(new String[0]));
    }
  }

  @Override
  protected String torchOutputExtension() {
    return "wixmst";
  }

  // private DependencyTreeBuilder treeBuilder;
  @SuppressWarnings("unchecked")
  protected Set<Artifact> getJARDependencySets(Artifact inputArtifact)
      throws MojoExecutionException {
    FilterArtifacts filter = new FilterArtifacts();
    // filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
    filter.addFilter(new TypeFilter("jar", ""));

    // start with all artifacts.
    Set<Artifact> artifacts;
    try {
      artifacts = resolveArtifactDependencies(inputArtifact);
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } catch (ArtifactNotFoundException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } catch (ProjectBuildingException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } catch (InvalidDependencyVersionException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    // perform filtering
    try {
      artifacts = filter.filter(artifacts);
    } catch (ArtifactFilterException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    return artifacts;
  }

  private void addJARSourceRoots(Commandline cl, Artifact inputArtifact, String bindPathOpt)
      throws MojoExecutionException {
    // TODO: transitive only through direct attached jars...
    Set<Artifact> jarArtifacts = getJARDependencySets(inputArtifact);
    getLog().info("Adding " + jarArtifacts.size() + " dependent JAR paths");
    if (!jarArtifacts.isEmpty()) {
      for (Artifact jar : jarArtifacts) {
        if (null != jar.getFile().getParentFile()) { // file is ment to always be in some folder..
                                                     // just in case? hacky defensive programming...
          getLog().debug(String.format("JAR added dependency %1$s", jar.getArtifactId()));
          // Warn: may need to make artifacts unique using groupId... but nar doesn't do that yet.
          // when there are multiple jars with the same name,
          // there is a conflict between requirements for reactor 'compile' build Vs 'install' build
          // that can later be used in a patch,
          // the conflict is due to pathing or lack there of from compile not having the package id
          // in the path
          // so -b option used in linking cannot specify just the local repo, it must include the
          // full path to versioned package folder or 'target'
          cl.addArguments(new String[] {bindPathOpt,
              jar.getFile().getParentFile().getAbsolutePath() + "\\"});// .getPath()
        }
      }
    }
  }

  /**
   * Prepare and execute pyro commandline tool
   * 
   * @param pyroTool
   * @param patchInputFile
   * @param transformInputFile
   * @param archOutputFile
   * @throws MojoExecutionException
   */
  protected void pyro(File pyroTool, Artifact baseInputArtifact, Artifact latestInputArtifact,
      String arch, File patchInputFile, File transformInputFile, File archOutputFile)
      throws MojoExecutionException {
    getLog().info(" -- Pyro : " + archOutputFile.getPath());
    Commandline cl = new Commandline();

    cl.setExecutable(pyroTool.getAbsolutePath());
    // cl.setWorkingDirectory(wxsInputDirectory);
    addToolsetGeneralOptions(cl);
    addReuseCabOptions(cl, arch);
    addOtherOptions(cl);

    if ("wixpdb".equals(baseInputArtifact.getType())) { // already checked that artifact types match
      if (narUnpackDirectory.exists()) { // && if any nar dependencies, otherwise it isn't needed
        cl.addArguments(new String[] {"-bt", narUnpackDirectory.getAbsolutePath() + "\\"});// .getPath()
        cl.addArguments(new String[] {"-bu", narUnpackDirectory.getAbsolutePath() + "\\"});// .getPath()
      }
      addJARSourceRoots(cl, baseInputArtifact, "-bt");
      addJARSourceRoots(cl, latestInputArtifact, "-bu");
    }

    // addOptions(cl);
    addWixExtensions(cl);
    cl.addArguments(new String[] {patchInputFile.getAbsolutePath(), "-t", baseline,
        transformInputFile.getAbsolutePath(), "-out", archOutputFile.getAbsolutePath()});
    // addOtherOptions(cl);

    pyro(cl);
  }

  /**
   * Prepare and execute the Uber pyro commandline
   * 
   * @param pyroTool
   * @param patchInputFile
   * @param transformInputFiles
   * @param archOutputFile
   * @throws MojoExecutionException
   */
  protected void pyro(File pyroTool, File patchInputFile, Map<String, File> transformInputFiles,
      File archOutputFile) throws MojoExecutionException {
    getLog().info(" -- Pyro : " + archOutputFile.getPath());
    Commandline cl = new Commandline();

    cl.setExecutable(pyroTool.getAbsolutePath());
    // cl.setWorkingDirectory(wxsInputDirectory);
    addToolsetGeneralOptions(cl);

    // if( narUnpackDirectory.exists() )
    // allFileSourceRoots.add(narUnpackDirectory.getAbsolutePath());


    // addOptions(cl);
    addWixExtensions(cl);
    cl.addArguments(new String[] {patchInputFile.getAbsolutePath()});
    for (Map.Entry<String, File> entry : transformInputFiles.entrySet()) {
      cl.addArguments(new String[] {"-t", baseline + "_" + entry.getKey().replace('-', '_'),
          entry.getValue().getAbsolutePath()});
    }
    cl.addArguments(new String[] {"-out", archOutputFile.getAbsolutePath()});
    // addOtherOptions(cl);

    pyro(cl);
  }

  /**
   * Execute the given command line parsing output for pyro comments
   * 
   * @param cl
   * @throws MojoExecutionException
   */
  protected void pyro(Commandline cl) throws MojoExecutionException {
    try {
      if (verbose) {
        getLog().info(cl.toString());
      } else {
        getLog().debug(cl.toString());
      }

      // TODO: maybe should report or do something with return value.
      int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

        public void consumeLine(final String line) {
          // TODO: pyro specific message handling
          if (line.contains(" : error ")) {
            getLog().error(line);
          } else if (line.contains(" : warning ")) {
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
        throw new MojoExecutionException("Problem executing pyro, return code " + returnValue);
      }
    } catch (CommandLineException e) {
      // throw new MojoExecutionException( "Error running mapping-tools.",
      // e );
      throw new MojoExecutionException("Problem executing pyro", e);
    }
  }

  @SuppressWarnings("unchecked")
  protected Set<Artifact> getDependencySets() throws MojoExecutionException {
    // add filters in well known order, least specific to most specific
    FilterArtifacts filter = new FilterArtifacts();

    filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));

    // filter.addFilter( new ScopeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeScope
    // ),
    // DependencyUtil.cleanToBeTokenizedString( this.excludeScope ) ) );
    //
    // filter.addFilter( new TypeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeTypes
    // ),
    // DependencyUtil.cleanToBeTokenizedString( this.excludeTypes ) ) );
    //
    // filter.addFilter( new ClassifierFilter( DependencyUtil.cleanToBeTokenizedString(
    // this.includeClassifiers ),
    // DependencyUtil.cleanToBeTokenizedString( this.excludeClassifiers ) ) );
    //
    // filter.addFilter( new GroupIdFilter( DependencyUtil.cleanToBeTokenizedString(
    // this.includeGroupIds ),
    // DependencyUtil.cleanToBeTokenizedString( this.excludeGroupIds ) ) );
    //
    // filter.addFilter( new ArtifactIdFilter( DependencyUtil.cleanToBeTokenizedString(
    // this.includeArtifactIds ),
    // DependencyUtil.cleanToBeTokenizedString( this.excludeArtifactIds ) ) );

    filter.addFilter(new TypeFilter(PACK_INSTALL, ""));
    // String clasfilter = arch+"-"+culture+","+arch+"-neutral";
    // getLog().debug(clasfilter);
    // filter.addFilter( new ClassifierFilter( clasfilter, "" ) );

    // start with all artifacts.
    Set<Artifact> artifacts = project.getArtifacts();

    // perform filtering
    try {
      artifacts = filter.filter(artifacts);
    } catch (ArtifactFilterException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    return artifacts;
  }

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info(getClass().getName() + " skipped");
      return;
    }

    File torchTool = validateTool();
    File pyroTool = new File(toolDirectory, "/bin/pyro.exe");
    if (!pyroTool.exists())
      throw new MojoExecutionException("Pyro tool doesn't exist " + pyroTool.getAbsolutePath());

    defaultLocale();

    // Set<Artifact> artifacts = getDependencySets();

    for (String arch : getPlatforms()) {
      // for the Uber patch
      // Map<String,File> archIntermediateFiles = new HashMap<String, File>();
      for (String culture : culturespecs()) {

        if (!baseArtifactItem.getType().equals(patchedArtifactItem.getType()))
          throw new MojoExecutionException(
              "Wix Pyro currently requires that both inputs to the patch are the same type, wixpdb or msi.");


        Artifact baseInputArtifact =
            getRelatedArtifact(baseArtifactItem, arch, culture, baseArtifactItem.getType());
        Artifact latestInputArtifact =
            getRelatedArtifact(patchedArtifactItem, arch, culture, patchedArtifactItem.getType());

        File baseInputFile = getRelatedArtifactFiles(baseInputArtifact);
        File latestInputFile = getRelatedArtifactFiles(latestInputArtifact);

        //
        File archIntermediateFile =
            getOutput(intDirectory, arch, getPrimaryCulture(culture), "wixmst");
        torch(torchTool, baseInputFile, latestInputFile, archIntermediateFile);

        File archPatchFile = getOutput(arch, culture, "wixmsp"); // output from earlier light
        File archOutputFile = getOutput(arch, culture, getPackageOutputExtension());
        pyro(pyroTool, baseInputArtifact, latestInputArtifact, arch, archPatchFile,
            archIntermediateFile, archOutputFile);

        // for Uber patch
        // archIntermediateFiles.put(culture, archIntermediateFile);
      }
      // Uber patch..
      // String culture = baseCulturespec();
      // File patchFile = getOutput(arch, culture, "wixmsp"); // output from earlier light
      // File outputFile = getOutput(arch, culture, getPackageOutputExtension());
      // pyro(pyroTool, patchFile, archIntermediateFiles, outputFile);
    }
  }

  /**
   * Based on Maven-dependency-plugin AbstractFromConfigurationMojo.
   * 
   * Resolves the Artifact from the remote repository if necessary. If no version is specified, it
   * will be retrieved from the dependency list or from the DependencyManagement section of the pom.
   * 
   * @param artifactItem containing information about artifact from plugin configuration.
   * @return Artifact object representing the specified file.
   * @throws MojoExecutionException with a message if the version can't be found in
   *         DependencyManagement.
   */
  protected Artifact getRelatedArtifact(ArtifactItem artifactItem, String arch, String culture,
      String type) throws MojoExecutionException {

    VersionRange vr;
    try {
      vr = VersionRange.createFromVersionSpec(artifactItem.getVersion());
    } catch (InvalidVersionSpecificationException e1) {
      vr = VersionRange.createFromVersion(artifactItem.getVersion());
    }

    Set<Artifact> artifactSet = new HashSet<Artifact>();

    String classifier = arch + "-" + (culture == null ? "neutral" : getPrimaryCulture(culture));
    getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), type, artifactSet, vr,
        classifier);

    if (artifactSet.size() != 1) // this is more like an assert - we are only asking for one, and if
                                 // none it already threw.
      throw new MojoExecutionException(String.format(
          "Found multiple artifacts for : %1:%2:%3:%4:%5", artifactItem.getGroupId(),
          artifactItem.getArtifactId(), type, vr, classifier));

    return artifactSet.iterator().next();
  }

  protected File getRelatedArtifactFiles(Artifact artifact) throws MojoExecutionException {
    if ("wixpdb".equals(artifact.getType()))
      return artifact.getFile();
    throw new MojoExecutionException("Incomplete Mojo - add tools for admin unpacking msi");
    // else{
    // File resolvedArtifactFile = getOutput(new File(intDirectory,"base"), arch, culture, "msi");
    //
    // throw new MojoExecutionException("Incomplete Mojo - add tools for admin unpacking msi");
    // // copy artifactFile to resolvedArtifactFile
    // // unpack resolvedArtifactFile in intdir like
    // //msiexec /a %newmsi% TARGETDIR="%workdir%\new" /qb /l*v "%workdir%\logs\new.log"
    // Reboot=ReallySuppres
    // // return resolvedArtifactFile;
    // }

  }

  // Maven 3
  // File repo = this.session.getLocalRepository().getBasedir();
  // Collection<Artifact> deps = new Aether(this.getProject(), repo).resolve(
  // new DefaultArtifact("junit", "junit-dep", "", "jar", "4.10"),
  // JavaScopes.RUNTIME
  // );

  @SuppressWarnings("unchecked")
  protected Set<Artifact> resolveDependencyArtifacts(MavenProject theProject)
      throws ArtifactResolutionException, ArtifactNotFoundException,
      InvalidDependencyVersionException {
    Set<Artifact> artifacts =
        theProject.createArtifacts(this.factory, null, new ScopeArtifactFilter(
            Artifact.SCOPE_COMPILE));
    // doubt: scope if not null is ignoring the Provided scope elements - all examples show using
    // some scope

    getLog().info("Checking " + artifacts.size() + " dependents of " + theProject.getId());
    for (Artifact artifact : artifacts) {
      getLog().debug("Checking dependent " + artifact.getId());
      // resolve the new artifact
      this.resolver.resolve(artifact, this.remoteArtifactRepositories, this.localRepository);
    }
    return artifacts;
  }

  /**
   * This method resolves all transitive dependencies of an artifact.
   * 
   * @param artifact the artifact used to retrieve dependencies
   * @return resolved set of dependencies
   * @throws ArtifactResolutionException
   * @throws ArtifactNotFoundException
   * @throws ProjectBuildingException
   * @throws InvalidDependencyVersionException
   * 
   */
  protected Set<Artifact> resolveArtifactDependencies(Artifact artifact)
      throws ArtifactResolutionException, ArtifactNotFoundException, ProjectBuildingException,
      InvalidDependencyVersionException {
    Artifact pomArtifact =
        this.factory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(),
            artifact.getVersion(), "", "pom");

    MavenProject pomProject =
        mavenProjectBuilder.buildFromRepository(pomArtifact, this.remoteArtifactRepositories,
            this.localRepository);

    // List<Dependency> dependencies = pomProject.getDependencies();
    // for( Dependency dep : dependencies ){
    // getLog().info( "Checking dependent " + dep.getArtifactId() );
    // factory.createDependencyArtifact(groupId, artifactId, vr, type, classifier,
    // Artifact.SCOPE_COMPILE);
    // }
    return resolveDependencyArtifacts(pomProject);
  }
}
