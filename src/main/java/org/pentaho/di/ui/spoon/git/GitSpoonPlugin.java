package org.pentaho.di.ui.spoon.git;

import org.pentaho.di.ui.spoon.SpoonLifecycleListener;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPlugin;
import org.pentaho.di.ui.spoon.SpoonPluginCategories;
import org.pentaho.di.ui.spoon.SpoonPluginInterface;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;

@SpoonPlugin( id = "GitSpoonPlugin", image = "" )
@SpoonPluginCategories( { "spoon" } )
public class GitSpoonPlugin implements SpoonPluginInterface, SpoonLifecycleListener {

  private GitPerspective perspective;
  private GitSpoonMenuController gitSpoonMenuController;

  public GitSpoonPlugin() throws XulException {
    this.perspective = new GitPerspective();
    this.gitSpoonMenuController = new GitSpoonMenuController();
  }

  @Override
  public void onEvent( SpoonLifeCycleEvent evt ) {
    // TODO Auto-generated method stub
  }

  @Override
  public void applyToContainer( String category, XulDomContainer container ) throws XulException {
    container.registerClassLoader( getClass().getClassLoader() );
    if ( category.equals( "spoon" ) ) {
      container.loadOverlay( "org/pentaho/di/ui/spoon/git/xul/git_spoon_overlays.xul" );
      container.addEventHandler( gitSpoonMenuController );
    }
  }

  @Override
  public SpoonLifecycleListener getLifecycleListener() {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public SpoonPerspective getPerspective() {
    return perspective;
  }

}
