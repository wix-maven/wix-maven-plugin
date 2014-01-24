package com.github.wix_maven;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Goal that unpacks the project dependencies from the repository to a defined location.
 * 
 * @goal unpack-dependencies
 * @phase process-sources
 * @requiresProject true
 * @requiresDependencyResolution test
 */
// @Mojo( name = "unpack-dependencies", requiresDependencyResolution = ResolutionScope.TEST,defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe
// = true )
public class UnpackDependenciesMojo extends AbstractWixMojo {
	/**
	 * A comma separated list of file patterns to include when unpacking the artifact.<br>
	 * i.e. <code>**\/*.xml,**\/*.properties</code><br>
	 * NOTE: Excludes patterns override the includes.<br>
	 * (component code = <code>return isIncluded( name ) AND !isExcluded( name );</code>)
	 * 
	 * @parameter
	 */
	// @Parameter( property = "mdep.unpack.includes" )
	private String includes;

	/**
	 * A comma separated list of file patterns to exclude when unpacking the artifact.<br>
	 * i.e. <code>**\/*.xml,**\/*.properties</code><br>
	 * NOTE: Excludes patterns override the includes.<br>
	 * (component code = <code>return isIncluded( name ) AND !isExcluded( name );</code>)
	 * 
	 * @parameter
	 */
	// @Parameter( property = "mdep.unpack.excludes" )
	private String excludes;

	/**
	 * Artifact collector, needed to resolve dependencies.
	 * 
	 * @component role="org.apache.maven.artifact.resolver.ArtifactCollector"
	 * @required
	 * @readonly
	 */
	// @Component( role = ArtifactCollector.class )
	protected ArtifactCollector artifactCollector;

	/**
	 * Main entry into mojo. This method gets the dependencies and iterates through each one passing it to DependencyUtil.unpackFile().
	 * 
	 * @throws MojoExecutionException
	 *             with a message if an error occurs.
	 * @see #getDependencies
	 * @see DependencyUtil#unpackFile(Artifact, File, File, ArchiverManager, Log)
	 */
	public void execute() throws MojoExecutionException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		Set<Artifact> artifacts = getDependencySets();
		getLog().info("WiX dependencies");
		for (Artifact artifact : artifacts) {
			getLog().info(artifact.getFile().getName());
			Set<Artifact> depArtifacts = getRelatedArtifacts(artifact);
			for (Artifact depArtifact : depArtifacts) {
				getLog().info(depArtifact.getFile().getName());
			}
		}

		// DependencyStatusSets dss = getDependencySets( );
		//
		// for ( Artifact artifact : dss.getResolvedDependencies() )
		// {
		// File destDir;
		// destDir = DependencyUtil.getFormattedOutputDirectory( useSubDirectoryPerScope, useSubDirectoryPerType,
		// useSubDirectoryPerArtifact, useRepositoryLayout,
		// stripVersion, outputDirectory, artifact );
		// unpack( artifact, destDir, getIncludes(), getExcludes() );
		// DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler( artifact, this.markersDirectory );
		// handler.setMarker();
		// }
		//
		// for ( Artifact artifact : dss.getSkippedDependencies() )
		// {
		// getLog().info( artifact.getFile().getName() + " already exists in destination." );
		// }
	}

	/**
	 * Method creates filters and filters the projects dependencies. This method also transforms the dependencies if classifier is set. The
	 * dependencies are filtered in least specific to most specific order
	 * 
	 * @param stopOnFailure
	 * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
	 * @throws MojoExecutionException
	 */
	protected Set<Artifact> getDependencySets() throws MojoExecutionException {
		// add filters in well known order, least specific to most specific
		FilterArtifacts filter = new FilterArtifacts();

		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));

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

		filter.addFilter(new TypeFilter("wixlib,msi,nar", ""));

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

		// transform artifacts if classifier is set
		// DependencyStatusSets status = null;
		// if ( StringUtils.isNotEmpty( classifier ) )
		// {
		// status = getClassifierTranslatedDependencies( artifacts, true );
		// }
		// else
		// {
		// status = filterMarkedDependencies( artifacts );
		// }
		//
		// return status;
	}

	/**
	 * @return Returns a comma separated list of excluded items
	 */
	public String getExcludes() {
		// return DependencyUtil.cleanToBeTokenizedString( this.excludes );
		return this.excludes;
	}

	/**
	 * @param excludes
	 *            A comma separated list of items to exclude i.e. <code>**\/*.xml, **\/*.properties</code>
	 */
	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}

	/**
	 * @return Returns a comma separated list of included items
	 */
	public String getIncludes() {
		// return DependencyUtil.cleanToBeTokenizedString( this.includes );
		return this.includes;
	}

	/**
	 * @param includes
	 *            A comma separated list of items to include i.e. <code>**\/*.xml, **\/*.properties</code>
	 */
	public void setIncludes(String includes) {
		this.includes = includes;
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
	protected Set<Artifact> getRelatedArtifacts(Artifact artifactItem) throws MojoExecutionException {
		Artifact artifact;
		Set<Artifact> artifactSet = new HashSet<Artifact>();

		// Map managedVersions = createManagedVersionMap( factory, project.getId(), project.getDependencyManagement() );
		VersionRange vr;
		try {
			vr = VersionRange.createFromVersionSpec(artifactItem.getVersion());
		} catch (InvalidVersionSpecificationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			vr = VersionRange.createFromVersion(artifactItem.getVersion());
		}

//		if ("msi".equalsIgnoreCase(artifactItem.getType()) || "msp".equalsIgnoreCase(artifactItem.getType())
//				|| "wixlib".equalsIgnoreCase(artifactItem.getType()) || "msm".equalsIgnoreCase(artifactItem.getType())) {
//			// artifactItem.getFile().getWixInfo();
//			// if (cultures.isEmpty())
//			// cultures.add(null);
//
//			// for (String culture : cultures) {
//
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
//					if( hasSomething == false )
//						throw e;
//				}
//			}
//
//			if ("msi".equalsIgnoreCase(artifactItem.getType()) || "msp".equalsIgnoreCase(artifactItem.getType())) {
//				String classifier = arch + "-" + (culture == null ? "neutral" : getPrimaryCulture(culture) );
//				getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), "wixpdb", artifactSet, vr, classifier);
//			}
//		}
//		if ("nar".equalsIgnoreCase(artifactItem.getType())) {
			// get one of both 32 & 64 bit... how do we tell whats there to use?
			// go through nar
//			for (String arch : getPlatforms()) {
				// for (String culture : cultures) {
//				String culture = null;
//				String classifier = arch + "-" + (culture == null ? "neutral" : culture);

//				getArtifact(artifactItem, artifactSet, vr, "x86-Windows-msvc-shared");
//			}			
//		} 
		return artifactSet;
	}

}
