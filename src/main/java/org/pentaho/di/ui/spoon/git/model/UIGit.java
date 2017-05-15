package org.pentaho.di.ui.spoon.git.model;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIGit extends XulEventSourceAdapter {

  private Git git;
  private String authorName;
  private String commitMessage;

  public Git getGit() {
    return git;
  }

  public void setGit( Git git ) {
    this.git = git;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName( String authorName ) {
    this.authorName = authorName;
    firePropertyChange( "authorName", null, authorName );
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public void setCommitMessage( String commitMessage ) {
    this.commitMessage = commitMessage;
    firePropertyChange( "commitMessage", null, commitMessage );
  }

  public RevCommit commit( String name, String email, String message ) throws Exception {
    return git.commit().setAuthor( name, email ).setMessage( message ).call();
  }
}
