package org.pentaho.di.ui.spoon.git;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.ui.spoon.git.model.UIGit;

public class UIGitTest {
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    uiGit = new UIGit();
    uiGit.setAuthorName( "test <test@example.com>" );
    uiGit.setCommitMessage( "test" );
  }

  @Test
  public void testGetAuthorName() {
    assertEquals( "test <test@example.com>", uiGit.getAuthorName() );
  }

  @Test
  public void testGetCommitMessage() {
    assertEquals( "test", uiGit.getCommitMessage() );
  }
}
