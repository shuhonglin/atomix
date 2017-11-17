/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.primitive.session;

import io.atomix.cluster.NodeId;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.PrimitiveType;
import io.atomix.storage.buffer.HeapBytes;

import java.util.function.Function;

/**
 * Provides an interface to communicating with a client via session events.
 * <p>
 * Sessions represent a connection between a single client and all servers in a Raft cluster. Session information
 * is replicated via the Raft consensus algorithm, and clients can safely switch connections between servers without
 * losing their session. All consistency guarantees are provided within the context of a session. Once a session is
 * expired or closed, linearizability, sequential consistency, and other guarantees for events and operations are
 * effectively lost. Session implementations guarantee linearizability for session messages by coordinating between
 * the client and a single server at any given time. This means messages {@link #publish(PrimitiveEvent) published}
 * via the {@link Session} are guaranteed to arrive on the other side of the connection exactly once and in the order
 * in which they are sent by replicated state machines. In the event of a server-to-client message being lost, the
 * message will be resent so long as at least one Raft server is able to communicate with the client and the client's
 * session does not expire while switching between servers.
 * <p>
 * Messages are sent to the other side of the session using the {@link #publish(PrimitiveEvent)} method:
 * <pre>
 *   {@code
 *     session.publish("myEvent", "Hello world!");
 *   }
 * </pre>
 * When the message is published, it will be queued to be sent to the other side of the connection. Raft guarantees
 * that the message will eventually be received by the client unless the session itself times out or is closed.
 */
public interface Session {

  /**
   * Returns the session identifier.
   *
   * @return The session identifier.
   */
  SessionId sessionId();

  /**
   * Returns the session's service name.
   *
   * @return The session's service name.
   */
  String serviceName();

  /**
   * Returns the session's service type.
   *
   * @return The session's service type.
   */
  PrimitiveType serviceType();

  /**
   * Returns the node identifier to which the session belongs.
   *
   * @return The node to which the session belongs.
   */
  NodeId nodeId();

  /**
   * Returns the session timeout.
   *
   * @return The session timeout.
   */
  @Deprecated
  default long timeout() {
    return maxTimeout();
  }

  /**
   * Returns the minimum session timeout.
   *
   * @return The minimum session timeout.
   */
  long minTimeout();

  /**
   * Returns the maximum session timeout.
   *
   * @return The maximum session timeout.
   */
  long maxTimeout();

  /**
   * Returns the session state.
   *
   * @return The session state.
   */
  State getState();

  /**
   * Adds a state change listener to the session.
   *
   * @param listener the state change listener to add
   */
  void addListener(SessionEventListener listener);

  /**
   * Removes a state change listener from the session.
   *
   * @param listener the state change listener to remove
   */
  void removeListener(SessionEventListener listener);

  /**
   * Publishes an empty event to the session.
   *
   * @param eventType the event type
   */
  default void publish(EventType eventType) {
    publish(new PrimitiveEvent(eventType, HeapBytes.EMPTY));
  }

  /**
   * Publishes an event to the session.
   *
   * @param eventType the event identifier
   * @param encoder the event value encoder
   * @param event the event value
   * @param <T> the event type
   */
  default <T> void publish(EventType eventType, Function<T, byte[]> encoder, T event) {
    publish(eventType, encoder.apply(event));
  }

  /**
   * Publishes an event to the session.
   *
   * @param eventType the event identifier
   * @param event the event to publish
   * @throws NullPointerException if the event is {@code null}
   */
  default void publish(EventType eventType, byte[] event) {
    publish(new PrimitiveEvent(eventType, event));
  }

  /**
   * Publishes an event to the session.
   *
   * @param event the event to publish
   */
  void publish(PrimitiveEvent event);

  /**
   * Session state enums.
   */
  enum State {
    OPEN(true),
    SUSPICIOUS(true),
    EXPIRED(false),
    CLOSED(false);

    private final boolean active;

    State(boolean active) {
      this.active = active;
    }

    public boolean active() {
      return active;
    }
  }

}
