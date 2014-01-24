package com.github.wix_maven;

import java.io.File;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/***
 * Smoke to perform 'unit' testing of msi/msp
 * Smoke runs ICE similar to light, this goal allows a seperate execution from the linker step.
 * Optionally translate into unit test report output
 * 
 * @goal smoke
 * @phase test
 * @requiresProject true
 */
public class SmokeMojo extends AbstractPackageable {

	/**
	 * Skip running of smoke goal.
	 * 
	 * @parameter expression="${wix.skipTests}" default-value="false"
	 */
	protected boolean skipTests;
	
	/**
	 * Skip running of smoke goal.
	 * 
	 * @parameter expression="${wix.reportDirectory}" default-value="${projec.basedir}/surefire-reports"
	 */
	protected File reportDirectory;
	
	protected void addOptions(Commandline cl) throws MojoExecutionException {
		if( VALIDATE_SUPPRESS.equalsIgnoreCase(validate) 
				|| VALIDATE_LINK.equalsIgnoreCase(validate)
				){
			cl.addArguments( new String[] { "-sval" } );
		}
	}

	public void multiSmoke(File smokeTool) throws MojoExecutionException, MojoFailureException {

		defaultLocale();

		for (String arch : getPlatforms()) {
// TODO: validate all?			for (String culture : culturespecs()) {
			String culture = baseCulturespec();
			{
				File archOutputFile = getOutput(arch, culture, outputExtension());

				getLog().info(" -- Smoke testing : " + archOutputFile.getPath());
				
				Commandline cl = new Commandline();

				cl.setExecutable(smokeTool.getAbsolutePath());
				cl.setWorkingDirectory(relativeBase);
				addToolsetGeneralOptions(cl);
				addOptions(cl);
			}
		}
	}


	protected void smoke(Commandline cl) throws MojoExecutionException {
		try {
			if (verbose) {
				getLog().info(cl.toString());
			} else {
				getLog().debug(cl.toString());
			}

			// TODO: maybe should report or do something with return value.
			int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

				public void consumeLine(final String line) {
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
				throw new MojoExecutionException("Problem executing smoke, return code " + returnValue);
			}
		} catch (CommandLineException e) {
			// throw new MojoExecutionException( "Error running mapping-tools.",
			// e );
			throw new MojoExecutionException("Problem executing smoke", e);
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		if ( skip || skipTests )
		{
			if( verbose )
				getLog().info( getClass().getName() + " skipped" );
			return;
		}

		if (reportDirectory != null)
			reportDirectory.mkdirs();

		if( ! ( PACK_INSTALL.equalsIgnoreCase(getPackaging())
				|| PACK_PATCH.equalsIgnoreCase(getPackaging())
				) )
			throw new MojoFailureException("Can only smoke test .msi or .msp");

		File smokeTool = new File(toolDirectory, "/bin/smoke.exe");
		if (!smokeTool.exists())
			throw new MojoExecutionException("Smoke tool doesn't exist " + smokeTool.getAbsolutePath());

		multiSmoke(smokeTool);
	}

	/**
	 * 
	 * @return
	 */
	private String outputExtension() {
		return getPackaging();
	}

}
