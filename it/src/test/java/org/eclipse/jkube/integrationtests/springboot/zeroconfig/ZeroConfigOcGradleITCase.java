/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.springboot.zeroconfig;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.ImageStream;
import org.eclipse.jkube.integrationtests.OpenShiftCase;
import org.eclipse.jkube.integrationtests.gradle.JKubeGradleRunner;
import org.eclipse.jkube.integrationtests.jupiter.api.Application;
import org.eclipse.jkube.integrationtests.jupiter.api.Gradle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.DeploymentConfigAssertion.awaitDeploymentConfig;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.PodAssertion.awaitPod;
import static org.eclipse.jkube.integrationtests.assertions.ServiceAssertion.awaitService;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.eclipse.jkube.integrationtests.springboot.zeroconfig.ZeroConfig.GRADLE_APPLICATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@Application(GRADLE_APPLICATION)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZeroConfigOcGradleITCase extends ZeroConfig implements OpenShiftCase {

  @Gradle(project = "sb-zero-config")
  private JKubeGradleRunner gradle;

  @Test
  @Order(1)
  @DisplayName("ocResource, should create manifests")
  void ocResource() {
    // When
    gradle.tasks("ocResource").build();
    // Then
    var resourcePath = gradle.getModulePath().resolve("build").resolve("classes").resolve("java")
      .resolve("main").resolve("META-INF").resolve("jkube");
    assertThat(resourcePath.toFile(), anExistingDirectory());
    assertListResource(resourcePath.resolve("openshift.yml"));
    assertThat(resourcePath.resolve("openshift").resolve("sb-zero-config-deploymentconfig.yml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(resourcePath.resolve("openshift").resolve("sb-zero-config-route.yml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(resourcePath.resolve("openshift").resolve("sb-zero-config-service.yml").toFile(),
      yaml(not(anEmptyMap())));
  }

  @Test
  @Order(1)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("ocBuild, should create image")
  void ocBuild() {
    // When
    gradle.tasks("ocBuild").build();
    // Then
    final ImageStream is = getOpenShiftClient().imageStreams().withName(getApplication()).get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
  }

  @Test
  @Order(2)
  @DisplayName("ocHelm, should create Helm charts")
  void ocHelm() {
    // When
    gradle.tasks("ocHelm").build();
    // Then
    final var helmDirectory = gradle.getModulePath().resolve("build").resolve("jkube")
      .resolve("helm").resolve(getApplication()).resolve("openshift");
    assertThat(helmDirectory.resolve(getApplication() + "-0.0.0-SNAPSHOT.tar.gz").toFile(),
      anExistingFile());
    assertThat(helmDirectory.resolve("Chart.yaml").toFile(), yaml(allOf(
      aMapWithSize(3),
      hasEntry("apiVersion", "v1"),
      hasEntry("name", getApplication()),
      hasEntry("version", "0.0.0-SNAPSHOT")
    )));
    assertThat(helmDirectory.resolve("values.yaml").toFile(), yaml(anEmptyMap()));
    assertThat(helmDirectory.resolve("templates").resolve("sb-zero-config-deploymentconfig.yaml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(helmDirectory.resolve("templates").resolve("sb-zero-config-route.yaml").toFile(),
      yaml(not(anEmptyMap())));
    assertThat(helmDirectory.resolve("templates").resolve("sb-zero-config-service.yaml").toFile(),
      yaml(not(anEmptyMap())));
  }


  @Test
  @Order(2)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("ocApply, should deploy pod and service")
  @SuppressWarnings("unchecked")
  void ocApply() throws Exception {
    // When
    gradle.tasks("ocApply").build();
    // Then
    final Pod pod = awaitPod(this)
      .logContains("Started ZeroConfigApplication in", 40)
      .getKubernetesResource();
    awaitService(this, pod.getMetadata().getNamespace())
      .assertPorts(hasSize(1))
      .assertPort("http", 8080, false);
    awaitDeploymentConfig(this, pod.getMetadata().getNamespace())
      .assertReplicas(equalTo(1))
      .assertContainers(hasSize(1))
      .assertContainers(hasItems(allOf(
        hasProperty("image", containsString("sb-zero-config@sha256")),
        hasProperty("name", equalTo("spring-boot")),
        hasProperty("ports", hasSize(3)),
        hasProperty("ports", hasItems(allOf(
          hasProperty("name", equalTo("http")),
          hasProperty("containerPort", equalTo(8080))
        )))
      )));
  }

  @Test
  @Order(3)
  @DisplayName("ocLog, should retrieve log")
  void ocLog() {
    // When
    final var result = gradle.tasks("ocLog", "-Pjkube.log.follow=false").build();
    // Then
    assertThat(result.getOutput(),
      stringContainsInOrder("Tomcat started on port(s): 8080", "Started ZeroConfigApplication in", "seconds"));
  }

  @Test
  @Order(4)
  @DisplayName("ocUndeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    gradle.tasks("ocUndeploy").build();
    // Then
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources()
      .assertDeploymentDeleted();
    cleanUpCluster();
  }
}
