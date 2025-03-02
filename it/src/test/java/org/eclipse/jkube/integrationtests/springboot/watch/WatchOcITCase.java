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
package org.eclipse.jkube.integrationtests.springboot.watch;

import org.junit.jupiter.api.Tag;

import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;

@Tag(OPEN_SHIFT)
class WatchOcITCase extends Watch {
  @Override
  String getPrefix() {
    return "oc";
  }
}

