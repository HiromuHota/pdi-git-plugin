package org.pentaho.ui.xul.util;

import org.pentaho.ui.xul.XulComponent;

public interface XulDialogLambdaCallback<T> extends XulDialogCallback<T> {
  @Override
  default void onError( XulComponent sender, Throwable t ) { }
}
