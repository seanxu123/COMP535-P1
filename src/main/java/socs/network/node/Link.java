package socs.network.node;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Link {

  RouterDescription router1;
  RouterDescription router2;
  int weight;

  Socket socket;
  ObjectOutputStream oos;
  ObjectInputStream ois;

  boolean startedByUs = false;

  public Link(RouterDescription r1, RouterDescription r2, int weight, Socket socket, ObjectOutputStream oos, ObjectInputStream ois) {
    router1 = r1;
    router2 = r2;
    this.weight = weight;
    this.socket = socket;
    this.oos = oos;
    this.ois = ois;
  }
}
