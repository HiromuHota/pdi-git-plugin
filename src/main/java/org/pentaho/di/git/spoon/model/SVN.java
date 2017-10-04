package org.pentaho.di.git.spoon.model;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNStatus;
import org.tigris.subversion.svnclientadapter.SVNClientAdapterFactory;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;
import org.tigris.subversion.svnclientadapter.javahl.JhlClientAdapterFactory;

public class SVN extends VCS implements IVCS {

  static {
    try {
      JhlClientAdapterFactory.setup();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
  }

  private ISVNClientAdapter svnClient;
  private String directory;
  private File root;

  public SVN() {
    svnClient = SVNClientAdapterFactory.createSVNClient( JhlClientAdapterFactory.JAVAHL_CLIENT );
  }

  @Override
  public String getAuthorName( String commitId ) {
    UIRepositoryObjectRevisions revisions = getRevisions();
    UIRepositoryObjectRevision revision = revisions.stream()
        .filter( rev -> rev.getName().equals( commitId ) )
        .findFirst().get();
    return revision.getLogin();
  }

  @Override
  public String getCommitMessage( String commitId ) {
    UIRepositoryObjectRevisions revisions = getRevisions();
    UIRepositoryObjectRevision revision = revisions.stream()
        .filter( rev -> rev.getName().equals( commitId ) )
        .findFirst().get();
    return revision.getComment();
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
    try {
      return svnClient.getInfo( root ).getUrlString().replaceFirst( getRemote() + "/", "" );
    } catch ( SVNClientException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public List<String> getBranches() {
    ISVNDirEntry[] dirEntries = null;
    SVNUrl url = null;
    try {
      url = new SVNUrl( getRemote() );
      dirEntries = svnClient.getList( url, SVNRevision.HEAD, true );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
      return null;
    } catch ( MalformedURLException e ) {
      e.printStackTrace();
    }
    return Arrays.stream( dirEntries )
      .filter( dirEntry -> dirEntry.getNodeKind() == SVNNodeKind.DIR )
      .map( dirEntry -> dirEntry.getPath() )
      .collect( Collectors.toList() );
  }

  @Override
  public String getRemote() {
    try {
      return svnClient.getInfo( root ).getRepository().toString();
    } catch ( SVNClientException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public boolean hasRemote() {
    return true;
  }

  @Override
  public boolean commit( String authorName, String message ) {
    try {
      svnClient.commit( new File[]{ root }, message, true );
      return true;
    } catch ( SVNClientException e ) {
      if ( promptUsernamePassword() ) {
        return commit( authorName, message );
      } else {
        return false;
      }
    }
  }

  @Override
  public UIRepositoryObjectRevisions getRevisions() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    long startRevision = 1;
    ISVNLogMessage[] messages = null;
    try {
      messages = svnClient.getLogMessages( root, new SVNRevision.Number( startRevision ), SVNRevision.HEAD, false, false, 100 );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        promptUsernamePassword();
        return getRevisions();
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
    Arrays.stream( messages ).forEach( logMessage -> {
      PurObjectRevision rev = new PurObjectRevision(
          logMessage.getRevision().toString(),
          logMessage.getAuthor(),
          logMessage.getDate(),
          logMessage.getMessage() );
      revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
    } );
    List<UIFile> stagedFiles = null;
    try {
      stagedFiles = getStagedFiles();
    } catch ( Exception e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if ( stagedFiles.size() != 0 ) {
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
    svnClient.getStatus( root, true, false, false,
      false, false, ( String path, ISVNStatus status ) -> {
        files.add( new UIFile( path.replaceFirst( directory, "" ), convertTypeToGit( status.getTextStatus().toString() ), true ) );
      } );
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    Arrays.stream( svnClient.diffSummarize( svnClient.getInfo( root ).getRepository(), null, new SVNRevision.Number( Long.parseLong( oldCommitId ) ),
         new SVNRevision.Number( Long.parseLong( newCommitId ) ),
        100, true ) ).forEach( diffStatus -> {
          files.add( new UIFile( diffStatus.getPath().replaceFirst( directory, "" ), convertTypeToGit( diffStatus.getDiffKind().toString() ), false ) );
        }
    );
    return files;
  }

  @Override
  public boolean hasStagedFiles() throws Exception {
    return !getStagedFiles().isEmpty();
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
  public void checkout( String name ) throws Exception {
    svnClient.switchToUrl( root, new SVNUrl( getRemote() + "/" + name ), SVNRevision.HEAD, true );
  }

  private static ChangeType convertTypeToGit( String type ) {
    if ( type.equals( "added" ) ) {
      return ChangeType.ADD;
    } else if ( type.equals( "deleted" ) ) {
      return ChangeType.DELETE;
    } else if ( type.equals( "modified" ) ) {
      return ChangeType.MODIFY;
    } else if ( type.equals( "replaced" ) ) {
      return ChangeType.MODIFY;
    } else {
      return null;
    }
  }

  @Override
  public void setCredential( String username, String password ) {
    svnClient.setUsername( username );
    svnClient.setPassword( password );
  }

  private ISVNLogMessage resolve( String commitId ) {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    ISVNLogMessage[] messages = null;
    try {
      messages = svnClient.getLogMessages( root, new SVNRevision.Number( Long.parseLong( commitId ) ), SVNRevision.HEAD, false, false, 1 );
    } catch ( SVNClientException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
    Arrays.stream( messages ).forEach( logMessage -> {
      PurObjectRevision rev = new PurObjectRevision(
          logMessage.getRevision().toString(),
          logMessage.getAuthor(),
          logMessage.getDate(),
          logMessage.getMessage() );
      revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
    } );
  }
}
