/*
 * Copyright 2017 Hitachi America, Ltd., R&D.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pentaho.di.git.spoon;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.URIish;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.git.spoon.model.IVCS;
import org.pentaho.di.git.spoon.model.UIFile;
import org.pentaho.di.git.spoon.model.UIGit;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.dom.DocumentFactory;
import org.pentaho.ui.xul.dom.dom4j.ElementDom4J;
import org.pentaho.ui.xul.swt.custom.MessageDialogBase;
import org.pentaho.ui.xul.util.XulDialogCallback;

public class GitControllerTest {

  private static final Class<?> PKG = RepositoryExplorer.class;

  private static final String CONFIRMBOX = "confirmbox";
  private static final String MESSAGEBOX = "messagebox";
  private static final String PROMPTBOX = "promptbox";
  private Document document;
  private GitController controller;
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    controller = spy( new GitController() );
    controller.setAuthorName( "test <test@example.com>" );
    controller.setCommitMessage( "test" );
    uiGit = mock( UIGit.class );
    controller.setVCS( uiGit );
    doNothing().when( controller ).fireSourceChanged();
    doReturn( false ).when( controller ).anyChangedTabs();
    doNothing().when( controller ).addGraph( any( EngineMetaInterface.class ), anyString() );
    doNothing().when( controller ).loadMainPerspective();

    DocumentFactory.registerElementClass( ElementDom4J.class );
    document = mock( Document.class );
    XulDomContainer xulDomContainer = mock( XulDomContainer.class );
    when( xulDomContainer.getDocumentRoot() ).thenReturn( document );
    controller.setXulDomContainer( xulDomContainer );
  }

  @Test
  public void testGetAuthorName() {
    assertEquals( "test <test@example.com>", controller.getAuthorName() );
  }

  @Test
  public void testGetCommitMessage() {
    assertEquals( "test", controller.getCommitMessage() );
  }

  @Test
  public void shouldInitializeGitOnAccept() throws Exception {
    XulConfirmBox confirm = new XulConfirmBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.createElement( CONFIRMBOX ) ).thenReturn( confirm );
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );

    controller.initGit( "random-path" );

    verify( uiGit ).initRepo( anyString() );
  }

  @Test
  public void shouldNotInitializeGitOnCencel() throws Exception {
    XulConfirmBox confirm = new XulConfirmBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.createElement( CONFIRMBOX ) ).thenReturn( confirm );

    controller.initGit( "random-path" );

    verify( uiGit, never() ).initRepo( anyString() );
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
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );
    doReturn( false ).when( uiGit ).hasStagedFiles();

    controller.commit();

    verify( uiGit, never() ).commit( any(), anyString() );
  }

  @Test
  public void shouldNotCommitWhenEmptyCommitMessage() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedFiles();
    controller.setCommitMessage( "" );

    controller.commit();

    verify( uiGit, never() ).commit( any(), anyString() );
  }

  @Test
  public void shouldCommit() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedFiles();

    controller.commit();

    verify( uiGit ).commit( any(), anyString() );
  }

  @Test
  public void shouldFireSourceChangedWhenSuccessful() throws Exception {
    doReturn( true ).when( uiGit ).hasRemote();
    doReturn( true ).when( uiGit ).isClean();
    doReturn( true ).when( uiGit ).pull();

    controller.pull();

    verify( controller ).fireSourceChanged();
  }

  @Test
  public void shouldShowSuccessWhenPushSucceeds() throws Exception {
    XulMessageBox message = spy( new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT ) );
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasRemote();
    PushResult result = mock( PushResult.class );
    doReturn( new URIish( "https://test.example.com" ) ).when( result ).getURI();
    RemoteRefUpdate update = mock( RemoteRefUpdate.class );
    when( update.getStatus() ).thenReturn( Status.OK );
    when( result.getRemoteUpdates() ).thenReturn( Arrays.asList( update ) );

    controller.push();
    controller.push( IVCS.TYPE_BRANCH );

    verify( uiGit ).push( "default" );
    verify( uiGit ).push( IVCS.TYPE_BRANCH );
  }

  @Test
  public void shouldNotEditRemoteOnCancel() throws Exception {
    XulPromptBox prompt = new XulPromptBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.createElement( PROMPTBOX ) ).thenReturn( prompt );

    controller.editRemote();

    verify( uiGit, never() ).addRemote( anyString() );
  }

  @Test
  public void testCheckout() throws Exception {
    PurObjectRevision rev = new PurObjectRevision( "000", "test", new Date(), "hoge" );
    doReturn( new UIRepositoryObjectRevision( rev ) ).when( controller ).getFirstSelectedRevision();
    doCallRealMethod().when( uiGit ).getExpandedName( anyString(), anyString() );
    doReturn( "000000" ).when( uiGit ).getCommitId( "000" );

    controller.checkout();
    verify( uiGit ).checkout( "000000" );
  }

  @Test
  public void testOpenFile() throws Exception {
    UIFile file = new UIFile( "test.ktr", ChangeType.MODIFY, true );
    doReturn( Collections.singletonList( file ) ).when( controller ).getSelectedChangedFiles();
    doReturn( new FileInputStream( new File( "src/test/resources/r1.ktr" ) ) ).when( uiGit ).open( "test.ktr", IVCS.WORKINGTREE );
    controller.openFile();
    verify( controller ).loadMainPerspective();

    file = new UIFile( "test.kjb", ChangeType.MODIFY, true );
    doReturn( false ).when( controller ).isOnlyWIP();
    doReturn( new UIRepositoryObjectRevision( new PurObjectRevision( "XXX", "", null, "" ) ) ).when( controller ).getFirstSelectedRevision();
    doReturn( Collections.singletonList( file ) ).when( controller ).getSelectedChangedFiles();
    doReturn( new FileInputStream( new File( "src/test/resources/r1.ktr" ) ) ).when( uiGit ).open( "test.kjb", "XXX" );
    controller.openFile();
    verify( controller, times( 2 ) ).loadMainPerspective();
  }

  @Test
  public void testVisualDiff() throws Exception {
    XulMessageBox message = spy( new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT ) );
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );
    UIFile file = new UIFile( "test.txt", ChangeType.MODIFY, true );
    doReturn( Collections.singletonList( file ) ).when( controller ).getSelectedChangedFiles();
    controller.visualdiff();
    verify( message ).setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );

    // .ktr
    file = new UIFile( "test.ktr", ChangeType.MODIFY, true );
    doReturn( Collections.singletonList( file ) ).when( controller ).getSelectedChangedFiles();
    doReturn( new FileInputStream( new File( "src/test/resources/r1.ktr" ) ) ).when( uiGit ).open( "test.ktr", Constants.HEAD );
    doReturn( new FileInputStream( new File( "src/test/resources/r2.ktr" ) ) ).when( uiGit ).open( "test.ktr", IVCS.WORKINGTREE );
    controller.visualdiff();
    verify( uiGit ).open( "test.ktr", Constants.HEAD );
    verify( uiGit ).open( "test.ktr", IVCS.WORKINGTREE );
    verify( controller ).loadMainPerspective();

    // conflicted ktr
    file = new UIFile( "test.kjb.ours", ChangeType.ADD, false );
    File dir = File.createTempFile( "git_test_", "_controller" );
    dir.delete();
    dir.mkdir();
    File ours = new File( dir.getPath(), "test.kjb.ours" );
    File theirs = new File( dir.getPath(), "test.kjb.theirs" );
    FileUtils.copyFile( new File( "src/test/resources/r1.kjb" ), ours );
    FileUtils.copyFile( new File( "src/test/resources/r2.kjb" ), theirs );
    doReturn( dir.getPath() ).when( uiGit ).getDirectory();
    doReturn( Collections.singletonList( file ) ).when( controller ).getSelectedChangedFiles();
    doReturn( new FileInputStream( ours ) ).when( uiGit ).open( "test.kjb.ours", IVCS.WORKINGTREE );
    doReturn( new FileInputStream( theirs ) ).when( uiGit ).open( "test.kjb.theirs", IVCS.WORKINGTREE );
    controller.visualdiff();
    FileUtils.deleteDirectory( dir );
    verify( uiGit ).open( "test.kjb.ours", IVCS.WORKINGTREE );
    verify( uiGit ).open( "test.kjb.theirs", IVCS.WORKINGTREE );
    verify( controller, times( 2 ) ).loadMainPerspective();
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

  static class XulMessageBoxMock extends MessageDialogBase implements XulMessageBox {
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

  private static class XulPromptBoxMock extends MessageDialogBase implements XulPromptBox {
    private final XulDialogCallback.Status status;
    private String value;

    public XulPromptBoxMock( XulDialogCallback.Status status ) {
      super( PROMPTBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, value );
      }
      return 0;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public void setValue( String value ) {
      this.value = value;
    }
  }
}
