package org.pentaho.di.git.spoon;

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
import org.pentaho.di.git.spoon.dialog.CloneRepositoryDialog;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.UIGit;

@RunWith( MockitoJUnitRunner.class )
public class GitSpoonMenuControllerTest extends RepositoryTestCase {

  @Spy
  private GitSpoonMenuController controller;
  @Mock
  private CloneRepositoryDialog cloneRepositoryDialog;
  @Mock
  private UsernamePasswordDialog usernamePasswordDialog;
  @Mock
  private GitRepository repo;
  @Mock
  private GitController gitController;
  @Mock
  private UIGit git;

  @Rule
  public TemporaryFolder dstFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    controller.setGitController( gitController );
    doReturn( cloneRepositoryDialog ).when( controller ).getCloneRepositoryDialog( any( GitRepository.class ) );
    doReturn( usernamePasswordDialog ).when( controller ).getUsernamePasswordDialog();
    doReturn( null ).when( controller ).getShell();
    doReturn( git ).when( controller ).getVCS( any( GitRepository.class ) );
    doNothing().when( controller ).saveRepository( any( GitRepository.class ) );
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
    doReturn( true ).when( git ).cloneRepo( anyString(), anyString() );

    controller.cloneRepo();

    verify( controller ).showMessageBox( eq( "Success" ), anyString() );
  }
}
