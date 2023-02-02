/*************************************************************************************
 * Copyright (c) 2018-2019 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package org.jboss.tools.rsp.server.minishift.download;

import java.io.File;
import java.util.List;

import org.jboss.jdf.stacks.model.Stacks;
import org.jboss.tools.rsp.eclipse.core.runtime.IProgressMonitor;
import org.jboss.tools.rsp.runtime.core.model.DownloadRuntime;
import org.jboss.tools.rsp.runtime.core.model.IDownloadRuntimeRunner;
import org.jboss.tools.rsp.runtime.core.model.IRuntimeInstaller;
import org.jboss.tools.rsp.server.minishift.servertype.impl.MinishiftServerTypes;
import org.jboss.tools.rsp.server.redhat.download.stacks.AbstractStacksDownloadRuntimesProvider;
import org.jboss.tools.rsp.server.spi.model.IServerManagementModel;
import org.jboss.tools.rsp.stacks.core.model.StacksManager;

public class MinishiftCdkDownloadRuntimesProvider extends AbstractStacksDownloadRuntimesProvider {
    private static final String MINISHIFT_YAML_DEFAULT_URL = 
    		"https://raw.githubusercontent.com/jboss-developer/jboss-stacks/1.0.0.Final/minishift.yaml";
    private static final String URL_PROPERTY_MINISHIFT_STACKS = "org.jboss.tools.stacks.minishift.url";
    private static final String MINISHIFT_YAML_URL = System.getProperty(URL_PROPERTY_MINISHIFT_STACKS, MINISHIFT_YAML_DEFAULT_URL);
	private IServerManagementModel model;

	public MinishiftCdkDownloadRuntimesProvider(IServerManagementModel model) {
		this.model = model;
	}

	
	@Override
	public String getId() {
		return "Minishift-CDK";
	}
	protected File getDataFolder() {
		return model.getDataStoreModel().getDataLocation();
	}

	@Override
	protected Stacks[] getStacks(IProgressMonitor monitor) {
		Stacks ret = new StacksManager(getDataFolder()).getStacks(MINISHIFT_YAML_URL, 
				"Loading CDK / Minishift / CRC Downloadable Runtimes", monitor);
		return ret == null ? null : new Stacks[] {ret};
	}

	@Override
	protected String getLegacyId(String id) {
		return null;
	}

	@Override
	protected boolean requiresDisclaimer(String runtimeId) {
		return false;
	}

	@Override
	protected boolean runtimeTypeIsRegistered(String runtimeId) {
		return MinishiftServerTypes.getServerForRuntime(runtimeId) != null;
	}

	@Override
	protected void traverseStacks(Stacks stacks, List<DownloadRuntime> list, IProgressMonitor monitor) {
		traverseStacks(stacks, list, "MINISHIFT", monitor);
	}

	@Override
	public IDownloadRuntimeRunner getDownloadRunner(DownloadRuntime dr) {
		DownloadRuntime dlrt = findDownloadRuntime(dr.getId());
		if( dlrt == null || !dlrt.equals(dr))
			return null;
		
		String installer = (dr.getInstallationMethod() == null ? 
				IRuntimeInstaller.EXTRACT_INSTALLER : dr.getInstallationMethod());
		// TODO verify installer exists? 
		
		String prop = dr.getProperty(DownloadRuntime.PROPERTY_REQUIRES_CREDENTIALS);
		
		if( "true".equalsIgnoreCase(prop))
			// // Requires credentials is handled by another executor
			return new CDKDownloadExecutor(dr, model);
		
		//for testing 
		if( dlrt.getId().toLowerCase().startsWith("crc")) {
			return new CRCDownloadExecutor(dr, model);
		}

		return new MinishiftLicenseOnlyDownloadExecutor(dr, model);
	}

}
