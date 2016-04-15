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
/*
 * Derived from work NAR-maven-plugin (c) Mark Donscelmann
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.StringUtils;

/**
 * Create meta about this build for dependency inclusion.
 */
@Mojo( name = "prepare-package", requiresProject= true, defaultPhase=LifecyclePhase.PREPARE_PACKAGE )
public class PreparePackageMojo extends AbstractPackageable {

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		WixInfo info = new WixInfo(project.getGroupId(), project.getArtifactId(), project.getVersion());
		// TODO: variations for multi culture
		defaultLocale();
		Set<String> cultures = culturespecs();
		
		if (cultures.contains(null)){ // cultures.isEmpty() shouldn't hit always add at least 1 culture null in defaultLocale
			info.setCulture("neutral");
		} else {
			info.setCulture(StringUtils.join(cultures.iterator(), ","));
		}

		info.setPlatform(StringUtils.join(getPlatforms().iterator(), ","));

		// mergeLevel
		//if( ML_FULL.equalsIgnoreCase(mergeLevel) || ML_TRANSFORM.equalsIgnoreCase(mergeLevel) )
			
		try {
			info.writeToPath(project.getBuild().getOutputDirectory());
		} 
		catch (IOException ioe) {
			throw new MojoExecutionException("Cannot write properties file", ioe);
		}

	}

}
