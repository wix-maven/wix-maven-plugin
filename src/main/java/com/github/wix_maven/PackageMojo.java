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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * Jars up the files associated with the msi/installbundle such as cabs.
 */
@Mojo( name = "package", requiresProject= true, defaultPhase=LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractPackageable {

	/**
	 * To look up Archiver/UnArchiver implementations
	 */
	//( role=org.codehaus.plexus.archiver.manager.ArchiverManager.class )
	@Component
	private ArchiverManager archiverManager;

	/**
	 * Used for attaching the artifact in the project
	 */
	//( role=org.apache.maven.project.MavenProjectHelper.class )
	@Component
	private MavenProjectHelper projectHelper;

	// TODO: work out what to package
	// TODO: variations for multi culture
	// TODO: work out how we should package - many different parts now, should the be allowed separately 
	public final void execute() throws MojoExecutionException, MojoFailureException {

		if ( skip )
		{
			if( verbose )
				getLog().info( getClass().getName() + " skipped" );
			return;
		}

		defaultLocale();

		String[] cabs = getExternalCabs( );
		File cabsCollection = new File(project.getBuild().getOutputDirectory(), "cabs");
		if( cabs.length > 0 && !cabsCollection.exists() ) {
				cabsCollection.mkdirs();
		}
		// project.getArtifact().is();
		// mergeLevel(culture== null ? "neutral" : culture);
		File archOutputFile;
		for (String arch : getPlatforms()) {
		
			String baseCulturespec = baseCulturespec();
			String baseClassifier = arch + "-" + (baseCulturespec == null ? "neutral" : getPrimaryCulture(baseCulturespec));

			if( ! ML_REPACK.equalsIgnoreCase(mergeLevel) || packLevel.contains(PL_PACKAGE) ){ // none,trans need to include base, full include every
				archOutputFile = getOutput(arch, baseCulturespec, getPackageOutputExtension());
				projectHelper.attachArtifact(project, getPackaging(), baseClassifier, archOutputFile);
				if( packLevel.contains(PL_CULTURE_CAB) ){
					packageCabs(cabs, arch, outputDirectory, baseClassifier, cabsCollection, null);
				}
			}
			
			if( packLevel.contains(PL_WIXPDB) ){
				File basePDBOutputFile = getOutput(arch, baseCulturespec, "wixpdb");
				if (basePDBOutputFile.exists())
					projectHelper.attachArtifact(project, "wixpdb", baseClassifier, basePDBOutputFile);
			}
			
			if( packLevel.contains(PL_CACHED_CAB) ){
				packageCabs(cabs, arch, cabCacheDirectory, arch, cabsCollection, null);
			}

			// can't repack anything but an msi it seems
			if( ML_REPACK.equalsIgnoreCase(mergeLevel) && PACK_INSTALL.equalsIgnoreCase(getPackaging()) ){
				String repackClassifier =  arch + "-" + "multi";
				if( packLevel.contains(PL_DEFAULT) ){
					archOutputFile = getOutput(arch, null, getPackageOutputExtension());
					projectHelper.attachArtifact( project, getPackaging(), repackClassifier, archOutputFile );
				}
				if( packLevel.contains(PL_CULTURE_CAB) || packLevel.contains(PL_DEFAULT) ){
					packageCabs(cabs, arch, outputDirectory, repackClassifier, cabsCollection, null);
				}
			}
			
			if ( ! ML_BASE.equalsIgnoreCase(mergeLevel) 
					&& ( PACK_INSTALL.equalsIgnoreCase(getPackaging()) || PACK_PATCH.equalsIgnoreCase(getPackaging()) ) ) {

				// if build includes cultures
				for (String culturespec : alternateCulturespecs()) {
					String classifier = arch + "-" + getPrimaryCulture(culturespec);
					
					if( packLevel.contains(PL_CULTURE_CAB) ){
						packageCabs(cabs, arch, outputDirectory, classifier, cabsCollection, culturespec);
					}
					
					if( packLevel.contains(PL_WIXPDB) ){
						File archPDBOutputFile = getOutput(arch, culturespec, "wixpdb");
						if (archPDBOutputFile.exists())
							projectHelper.attachArtifact(project, "wixpdb", classifier, archPDBOutputFile);
					}

					// if capture transforms...
					if( ML_REPACK.equalsIgnoreCase(mergeLevel) && packLevel.contains(PL_MST) 
						|| ML_TRANSFORM.equalsIgnoreCase(mergeLevel) && ( packLevel.contains(PL_DEFAULT) || packLevel.contains(PL_MST))
						 )	{
							File archMSTOutputFile = getOutput(arch, culturespec, "mst");
							if (archMSTOutputFile.exists())
								projectHelper.attachArtifact(project, "mst", classifier, archMSTOutputFile);
							else 
								getLog().warn(String.format("Missing expected mst file for architecture", archMSTOutputFile, culturespec));
					} 
					
					// all the msi/msp cultures
					if( packLevel.contains(PL_PACKAGE) 
							|| ( ML_DEFAULT.equalsIgnoreCase(mergeLevel) && packLevel.contains(PL_DEFAULT) ) 
						) {
						archOutputFile = getOutput(arch, culturespec, getPackageOutputExtension());
						if (archOutputFile.exists())
							projectHelper.attachArtifact(project, getPackaging(), classifier, archOutputFile);
						else 
							getLog().warn(String.format("Missing expected msi file for architecture", getPackaging(), archOutputFile, culturespec));
					}
				}
			}
		}
	}

	private void packageCabs(String[] cabs, String arch, File sourceDir, String classifierBase, File cabsCollection, String culturespec ) {
		File classifiedCabsCollection = new File( cabsCollection, classifierBase);
		if( !classifiedCabsCollection.exists() )
			classifiedCabsCollection.mkdirs();
		
		for (String cab : cabs) {
			if( cab.trim().isEmpty() ) continue;
			File archCabFile = new File( getOutputPath(sourceDir, arch, culturespec), cab.trim() );
			if( ! archCabFile.exists() || ! archCabFile.isFile() )
				getLog().warn("Packing " + classifierBase + " didn't find " + archCabFile );
			else {
                // destination = destination.getParentFile();
                try {
					FileUtils.copyFileToDirectory( archCabFile, classifiedCabsCollection );
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					getLog().warn("Packing " + classifierBase + " file " + archCabFile );
					// TODO: resolve if we should raise this as a failure/excution exception or just ignore.
					//throw new MojoExecutionException( "NAR: could not copy include files", e );
				}

				// As seperate cab files... made a mess of file names :(
//				projectHelper.attachArtifact( project, "cab", classifierBase + "-" + archCabFile, archCabFile );
			}
		}
	}

}
