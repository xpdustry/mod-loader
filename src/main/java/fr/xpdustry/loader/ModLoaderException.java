package fr.xpdustry.loader;

import java.io.Serial;

public class ModLoaderException extends Exception {

  @Serial
  private static final long serialVersionUID = -265108231696587650L;

  public ModLoaderException(String message) {
    super(message);
  }
}
