package org.pentaho.di.spoon.git;

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

  public GitSpoonPlugin() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public void onEvent(SpoonLifeCycleEvent evt) {
    // TODO Auto-generated method stub
  }

  @Override
  public void applyToContainer(String category, XulDomContainer container) throws XulException {
    // TODO Auto-generated method stub
  }

  @Override
  public SpoonLifecycleListener getLifecycleListener() {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public SpoonPerspective getPerspective() {
    // TODO Auto-generated method stub
    return null;
  }

}
