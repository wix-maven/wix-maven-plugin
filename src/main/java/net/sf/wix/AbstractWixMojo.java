package net.sf.wix;

/*
 * Copyright ---
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;

public abstract class AbstractWixMojo extends AbstractMojo {

	/**
	 * Skip running of NAR plugins (any) altogether.
	 * 
	 * @parameter expression="${wix.skip}" default-value="false"
	 */
	protected boolean skip;

	/**
	 * The directory to scan for wix files.
	 * For each build type there is at least one wxs file required
	 * 
	 * @parameter default-value="${project.basedir}/src/main/wix"
	 * @required
	 */
	protected File wxsInputDirectory;

	/**
	 * WiX extensions to use
	 * 
	 * @parameter
	 */
	protected String[] extensions;

	/**
	 * Reference paths to WiX extensions to use default list containing the wixlib path only.
	 * 
	 * @parameter
	 */
	// protected String[] referencePaths;

	/**
	 * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
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
	 * @parameter expression="${wix.outputDirectory}" default-value="${project.build.directory}/wixobj/Release"
	 * @required
	 */
	protected File intDirectory;

	/**
	 * Intermediate directory - will have ${arch} appended
	 * 
	 * @parameter expression="${wix.toolsPath}" default-value="${project.build.directory}/wix-tools"
	 * @required
	 */
	protected File toolDirectory;

	/**
	 * Intermediate directory - will have ${arch} appended
	 * 
	 * @parameter expression="${wix.unpackPath}" default-value="${project.build.directory}/unpack"
	 * @required
	 */
	protected File unpackDirectory;

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
	protected String toolsPluginArtifactId = "wix-toolset";
	
	/**
	 * Group id of the toolset jar to unpack.
	 * 
	 * @parameter default-value="org.wixtoolset"
	 * @required
	 * */
	private String toolsPluginGroupId;

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
	private ArtifactRepository localRepository;

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

	public final String PACK_LIB="wixlib";
	public final String PACK_MERGE="msm";
	public final String PACK_INSTALL="msi";
	public final String PACK_PATCH="msp";
	public final String PACK_BUNDLE="bundle";

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
	
	// TODO: should be a better pattern for lookup of the tools attached to this pluggin
	protected Artifact findToolsArtifact() throws MojoExecutionException {
		// return (Artifact) mavenPlugin.getArtifactMap().get(ArtifactUtils.versionlessKey(mavenPlugin.getGroupId(), toolsId));
		if (null != pluginArtifacts) {
			for (Iterator artifactIterator = pluginArtifacts.iterator(); artifactIterator.hasNext();) {
				Artifact artifact = (Artifact) artifactIterator.next();
				if (artifact.getGroupId().equals(toolsPluginGroupId) && artifact.getArtifactId().equals(toolsPluginArtifactId)) { // && artifact.getClassifier().equals(
																												// env.arch )
					return artifact;
				}
			}
		}
		getLog().error(String.format("Tools Artifact %1$s:%2$s not found", toolsPluginGroupId, toolsPluginArtifactId));
		throw new MojoExecutionException(String.format("Unable to find %1$s dependency", toolsPluginArtifactId));
	}

	protected void unpackFileBasedResources() throws MojoExecutionException {
		getLog().debug("unpacking binaries");

		Artifact artifact = findToolsArtifact();

		getLog().debug(String.format("Using %1$s %2$s", toolsPluginArtifactId, artifact));

		File pluginJar = artifact.getFile();

		getLog().debug(String.format("Extracting %1$s to %2$s", pluginJar, toolDirectory));

		zipUnArchiver.setSourceFile(pluginJar);

		// zipUnArchiver. set 'if newer' ?
		String subfolder = "bin";
		zipUnArchiver.extract(subfolder, toolDirectory);
		// TODO: Still deciding if this content should be optional
		// if( visualStudioUse )
		// zipUnArchiver.extract( "etc", toolDirectory );

		if (!toolDirectory.exists()){
			getLog().info(String.format("Extracting %3$s %1$s to %2$s", pluginJar, toolDirectory, subfolder));
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

	protected void addWixExtensions(Commandline cl) throws MojoExecutionException {
		Set<Artifact> dependentExtensions = getExtDependencySets();
		getLog().info( "Adding "+dependentExtensions.size()+" dependentExtensions" );
		for (Artifact ext : dependentExtensions) {
			getLog().info(ext.getFile().getName());
			cl.addArguments(new String[] { "-ext", ext.getFile().getAbsolutePath() });
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
			
		outFile = new File(outFile, project.getBuild().getFinalName() + "."+ extension ); // TODO: does this nead to vary with package type? packaging
	
		return outFile;
	}

	protected File getOutputPath(File baseDir, String arch, String culture) {
		File outFile = null;
		if (baseDir == null)
			outFile = new File(arch);
		else
			outFile = new File(baseDir, arch);
	
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
		return a == b || !(a == null || b == null) && StringUtils.equals(a.getGroupId(), b.getGroupId())
				&& StringUtils.equals(a.getArtifactId(), b.getArtifactId()) && StringUtils.equals(a.getVersion(), b.getVersion())
				&& StringUtils.equals(a.getType(), b.getType()) && StringUtils.equals(a.getClassifier(), b.getClassifier());
	}

}
