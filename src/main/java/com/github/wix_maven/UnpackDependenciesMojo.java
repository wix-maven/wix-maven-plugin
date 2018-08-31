package com.github.wix_maven;

/*
 * #%L WiX Toolset (Windows Installer XML) Maven Plugin %% Copyright (C) 2013 - 2014 GregDomjan
 * NetIQ %% Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License. #L%
 */
/*
 * Derived from work NAR-maven-plugin (c) Mark Donscelmann
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

/**
 * Goal that unpacks the project dependencies from the repository to a defined location.
 */

@Mojo(name = "unpack-dependencies", requiresDependencyResolution = ResolutionScope.TEST,
    defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class UnpackDependenciesMojo extends AbstractWixMojo {
  /**
   * A comma separated list of file patterns to include when unpacking the artifact.<br>
   * i.e. <code>wix-locale/**,cabs/**</code><br>
   * NOTE: Excludes patterns override the includes.<br>
   * (component code = <code>return isIncluded( name ) AND !isExcluded( name );</code>)
   */
  @Parameter(property = "wix.unpack.includes", defaultValue = "wix-locale/**,cabs/**")
  private String wixUnpackIncludes = "";

  /**
   * A comma separated list of file patterns to exclude when unpacking the artifact.<br>
   * i.e. <code>**\/*.xml,**\/*.properties</code><br>
   * NOTE: Excludes patterns override the includes.<br>
   * (component code = <code>return isIncluded( name ) AND !isExcluded( name );</code>)
   */
  @Parameter(property = "wix.unpack.excludes")
  private String wixUnpackExcludes = "";

  /**
   * Artifact collector, needed to resolve dependencies.
   */
  @Component
  // ( role = ArtifactCollector.class )
  protected ArtifactCollector artifactCollector;

  /**
   * Perform the Mojo action of getting dependencies and unpacking them.
   * 
   * @throws MojoExecutionException with a message if an error occurs.
   */
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info(getClass().getName() + " skipped");
      return;
    }

    getLog().info("WiX dependencies");
    Set<Artifact> wixDependencies = getWixDependencySets();

    for (Iterator<Artifact> i = wixDependencies.iterator(); i.hasNext();) {
      Artifact libGroup = i.next();
      if (!libGroup.hasClassifier()) {
        getLog().debug("Attempting to unpack resources for " + libGroup.toString());
        unpackResource(libGroup);
      }
    }
  }

  private void unpackResource(Artifact libGroup) {
    // TODO: support compile if( libGroup.getFile().isFile() )
    zipUnArchiver.setSourceFile(libGroup.getFile());
    File resUnpackDirectory = wixUnpackDirectory(libGroup);
    // zipUnArchiver.extract(subfolder, resUnpackDirectory);

    if (!resUnpackDirectory.exists())
      resUnpackDirectory.mkdirs();

    zipUnArchiver.setDestDirectory(resUnpackDirectory);
    IncludeExcludeFileSelector[] selectors =
        new IncludeExcludeFileSelector[] {new IncludeExcludeFileSelector()};
    selectors[0].setIncludes(getIncludes());
    selectors[0].setExcludes(getExcludes());
    zipUnArchiver.setFileSelectors(selectors);
    zipUnArchiver.extract();
  }

  /**
   * @return Returns a comma separated list of excluded items
   */
  public String[] getExcludes() {
    // return DependencyUtil.cleanToBeTokenizedString( this.excludes );
    return this.wixUnpackExcludes.split(",");
  }

  /**
   * @param excludes A comma separated list of items to exclude i.e.
   *        <code>**\/*.xml, **\/*.properties</code>
   */
  public void setExcludes(String excludes) {
    this.wixUnpackExcludes = excludes;
  }

  /**
   * @return Returns a comma separated list of included items
   */
  public String[] getIncludes() {
    // return DependencyUtil.cleanToBeTokenizedString( this.includes );
    return this.wixUnpackIncludes.split(",");
  }

  /**
   * @param includes A comma separated list of items to include i.e.
   *        <code>**\/*.xml, **\/*.properties</code>
   */
  public void setIncludes(String includes) {
    this.wixUnpackIncludes = includes;
  }
}
