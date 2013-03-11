package net.sf.wix;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Detach the bundle engine from the bundle for signing.
 * TODO: might be more appropriate to use custom phase.
 * insignia -ib bundle.exe -o engine.exe
 * ... sign engine.exe
 *  
 * @goal detach-bundle
 * @phase process-classes
 * @requiresProject true
 */
public class DetachBundleEngineMojo extends AbstractInsigniaMojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		if( !signBundleEngine ){
			// TODO: verbose?  may be accidental, warning?
			getLog().info("Skipping bundle engine detach");
			return;
		}
		
		if( !"bundle".equalsIgnoreCase(packaging) )
			getLog().warn("Attempting to detach bundle from " + packaging );

		File torchTool = validateTool();
		defaultLocale();
		
		for (String arch : getPlatforms()) {
			for (String culture : culturespecs()) {

				File archOutputFile = getOutput(arch, culture, packaging );

				getLog().info(" -- Detaching bundle engine from : " + archOutputFile.getPath());

				Commandline cl = insignia(torchTool);
				
				File resovledBundleEnginePath = getOutputPath(bundleEnginePath, arch, culture);
				if( !resovledBundleEnginePath.exists() )
					resovledBundleEnginePath.mkdirs();
				File resovledBundleEngineFile = new File( resovledBundleEnginePath, bundleEngineName );
				cl.addArguments(new String[] { "-ib", archOutputFile.getAbsolutePath(), "-out", resovledBundleEngineFile.getAbsolutePath() });

				insignia(cl);
			}
		}
	}
}
