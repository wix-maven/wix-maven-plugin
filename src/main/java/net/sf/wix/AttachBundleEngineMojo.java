package net.sf.wix;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Attach the (signed) bundle engine back to the bundle.
 * TODO: might be more appropriate to use custom phase.
 *  insignia -ab engine.exe bundle.exe -o bundle.exe
 *  ... sign bundle.exe
 *  			
 * @goal attach-bundle
 * @phase prepare-package
 * @requiresProject true
 */
public class AttachBundleEngineMojo extends AbstractInsigniaMojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		if( !signBundleEngine ){
			// TODO: verbose?  may be accidental, warning?
			getLog().info("Skipping bundle engine attach");
			return;
		}
		
		if( !"bundle".equalsIgnoreCase(packaging) )
			getLog().warn("Attempting to detach bundle from " + packaging );

		File torchTool = validateTool();
		defaultLocale();

		for (String arch : getPlatforms()) {
			for (String culture : culturespecs()) {

				File archOutputFile = getOutput(arch, culture, packaging );

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
