/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.spi.servertype;

import java.util.List;

import org.jboss.tools.rsp.api.dao.DeployableReference;
import org.jboss.tools.rsp.api.dao.DeployableState;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;

public interface IServerPublishModel {
	/**
	 * Get a resource delta for the given deployment
	 * @param reference
	 * @return
	 */
	public IDeployableDelta getDeployableResourceDelta(DeployableReference reference);
	
	/**
	 * Get any assembly resource mappings for a given deployable
	 * @param reference
	 * @return
	 */
	public IDeploymentAssemblyMapping[] getDeployableResourceMappings(DeployableReference reference);
	

	/**
	 * Adds a deployable to the list of objects we want published to the server. 
	 * On the next publish request, a publish of this deployable will be attempted. 
	 *  
	 * @param req
	 * @return IStatus#OK if the deployable was added. IStatus.ERROR otherwise.
	 */
	public IStatus addDeployable(DeployableReference ref);

	/**
	 * Returns {@code true} if the given reference is contained in this model. This
	 * is the case if the deployable was previously added. Deployables are
	 * identified by their path.
	 * 
	 * @param reference
	 * @return true if the given reference is contained in this model
	 * 
	 * @see DeployableReference#getPath
	 */
	public boolean contains(DeployableReference reference);

	/**
	 * Removes a deployable from the list of objects we want published to the server. 
	 * On the next publish request, a removal of this deployable will be attempted. 
	 *  
	 * @param reference
	 * @return IStatus#OK if the deployable was removed. IStatus.ERROR otherwise.
	 * 
	 * @see #removeDeployable
	 */
	public IStatus removeDeployable(DeployableReference reference);

	/**
	 * Returns a list of the deployables for this server and their current states
	 * @return
	 */
	public List<DeployableState> getDeployableStates();

	/**
	 * Returns a list of the deployables for this server and their current states
	 * @return
	 */
	public List<DeployableState> getDeployableStatesWithOptions();

	/**
	 * Allows the framework to initialize the model from a data store
	 * @param references
	 */
	public void initialize(List<DeployableReference> references);

	/**
	 * Sets the publish state for a deployable. 
	 * Clients should call this method after publishing to update
	 * the model with what the current state is.
	 * @param reference
	 * @param publishState
	 */
	public void setDeployablePublishState(DeployableReference reference, int publishState);

	/**
	 * Sets the run state (starting, started, etc) for a deployment.
	 * Clients should call this method after publishing, 
	 * stopping, starting, etc a given deployment. 
	 * 
	 * @param reference
	 * @param runState
	 */
	public void setDeployableState(DeployableReference reference, int runState);

	/**
	 * Returns the current state for the given deployment. Return {@code null} if
	 * the deployment doesn't exist yet.
	 * 
	 * @param reference
	 * @return
	 */
	public DeployableState getDeployableState(DeployableReference reference);
	
	/**
	 * Ensure the DeployableReference is filled with the options it was originally
	 * created with.
	 * 
	 * @return
	 */
	public DeployableReference fillOptionsFromCache(DeployableReference reference);

	/**
	 * Forces the model to remove the given deployable from its stores entirely.
	 * @param reference
	 */
	public void deployableRemoved(DeployableReference reference);
	
	
	/**
	 * Set the publish state of the entire server, 
	 * typically based on some union of the individual modules' publish states
	 * @param state
	 * @param fire
	 */
	public void setServerPublishState(int state, boolean fire);

	/**
	 * Get the publish state of the entire server, 
	 * typically based on some union of the individual modules' publish states
	 * 
	 * @return
	 */
	public int getServerPublishState();

	/**
	 * Update the server publish state based on the state of its deployments 
	 */
	public void updateServerPublishStateFromDeployments(boolean fireEvent);
}
