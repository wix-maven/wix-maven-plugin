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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Using insignia tool to inscribe each msi with signature details of external cabs
 * ie. insignia -im setup.msi
 *  
 *  TODO: support writing the inscribed msi to a different location/name
 *  
 * @goal inscribe
 * @phase prepare-package
 * @requiresProject true
 */
public class InscribeMojo extends AbstractInsigniaMojo {

	/**
	 * Indicate if the msi/msp cabs will be signed and so should be inscribed with signatures. 
	 * 
	 * @parameter expression="${wix.inscribePackage}" default-value="false"
	 */
	protected boolean inscribePackage;
	
	/**
	 * TODO: Indicate if the msi/msp should be inscribed with signatures from cabs in the cab cache 
	 * 
	 * @parameter expression="${wix.inscribeUsingCabCache}" default-value="false"
	 */
	// protected boolean inscribeUsingCabCache;
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		if( !inscribePackage )
		{
			return;
		}
		
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		File torchTool = validateTool();

		// TODO: add auto ident cultures?

		for (String arch : getPlatforms()) {
			for (String culture : culturespecs()) {

				File archOutputFile = getOutput(arch, culture, getPackageOutputExtension());

				getLog().info(" -- Inscribing : " + archOutputFile.getPath());

				// first... if desired, re copy cabs from cache as there is no option to use them instead of the co-located cabs  
				Commandline cl = insignia(torchTool);
				cl.addArguments(new String[] { "-im", archOutputFile.getAbsolutePath() });

				insignia(cl);
			}
		}
	}

}
