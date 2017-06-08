package org.pentaho.di.ui.spoon.git;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.eclipse.jface.window.Window;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.Spy;
import org.pentaho.di.ui.spoon.git.dialog.CloneRepositoryDialog;
import org.pentaho.di.ui.spoon.git.dialog.UsernamePasswordDialog;

@RunWith( MockitoJUnitRunner.class )
public class GitSpoonMenuControllerTest extends RepositoryTestCase {

  @Spy
  private GitSpoonMenuController controller;
  @Mock
  private CloneRepositoryDialog cloneRepositoryDialog;
  @Mock
  private UsernamePasswordDialog usernamePasswordDialog;

  @Rule
  public TemporaryFolder dstFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    doReturn( cloneRepositoryDialog ).when( controller ).getCloneRepositoryDialog();
    doReturn( usernamePasswordDialog ).when( controller ).getUsernamePasswordDialog();
    doNothing().when( controller ).showMessageBox( anyString(), anyString() );
  }

  @Test
  public void testShouldNotCloneOnCancel() throws Exception {
    when( cloneRepositoryDialog.open() ).thenReturn( Window.CANCEL );

    controller.cloneRepo();

    verify( cloneRepositoryDialog, never() ).getDirectory();
  }

  @Test
  public void testCloneShouldSucceed() throws Exception {
    when( cloneRepositoryDialog.open() ).thenReturn( Window.OK );
    when( cloneRepositoryDialog.getURL() ).thenReturn( db.getDirectory().getPath() );
    when( cloneRepositoryDialog.getDirectory() ).thenReturn( dstFolder.getRoot().getPath() );

    controller.cloneRepo();

    verify( controller ).showMessageBox( eq( "Success" ), anyString() );
  }

  @Test
  public void testCloneShouldFailWhenDirNotFound() throws Exception {
    when( cloneRepositoryDialog.open() ).thenReturn( Window.OK );
    when( cloneRepositoryDialog.getURL() ).thenReturn( db.getDirectory().getPath() );
    when( cloneRepositoryDialog.getDirectory() ).thenReturn( dstFolder.getRoot().getPath() + "notexists" );

    controller.cloneRepo();

    verify( controller ).showMessageBox( eq( "Error" ), anyString() );
  }

  @Test
  public void testCloneShouldFailWhenURLNotFound() throws Exception {
    when( cloneRepositoryDialog.open() ).thenReturn( Window.OK );
    when( cloneRepositoryDialog.getURL() ).thenReturn( "fakeURL" );
    when( cloneRepositoryDialog.getDirectory() ).thenReturn( dstFolder.getRoot().getPath() );

    controller.cloneRepo();

    verify( controller ).showMessageBox( eq( "Error" ), anyString() );
  }
}
