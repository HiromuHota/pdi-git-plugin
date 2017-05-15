package org.pentaho.di.ui.spoon.git;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.dom.DocumentFactory;
import org.pentaho.ui.xul.dom.dom4j.ElementDom4J;
import org.pentaho.ui.xul.swt.custom.MessageDialogBase;
import org.pentaho.ui.xul.util.XulDialogCallback;

public class GitControllerTest extends RepositoryTestCase {

  private static final String CONFIRMBOX = "confirmbox";
  private static final String MESSAGEBOX = "messagebox";
  private Document document;
  private GitController controller;
  private Git git;
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    git = new Git( db );
    controller = spy( new GitController() );
    uiGit = mock( UIGit.class );
    controller.setUIGit( uiGit );
    doNothing().when( controller ).fireSourceChanged();

    DocumentFactory.registerElementClass( ElementDom4J.class );
    document = mock( Document.class );
    XulDomContainer xulDomContainer = mock( XulDomContainer.class );
    when( xulDomContainer.getDocumentRoot() ).thenReturn( document );
    controller.setXulDomContainer( xulDomContainer );
  }

  @Test
  public void shouldInitializeGitOnAccept() throws Exception {
    XulConfirmBox prompt = new XulConfirmBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( CONFIRMBOX ) ).thenReturn( prompt );
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );

    controller.initGit( "random-path" );

    verify( uiGit ).initGit( anyString() );
  }

  @Test
  public void shouldNotInitializeGitOnCencel() throws Exception {
    XulConfirmBox prompt = new XulConfirmBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.getElementById( CONFIRMBOX ) ).thenReturn( prompt );

    controller.initGit( "random-path" );

    verify( uiGit, never() ).initGit( anyString() );
  }

  @Test
  public void testAddToIndex() {
  }

  @Test
  public void testRemoveFromIndex() {
  }

  @Test
  public void shouldNotCommitWhenNoStagedObjects() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( false ).when( uiGit ).hasStagedObjects();

    controller.commit();

    verify( uiGit, never() ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void shouldNotCommitWhenAuthorNameMalformed() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedObjects();
    doReturn( "random author" ).when( uiGit ).getAuthorName();

    controller.commit();

    verify( uiGit, never() ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void shouldCommit() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedObjects();
    doReturn( "test <test@example.com>" ).when( uiGit ).getAuthorName();
    doReturn( "test" ).when( uiGit ).getCommitMessage();

    controller.commit();

    verify( uiGit ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void testPush() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    // create other repository
    Repository db2 = createWorkRepository();

    // setup the first repository
    final StoredConfig config = db.getConfig();
    RemoteConfig remoteConfig = new RemoteConfig( config, "origin" );
    URIish uri = new URIish( db2.getDirectory().toURI().toURL() );
    remoteConfig.addURI( uri );
    remoteConfig.update( config );
    config.save();

    // commit a test file
    writeTrashFile( "a.ktr", "content" );
    git.add().addFilepattern( "." ).call();
    RevCommit commit = git.commit().setAuthor( "test", "test@example.com" ).setMessage( "initial commit" ).call();

    // push
    controller.push();

    assertEquals( commit.getId(), db2.resolve( commit.getId().getName() + "^{commit}" ) );
  }

  @Test
  public void testDeleteRemote() throws Exception {
    RemoteConfig remoteConfig = setupRemote();

    RemoteConfig remote = controller.deleteRemote();

    // assert that the removed remote is the initial remote
    assertRemoteConfigEquals( remoteConfig, remote );
    // assert that there are no remotes left
    assertTrue( RemoteConfig.getAllRemoteConfigs( db.getConfig() ).isEmpty() );
  }

  private RemoteConfig setupRemote() throws IOException, URISyntaxException {
     // create another repository
    Repository remoteRepository = createWorkRepository();

    // set it up as a remote to this repository
    final StoredConfig config = db.getConfig();
    RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );

    RefSpec refSpec = new RefSpec();
    refSpec = refSpec.setForceUpdate( true );
    refSpec = refSpec.setSourceDestination( Constants.R_HEADS + "*",
                  Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/*" );
    remoteConfig.addFetchRefSpec( refSpec );

    URIish uri = new URIish( remoteRepository.getDirectory().toURI().toURL() );
    remoteConfig.addURI( uri );

    remoteConfig.update( config );
    config.save();

    return remoteConfig;
  }

  private void assertRemoteConfigEquals( RemoteConfig expected, RemoteConfig actual ) {
    assertEquals( expected.getName(), actual.getName() );
    assertEquals( expected.getURIs(), actual.getURIs() );
    assertEquals( expected.getPushURIs(), actual.getPushURIs() );
    assertEquals( expected.getFetchRefSpecs(), actual.getFetchRefSpecs() );
    assertEquals( expected.getPushRefSpecs(), actual.getPushRefSpecs() );
  }

  private static class XulConfirmBoxMock extends MessageDialogBase implements XulConfirmBox {
    private final XulDialogCallback.Status status;

    public XulConfirmBoxMock( XulDialogCallback.Status status ) {
      super( CONFIRMBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, null );
      }
      return 0;
    }
  }

  private static class XulMessageBoxMock extends MessageDialogBase implements XulMessageBox {
    private final XulDialogCallback.Status status;

    public XulMessageBoxMock( XulDialogCallback.Status status ) {
      super( MESSAGEBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, null );
      }
      return 0;
    }
  }
}
