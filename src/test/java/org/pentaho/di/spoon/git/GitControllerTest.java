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
    fail("Not yet implemented");
  }

  @Test
  public void testGetUnstagedObjects() {
    fail("Not yet implemented");
  }

  @Test
  public void testGetStagedObjects() {
    fail("Not yet implemented");
  }

  @Test
  public void testAddToIndex() {
    fail("Not yet implemented");
  }

  @Test
  public void testRemoveFromIndex() {
    fail("Not yet implemented");
  }

  @Test
  public void testCommit() {
    fail("Not yet implemented");
  }

  @Test
  public void testPush() {
    fail("Not yet implemented");
  }

}
