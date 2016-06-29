/*
 * SonarSource :: Update Center :: Common
 * Copyright (C) 2010-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.updatecenter.common;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.updatecenter.common.UpdateCenterDeserializer.Mode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.SortedSet;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;

public class UpdateCenterDeserializerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void read_infos_from_properties() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      assertThat(center.getSonar().getVersions()).contains(Version.create("2.2"), Version.create("2.3"));
      assertThat(center.getSonar().getRelease(Version.create("2.2")).getDownloadUrl()).isEqualTo(new URL("http://dist.sonar.codehaus.org/sonar-2.2.zip"));

      Plugin clirr = center.getUpdateCenterPluginReferential().findPlugin("clirr");
      assertThat(clirr.getName()).isEqualTo("Clirr");
      assertThat(clirr.getDescription()).isEqualTo("Clirr Plugin");
      assertThat(clirr.getIssueTrackerUrl()).isEqualTo("http://jira.codehaus.org/issueTracker/for/clirr");
      assertThat(clirr.isSupportedBySonarSource()).isTrue();
      assertThat(clirr.getVersions()).contains(Version.create("1.0"), Version.create("1.1"));

      assertThat(clirr.getSourcesUrl()).isNull();
      assertThat(clirr.getDevelopers()).isEmpty();

      assertThat(clirr.getRelease(Version.create("1.0")).getDownloadUrlString()).isEqualTo("http://dist.sonar-plugins.codehaus.org/clirr-1.0.jar");
      assertThat(clirr.getRelease(Version.create("1.0")).getMinimumRequiredSonarVersion()).isEqualTo(Version.create("2.2"));
      assertThat(clirr.getRelease(Version.create("1.0")).getLastRequiredSonarVersion()).isEqualTo(Version.create("2.2"));

      Plugin motionchart = center.getUpdateCenterPluginReferential().findPlugin("motionchart");
      assertThat(motionchart.isSupportedBySonarSource()).isFalse();

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_add_developers() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-developers.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Plugin clirr = center.getUpdateCenterPluginReferential().findPlugin("clirr");
      assertThat(clirr.getDevelopers()).hasSize(3);
      assertThat(clirr.getDevelopers()).contains("Mike Haller", "Freddy Mallet", "Simon Brandhof");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_add_sources_url() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-scm.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Plugin clirr = center.getUpdateCenterPluginReferential().findPlugin("clirr");
      assertThat(clirr.getSourcesUrl()).isEqualTo("scm:svn:https://svn.codehaus.org/sonar-plugins/tags/sonar-clirr-plugin-1.1");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_add_dependencies() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-requires-plugins.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter pluginReferential = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      assertThat(pluginReferential.getUpdateCenterPluginReferential().getLastMasterReleasePlugins()).hasSize(3);

      Plugin clirr = pluginReferential.getUpdateCenterPluginReferential().findPlugin("clirr");
      assertThat(clirr.getName()).isEqualTo("Clirr");
      List<Release> requiredReleases = newArrayList(clirr.getRelease(Version.create("1.1")).getOutgoingDependencies());
      assertThat(requiredReleases).hasSize(2);
      assertThat(requiredReleases.get(0).getArtifact().getKey()).isEqualTo("foo");
      assertThat(requiredReleases.get(0).getVersion().getName()).isEqualTo("1.0");
      assertThat(requiredReleases.get(1).getArtifact().getKey()).isEqualTo("bar");
      assertThat(requiredReleases.get(1).getVersion().getName()).isEqualTo("1.1");
    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-6
  @Test
  public void should_resolve_ranges() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-ranges.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter pluginReferential = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Plugin clirr = pluginReferential.getUpdateCenterPluginReferential().findPlugin("clirr");
      SortedSet<Version> requiredSonarVersion = clirr.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(6);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.3");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("2.7.1");

      Plugin motionchart = pluginReferential.getUpdateCenterPluginReferential().findPlugin("motionchart");
      requiredSonarVersion = motionchart.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(6);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.2");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("2.8");
      assertThat(requiredSonarVersion).excludes(Version.create("2.7"));

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-7
  @Test
  public void should_resolve_latest() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-latest.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter pluginReferential = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Plugin clirr = pluginReferential.getUpdateCenterPluginReferential().findPlugin("clirr");
      SortedSet<Version> requiredSonarVersion = clirr.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(7);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.3");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("2.8");

      Plugin motionchart = pluginReferential.getUpdateCenterPluginReferential().findPlugin("motionchart");
      requiredSonarVersion = motionchart.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(4);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.4");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("2.8");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-15
  @Test
  public void should_resolve_latest_using_sonar_devVersion() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-latest-and-devVersion.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter pluginReferential = new UpdateCenterDeserializer(Mode.DEV, false).fromProperties(props);

      Plugin clirr = pluginReferential.getUpdateCenterPluginReferential().findPlugin("clirr");
      SortedSet<Version> requiredSonarVersion = clirr.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(8);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.3");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("3.0");

      Plugin motionchart = pluginReferential.getUpdateCenterPluginReferential().findPlugin("motionchart");
      requiredSonarVersion = motionchart.getRelease(Version.create("1.1")).getRequiredSonarVersions();
      assertThat(requiredSonarVersion).hasSize(4);
      assertThat(requiredSonarVersion.first().toString()).isEqualTo("2.4");
      assertThat(requiredSonarVersion.last().toString()).isEqualTo("3.0");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_parse_lts() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/sonar-lts.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);
      assertThat(center.getSonar().getLtsRelease().getVersion()).isEqualTo(Version.create("2.3"));
    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_throw_if_lts_invalid() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/sonar-lts-invalid.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("ltsVersion seems wrong as it is not listed in SonarQube versions");
      new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);
    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Test
  public void should_load_when_LATEST_is_on_latest_plugin_version_and_on_private_version_Prod() throws IOException, URISyntaxException {
    URL url = getClass().getResource("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/splitFileFormat/LATEST-is-on-latest-plugin-version/update-center.properties");
    UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromManyFiles(new File(url.toURI()));

    Plugin fooPlugin = center.getUpdateCenterPluginReferential().findPlugin("foo");
    assertThat(fooPlugin.getPublicVersions()).onProperty("name").containsOnly("1.0", "1.1");
  }

  @Test
  public void should_load_when_LATEST_is_on_latest_plugin_version_and_on_private_version_Dev() throws IOException, URISyntaxException {
    URL url = getClass().getResource("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/splitFileFormat/LATEST-is-on-latest-plugin-version/update-center.properties");
    UpdateCenter center = new UpdateCenterDeserializer(Mode.DEV, false).fromManyFiles(new File(url.toURI()));

    Plugin fooPlugin = center.getUpdateCenterPluginReferential().findPlugin("foo");
    assertThat(fooPlugin.getPublicVersions()).onProperty("name").containsOnly("1.0", "1.1");
    assertThat(fooPlugin.getPrivateVersions()).onProperty("name").containsOnly("1.2");
  }

  @Test
  public void should_throw_when_LATEST_is_another_plugin_version_then_latest() throws IOException, URISyntaxException {
    URL url = getClass().getResource(
      "/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/splitFileFormat/LATEST_is_another_plugin_version_then_latest/update-center.properties");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Only the latest release of plugin foo may depend on LATEST SonarQube");

    new UpdateCenterDeserializer(Mode.PROD, false).fromManyFiles(new File(url.toURI()));
  }

  // UPC-29
  @Test
  public void should_load_split_format_in_dev_mode() throws IOException, URISyntaxException {
    URL url = getClass().getResource("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/splitFileFormat/nominal/update-center.properties");
    UpdateCenter center = new UpdateCenterDeserializer(Mode.DEV, false).fromManyFiles(new File(url.toURI()));
    assertThat(center.getSonar().getLtsRelease().getVersion()).isEqualTo(Version.create("3.7.1"));

    Plugin abapPlugin = center.getUpdateCenterPluginReferential().findPlugin("abap");
    assertThat(abapPlugin.getDevRelease().getVersion()).isEqualTo(Version.create("2.2.1-SNAPSHOT"));
    assertThat(abapPlugin.getIssueTrackerUrl()).isEqualTo("http://issue.tracker.url/from/properties/file");

    Plugin phpPlugin = center.getUpdateCenterPluginReferential().findPlugin("php");
    assertThat(phpPlugin.isSupportedBySonarSource()).isTrue();
    assertThat(phpPlugin.getDevRelease().getVersion()).isEqualTo(Version.create("2.3-SNAPSHOT"));
    assertThat(phpPlugin.getPublicVersions()).onProperty("name").containsOnly("2.1", "2.2");
    assertThat(phpPlugin.getPrivateVersions()).onProperty("name").containsOnly("2.2.1");
    assertThat(phpPlugin.getArchivedVersions()).onProperty("name").containsOnly("2.0");

    Plugin ssqvPlugin = center.getUpdateCenterPluginReferential().findPlugin("ssqv");
    assertThat(ssqvPlugin.getDevRelease().getVersion()).isEqualTo(Version.create("1.1-SNAPSHOT"));
    assertThat(ssqvPlugin.getPublicVersions()).onProperty("name").containsOnly("1.0", "1.1");

  }

  // UPC-29
  @Test
  public void should_load_split_format_in_prod_mode() throws IOException, URISyntaxException {
    URL url = getClass().getResource("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/splitFileFormat/nominal/update-center.properties");
    UpdateCenter center = new UpdateCenterDeserializer(Mode.PROD, false).fromManyFiles(new File(url.toURI()));
    assertThat(center.getSonar().getLtsRelease().getVersion()).isEqualTo(Version.create("3.7.1"));
    assertThat(center.getUpdateCenterPluginReferential().findPlugin("abap").getDevRelease()).isNull();
    assertThat(center.getUpdateCenterPluginReferential().findPlugin("php").getDevRelease()).isNull();
    assertThat(center.getUpdateCenterPluginReferential().findPlugin("ssqv").getDevRelease()).isNull();

    Plugin ssqvPlugin = center.getUpdateCenterPluginReferential().findPlugin("ssqv");
    assertThat(ssqvPlugin.getPublicVersions()).onProperty("name").containsOnly("1.0", "1.1");
    SortedSet<Version> requiredSonarVersion10 = ssqvPlugin.getRelease(Version.create("1.0")).getRequiredSonarVersions();
    assertThat(requiredSonarVersion10).hasSize(1);
    assertThat(requiredSonarVersion10.first().toString()).isEqualTo("3.7");
    assertThat(requiredSonarVersion10.last().toString()).isEqualTo("3.7");

    SortedSet<Version> requiredSonarVersion11 = ssqvPlugin.getRelease(Version.create("1.1")).getRequiredSonarVersions();
    assertThat(requiredSonarVersion11).hasSize(1);
    assertThat(requiredSonarVersion11.first().toString()).isEqualTo("4.0");
    assertThat(requiredSonarVersion11.last().toString()).isEqualTo("4.0");

  }

  // UPC-29
  @Test
  public void should_fail_if_overlap_in_sqVersion_of_public_releases() throws IOException {

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("SQ version 2.7.1 is declared compatible with two public versions of Clirr plugin: 1.1 and 1.0");

    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-overlap-sqVersion.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-29
  @Test
  public void should_ignore_errors() throws IOException {

    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-with-overlap-sqVersion.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      new UpdateCenterDeserializer(Mode.PROD, true).fromProperties(props);

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-30
  @Test
  public void should_override_defaults() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-override-defaults.properties");
    try {
      Properties props = new Properties();
      props.load(input);
      UpdateCenter updateCenter = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Release sonar2_2 = updateCenter.getSonar().getRelease("2.2");
      Release sonar2_3 = updateCenter.getSonar().getRelease("2.3");
      assertThat(sonar2_2.getChangelogUrlString()).isEqualTo("http://changelog");
      assertThat(sonar2_3.getChangelogUrlString()).isEqualTo("http://changelog2.3");

      Plugin clirr = updateCenter.getUpdateCenterPluginReferential().findPlugin("clirr");
      Release clirr1_0 = clirr.getRelease(Version.create("1.0"));
      Release clirr1_1 = clirr.getRelease(Version.create("1.1"));
      assertThat(clirr1_0.getChangelogUrlString()).isEqualTo("http://changelog");
      assertThat(clirr1_1.getChangelogUrlString()).isEqualTo("http://changelog1.1");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-29
  @Test
  public void should_fail_if_duplicate_sq_version() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/sonar-duplicate.properties");
    try {
      Properties props = new Properties();
      props.load(input);

      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("Duplicate version for SonarQube: 2.8");

      new UpdateCenterDeserializer(Mode.DEV, false).fromProperties(props);

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-29
  @Test
  public void should_fail_if_duplicate_plugin_version() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/updates-duplicate-plugin.properties");
    try {
      Properties props = new Properties();
      props.load(input);

      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("Duplicate version for plugin clirr: 1.1");

      new UpdateCenterDeserializer(Mode.DEV, false).fromProperties(props);

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-28
  @Test
  public void test_plugin_with_only_private_and_dev_version() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/plugin-with-only-private-and-devVersion.properties");
    try {
      Properties props = new Properties();
      props.load(input);

      UpdateCenter updateCenter = new UpdateCenterDeserializer(Mode.DEV, false).fromProperties(props);

      Plugin clirr = updateCenter.getUpdateCenterPluginReferential().findPlugin("clirr");
      clirr.getRelease(Version.create("1.0"));
      clirr.getRelease(Version.create("1.1"));
      clirr.getRelease(Version.create("1.2-SNAPSHOT"));

      updateCenter = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      thrown.expect(NoSuchElementException.class);
      thrown.expectMessage("Unable to find plugin with key clirr");

      updateCenter.getUpdateCenterPluginReferential().findPlugin("clirr");

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  // UPC-38
  @Test
  public void test_plugin_with_archived_version() throws IOException {
    InputStream input = getClass().getResourceAsStream("/org/sonar/updatecenter/common/UpdateCenterDeserializerTest/plugin-with-archived-versions.properties");
    try {
      Properties props = new Properties();
      props.load(input);

      UpdateCenter updateCenter = new UpdateCenterDeserializer(Mode.PROD, false).fromProperties(props);

      Plugin clirr = updateCenter.getUpdateCenterPluginReferential().findPlugin("clirr");
      assertThat(clirr.getRelease(Version.create("1.0")).isArchived()).isTrue();
      assertThat(clirr.getRelease(Version.create("1.1")).isArchived()).isFalse();

    } finally {
      IOUtils.closeQuietly(input);
    }
  }
}
