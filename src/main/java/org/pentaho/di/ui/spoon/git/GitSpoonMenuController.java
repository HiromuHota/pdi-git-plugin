package org.pentaho.di.ui.spoon.git;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.pentaho.di.ui.spoon.ISpoonMenuController;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.git.dialog.CloneRepositoryDialog;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;

public class GitSpoonMenuController extends AbstractXulEventHandler implements ISpoonMenuController {

  private static GitSpoonMenuController instance = null;

  private GitSpoonMenuController() {
  }

  public static GitSpoonMenuController getInstance() {
    if ( instance == null ) {
      instance = new GitSpoonMenuController();
      Spoon.getInstance().addSpoonMenuController( instance );
    }
    return instance;
  }

  @Override
  public void updateMenu( Document doc ) {
  }

  @Override
  public String getName() {
    return "gitSpoonMenuController";
  }

  public void cloneRepo() {
    CloneRepositoryDialog dialog = new CloneRepositoryDialog( Spoon.getInstance().getShell() );
    if ( dialog.open() == Window.OK ) {
      String url = dialog.getURL();
      String directory = null;
      try {
        URIish uri = new URIish( url );
        directory = dialog.getDirectory() + File.separator + uri.getHumanishName();
        Git git = UIGit.cloneRepo( directory, url );
        git.close();
      } catch ( URISyntaxException e ) {
        e.printStackTrace();
      } catch ( TransportException e ) {
        if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
          try {
            FileUtils.delete( new File( directory ), FileUtils.RECURSIVE );
          } catch ( IOException e1 ) {
            e1.printStackTrace();
          }
        } else {
          e.printStackTrace();
        }
      } catch ( Exception e ) {
        e.printStackTrace();
      }
    }
  }
}
