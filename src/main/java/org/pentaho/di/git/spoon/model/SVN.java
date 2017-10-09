package org.pentaho.di.git.spoon.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Revision;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
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
import org.tigris.subversion.svnclientadapter.SVNStatusKind;
import org.tigris.subversion.svnclientadapter.SVNUrl;
import org.tigris.subversion.svnclientadapter.javahl.AbstractJhlClientAdapter;
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
  private File root;

  public SVN() {
    svnClient = SVNClientAdapterFactory.createSVNClient( JhlClientAdapterFactory.JAVAHL_CLIENT );
  }

  @Override
  public void add( String name ) {
    try {
      svnClient.addFile( new File( directory, name ) );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
  }

  @Override
  public void revertPath( String path ) {
    try {
      svnClient.revert( new File( directory + File.separator + FilenameUtils.separatorsToSystem( path ) ), false );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
  }

  @Override
  public void resetPath( String path ) {
    String fullpath = directory + File.separator + FilenameUtils.separatorsToSystem( path );
    try {
      ISVNStatus status = svnClient.getSingleStatus( new File( fullpath ) );
      if ( !status.getTextStatus().toString().equals( "added" ) ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Versioned files are always staged for the next commit" );
        return;
      }
    } catch ( SVNClientException e1 ) {
      e1.printStackTrace();
    }
    AbstractJhlClientAdapter client = (AbstractJhlClientAdapter) svnClient;
    try {
      client.getSVNClient().remove(
          new HashSet<String>( Arrays.asList( fullpath ) ),
          false, true, null, null, null );
    } catch ( ClientException e ) {
      e.printStackTrace();
    }
  }

  @Override
  public String diff( String oldCommitId, String newCommitId, String file ) {
    AbstractJhlClientAdapter client = (AbstractJhlClientAdapter) svnClient;
    OutputStream outStream = new ByteArrayOutputStream();
    try {
      String target = null;
      if ( newCommitId.equals( IVCS.INDEX ) ) {
        target = directory + File.separator + FilenameUtils.separatorsToSystem( file );
      } else {
        target = svnClient.getInfo( root ).getUrl().toString() + "/" + file;
      }
      client.getSVNClient().diff(
          target,
          null, resolveRevision( oldCommitId ), resolveRevision( newCommitId ),
          null, outStream, Depth.infinityOrImmediates( true ), null, true, false, false, false, false, false );
      return outStream.toString().replaceAll( "\n", System.getProperty( "line.separator" ) );
    } catch ( Exception e ) {
      return e.getMessage();
    }
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
  public String getCommitId( String revstr ) {
    return revstr;
  }

  @Override
  public String getParentCommitId( String revstr ) {
    try {
      return getCommitId( Long.toString( ( Long.parseLong( revstr ) - 1 ) ) );
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    }
    return null;
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
    } catch ( Exception e ) {
      e.printStackTrace();
      return false;
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
    if ( !isClean() ) {
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
    List<UIFile> files = new ArrayList<UIFile>();
    svnClient.getStatus( root, true, false, false,
      false, false, ( String path, ISVNStatus status ) -> {
        if ( status.getTextStatus().equals( SVNStatusKind.UNVERSIONED ) ) {
          files.add( new UIFile( path.replaceFirst( directory.replace( "\\", "/" ) + "/", "" ), convertTypeToGit( status.getTextStatus().toString() ), false ) );
        }
      } );
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles() throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    svnClient.getStatus( root, true, false, false,
      false, false, ( String path, ISVNStatus status ) -> {
        if ( !status.getTextStatus().equals( SVNStatusKind.UNVERSIONED ) ) {
          files.add( new UIFile( path.replaceFirst( directory.replace( "\\", "/" ) + "/", "" ), convertTypeToGit( status.getTextStatus().toString() ), true ) );
        }
      } );
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    Arrays.stream( svnClient.diffSummarize( svnClient.getInfo( root ).getUrl(), null, new SVNRevision.Number( Long.parseLong( oldCommitId ) ),
         new SVNRevision.Number( Long.parseLong( newCommitId ) ),
        100, true ) ).forEach( diffStatus -> {
          files.add( new UIFile( diffStatus.getPath().replaceFirst( directory.replace( "\\", "\\\\" ), "" ),
              convertTypeToGit( diffStatus.getDiffKind().toString() ), false ) );
        }
    );
    return files;
  }

  @Override
  public boolean hasStagedFiles() throws Exception {
    return !getStagedFiles().isEmpty();
  }

  @Override
  public boolean isClean() {
    try {
      return getStagedFiles().isEmpty() & getUnstagedFiles().isEmpty();
    } catch ( Exception e ) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public InputStream open( String file, String commitId ) {
    try {
      if ( commitId.equals( IVCS.WORKINGTREE ) ) {
        return new FileInputStream( new File( directory + File.separator + FilenameUtils.separatorsToSystem( file ) ) );
      } else if ( commitId.equals( Constants.HEAD ) ) {
        return svnClient.getContent( new File( directory + File.separator + FilenameUtils.separatorsToSystem( file ) ),
          SVNRevision.HEAD );
      } else {
        return svnClient.getContent( svnClient.getInfo( root ).getUrl().appendPath( file ),
          new SVNRevision.Number( Long.parseLong( commitId ) ) );
      }
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    } catch ( FileNotFoundException e ) {
      e.printStackTrace();
    }
    return null;
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
    if ( type.equals( "added" ) | type.equals( "unversioned" ) ) {
      return ChangeType.ADD;
    } else if ( type.equals( "deleted" ) ) {
      return ChangeType.DELETE;
    } else if ( type.equals( "modified" ) | type.equals( "normal" ) ) {
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

  private Revision resolveRevision( String commitId ) {
    if ( commitId.equals( Constants.HEAD ) ) {
      return Revision.HEAD;
    } else if ( commitId.equals( IVCS.INDEX ) ) {
      return Revision.WORKING;
    } else {
      return Revision.getInstance( Long.parseLong( commitId ) );
    }
  }
}
