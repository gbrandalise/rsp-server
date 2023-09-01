/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.client.cli;

public interface InputHandler {
	public String getPrompt();
	public void setPromptShown();
	public boolean isPromptShown();
	public void handleInput(String line) throws Exception;
	public boolean isSecret();
	public boolean isDone();
}
