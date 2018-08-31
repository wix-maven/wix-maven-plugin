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

import java.io.File;
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
@Mojo( name = "light", requiresProject= true, defaultPhase=LifecyclePhase.COMPILE, requiresDependencyResolution=ResolutionScope.COMPILE )
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
			cl.addArguments(new String[] { "-cultures:" + culture });
	}

	private void addReuseCabOptions(Commandline cl, String arch) {
		// TODO: culture might be a list of primary and fallback cultures
		if (reuseCabs) {
			File resolvedCabCacheDirectory = new File(cabCacheDirectory, arch); // TODO: provide pattern replace
			cl.addArguments(new String[] { "-reusecab", "-cc", resolvedCabCacheDirectory.getAbsolutePath() + "\\" });
			if (!resolvedCabCacheDirectory.exists())
				resolvedCabCacheDirectory.mkdirs();
		}
	}

	protected void addValidationOptions(Commandline cl) throws MojoExecutionException {
		if( VALIDATE_SUPPRESS.equalsIgnoreCase(validate) 
				|| VALIDATE_UNIT.equalsIgnoreCase(validate)
				){
			cl.addArguments( new String[] { "-sval" } );
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

		File linkTool = new File(toolDirectory, "/bin/light.exe");
		if (!linkTool.exists())
			throw new MojoExecutionException("Light tool doesn't exist " + linkTool.getAbsolutePath());

		defaultLocale();

		Set<Artifact> wixDependencies = getWixDependencySets();
		
		for (Iterator<Artifact> i = wixDependencies.iterator(); i.hasNext();) {
			Artifact libGroup = i.next();
			if( !libGroup.hasClassifier() ){
				getLog().debug("Attempting to unpack resources for " + libGroup.toString());
				unpackResource(libGroup);
			}
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
					Set<File> objects = scanner.getIncludedSources(getArchIntDirectory(arch,culture), archOutputFile);
					// **/{arch}/*.wixlib
					// **/{arch}/*.wixobj

					
					Set<String> allSourceRoots = new LinkedHashSet<String>(fileSourceRoots); // we need this to keep order for more specific locale
					List<File> locales = null;																		// coming first
					if (wxlInputDirectory.exists()) {
						// culture might be a list of primary and fallback cultures
						// include all the wxl files and the -culture option will sort them out.
						// include the files from only the primary culture and the nuetral.
						scanner = new SimpleSourceInclusionScanner(getLocaleIncludes(), getLocaleExcludes());
						scanner.addSourceMapping(new SingleTargetSourceMapping(".wxl", archOutputFile.getName()));
						// The order of -loc is currently (wix 3.7) important due to an issue with UI element
						locales = asSortedList(scanner.getIncludedSources(wxlInputDirectory, archOutputFile));

						addBinderOption(wxlInputDirectory, culture, allSourceRoots);
					}
					if( unpackDirectory.exists() ){
						allSourceRoots.add(unpackDirectory.getAbsolutePath());
					}

					Set<String> objectFiles = new HashSet<String>();
					if (!objects.isEmpty()) {
						for (Iterator<File> i = objects.iterator(); i.hasNext();) {
							objectFiles.add(getRelative( i.next() ) );
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
								objectFiles.add(getRelative( lib.getFile() ));
							}
						}
					}

					if (!objectFiles.isEmpty()) {
						Commandline cl = new Commandline();

						cl.setExecutable(linkTool.getAbsolutePath());
						cl.setWorkingDirectory(relativeBase);//						wxsInputDirectory
						addToolsetGeneralOptions(cl);

						if (bindFiles)
							cl.addArguments(new String[] { "-bf" });

						cl.addArguments(new String[] { "-out", archOutputFile.getAbsolutePath() });

						addOptions(cl, allSourceRoots);
						addValidationOptions(cl);
						addLocaleOptions(cl, culture);

						if (locales != null) {
							for (Iterator<File> i = locales.iterator(); i.hasNext();) {
								cl.addArguments(new String[] { "-loc", getRelative( i.next() ) });
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
			getLog().debug(String.format("Warning: %1$s:%2$s resources not unpacked", libGroup.getGroupId(), libGroup.getArtifactId()));
		}
	}

	private void unpackResource(Artifact libGroup) {
		// TODO: support compile if( libGroup.getFile().isFile() )
		zipUnArchiver.setSourceFile(libGroup.getFile());
		File resUnpackDirectory = wixUnpackDirectory(libGroup);
//		zipUnArchiver.extract(subfolder, resUnpackDirectory);

		if( !resUnpackDirectory.exists() )
			resUnpackDirectory.mkdirs();

		zipUnArchiver.setDestDirectory( resUnpackDirectory );
		IncludeExcludeFileSelector[] selectors = new IncludeExcludeFileSelector[] { new IncludeExcludeFileSelector() };
		selectors[0].setIncludes( "wix-locale/**,cabs/**".split( "," ) );
		zipUnArchiver.setFileSelectors( selectors );
		zipUnArchiver.extract();
	}
}
