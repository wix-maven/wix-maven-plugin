package com.github.wix_maven;

/*
 * #%L
 * WiX Toolset (Windows Installer XML) Maven Plugin
 * %%
 * Copyright (C) 2013 - 2014 GregDomjan NetIQ
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;

public abstract class AbstractWixMojo extends AbstractMojo {

	/**
	 * Skip running of all wix plugin goals altogether.
	 * 
	 * @parameter expression="${wix.skip}" default-value="false"
	 */
	protected boolean skip;

	/**
	 * The output type:
	 *  <li> msi - Windows installer
	 *  <li> msm - Merge Module
	 *  <li> wixlib - Wix library
	 *  <li> msp - Windows patch
	 *  <li> bundle - wix bootstrapper
	 * 
	 * @parameter default-value="${project.packaging}"
	 * @required
	 */
	protected String packaging;
	
	/**
	 * The directory to scan for wix files.
	 * For each build type there is at least one wxs file required
	 * 
	 * @parameter default-value="${project.basedir}/src/main/wix"
	 * @required
	 */
	protected File wxsInputDirectory;

	/**
	 * Should validation be run, and when.
	 * <li>linking - Run validation during linking from light/lit. 
	 * <li>unit - Run validation as unit test, suppressing validation during linking (light/lit).
	 * <li>both - Run validation during linking from light/lit and also as unit test.
	 * <li>suppress - Suppressing validation during linking (light/lit)
	 * 
	 * @parameter expression="${wix.validate}" default-value="unit"
	 */
	protected String validate;
	static final String VALIDATE_LINK = "linking";
	static final String VALIDATE_UNIT = "unit";
	static final String VALIDATE_BOTH = "both";
	static final String VALIDATE_SUPPRESS = "suppress";

	/**
	 * Reference paths to WiX extensions to use default list containing the wixlib path only.
	 * 
	 * @parameter
	 */
	// protected String[] referencePaths;

	/**
	 * Show additional info such as the wix toolset logo
	 * 
	 * @parameter expression="${wix.verbose}" default-value="false"
	 */
	protected boolean verbose;

	/**
	 * The Platforms (Archictecture) for the msi. <br> 
	 * Some choices are: x86, intel, x64, intel64, or ia64 <br> 
	 * If list is empty default-value="x86" <br> 
	 * Will set: 
	 * candle -dPlatform= -arch
	 * 
	 * @parameter 
	 */
	private Set<String> platforms;

	/**
	 * Intermediate directory - will have ${arch} appended
	 * 
	 * @parameter expression="${wix.intDirectory}" default-value="${project.build.directory}/wixobj/Release"
	 * @required
	 */
	protected File intDirectory;

	/**
	 * Where to unpack the wix tools
	 * TODO: might need to do something about including tools version in path, or manage the unpacking more cleanly
	 * 
	 * @parameter expression="${wix.toolsPath}" default-value="${project.build.directory}/wix-tools"
	 * @required
	 */
	protected File toolDirectory;

	/**
	 * When to unpack the wix tools. 
	 * Default is to unpack the tools every time and overwrite, set to false to only overwrite if the tools are newer.
	 * This is provided to allow newer WIX test binaries to be dropped in, rather than having to install/deploy the wix-tools.
	 * 
	 * @parameter expression="${wix.toolDirectoryOverwrite}" default-value="true"
	 * @required
	 */
	protected boolean toolDirectoryOverwrite = true;

	/**
	 * Intermediate directory - will have ${arch} appended
	 * 
	 * @parameter expression="${wix.unpackPath}" default-value="${project.build.directory}/unpack"
	 * @required
	 */
	protected File unpackDirectory;

	/**
	 * A relative base path to shorten commandline references to files in the project.
	 * Default is the project base directory, if alternate locations are given for wxs, wxl files it may be appropriate to change this.
	 * 
	 * @parameter expression="${wix.relativeBase}" default-value="${project.basedir}"
	 */
	protected File relativeBase;
	
	/**
	 * Leave the xsd-tools behind after compilation for extended use outside this goal.
	 * 
	 * @parameter expression="${wix.extendedUse}" default-value="false"
	 */
	protected boolean extendedUse;

	/**
	 * Artifact id of the toolset jar to unpack.
	 * 
	 * @parameter default-value="wix-toolset"
	 * @required
	 */
	protected String toolsPluginArtifactId;

	/**
	 * Group id of the toolset jar to unpack.
	 * 
	 * @parameter default-value="org.wixtoolset.maven"
	 * @required
	 * */
	private String toolsPluginGroupId;

	/**
	 * Artifact id of the toolset jar to unpack.
	 * 
	 * @parameter default-value="wix-bootstrap"
	 * @required
	 */
	private String bootstrapPluginArtifactId;
	
	/**
	 * Group id of the toolset jar to unpack.
	 * 
	 * @parameter default-value="org.wixtoolset.maven"
	 * @required
	 * */
	private String bootstrapPluginGroupId;

	/**
	 * Base Name of the generated wix objects.
	 * 
	 * @parameter expression="${wix.finalName}" default-value="${project.build.finalName}"
	 * @required
	 */
	//@Parameter(alias = "wixName", property = "wix.finalName", defaultValue = "${project.build.finalName}")
	private String finalName;
	
	/**
	 * @parameter default-value="${plugin.artifacts}"
	 * @required
	 * @readonly
	 * */
	private List pluginArtifacts;

	/**
	 * The Zip archiver.
	 * 
	 * @component role="org.codehaus.plexus.archiver.UnArchiver" roleHint="zip"
	 * @readonly
	 * @required
	 */
	protected ZipUnArchiver zipUnArchiver;

	/**
	 * To search for artifacts within the reactor and ensure consistent behaviour between Maven 2 and Maven 3.
	 * 
	 * @parameter expression = "${reactorProjects}"
	 * @readonly = true
	 * @required = true
	 */
	protected List<MavenProject> reactorProjects;

	/**
	 * Used to look up Artifacts in the remote repository.
	 * 
	 * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
	 * @required
	 * @readonly
	 */
	protected ArtifactFactory factory;

	/**
	 * Used to look up Artifacts in the remote repository.
	 * 
	 * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
	 * @required
	 * @readonly
	 */
	protected ArtifactResolver resolver;

	/**
	 * @parameter expression="${localRepository}"
	 * @required
	 * @readonly
	 */
	protected ArtifactRepository localRepository;

	/**
	 * Remote repositories which will be searched for nar attachments.
	 * 
	 * @parameter expression="${project.remoteArtifactRepositories}"
	 * @required
	 * @readonly
	 */
	protected List remoteArtifactRepositories;

	/**
	 * Artifact collector, needed to resolve dependencies.
	 * 
	 * @component role="org.apache.maven.artifact.resolver.ArtifactCollector"
	 * @required
	 * @readonly
	 */
	// @Component( role = ArtifactCollector.class )
	//protected ArtifactCollector artifactCollector;

	/**
	 * @parameter default-value="${project}"
	 * @required
	 * @readonly
	 * */
	protected MavenProject project;

	public final String PACK_LIB = "wixlib";
	public final String PACK_MERGE = "msm";
	public final String PACK_INSTALL = "msi";
	public final String PACK_PATCH = "msp";
	public final String PACK_BUNDLE = "bundle";

	protected Set<String> getPlatforms() {
		if (platforms == null)
			platforms = new HashSet<String>();
		if (platforms.isEmpty())
		{
			// Warning, there are parts of the code that assume there must be at least one arch, and that none will be null.
			getLog().info("No platforms specified, using default 'x86'");
			platforms.add("x86");
		}
		return platforms;
	}

	protected void setPlatforms(Set<String> platforms) {
		this.platforms = platforms;
	}

	protected File getArchIntDirectory( String arch ){
		return new File( intDirectory, arch );
	}
	
	protected Artifact[] findToolsArtifacts() throws MojoExecutionException{
		
		ArrayList<Artifact> tools = new ArrayList<Artifact>(2);
		tools.add(findToolsArtifact(toolsPluginGroupId, toolsPluginArtifactId));
		if( PACK_BUNDLE.equalsIgnoreCase( packaging ) )
			tools.add(findToolsArtifact(bootstrapPluginGroupId, bootstrapPluginArtifactId));

		// TODO: Still deciding if this content should be optional
		// if( visualStudioUse )
		// zipUnArchiver.extract( "etc", toolDirectory );	

		return tools.toArray(new Artifact[tools.size()]); 
	}
	
	// TODO: should be a better pattern for lookup of the tools attached to this pluggin
	protected Artifact findToolsArtifact(String pluginGroupId, String pluginArtifactId) throws MojoExecutionException {
		// return (Artifact) mavenPlugin.getArtifactMap().get(ArtifactUtils.versionlessKey(mavenPlugin.getGroupId(), toolsId));
		if (null != pluginArtifacts) {
			for (Iterator artifactIterator = pluginArtifacts.iterator(); artifactIterator.hasNext();) {
				Artifact artifact = (Artifact) artifactIterator.next();
				if (artifact.getGroupId().equals(pluginGroupId) 
						&& artifact.getArtifactId().equals(pluginArtifactId)
						// && artifact.getClassifier().equals( env.arch )
						) { 
					return artifact;
				}
			}
		}
		getLog().error(String.format("Tools Artifact %1$s:%2$s not found", pluginGroupId, pluginArtifactId));
		throw new MojoExecutionException(String.format("Unable to find %1$s dependency", pluginArtifactId));
	}

	protected void unpackFileBasedResources() throws MojoExecutionException {
		getLog().debug("unpacking binaries");

		Artifact[] tools = findToolsArtifacts();
		final String subfolder = "bin";
		File pluginJar;
		for (Artifact artifact : tools) {

			getLog().debug(String.format("Using %1$s %2$s", toolsPluginArtifactId, artifact));

			pluginJar = artifact.getFile();

			getLog().debug(String.format("Using tools jar %1$s", pluginJar));

			zipUnArchiver.setSourceFile(pluginJar);
			
			getLog().info(String.format("Extracting %3$s %1$s to %2$s", pluginJar, toolDirectory, subfolder));
			zipUnArchiver.extract(subfolder, toolDirectory);	
		}

		if (!toolDirectory.exists()){
			throw new MojoExecutionException("Error extracting resources from mapping-tools.");
		}
	}

	protected void cleanupFileBasedResources() {
		try {
			FileUtils.deleteDirectory(toolDirectory);
		} catch (IOException e) {
			getLog().warn("Post build cleanup - Unable to cleanup mapping-tools folder", e);
		}
	}

	protected void getArtifact(String groupId, String artifactId, String type, Set<Artifact> artifactSet, VersionRange vr, String classifier) throws MojoExecutionException {
		Artifact artifact = factory.createDependencyArtifact(groupId, artifactId, vr, type, classifier,
				Artifact.SCOPE_COMPILE);
	
		// if ( StringUtils.isEmpty( artifactItem.getClassifier() ) )
		// {
		// artifact = factory.createDependencyArtifact( artifactItem.getGroupId(), artifactItem.getArtifactId(), vr,
		// artifactItem.getType(), null, Artifact.SCOPE_COMPILE );
		// }
		// else
		// {
		// artifact = factory.createDependencyArtifact( artifactItem.getGroupId(), artifactItem.getArtifactId(), vr,
		// artifactItem.getType(), artifactItem.getClassifier(),
		// Artifact.SCOPE_COMPILE );
		// }
	
		// Maven 3 will search the reactor for the artifact but Maven 2 does not
		// to keep consistent behaviour, we search the reactor ourselves.
		Artifact result = getArtifactFomReactor(artifact);
		if (result != null) {
			// return result;
			artifactSet.add(result);
			return;
		}
	
		try {
			// mdep-50 - rolledback for now because it's breaking some functionality.
			/*
			 * List listeners = new ArrayList(); Set theSet = new HashSet(); theSet.add( artifact ); ArtifactResolutionResult artifactResolutionResult
			 * = artifactCollector.collect( theSet, project .getArtifact(), managedVersions, this.local, project.getRemoteArtifactRepositories(),
			 * artifactMetadataSource, null, listeners ); Iterator iter = artifactResolutionResult.getArtifactResolutionNodes().iterator(); while (
			 * iter.hasNext() ) { ResolutionNode node = (ResolutionNode) iter.next(); artifact = node.getArtifact(); }
			 */
	
			resolver.resolve(artifact, remoteArtifactRepositories, localRepository);
			artifactSet.add(artifact);
		} catch (ArtifactResolutionException e) {
			throw new MojoExecutionException("Unable to resolve artifact.", e);
		} catch (ArtifactNotFoundException e) {
			throw new MojoExecutionException("Unable to find artifact.", e);
		}
	}

	/**
	 * Copied from Maven-dependency-plugin Checks to see if the specified artifact is available from the reactor.
	 * 
	 * @param artifact
	 *            The artifact we are looking for.
	 * @return The resolved artifact that is the same as the one we were looking for or <code>null</code> if one could not be found.
	 */
	private Artifact getArtifactFomReactor(Artifact artifact) {
		// check project dependencies first off
		for (Artifact a : (Set<Artifact>) project.getArtifacts()) {
			if (equals(artifact, a) && hasFile(a)) {
				return a;
			}
		}
	
		// check reactor projects
		for (MavenProject p : reactorProjects == null ? Collections.<MavenProject> emptyList() : reactorProjects) {
			// check the main artifact
			if (equals(artifact, p.getArtifact()) && hasFile(p.getArtifact())) {
				return p.getArtifact();
			}
	
			// check any side artifacts
			for (Artifact a : (List<Artifact>) p.getAttachedArtifacts()) {
				if (equals(artifact, a) && hasFile(a)) {
					return a;
				}
			}
		}
	
		// not available
		return null;
	}

	protected void addExtension(Commandline cl, String extFile){
		cl.addArguments(new String[] { "-ext", extFile });
	}
	
	protected void addWixExtensions(Commandline cl) throws MojoExecutionException {
		Set<Artifact> dependentExtensions = getExtDependencySets();
		getLog().info( "Adding "+dependentExtensions.size()+" dependentExtensions" );
		for (Artifact ext : dependentExtensions) {
			getLog().info(ext.getFile().getName());

			addExtension( cl, ext.getFile().getAbsolutePath() );
		}
//		if (extensions != null) {
//			for (String ext : extensions) {
//				// for toolDirectory + referencePaths
//				File extension = new File(toolDirectory, ext);
//				cl.addArguments(new String[] { "-ext", extension.getAbsolutePath() });
//			}
//		}
	}
	
	protected File getOutput(File baseDir, String arch, String culture, String extension) {
	
		File outFile = getOutputPath(baseDir, arch, culture);
			
		outFile = new File(outFile, finalName + "."+ extension ); // TODO: does this nead to vary with package type? packaging
	
		return outFile;
	}

	protected File getOutputPath(File baseDir, String arch, String culture) {
		File outFile = null;
		if (baseDir == null) // use project base dir because File uses the CWD by default which may not be the maven project directory that is the expected convention
			outFile = new File( project.getBasedir(), arch);
		else
			outFile = new File( baseDir, arch);
	
		if (culture != null)
			outFile = new File( outFile, culture );
		return outFile;
	}

	protected Set<Artifact> getExtDependencySets() throws MojoExecutionException {
		// add filters in well known order, least specific to most specific
		FilterArtifacts filter = new FilterArtifacts();

		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), false));

		// filter.addFilter( new ScopeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeScope ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeScope ) ) );
		//
		// filter.addFilter( new TypeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeTypes ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeTypes ) ) );
		//
		// filter.addFilter( new ClassifierFilter( DependencyUtil.cleanToBeTokenizedString( this.includeClassifiers ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeClassifiers ) ) );
		//
		// filter.addFilter( new GroupIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeGroupIds ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeGroupIds ) ) );
		//
		// filter.addFilter( new ArtifactIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeArtifactIds ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeArtifactIds ) ) );

		filter.addFilter(new TypeFilter("wixext,wixExt", ""));
		// String clasfilter = arch+"-"+culture+","+arch+"-neutral";
		// getLog().debug(clasfilter);
		// filter.addFilter( new ClassifierFilter( clasfilter, "" ) );

		// start with all artifacts.
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = project.getArtifacts();

		// perform filtering
		try {
			artifacts = filter.filter(artifacts);
		} catch (ArtifactFilterException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return artifacts;
	}
	
	protected void addToolsetGeneralOptions(Commandline cl) {
		if (!verbose)
			cl.addArguments(new String[] { "-nologo" });
		// TODO: work on suppressing warnings config, different list of suppressions for each tool
		//cl.addArguments(new String[] { "-sw" });
	}

	protected Set<Artifact> getJARDependencySets() throws MojoExecutionException {
		FilterArtifacts filter = new FilterArtifacts();
//		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
		filter.addFilter(new TypeFilter("jar", ""));

		// start with all artifacts.
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = project.getArtifacts();

		// perform filtering
		try {
			artifacts = filter.filter(artifacts);
		} catch (ArtifactFilterException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return artifacts;
	}

	protected Set<Artifact> getNPANDAYDependencySets()
			throws MojoExecutionException {
		FilterArtifacts filter = new FilterArtifacts();
		// Cannot do this filter in maven3 as it blocks classifiers - works in maven 2.
		// filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
		filter.addFilter(new TypeFilter(
				"dotnet-library,dll,dotnet-library-config,dll.config,dotnet-executable,exe,dotnet-executable-config,exe.config",
				""));

		// start with all artifacts.
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = project.getArtifacts();

		// perform filtering
		try {
			artifacts = filter.filter(artifacts);
		} catch (ArtifactFilterException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return artifacts;
	}

	protected Set<Artifact> getWixDependencySets() throws MojoExecutionException {
		FilterArtifacts filter = new FilterArtifacts();
		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
		filter.addFilter(new TypeFilter("wixlib,msm,msp,msi,bundle", null));
		filter.addFilter(new ClassifierFilter( "x86,x64,intel", null){
		    /*
		     * (non-Javadoc)
		     * 
		     * @see org.apache.maven.plugin.dependency.utils.filters.AbstractArtifactFeatureFilter#compareFeatures(String,String)
		     */
	
		    protected boolean compareFeatures( String lhs, String rhs )
		    {
		        return lhs == null || lhs.startsWith( rhs );
		    }
		} );
		
		// start with all artifacts.
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = project.getArtifacts();
	
		// perform filtering
		try {
			artifacts = filter.filter(artifacts);
		} catch (ArtifactFilterException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	
		return artifacts;
	}

	protected File wixUnpackDirectory(Artifact wixArtifact) {
		return new File(unpackDirectory, wixArtifact.getGroupId() + "-" + wixArtifact.getArtifactId() + "-" + wixArtifact.getBaseVersion() );
	}

	public static final String getPrimaryCulture(String culturespec) {
		if (null != culturespec)
			return culturespec.split(";")[0];
		return culturespec;
	}

	/**
	 * Copied from Maven-dependency-plugin Returns <code>true</code> if the artifact has a file.
	 * 
	 * @param artifact
	 *            the artifact (may be null)
	 * @return <code>true</code> if and only if the artifact is non-null and has a file.
	 */
	private static boolean hasFile(Artifact artifact) {
		return artifact != null && artifact.getFile() != null && artifact.getFile().isFile();
	}

	/**
	 * Copied from Maven-dependency-plugin Null-safe compare of two artifacts based on groupId, artifactId, version, type and classifier.
	 * 
	 * @param a
	 *            the first artifact.
	 * @param b
	 *            the second artifact.
	 * @return <code>true</code> if and only if the two artifacts have the same groupId, artifactId, version, type and classifier.
	 */
	private static boolean equals(Artifact a, Artifact b) {
		return a == b || !(a == null || b == null) 
				&& StringUtils.equals(a.getGroupId(), b.getGroupId())
				&& StringUtils.equals(a.getArtifactId(), b.getArtifactId())
				&& StringUtils.equals(a.getVersion(), b.getVersion())
				&& StringUtils.equals(a.getType(), b.getType())
				&& StringUtils.equals(a.getClassifier(), b.getClassifier());
	}

	protected String getRelative( File target ) {
		try {
			String relPath = getRelativePath( relativeBase.getCanonicalPath(), target );
			if( relPath.length() < target.getAbsolutePath().length() )
				return relPath;
		} catch (IOException ex) {
		}
		return target.getPath();
	}
	
	/**
	 * Based on Maven-dependency-plugin AbstractFromConfigurationMojo.
	 * 
	 * Resolves the Artifact from the remote repository if necessary. If no version is specified, it will be retrieved from the dependency list or
	 * from the DependencyManagement section of the pom.
	 * 
	 * @param artifactItem
	 *            containing information about artifact from plugin configuration.
	 * @return Artifact object representing the specified file.
	 * @throws MojoExecutionException
	 *             with a message if the version can't be found in DependencyManagement.
	 */
	protected Set<Artifact> getRelatedArtifacts(Artifact artifactItem, String arch, String culture)
			throws MojoExecutionException {

		Set<Artifact> artifactSet = new HashSet<Artifact>();

		// Map managedVersions = createManagedVersionMap( factory, project.getId(), project.getDependencyManagement() );
		VersionRange vr;
		try {
			vr = VersionRange.createFromVersionSpec(artifactItem.getVersion());
		} catch (InvalidVersionSpecificationException e1) {
			vr = VersionRange.createFromVersion(artifactItem.getVersion());
		}

		if (PACK_LIB.equalsIgnoreCase(artifactItem.getType()) || PACK_MERGE.equalsIgnoreCase(artifactItem.getType())) {
			boolean hasSomething = true;
			// even if this module has culture it's base modules may be neutral
			try {
				String classifier = arch + "-" + "neutral";
				getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), artifactItem.getType(), artifactSet, vr, classifier);
			} catch (MojoExecutionException e) {
				if (culture == null)
					throw e;
				hasSomething = false;
			}

			if (culture != null) {
				try {
					String classifier = arch + "-" + getPrimaryCulture(culture);
					getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), artifactItem.getType(), artifactSet, vr, classifier);
				} catch (MojoExecutionException e) {
					if (hasSomething == false)
						throw e;
				}
			}
		}

		// list out all the dependencies with their classifiers
//		if ("msi".equalsIgnoreCase(artifactItem.getType()) ) {
//			boolean hasSomething = true;
//			// even if this module has culture it's base modules may be neutral
//			try {
//				String classifier = arch + "-" + "neutral";
//				getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), artifactItem.getType(), artifactSet, vr, classifier);
//			} catch (MojoExecutionException e) {
//				if (culture == null)
//					throw e;
//				hasSomething = false;
//			}
//
//			if (culture != null) {
//				try {
//					String classifier = arch + "-" + getPrimaryCulture(culture);
//					getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), artifactItem.getType(), artifactSet, vr, classifier);
//				} catch (MojoExecutionException e) {
//					if (hasSomething == false)
//						throw e;
//				}
//			}
//		}		
//		
		// else if ("nar".equalsIgnoreCase(artifactItem.getType())) {
		// get one of both 32 & 64 bit... how do we tell whats there to use?
		// go through nar
		// for (String arch : getPlatforms()) {
		// for (String culture : cultures) {
		// String culture = null;
		// String classifier = arch + "-" + (culture == null ? "neutral" : culture);

		// getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), artifactItem.getType(), artifactSet, vr, "x86-Windows-msvc-shared");
		// }
		// }
		return artifactSet;
	}

	/**
	 * Returns a relative path for the targetFile relative to the base
	 * directory. - copied from Ant CPPTasks
	 * 
	 * @param base
	 *            base directory as returned by File.getCanonicalPath()
	 * @param targetFile
	 *            target file
	 * @return relative path of target file. Returns targetFile if there were no
	 *         commonalities between the base and the target
	 * 
	 */
	public static String getRelativePath(final String base,
			final File targetFile) {
		try {
			//
			// remove trailing file separator
			//
			String canonicalBase = base;
			if (base.charAt(base.length() - 1) != File.separatorChar) {
				canonicalBase = base + File.separatorChar;
			}
			//
			// get canonical name of target
			//
			String canonicalTarget;
			// if (System.getProperty("os.name").equals("OS/400"))
			// canonicalTarget = targetFile.getPath();
			// else
			canonicalTarget = targetFile.getCanonicalPath();
			if (canonicalBase.startsWith(canonicalTarget + File.separatorChar)) {
				canonicalTarget = canonicalTarget + File.separator;
			}
			if (canonicalTarget.equals(canonicalBase)) {
				return ".";
			}
			//
			// see if the prefixes are the same
			//
			if (canonicalBase.substring(0, 2).equals("\\\\")) {
				//
				// UNC file name, if target file doesn't also start with same
				// server name, don't go there
				int endPrefix = canonicalBase.indexOf('\\', 2);
				String prefix1 = canonicalBase.substring(0, endPrefix);
				String prefix2 = canonicalTarget.substring(0, endPrefix);
				if (!prefix1.equals(prefix2)) {
					return canonicalTarget;
				}
			} else {
				if (canonicalBase.substring(1, 3).equals(":\\")) {
					int endPrefix = 2;
					String prefix1 = canonicalBase.substring(0, endPrefix);
					String prefix2 = canonicalTarget.substring(0, endPrefix);
					if (!prefix1.equals(prefix2)) {
						return canonicalTarget;
					}
				} else {
					if (canonicalBase.charAt(0) == '/') {
						if (canonicalTarget.charAt(0) != '/') {
							return canonicalTarget;
						}
					}
				}
			}
			char separator = File.separatorChar;
			int lastCommonSeparator = -1;
			int minLength = canonicalBase.length();
			if (canonicalTarget.length() < minLength) {
				minLength = canonicalTarget.length();
			}
			//
			// walk to the shorter of the two paths
			// finding the last separator they have in common
			for (int i = 0; i < minLength; i++) {
				if (canonicalTarget.charAt(i) == canonicalBase.charAt(i)) {
					if (canonicalTarget.charAt(i) == separator) {
						lastCommonSeparator = i;
					}
				} else {
					break;
				}
			}
			StringBuffer relativePath = new StringBuffer(50);
			//
			// walk from the first difference to the end of the base
			// adding "../" for each separator encountered
			//
			for (int i = lastCommonSeparator + 1; i < canonicalBase.length(); i++) {
				if (canonicalBase.charAt(i) == separator) {
					if (relativePath.length() > 0) {
						relativePath.append(separator);
					}
					relativePath.append("..");
				}
			}
			if (canonicalTarget.length() > lastCommonSeparator + 1) {
				if (relativePath.length() > 0) {
					relativePath.append(separator);
				}
				relativePath.append(canonicalTarget.substring(lastCommonSeparator + 1));
			}
			return relativePath.toString();
		} catch (IOException ex) {
		}
		return targetFile.toString();
	}
}
