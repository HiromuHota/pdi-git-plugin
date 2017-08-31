package org.pentaho.di.git.spoon.model;

import java.io.InputStream;
import java.util.List;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.transport.PushResult;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

public interface VCS {

  String WORKINGTREE = "WORKINGTREE";
  String INDEX = "INDEX";
  String GIT = "Git";

  String getDirectory();

  /**
   * If git is null or not
   * @return
   */
  boolean isOpen();

  /**
   * Get the author name as defined in the .git
   * @return
   */
  String getAuthorName();

  /**
   * Get the author name for a commit
   * @param commitId
   * @return
   * @throws Exception
   */
  String getAuthorName( String commitId ) throws Exception;

  String getCommitMessage( String commitId ) throws Exception;

  /**
   * Get SHA-1 commit Id
   * @param revstr: (e.g., HEAD, SHA-1)
   * @return
   * @throws Exception
   */
  String getCommitId( String revstr ) throws Exception;

  /**
   * Get the current branch
   * @return Current branch
   */
  String getBranch();

  /**
   * Get a list of local branches
   * @return
   */
  List<String> getLocalBranches();

  /**
   * Get a list of all (local + remote) branches
   * @return
   */
  List<String> getBranches();

  String getRemote();

  void addRemote( String s ) throws Exception;

  void removeRemote() throws Exception;

  boolean hasRemote();

  void commit( String authorName, String message ) throws Exception;

  UIRepositoryObjectRevisions getRevisions() throws Exception;

  /**
   * Get the list of unstaged files
   * @return
   * @throws Exception
   */
  List<UIFile> getUnstagedFiles() throws Exception;

  /**
   * Get the list of staged files
   * @return
   * @throws Exception
   */
  List<UIFile> getStagedFiles() throws Exception;

  /**
   * Get the list of changed files between two commits
   * @param oldCommitId
   * @param newCommitId
   * @return
   * @throws Exception
   */
  List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) throws Exception;

  boolean hasStagedFiles() throws Exception;

  void initRepo( String baseDirectory ) throws Exception;

  void openRepo( String baseDirectory ) throws Exception;

  void closeRepo();

  void add( String filepattern ) throws Exception;

  void rm( String filepattern ) throws Exception;

  void reset( String path ) throws Exception;

  void resetHard() throws Exception;

  /**
   * Equivalent of <tt>git fetch; git merge --ff</tt>
   *
   * @return PullResult
   * @throws Exception
   * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-pull.html">Git documentation about Pull</a>
   */
  PullResult pull() throws Exception;

  PullResult pull( String username, String password ) throws Exception;

  Iterable<PushResult> push() throws Exception;

  Iterable<PushResult> push( String username, String password ) throws Exception;

  /**
   * Show diff for a commit
   * @param commitId
   * @return
   * @throws Exception
   */
  String show( String commitId ) throws Exception;

  String diff( String oldCommitId, String newCommitId ) throws Exception;

  String diff( String oldCommitId, String newCommitId, String file ) throws Exception;

  InputStream open( String file, String commitId ) throws Exception;

  /**
   * Checkout a branch or commit
   * @param name
   * @throws Exception
   */
  void checkout( String name ) throws Exception;

  /**
   * Checkout a file of a particular branch or commit
   * @param name branch or commit; null for INDEX
   * @param path
   * @throws Exception
   */
  void checkout( String name, String path ) throws Exception;

  void createBranch( String value ) throws Exception;

  void deleteBranch( String name, boolean force ) throws Exception;

  MergeResult mergeBranch( String value ) throws Exception;

  MergeResult mergeBranch( String value, String mergeStrategy ) throws Exception;

}
