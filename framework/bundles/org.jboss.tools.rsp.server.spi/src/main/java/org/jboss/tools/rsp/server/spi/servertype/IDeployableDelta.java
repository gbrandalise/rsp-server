/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.spi.servertype;

import java.nio.file.Path;
import java.util.Map;

public interface IDeployableDelta {
	/**
	 * Returns a map of changed resources within this deployable.
	 * The key is a relative path to the deployment's source directory.
	 * @return
	 */
	public Map<Path, IDeployableResourceDelta> getResourceDeltaMap();

}
