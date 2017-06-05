package org.pentaho.di.ui.spoon.git;

import org.pentaho.di.ui.spoon.ISpoonMenuController;
import org.pentaho.di.ui.spoon.Spoon;
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
  }
}
