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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Goal to validate the configuration
 * 
 * @goal validate
 * @phase validate
 */
public class ValidateMojo extends AbstractPackageable {

	/**
	 * Used for attaching the artifact in the project
	 * 
	 * @component
	 * @required
	 * @readonly
	 */
	private MavenProjectHelper projectHelper;
	
	public void execute() throws MojoExecutionException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		findToolsArtifacts();
		
		if (!wxsInputDirectory.exists()) {
			getLog().info("wxs input directory not found, nothing for candle to process");
			// TODO: check for other inputs to light
		}
		// TODO: check for lit
		
		
// attach artifacts for reactor dependency resolution
// considering some artifact types, like wixlib, as folders so that link step (lit) doesn't need to be run to test later compile steps... Run it as part of packaging. 
//
//		File archOutputFile;
//		for (String arch : getPlatforms()) {
//		
//			String baseCulturespec = baseCulturespec();
//			String baseClassifier = arch + "-" + (baseCulturespec == null ? "neutral" : getPrimaryCulture(baseCulturespec));
//
//			if( ! ML_REPACK.equalsIgnoreCase(mergeLevel) || packLevel.contains(PL_PACKAGE) ){ // none,trans need to include base, full include every
//				if( ! PACK_LIB.equalsIgnoreCase(getPackaging()) ){
//					archOutputFile = getOutput(arch, baseCulturespec, getPackageOutputExtension());
//				} else { 
//					archOutputFile = getArchIntDirectory(arch);
//				}
//				projectHelper.attachArtifact(project, getPackaging(), baseClassifier, archOutputFile);
//			}
//			
//			if( packLevel.contains(PL_WIXPDB) && ! PACK_LIB.equalsIgnoreCase(getPackaging()) ){
//				File basePDBOutputFile = getOutput(arch, baseCulturespec, "wixpdb");
//				projectHelper.attachArtifact(project, "wixpdb", baseClassifier, basePDBOutputFile);
//			}
//			
//			// can't repack anything but an msi it seems
//			if( ML_REPACK.equalsIgnoreCase(mergeLevel) && PACK_INSTALL.equalsIgnoreCase(getPackaging()) ){
//				String repackClassifier =  arch + "-" + "multi";
//				if( packLevel.contains(PL_DEFAULT) ){
//					archOutputFile = getOutput(arch, null, getPackageOutputExtension());
//					projectHelper.attachArtifact( project, getPackaging(), repackClassifier, archOutputFile );
//				}
//			}
//			
//			if ( baseCulturespec != null
//					&& ! ML_BASE.equalsIgnoreCase(mergeLevel) 
//					&& ( PACK_INSTALL.equalsIgnoreCase(getPackaging()) || PACK_PATCH.equalsIgnoreCase(getPackaging()) ) ) {
//
//				// if build includes cultures
//				for (String culturespec : alternateCulturespecs()) {
//					String classifier = arch + "-" + getPrimaryCulture(culturespec);
//					
//					if( packLevel.contains(PL_WIXPDB) ){
//						File archPDBOutputFile = getOutput(arch, culturespec, "wixpdb");
//						projectHelper.attachArtifact(project, "wixpdb", classifier, archPDBOutputFile);
//					}
//
//					// if capture transforms...
//					if( ML_REPACK.equalsIgnoreCase(mergeLevel) && packLevel.contains(PL_MST) 
//						|| ML_TRANSFORM.equalsIgnoreCase(mergeLevel) && ( packLevel.contains(PL_DEFAULT) || packLevel.contains(PL_MST))
//						 )	{
//							File archMSTOutputFile = getOutput(arch, culturespec, "mst");
//							projectHelper.attachArtifact(project, "mst", classifier, archMSTOutputFile);
//					} 
//					
//					// all the msi/msp cultures
//					if( packLevel.contains(PL_PACKAGE) 
//							|| ( ML_DEFAULT.equalsIgnoreCase(mergeLevel) && packLevel.contains(PL_DEFAULT) ) 
//						) {
//						archOutputFile = getOutput(arch, culturespec, getPackageOutputExtension());
//						projectHelper.attachArtifact(project, getPackaging(), classifier, archOutputFile);
//					}
//				}
//			}
//		}
	}

}