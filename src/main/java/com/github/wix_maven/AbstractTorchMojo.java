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
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;

/**
 * Abstract Goal which executes WiX torch to create diff files - mst, wixmst
 */
public abstract class AbstractTorchMojo extends AbstractPackageable {

	/**
	 * Preserve unmodified content in the output.
	 * 
	 * @parameter default-value="true"
	 */
	private boolean preserveUnmodified;

	protected void addTorchOptions(Commandline cl) {
		if (preserveUnmodified)
			cl.addArguments(new String[] { "-p" });
	}

	protected abstract void addValidationOptions(Commandline cl);

	protected abstract String torchOutputExtension();

	/**
	 * Prepare and execute torch command line tool
	 *  
	 * @param torchTool
	 * @param baseInputFile
	 * @param archInputFile
	 * @param archOutputFile
	 * @throws MojoExecutionException
	 */
	protected void torch(File torchTool, File baseInputFile, File archInputFile, File archOutputFile) throws MojoExecutionException {
		getLog().info(" -- Transform : " + archOutputFile.getPath());
		Commandline cl = new Commandline();

		cl.setExecutable(torchTool.getAbsolutePath());
		// cl.setWorkingDirectory(wxsInputDirectory);
		addToolsetGeneralOptions(cl);

		addTorchOptions(cl);
		addValidationOptions(cl);
		addWixExtensions(cl);
		cl.addArguments(new String[] { baseInputFile.getAbsolutePath(), archInputFile.getAbsolutePath(), "-out", archOutputFile.getAbsolutePath() });
		// addOtherOptions(cl);

		torch(cl);
	}

	/**
	 * Execute the given command line parsing output for torch comments
	 * @param cl
	 * @throws MojoExecutionException
	 */
	protected void torch(Commandline cl) throws MojoExecutionException {
		try {
			if (verbose) {
				getLog().info(cl.toString());
			} else {
				getLog().debug(cl.toString());
			}

			// TODO: maybe should report or do something with return value.
			int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

				public void consumeLine(final String line) {
					// TODO: torch specific message handling
					if (line.contains(" : error ")) {
						getLog().error(line);
					} else if (line.contains(" : warning ")) {
						getLog().warn(line);
					} else if (verbose) {
						getLog().info(line);
					} else {
						getLog().debug(line);
					}
				}

			}, new StreamConsumer() {

				public void consumeLine(final String line) {
					getLog().error(line);
				}

			});

			if (returnValue != 0) {
				throw new MojoExecutionException("Problem executing torch, return code " + returnValue);
			}
		} catch (CommandLineException e) {
			// throw new MojoExecutionException( "Error running mapping-tools.",
			// e );
			throw new MojoExecutionException("Problem executing torch", e);
		}
	}

	protected File validateTool() throws MojoExecutionException {
		File torchTool = new File(toolDirectory, "/bin/torch.exe");
		if (!torchTool.exists())
			throw new MojoExecutionException("Torch tool doesn't exist " + torchTool.getAbsolutePath());
		return torchTool;
	}

}
