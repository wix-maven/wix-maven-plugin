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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * TODO: support for tools -
 * 
 * WixCop static analysis WixCop will identify and optionally fix source code issues
 * 
 * Lux/Nit to perform unit testing of Custom actions
 * 
 * melt/dark - wxs generation from inputs - msi/msm
 */
/**
 * Goal to initialize the workspace with wix toolset.
 */
@Mojo(name = "toolset", defaultPhase = LifecyclePhase.INITIALIZE)
public class ToolsetMojo extends AbstractWixMojo {

  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info(getClass().getName() + " skipped");
      return;
    }

    unpackFileBasedResources();
    getLog().debug("WiX Toolset ready for work");
  }

}
