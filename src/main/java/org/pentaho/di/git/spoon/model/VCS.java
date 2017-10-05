package org.pentaho.di.git.spoon.model;

import java.io.InputStream;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.git.spoon.GitController;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

import com.google.common.annotations.VisibleForTesting;

public class VCS implements IVCS {

  protected static final Class<?> PKG = GitController.class;
  protected Shell shell;

  @VisibleForTesting
  void showMessageBox( String title, String message ) {
    MessageBox messageBox = new MessageBox( shell, SWT.OK );
    messageBox.setText( title );
    messageBox.setMessage( message );
    messageBox.open();
  }

  /**
   * Prompt the user to set username and password
   * @return true on success
   */
  protected boolean promptUsernamePassword() {
    UsernamePasswordDialog dialog = new UsernamePasswordDialog( shell );
    if ( dialog.open() == Window.OK ) {
      String username = dialog.getUsername();
      String password = dialog.getPassword();
      setCredential( username, password );
      return true;
    }
    return false;
  }

  @Override
  public String getDirectory() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isClean() {
    return false;
  }

  @Override
  public String getAuthorName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getAuthorName( String commitId ) {
    return null;
  }

  @Override
  public String getCommitMessage( String commitId ) {
    return null;
  }

  @Override
  public String getCommitId(String revstr) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getParentCommitId(String revstr) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getExpandedName( String name, String type ) {
    return name;
  }

  @Override
  public String getShortenedName(String name, String type) {
    // TODO Auto-generated method stub
    return null;
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
  public void addRemote(String s) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void removeRemote() throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean hasRemote() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean commit( String authorName, String message ) {
    return false;
  }

  @Override
  public UIRepositoryObjectRevisions getRevisions() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setCredential(String username, String password) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public List<UIFile> getUnstagedFiles() throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<UIFile> getStagedFiles() throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<UIFile> getStagedFiles(String oldCommitId, String newCommitId) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean hasStagedFiles() throws Exception {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void initRepo(String baseDirectory) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void openRepo(String baseDirectory) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void closeRepo() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void add( String filepattern ) {
  }

  @Override
  public void rm( String filepattern ) {
  }

  @Override
  public void reset( String name ) {
  }

  @Override
  public void reset( String name, String path ) {
  }

  @Override
  public void resetHard() throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean pull() {
    return false;
  }

  @Override
  public boolean push() {
    return false;
  }

  @Override
  public boolean push( String type ) {
    return false;
  }

  @Override
  public String show(String commitId) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String diff(String oldCommitId, String newCommitId) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String diff(String oldCommitId, String newCommitId, String file) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InputStream open(String file, String commitId) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void checkout(String name) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void checkout(String name, String path) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void createBranch(String value) throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deleteBranch(String name, boolean force) throws Exception {
    // TODO Auto-generated method stub
    
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
  public void setShell( Shell shell ) {
    this.shell = shell;
  }

  @Override
  public boolean merge() {
    return false;
  }

}
