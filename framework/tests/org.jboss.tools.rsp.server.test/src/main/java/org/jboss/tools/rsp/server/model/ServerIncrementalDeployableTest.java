/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.model;

import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_FULL;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_INCREMENTAL;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_STATE_ADD;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_STATE_FULL;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.PUBLISH_STATE_NONE;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.STATE_STARTED;
import static org.jboss.tools.rsp.api.ServerManagementAPIConstants.STATE_UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.jboss.tools.rsp.api.ServerManagementAPIConstants;
import org.jboss.tools.rsp.api.dao.DeployableReference;
import org.jboss.tools.rsp.api.dao.DeployableState;
import org.jboss.tools.rsp.api.dao.ServerState;
import org.jboss.tools.rsp.eclipse.core.runtime.CoreException;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;
import org.jboss.tools.rsp.server.filewatcher.FileWatcherService;
import org.jboss.tools.rsp.server.spi.filewatcher.IFileWatcherService;
import org.jboss.tools.rsp.server.spi.model.IServerManagementModel;
import org.jboss.tools.rsp.server.spi.servertype.IDeployableDelta;
import org.jboss.tools.rsp.server.spi.servertype.IDeployableResourceDelta.DELTA_TYPE;
import org.jboss.tools.rsp.server.spi.servertype.IServer;
import org.jboss.tools.rsp.server.util.DataLocationSysProp;
import org.jboss.tools.rsp.server.util.TestServerUtils;
import org.jboss.tools.rsp.server.util.generation.DeploymentGeneration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class ServerIncrementalDeployableTest {

	private static final String SERVER_FILENAME = "s3";
	private static final String SERVERS_DIR = "serverdeployabletestIncremental";
	private static final String SERVER_ID = "abc123a";
	private static final String SERVER_TYPE = "wonka6";
	private static final String DEPLOYMENTS_DIR = SERVERS_DIR + "_deployments";
	private static final String DEPLOYABLE_LABEL = "some.name";
	private static final String WAR_FILENAME = "hello-world-war-1.0.0.war";

	private static final DataLocationSysProp dataLocation = new DataLocationSysProp();

	@BeforeClass
	public static void beforeClass() {
		dataLocation.backup().set("ServerDeployableTestIncremental");
	}

	@AfterClass
	public static void afterClass() {
		dataLocation.restore();
	}

	private ServerModel sm;
	private File war;
	private Path serversDir;
	private IServer server;
	private CustomFileWatcherService watcherService;
	
	@Before
	public void before() throws IOException {
		this.serversDir = Files.createTempDirectory(SERVERS_DIR);
		this.war = new DeploymentGeneration().createWar(WAR_FILENAME, DEPLOYMENTS_DIR);
		this.watcherService = new CustomFileWatcherService();
		IServerManagementModel managementModel = createServerManagementModel(watcherService);
		this.sm = TestServerUtils.createServerModel(
				SERVER_FILENAME, serversDir, 
				TestServerUtils.getServerWithoutDeployablesString(SERVER_ID, SERVER_TYPE),
				TestServerDelegateWithDelay::new, SERVER_TYPE, 
				managementModel, null);
		this.server = sm.getServer(SERVER_ID);
	}

	private CountDownLatch[] fileWatcherStartSignal1 = new CountDownLatch[1];
	private CountDownLatch[] fileWatcherDoneSignal1 = new CountDownLatch[1];
	
	private CountDownLatch[] publishStartSignal1 = new CountDownLatch[1];
	private CountDownLatch[] publishDoneSignal1 = new CountDownLatch[1];
	private CountDownLatch[] publishStartSignal2 = new CountDownLatch[1];
	private CountDownLatch[] publishDoneSignal2 = new CountDownLatch[1];
	
	
	@Test
	public void dummy() {
		// just so there's no empty test file
	}
	
	@Test
	@Ignore
	public void testIncrementalPublishImplementationWithDelay() {
		defaultPublishImplementationWithDelayInternal(sm);

		DeployableReference reference = new DeployableReference(DEPLOYABLE_LABEL, war.getAbsolutePath());
		
		// Now change a file 
		server = sm.getServer(SERVER_ID);
		assertNotNull(server);
		assertNotNull(server.getDelegate());
		assertNotNull(server.getDelegate().getServerPublishModel());
		File indexjsp = new File(war, "index.jsp");
		File otherjsp = new File(war, "other.jsp");
		
		Path indexJspRelativePath = war.toPath().relativize(indexjsp.toPath());
		Path otherJspRelativePath = war.toPath().relativize(otherjsp.toPath());
		resetAllLatches();
		byte[] oldContents = null;
		try {
			oldContents = Files.readAllBytes(indexjsp.toPath());
			String asString = new String(oldContents) + "\n\n";
			Files.write(indexjsp.toPath(), asString.getBytes());
		} catch(IOException ioe) {
			fail(ioe.getMessage());
		}
		
		watcherService.setExpectedEvent(indexjsp.toPath(), StandardWatchEventKinds.ENTRY_MODIFY);
		fileWatcherStartSignal1[0].countDown();
		try {
			fileWatcherDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		
		// Ensure the module state is changed to incremental
		waitDeployableState(PUBLISH_STATE_INCREMENTAL,server, 10000);
		
		// Ensure delta marks changed resource as modified
		IDeployableDelta delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().containsKey(indexJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(indexJspRelativePath));
		assertEquals(DELTA_TYPE.MODIFIED, delta1.getResourceDeltaMap().get(indexJspRelativePath).getDeltaType());
		
		// Delete that resource
		resetAllLatches();
		indexjsp.delete();
		
		watcherService.setExpectedEvent(indexjsp.toPath(), StandardWatchEventKinds.ENTRY_DELETE);
		fileWatcherStartSignal1[0].countDown();
		try {
			fileWatcherDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		assertFalse(indexjsp.exists());

		delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().containsKey(indexJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(indexJspRelativePath));
		assertEquals(DELTA_TYPE.DELETED, delta1.getResourceDeltaMap().get(indexJspRelativePath).getDeltaType());

		// re-instate that resource and verify the delta goes back to just 'modified' instead of deleted
		resetAllLatches();
		try {
			Files.write(indexjsp.toPath(), oldContents);
		} catch(IOException ioe) {
			fail(ioe.getMessage());
		}
		watcherService.setExpectedEvent(indexjsp.toPath(), StandardWatchEventKinds.ENTRY_CREATE);
		fileWatcherStartSignal1[0].countDown();
		try {
			fileWatcherDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}

		delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().containsKey(indexJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(indexJspRelativePath));
		assertEquals(DELTA_TYPE.MODIFIED, 
				delta1.getResourceDeltaMap().get(indexJspRelativePath).getDeltaType());

		
		// Publish and verify the states are accurately matching expectations
		runPublishAssertStates(PUBLISH_INCREMENTAL, 
				PUBLISH_STATE_INCREMENTAL, STATE_STARTED,
				PUBLISH_STATE_NONE, STATE_STARTED, PUBLISH_STATE_NONE,
				PUBLISH_STATE_NONE, STATE_STARTED);


		
		// Delete that resource
		resetAllLatches();
		indexjsp.delete();
		
		watcherService.setExpectedEvent(indexjsp.toPath(), StandardWatchEventKinds.ENTRY_DELETE);
		fileWatcherStartSignal1[0].countDown();
		try {
			fileWatcherDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		assertFalse(indexjsp.exists());
		
		delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().containsKey(indexJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(indexJspRelativePath));
		assertEquals(DELTA_TYPE.DELETED, 
				delta1.getResourceDeltaMap().get(indexJspRelativePath).getDeltaType());



		// Add a different resource
		resetAllLatches();
		try {
			Files.write(otherjsp.toPath(), oldContents);
		} catch(IOException ioe) {
			fail(ioe.getMessage());
		}
		watcherService.setExpectedEvent(otherjsp.toPath(), StandardWatchEventKinds.ENTRY_CREATE);
		fileWatcherStartSignal1[0].countDown();
		try {
			fileWatcherDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		
		delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().containsKey(indexJspRelativePath));
		assertTrue(delta1.getResourceDeltaMap().containsKey(otherJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(indexJspRelativePath));
		assertNotNull(delta1.getResourceDeltaMap().get(otherJspRelativePath));
		assertEquals(DELTA_TYPE.DELETED, 
				delta1.getResourceDeltaMap().get(indexJspRelativePath).getDeltaType());
		assertEquals(DELTA_TYPE.CREATED, 
				delta1.getResourceDeltaMap().get(otherJspRelativePath).getDeltaType());


		// Publish and verify the states are accurately matching expectations
		runPublishAssertStates(PUBLISH_INCREMENTAL, 
				PUBLISH_STATE_INCREMENTAL, STATE_STARTED,
				PUBLISH_STATE_NONE, STATE_STARTED, PUBLISH_STATE_NONE,
				PUBLISH_STATE_NONE, STATE_STARTED);
		
		delta1 = server.getDelegate().getServerPublishModel().getDeployableResourceDelta(reference);
		assertTrue(delta1.getResourceDeltaMap().size() == 0);
	}

	protected void resetAllLatches() {
		fileWatcherStartSignal1[0] = new CountDownLatch(1);
		fileWatcherDoneSignal1[0] = new CountDownLatch(1);
		publishStartSignal1[0] = new CountDownLatch(1);
		publishStartSignal2[0] = new CountDownLatch(1);
		publishDoneSignal1[0] = new CountDownLatch(1);
		publishDoneSignal2[0] = new CountDownLatch(1);
		watcherService.setEnabled(true);
	}
	
	
	private void waitDeployableState(int expectedState, IServer server, int maxWait) {
		long current = System.currentTimeMillis();
		long end = current + maxWait;
		while(System.currentTimeMillis() < end) {
			ServerState ss = server.getDelegate().getServerState();
			List<DeployableState> dState = ss.getDeployableStates();
			assertNotNull(dState);
			assertEquals(1, dState.size());
			DeployableState oneState = dState.get(0);
			assertNotNull(oneState);
			if( expectedState == oneState.getPublishState()) {
				return;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		}
		fail();
	}
	
	
	public void defaultPublishImplementationWithDelayInternal(ServerModel sm) {
		server = sm.getServer(SERVER_ID);

		DeployableReference reference = new DeployableReference(DEPLOYABLE_LABEL, war.getAbsolutePath());
		IStatus added = sm.addDeployable(server, reference);
		assertTrue(TestServerUtils.isOk(added));

		List<DeployableState> deployables = sm.getDeployables(server);
		assertNotNull(deployables);
		assertEquals(1, deployables.size());
		
		ServerState ss = server.getDelegate().getServerState();
		List<DeployableState> dState = ss.getDeployableStates();
		assertNotNull(dState);
		assertEquals(1, dState.size());
		DeployableState oneState = dState.get(0);
		assertNotNull(oneState);
		assertEquals(PUBLISH_STATE_ADD, oneState.getPublishState());
		assertEquals(STATE_UNKNOWN, oneState.getState());

		// And verify the server state
		assertEquals(PUBLISH_STATE_FULL, ss.getPublishState());

		runPublishAssertStates(PUBLISH_FULL, 
				PUBLISH_STATE_ADD, STATE_UNKNOWN,
				PUBLISH_STATE_NONE, STATE_UNKNOWN, PUBLISH_STATE_NONE,
				PUBLISH_STATE_NONE, STATE_STARTED);
	}
	
	
	private void runPublishAssertStates(int publishType,
			int prePublishState, int preRunState,
			int midPublishState, int midRunState, int midServerPublishState,
			int postPublishState, int postRunState) {

		// Now do the publish
		publishStartSignal1[0] = new CountDownLatch(1);
		publishDoneSignal1[0] = new CountDownLatch(1);
		publishStartSignal2[0] = new CountDownLatch(1);
		publishDoneSignal2[0] = new CountDownLatch(1);

		try {
			sm.publish(server, publishType);
		} catch(CoreException ce) {
			fail(ce.getMessage());
		}
		
		// Verify module is set to no publish required and module is started
		ServerState ss = server.getDelegate().getServerState();
		List<DeployableState> dState = ss.getDeployableStates();
		assertNotNull(dState);
		assertEquals(1, dState.size());
		DeployableState oneState = dState.get(0);
		assertNotNull(oneState);
		assertEquals(prePublishState, oneState.getPublishState());
		assertEquals(preRunState, oneState.getState());
		
		// countdown once
		publishStartSignal1[0].countDown();
		try {
			publishDoneSignal1[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		
		ss = server.getDelegate().getServerState();
		dState = ss.getDeployableStates();
		assertNotNull(dState);
		assertEquals(1, dState.size());
		oneState = dState.get(0);
		assertNotNull(oneState);
		assertEquals(midPublishState, oneState.getPublishState());
		assertEquals(midRunState, oneState.getState());
		assertEquals(midServerPublishState, ss.getPublishState());

		
		// countdown once
		publishStartSignal2[0].countDown();
		try {
			publishDoneSignal2[0].await();
		} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
		
		ss = server.getDelegate().getServerState();
		dState = ss.getDeployableStates();
		assertNotNull(dState);
		assertEquals(1, dState.size());
		oneState = dState.get(0);
		assertNotNull(oneState);
		assertEquals(postPublishState, oneState.getPublishState());
		assertEquals(postRunState, oneState.getState());
	}

	private class TestServerDelegateWithDelay extends AbstractServerDelegate {
		public TestServerDelegateWithDelay(IServer server) {
			super(server);
		}
		@Override
		protected void publishDeployable(
				DeployableReference reference, 
				int publishRequestType, int modulePublishState) throws CoreException {
			new Thread("Test publish") {
				public void run() {
					try {
						publishStartSignal1[0].await();
					} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
					setDeployablePublishState2(reference, 
							ServerManagementAPIConstants.PUBLISH_STATE_NONE);
					publishDoneSignal1[0].countDown();
					
					try {
						publishStartSignal2[0].await();
					} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
					setDeployableState2(reference, 
							ServerManagementAPIConstants.STATE_STARTED);
					publishDoneSignal2[0].countDown();
				}
			}.start();
		}
		@Override
		protected void fireStateChanged(ServerState state) {
			// Do nothing
		}
		protected void setDeployablePublishState2(DeployableReference reference, int publishState) {
			setDeployablePublishState(reference, publishState);
		}

		protected void setDeployableState2(DeployableReference reference, int runState) {
			setDeployableState(reference, runState);
		}
	}

	private class CustomFileWatcherService extends FileWatcherService {
		private boolean enabled = false;
		
		private Path expectedPath;
		private WatchEvent.Kind<?> expectedKind;
		
		@Override
		protected void handleSingleEvent(WatchKey key, WatchEvent<?> event) {
			
			if( !getEnabled() ) {
				super.handleSingleEvent(key, event);
				return;
			}

			try {
				fileWatcherStartSignal1[0].await();			
			} catch (InterruptedException ie) {	 Thread.currentThread().interrupt();}
			
			super.handleSingleEvent(key, event);
			
			Path context = ((Path)key.watchable()).resolve((Path)event.context());
			if( context.equals(getExpectedPath()) && event.kind().equals(getExpectedKind())) {
				fileWatcherDoneSignal1[0].countDown();
			}
		}
		
		public synchronized void setEnabled(boolean w) {
			this.enabled = w;
		}
		private synchronized boolean getEnabled() {
			return this.enabled;
		}
		public synchronized void setExpectedEvent(Path p, WatchEvent.Kind<?> kind) {
			this.expectedPath = p;
			this.expectedKind = kind;
		}
		
		private Path getExpectedPath() {
			return expectedPath;
		}
		
		private WatchEvent.Kind<?> getExpectedKind() {
			return expectedKind;
		}
	}

	private IServerManagementModel createServerManagementModel(IFileWatcherService service) {
		IServerManagementModel model = mock(IServerManagementModel.class);
		service.start();
		when(model.getFileWatcherService()).thenReturn(service);
		return model; 
	}
}
