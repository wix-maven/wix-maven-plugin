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

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Attach the (signed) bundle engine back to the bundle.
 * TODO: might be more appropriate to use custom phase.
 *  insignia -ab engine.exe bundle.exe -o bundle.exe
 *  ... sign bundle.exe
 */
@Mojo( name = "attach-bundle", requiresProject= true, defaultPhase=LifecyclePhase.PREPARE_PACKAGE )
public class AttachBundleEngineMojo extends AbstractInsigniaMojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
		}

		if( !signBundleEngine ){
			// TODO: verbose?  may be accidental, warning?
			getLog().info("Skipping bundle engine attach");
			return;
		}
		
		if( !PACK_BUNDLE.equalsIgnoreCase(getPackaging()) )
			getLog().warn("Attempting to detach bundle engine from " + getPackaging() );

		File torchTool = validateTool();
		defaultLocale();

		for (String arch : getPlatforms()) {
			for (String culture : culturespecs()) {

				File archOutputFile = getOutput(arch, culture, getPackageOutputExtension() );

				// TODO: allow for changing name of the output bundle
				getLog().info(" -- Attaching bundle to : " + archOutputFile.getPath());

				Commandline cl = insignia(torchTool);

				File resovledBundleEngineFile = new File( getOutputPath(bundleEnginePath, arch, culture), bundleEngineName );
				
				cl.addArguments(new String[] { "-ab", resovledBundleEngineFile.getAbsolutePath(), archOutputFile.getAbsolutePath(), "-out", archOutputFile.getAbsolutePath() });

				insignia(cl);
			}
		}
	}
}
