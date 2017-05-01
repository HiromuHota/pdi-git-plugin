package org.pentaho.di.spoon.git;

import static org.junit.Assert.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class GitControllerTest extends RepositoryTestCase {

  private GitController controller = new GitController();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    Git git = new Git( db );
    controller.setGit( git );
  }

  @Test
  public void testGetPath() {
    assertEquals( db.getDirectory().getParent(), controller.getPath() );
  }

  @Test
  public void testGetRevisionObjects() {
  }

  @Test
  public void testGetUnstagedObjects() {
  }

  @Test
  public void testGetStagedObjects() {
  }

  @Test
  public void testAddToIndex() {
  }

  @Test
  public void testRemoveFromIndex() {
  }

  @Test
  public void testCommit() {
  }

  @Test
  public void testPush() {
  }

}
