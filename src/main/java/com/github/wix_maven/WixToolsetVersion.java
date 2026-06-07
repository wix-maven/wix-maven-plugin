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

/**
 * Represents the WiX Toolset version family in use.
 * <p>
 * Detection is based on the {@code wix.toolsPluginArtifactId} Maven parameter:
 * <ul>
 * <li>{@code wix-toolset} (default) → {@link #V3}</li>
 * <li>any artifact id containing {@code "wix-toolset4"}, {@code "wix-toolset5"},
 * {@code "wix-toolset6"}, or starting with {@code "wix4"} → {@link #V4_PLUS}</li>
 * </ul>
 */
public enum WixToolsetVersion {

  /** WiX Toolset v3 – separate executables: candle.exe, light.exe, etc. */
  V3,

  /** WiX Toolset v4 or later – unified {@code wix.exe} CLI with subcommands. */
  V4_PLUS;

  /**
   * Detect the toolset version from the Maven artifact id used for the tools package.
   * 
   * @param toolsPluginArtifactId the value of {@code wix.toolsPluginArtifactId}
   * @return {@link #V4_PLUS} when the artifact id signals a v4+ toolset, otherwise {@link #V3}
   */
  public static WixToolsetVersion detect(String toolsPluginArtifactId) {
    if (toolsPluginArtifactId == null) {
      return V3;
    }
    String lower = toolsPluginArtifactId.toLowerCase();
    // v4+: unified wix.exe CLI with subcommands
    if (lower.contains("wix-toolset4") || lower.contains("wix4") || lower.startsWith("wix4")
        || lower.contains("wix-toolset5") || lower.contains("wix5") || lower.startsWith("wix5")
        || lower.contains("wix-toolset6") || lower.contains("wix6") || lower.startsWith("wix6")
        || lower.contains("wix-toolset7") || lower.contains("wix7") || lower.startsWith("wix7")) {
      return V4_PLUS;
    }
    return V3;
  }
}
