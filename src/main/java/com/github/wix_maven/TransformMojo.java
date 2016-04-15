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
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Reference - 
 * transform between different locales
 * torch.exe -p -t language SampleMulti.msi Sample_Hu-hu.msi -out hu-hu.mst
 * torch.exe -p -t language SampleMulti.msi Sample_Fr-fr.msi -out fr-fr.mst
 */
/**
 * Goal which executes WiX torch to create diff files - mst, cab
 */
@Mojo( name = "transform", requiresProject= true, defaultPhase=LifecyclePhase.COMPILE )
public class TransformMojo extends AbstractTorchMojo {

	public TransformMojo() {
	}

	@Override
	protected void addValidationOptions(Commandline cl) {
		cl.addArguments(new String[] { "-t", "language" });
	}

	@Override
	protected String torchOutputExtension() {
		return "mst";
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		File torchTool = validateTool();

		// TODO: add auto ident cultures?
		// TODO: add handling for msp/burn
		if (localeList.size() < 2 
				|| ! ( ML_TRANSFORM.equalsIgnoreCase(mergeLevel) || ML_REPACK.equalsIgnoreCase(mergeLevel) )
			)
			return;

		Set<String> subcultures = alternateCulturespecs();
		String base = baseCulturespec();

		String extension = "mst";
		for (String arch : getPlatforms()) {
			File baseInputFile = getOutput(arch, base, getPackageOutputExtension());

			for (String culture : subcultures) {

				File archInputFile = getOutput(arch, culture, getPackageOutputExtension());
				File archOutputFile = getOutput(arch, culture, extension);

				torch(torchTool, baseInputFile, archInputFile, archOutputFile);
			}
		}
	}
}
