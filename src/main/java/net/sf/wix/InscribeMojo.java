package net.sf.wix;

import java.io.File;
import java.util.Set;

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
