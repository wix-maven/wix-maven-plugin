package net.sf.wix;

/*
 * Copyright ---
 *
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
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.compiler.util.scan.*;
import org.codehaus.plexus.compiler.util.scan.mapping.*;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Goal which executes WiX light to create a .msi file.
 * 
 * The following project dependency inclusion patterns apply<br>
 * Dependent Wixlib project 'Foo' with possible output redefined as 'bar' adds to commandline <br>
 * ${narunpack}\Foo-version\Bar.wixlib
 * 
 * @goal light
 * @phase package
 * @requiresProject true
 * @requiresDependencyResolution compile
 */
public class LightMojo extends AbstractLinker {

	/**
	 * Re use cabinet files across multiple linkages. (-reusecab)
	 * 
	 * @parameter expression="${wix.reuseCab}" default-value="false"
	 */
	private boolean reuseCabs;

	/**
	 * Location of the WiX localization files.
	 * 
	 * @parameter expression="${localizationFiles}"
	 */
	// private File[] localizationFiles;

	/**
	 * Bind files, only useful wixout format
	 * 
	 * @parameter expression="${wix.bindFiles.msi}" default-value="false"
	 */
	private boolean bindFiles;

	private void addLocaleOptions(Commandline cl, String culture) {
		// TODO: culture might be a list of primary and fallback cultures
		if (culture != null)
			cl.addArguments(new String[] { "-cultures:" + culture });
	}

	private void addReuseCabOptions(Commandline cl, String arch) {
		// TODO: culture might be a list of primary and fallback cultures
		if (reuseCabs) {
			File resolvedCabCacheDirectory = new File(cabCacheDirectory, arch); // TODO: provide pattern replace
			cl.addArguments(new String[] { "-reusecab", "-cc", resolvedCabCacheDirectory.getAbsolutePath() + "\\\\" });
			if (!resolvedCabCacheDirectory.exists())
				resolvedCabCacheDirectory.mkdirs();
		}
	}

	private String outputExtension() {
		if ("msp".equalsIgnoreCase(packaging)) { // final msp output is from pyro
			return "wixmsp";
		}
		// msi/msm extension - actual build differences are in xml
		return packaging;
	}

	protected Set<Artifact> getDependencySets() throws MojoExecutionException {
		FilterArtifacts filter = new FilterArtifacts();
		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
		filter.addFilter(new ClassifierFilter(null,"x86,x64,intel"){
		    /*
		     * (non-Javadoc)
		     * 
		     * @see org.apache.maven.plugin.dependency.utils.filters.AbstractArtifactFeatureFilter#compareFeatures(String,String)
		     */

		    protected boolean compareFeatures( String lhs, String rhs )
		    {
		        return lhs != null && lhs.startsWith( rhs );
		    }
		});
		filter.addFilter(new TypeFilter("wixlib,msi,msp", null));

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

	@Override
	protected void multilink(File toolDirectory) throws MojoExecutionException {

		File linkTool = new File(toolDirectory, "/bin/light.exe");
		if (!linkTool.exists())
			throw new MojoExecutionException("Light tool doesn't exist " + linkTool.getAbsolutePath());

		defaultLocale();

		Set<Artifact> artifacts = getDependencySets();
		
		for (Iterator<Artifact> i = artifacts.iterator(); i.hasNext();) {
			Artifact libGroup = i.next();
			getLog().debug("Attempting to unpack resources for " + libGroup.toString());
			unpackResource(libGroup);
		}
		
		for (String arch : getPlatforms()) {
			for (String culture : culturespecs()) {

				File archOutputFile = getOutput(arch, culture, outputExtension());

				getLog().info(" -- Linking : " + archOutputFile.getPath());
				try {
					// we are using source scanning to find all the files for the build - because all should be listed we don't check for just newer
					// ones.
					// TODO: add check to see if the msi is out of date compared to input files of all kinds.
					SourceInclusionScanner scanner = new SimpleSourceInclusionScanner(getIncludes(), getExcludes());
					scanner.addSourceMapping(new SingleTargetSourceMapping(".wixobj", archOutputFile.getName()));
					scanner.addSourceMapping(new SingleTargetSourceMapping(".wixlib", archOutputFile.getName()));
					@SuppressWarnings("unchecked")
					Set<File> objects = scanner.getIncludedSources(getArchIntDirectory(arch), archOutputFile);
					// **/{arch}/*.wixlib
					// **/{arch}/*.wixobj

					Set<File> locales = null;
					Set<String> allSourceRoots = new LinkedHashSet<String>(fileSourceRoots); // we need this to keep order for more specific locale
																								// coming first
					if (wxlInputDirectory.exists()) {
						// culture might be a list of primary and fallback cultures
						// include all the wxl files and the -culture option will sort them out.
						// include the files from only the primary culture and the nuetral.
						scanner = new SimpleSourceInclusionScanner(getLocaleIncludes(), getLocaleExcludes());
						scanner.addSourceMapping(new SingleTargetSourceMapping(".wxl", archOutputFile.getName()));
						locales = scanner.getIncludedSources(wxlInputDirectory, archOutputFile);

						addBinderOption(wxlInputDirectory, culture, allSourceRoots);
					}

					Set<String> objectFiles = new HashSet<String>();
					if (!objects.isEmpty()) {
						for (Iterator<File> i = objects.iterator(); i.hasNext();) {
							objectFiles.add(i.next().getPath());
						}
					}
					for (Iterator<Artifact> i = artifacts.iterator(); i.hasNext();) {
						Artifact libGroup = i.next();
						getLog().debug(libGroup.toString());
						unpackResource(libGroup);
						if ("wixlib".equalsIgnoreCase(libGroup.getType())) {
							// try unpack resources
							addResource(libGroup, culture, allSourceRoots);

							Set<Artifact> depArtifacts = getRelatedArtifacts(libGroup, arch, culture);
							for (Iterator<Artifact> j = depArtifacts.iterator(); j.hasNext();) {
								Artifact lib = j.next();
								objectFiles.add(lib.getFile().getAbsolutePath());
							}
						}
						if ("msi".equalsIgnoreCase(libGroup.getType())
//								|| "msp".equalsIgnoreCase(libGroup.getType())
								) {
							File resUnpackDirectory = new File(unpackDirectory, libGroup.getGroupId() + "-" + libGroup.getArtifactId());
							if( resUnpackDirectory.exists() )
								allSourceRoots.add(resUnpackDirectory.getAbsolutePath());
						}
					}

					if (!objectFiles.isEmpty()) {
						Commandline cl = new Commandline();

						cl.setExecutable(linkTool.getAbsolutePath());
						cl.setWorkingDirectory(wxsInputDirectory);
						addToolsetGeneralOptions(cl);

						if (bindFiles)
							cl.addArguments(new String[] { "-bf" });

						cl.addArguments(new String[] { "-out", archOutputFile.getAbsolutePath() });

						addOptions(cl, allSourceRoots);
						addLocaleOptions(cl, culture);

						if (locales != null) {
							for (Iterator<File> i = locales.iterator(); i.hasNext();) {
								cl.addArguments(new String[] { "-loc", i.next().getPath() });
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
		File resUnpackDirectory = new File(unpackDirectory, libGroup.getGroupId() + "-" + libGroup.getArtifactId());		

		File neutralFolder = new File(resUnpackDirectory, "wix-locale");
		if (neutralFolder.exists()) {
			addBinderOption(neutralFolder, culture, allSourceRoots);
		} else {
			getLog().debug(String.format("Warning: %1$s:%2$s resources not unpacked", libGroup.getGroupId(), libGroup.getArtifactId()));
		}
	}

	private void unpackResource(Artifact libGroup) {
		// TODO: support compile if( libGroup.getFile().isFile() )
		zipUnArchiver.setSourceFile(libGroup.getFile());
		File resUnpackDirectory = new File(unpackDirectory, libGroup.getGroupId() + "-" + libGroup.getArtifactId());
//		zipUnArchiver.extract(subfolder, resUnpackDirectory);

		if( !resUnpackDirectory.exists() )
			resUnpackDirectory.mkdirs();

		zipUnArchiver.setDestDirectory( resUnpackDirectory );
		IncludeExcludeFileSelector[] selectors = new IncludeExcludeFileSelector[] { new IncludeExcludeFileSelector() };
		selectors[0].setIncludes( "wix-locale/**,cabs/**".split( "," ) );
		zipUnArchiver.setFileSelectors( selectors );
		zipUnArchiver.extract();
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
	protected Set<Artifact> getRelatedArtifacts(Artifact artifactItem, String arch, String culture) throws MojoExecutionException {

		Set<Artifact> artifactSet = new HashSet<Artifact>();

		// Map managedVersions = createManagedVersionMap( factory, project.getId(), project.getDependencyManagement() );
		VersionRange vr;
		try {
			vr = VersionRange.createFromVersionSpec(artifactItem.getVersion());
		} catch (InvalidVersionSpecificationException e1) {
			vr = VersionRange.createFromVersion(artifactItem.getVersion());
		}

		if ("wixlib".equalsIgnoreCase(artifactItem.getType()) || "msm".equalsIgnoreCase(artifactItem.getType())) {
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

}
