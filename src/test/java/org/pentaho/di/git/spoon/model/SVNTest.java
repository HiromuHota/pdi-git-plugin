package org.pentaho.di.git.spoon.model;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

public class SVNTest {
  private VCS vcs;

  @Before
  public void setUp() throws Exception {
    vcs = spy( new SVN() );
    doNothing().when( vcs ).showMessageBox( anyString(), anyString() );
    vcs.openRepo( "/Users/hiromu/workspace/new-repo" );
  }

  @Test
  public void testCommit() {
    UIRepositoryObjectRevisions revisions = vcs.getRevisions();
    if ( vcs.getStagedFiles().size() != 0 ) {
      assertEquals( VCS.WORKINGTREE, revisions.get( 0 ).getName() );
      vcs.setCredential( "user", "password" );
      vcs.commit( "user", "message" );
      assertEquals( revisions.size(), vcs.getRevisions().size() );
    }
  }

  @Test
  public void testGetRevisions() throws Exception {
    UIRepositoryObjectRevisions revisions = vcs.getRevisions();
    assertEquals( "1", revisions.get( revisions.size() - 1 ).getName() );
  }

//  @Test
//  public void testGetUnstagedFiles() throws Exception {
//    List<UIFile> files = vcs.getUnstagedFiles();
//    assertTrue( files.isEmpty() );
//  }

  @Test
  public void testPull() throws Exception {
    vcs.pull();
  }

  @Test
  public void testGetRemote() {
    String remote = vcs.getRemote();
    assertNotNull( remote );
  }
}
