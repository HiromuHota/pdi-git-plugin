package org.pentaho.di.git.spoon.model;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SVN extends VCS implements IVCS {

  private SVNClientManager clientManager = SVNClientManager.newInstance();
  private String directory;
  private File root;

  public SVN() {

  }

  @Override
  public String getDirectory() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getAuthorName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getAuthorName( String commitId ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCommitMessage( String commitId ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCommitId( String revstr ) throws Exception {
    return revstr;
  }

  @Override
  public String getParentCommitId( String revstr ) throws Exception {
    return getCommitId( Long.toString( ( Long.parseLong( revstr ) - 1 ) ) );
  }

  @Override
  public String getBranch() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<String> getLocalBranches() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<String> getBranches() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getRemote() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addRemote( String s ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeRemote() throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean hasRemote() {
    return true;
  }

  @Override
  public void commit( String authorName, String message ) {
    try {
      clientManager.getCommitClient().doCommit( new File[]{ root }, true, message, null, null, false, false, SVNDepth.INFINITY );
    } catch ( SVNException e ) {
      promptUsernamePassword();
      commit( authorName, message );
    }
  }

  @Override
  public UIRepositoryObjectRevisions getRevisions() throws Exception {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    long startRevision = 1;
    clientManager.getLogClient().doLog( new File[]{ root }, SVNRevision.create( startRevision ), SVNRevision.WORKING,
        false, false, 100, logEntry -> {
        PurObjectRevision rev = new PurObjectRevision(
          logEntry.getRevision(),
          logEntry.getAuthor(),
          logEntry.getDate(),
          logEntry.getMessage() );
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
      } );
    if ( getStagedFiles().size() != 0 ) {
      PurObjectRevision rev = new PurObjectRevision(
          WORKINGTREE,
          "*",
          new Date(),
          " // " + VCS.WORKINGTREE );
      revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
    }
    Collections.reverse( revisions );
    return revisions;
  }

  @Override
  public List<UIFile> getUnstagedFiles() throws Exception {
    return new ArrayList<UIFile>();
  }

  @Override
  public List<UIFile> getStagedFiles() throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    clientManager.getStatusClient().doStatus( root, SVNRevision.WORKING, SVNDepth.INFINITY, false,
      false, false, false, status -> {
        files.add( new UIFile( status.getRepositoryRelativePath(), convertTypeToGit( status.getNodeStatus().getCode() ), true ) );
      },
      null );
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    clientManager.getDiffClient().doDiffStatus( new File( directory ), SVNRevision.create( Long.parseLong( oldCommitId ) ),
        new File( directory ), SVNRevision.create( Long.parseLong( newCommitId ) ),
        SVNDepth.INFINITY, true, diffStatus -> {
        files.add( new UIFile( diffStatus.getPath(), convertTypeToGit( diffStatus.getModificationType().getCode() ), false ) );
      }
    );
    return files;
  }

  @Override
  public boolean hasStagedFiles() throws Exception {
    return !getStagedFiles().isEmpty();
  }

  @Override
  public void initRepo( String baseDirectory ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void openRepo( String baseDirectory ) throws Exception {
    directory = baseDirectory;
    root = new File( directory );
  }

  @Override
  public void closeRepo() {
    root = null;
  }

  @Override
  public void add( String filepattern ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void rm( String filepattern ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void reset( String path ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void resetHard() throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean pull() {
    try {
      clientManager.getUpdateClient().doUpdate( root, SVNRevision.HEAD, SVNDepth.INFINITY, false, false );
    } catch ( SVNException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }
    return true;
  }

  @Override
  public boolean push() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String show( String commitId ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String diff( String oldCommitId, String newCommitId ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String diff( String oldCommitId, String newCommitId, String file ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InputStream open( String file, String commitId ) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void checkout( String name ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void checkout( String name, String path ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void createBranch( String value ) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void deleteBranch( String name, boolean force ) throws Exception {
    // TODO Auto-generated method stub

  }

  private static ChangeType convertTypeToGit( char type ) {
    switch ( type ) {
      case SVNLogEntryPath.TYPE_ADDED:
        return ChangeType.ADD;
      case SVNLogEntryPath.TYPE_DELETED:
        return ChangeType.DELETE;
      case SVNLogEntryPath.TYPE_MODIFIED:
        return ChangeType.MODIFY;
      case SVNLogEntryPath.TYPE_REPLACED:
        return ChangeType.MODIFY;
      default:
        return null;
    }
  }

  @Override
  public boolean isClean() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String getExpandedName(String name, String type) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getShortenedName(String name, String type) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setCredential(String username, String password) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void reset(String name, String path) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean push(String type) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public List<String> getTags() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void createTag(String name) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deleteTag(String name) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setShell(Shell shell) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean merge() {
    // TODO Auto-generated method stub
    return false;
  }
}
